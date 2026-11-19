package dev.ytcar.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.ytcar.data.Entry
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.MoreExecutors
import dev.ytcar.net.Net
import dev.ytcar.security.TrustedCallers
import java.util.concurrent.Executors
import dev.ytcar.data.VideoItem
import dev.ytcar.data.YtRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media3 session service. Gives Android Auto's native media UI a browse tree (Trending) and
 * voice/text search, and keeps audio playing in the background / while driving.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val searchCache = HashMap<String, List<MediaItem>>()

    override fun onCreate() {
        super.onCreate()
        PlayerHub.init(this)
        session = MediaLibrarySession.Builder(this, PlayerHub.player, Callback())
            // Notification artwork must also go through the host-policy client.
            .setBitmapLoader(
                DataSourceBitmapLoader(
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    OkHttpDataSource.Factory(Net.client),
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!PlayerHub.player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun <T> futureOf(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch {
            try { f.set(block()) } catch (t: Throwable) { f.setException(t) }
        }
        return f
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
        ).build()

    private fun video(v: VideoItem) = MediaItem.Builder()
        .setMediaId(v.url)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(v.title).setArtist(v.channel)
                .setArtworkUri(v.thumb?.toUri())
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build()
        ).build()

    private fun List<Entry>.asMedia(): List<MediaItem> =
        filterIsInstance<Entry.Video>().map { video(it.item) }

    private inner class Callback : MediaLibrarySession.Callback {
        /** Only Android Auto, the platform, and this app itself may connect. Everyone else is refused. */
        override fun onConnect(
            session: MediaSession, controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (TrustedCallers.isTrusted(this@PlaybackService, controller))
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            else MediaSession.ConnectionResult.reject()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?,
        ) = Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "YouTube"), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureOf {
            val items = when (parentId) {
                ROOT -> listOf(folder(TRENDING, "Trending"))
                TRENDING -> YtRepo.home().items.asMedia()
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            if (query.isBlank() || query.length > 200) return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            scope.launch {
                if (searchCache.size > 20) searchCache.clear()
                val r = try { YtRepo.search(query).items.asMedia() } catch (e: Exception) { emptyList() }
                searchCache[query] = r
                session.notifySearchResultChanged(browser, query, r.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val all = searchCache[query].orEmpty()
            if (pageSize <= 0 || page < 0) return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = futureOf {
            mediaItems.map { PlayerHub.resolveAudio(it) }.toMutableList()
        }
    }

    private companion object {
        const val ROOT = "root"
        const val TRENDING = "trending"
    }
}
