package dev.ytcar.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.ytcar.data.OkDownloader
import dev.ytcar.net.Net
import dev.ytcar.data.VideoItem
import dev.ytcar.data.YtRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

sealed interface Status {
    data object Idle : Status
    data object Loading : Status
    data object Ready : Status
    data class Error(val message: String) : Status
}

/**
 * Single process-wide ExoPlayer shared by the phone UI, the Android Auto media browser
 * (audio) and the Android Auto video screen.
 */
object PlayerHub {
    const val MAX_HEIGHT = 720

    private lateinit var appCtx: Context
    lateinit var player: ExoPlayer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var controller: ListenableFuture<MediaController>? = null

    val current = MutableStateFlow<VideoItem?>(null)
    val status = MutableStateFlow<Status>(Status.Idle)

    /**
     * HTTP(S)-only data source on the host-policy OkHttp client. Unlike DefaultDataSource it has no
     * file://, content://, asset:// or rtmp:// support, so a media URI can never read local data,
     * and every request/redirect is checked against [dev.ytcar.net.HostPolicy].
     */
    val dataSourceFactory: DataSource.Factory by lazy {
        OkHttpDataSource.Factory(Net.client).setUserAgent(OkDownloader.USER_AGENT)
    }

    fun init(ctx: Context) {
        if (::player.isInitialized) return
        appCtx = ctx.applicationContext
        player = ExoPlayer.Builder(appCtx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status.value = Status.Ready
            }

            override fun onPlayerError(error: PlaybackException) {
                status.value = Status.Error(error.message ?: error.errorCodeName)
            }
        })
    }

    /** Binds to the media session service so the OS keeps it alive (notification, background audio). */
    private fun ensureSession() {
        if (controller != null) return
        controller = MediaController.Builder(
            appCtx, SessionToken(appCtx, ComponentName(appCtx, PlaybackService::class.java))
        ).buildAsync()
    }

    fun play(item: VideoItem) {
        if (current.value?.url == item.url && status.value !is Status.Error && player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = true
            return
        }
        job?.cancel()
        current.value = item
        status.value = Status.Loading
        ensureSession()
        job = scope.launch {
            try {
                val info = YtRepo.streamInfo(item.url)
                player.setMediaSource(buildSource(info, item))
                player.prepare()
                player.playWhenReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status.value = Status.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // ---- stream selection -------------------------------------------------------------

    private val AVC_ITAGS = setOf(133, 134, 135, 136, 137, 160, 298, 299, 18, 22)

    private fun metadata(item: VideoItem) = MediaMetadata.Builder()
        .setTitle(item.title)
        .setArtist(item.channel)
        .setArtworkUri(item.thumb?.toUri())
        .build()

    private fun mediaItem(url: String, item: VideoItem) = MediaItem.Builder()
        .setUri(url)
        .setMediaId(item.url)
        .setMediaMetadata(metadata(item))
        .build()

    private fun progressive(url: String, item: VideoItem): MediaSource =
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem(url, item))

    private fun pickAudio(info: StreamInfo): AudioStream? {
        val all = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val m4a = all.filter { it.format == MediaFormat.M4A }
        return (m4a.ifEmpty { all }).maxByOrNull { it.averageBitrate }
    }

    private fun pickVideo(list: List<VideoStream>): VideoStream? {
        val ok = list.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val fit = ok.filter { it.height in 1..MAX_HEIGHT }.ifEmpty { ok.sortedBy { it.height }.take(1) }
        val avc = fit.filter { it.itag in AVC_ITAGS }.ifEmpty { fit.filter { it.format == MediaFormat.MPEG_4 } }
        return (avc.ifEmpty { fit }).maxWithOrNull(compareBy({ it.height }, { it.bitrate }))
    }

    private fun buildSource(info: StreamInfo, item: VideoItem): MediaSource {
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live && !info.hlsUrl.isNullOrEmpty()) {
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem(info.hlsUrl, item))
        }
        val audio = pickAudio(info)
        val videoOnly = pickVideo(info.videoOnlyStreams)
        if (audio != null && videoOnly != null) {
            return MergingMediaSource(progressive(videoOnly.content, item), progressive(audio.content, item))
        }
        pickVideo(info.videoStreams)?.let { return progressive(it.content, item) }
        audio?.let { return progressive(it.content, item) }
        error("No playable stream found for this video")
    }

    /**
     * For the Android Auto media browser: resolve a browse item to a playable audio-only item.
     * Only the media id is trusted and it must be a YouTube video id/URL. Any URI, metadata or
     * artwork supplied by the caller is discarded and rebuilt from YouTube's own response.
     */
    suspend fun resolveAudio(item: MediaItem): MediaItem {
        val url = YtRepo.canonicalVideoUrl(item.mediaId) ?: throw IllegalArgumentException("Invalid media id")
        val info = YtRepo.streamInfo(url)
        val audio = pickAudio(info) ?: error("No audio stream")
        current.value = null
        val thumb = info.thumbnails.maxByOrNull { it.width }?.url
        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(audio.content)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(info.name).setArtist(info.uploaderName)
                    .setArtworkUri(thumb?.toUri()).setIsPlayable(true).setIsBrowsable(false).build()
            ).build()
    }
}
