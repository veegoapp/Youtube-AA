package dev.ytcar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumb: String?,
    val durationSec: Long,
    val live: Boolean,
)

data class ChannelItem(val url: String, val name: String, val avatar: String?, val subscribers: Long)

sealed interface Entry {
    data class Video(val item: VideoItem) : Entry
    data class Channel(val item: ChannelItem) : Entry
}

/** One page of results plus (optionally) a function that loads the next page. */
class PageResult(val items: List<Entry>, val loadMore: (suspend () -> PageResult)? = null)

object YtRepo {
    private val yt get() = ServiceList.YouTube

    private fun List<Image>.best(): String? =
        (filter { it.width >= 300 }.minByOrNull { it.width } ?: maxByOrNull { it.width })?.url

    private fun toEntry(i: InfoItem, fallbackChannel: String? = null): Entry? = when (i) {
        is StreamInfoItem -> Entry.Video(
            VideoItem(
                url = i.url,
                title = i.name,
                channel = i.uploaderName?.takeIf { it.isNotBlank() } ?: fallbackChannel.orEmpty(),
                channelUrl = i.uploaderUrl,
                thumb = i.thumbnails.best(),
                durationSec = i.duration,
                live = i.streamType == StreamType.LIVE_STREAM,
            )
        )
        is ChannelInfoItem -> Entry.Channel(
            ChannelItem(i.url, i.name, i.thumbnails.best(), i.subscriberCount)
        )
        else -> null
    }

    private fun <T : InfoItem> pageOf(
        page: ListExtractor.InfoItemsPage<T>,
        fallbackChannel: String? = null,
        next: (suspend (Page) -> ListExtractor.InfoItemsPage<T>)?,
    ): PageResult {
        val items = page.items.mapNotNull { toEntry(it, fallbackChannel) }
        val np = page.nextPage
        val more: (suspend () -> PageResult)? =
            if (next != null && page.hasNextPage() && Page.isValid(np)) {
                { withContext(Dispatchers.IO) { pageOf(next(np!!), fallbackChannel, next) } }
            } else null
        return PageResult(items, more)
    }

    /** Home feed: the service's default kiosk (Trending), falling back to a generic search. */
    suspend fun home(): PageResult = withContext(Dispatchers.IO) {
        try {
            val ex = yt.kioskList.defaultKioskExtractor
            ex.fetchPage()
            val p = pageOf(ex.initialPage, null) { page ->
                val e = yt.kioskList.getDefaultKioskExtractor(page)
                e.fetchPage()
                e.initialPage
            }
            val f = withoutLive(p)
            if (f.items.isEmpty()) throw IllegalStateException("empty kiosk")
            f
        } catch (e: Exception) {
            withoutLive(searchBlocking("popular videos"))
        }
    }

    /** Drops live streams from a page (the default kiosk is mostly live streams now); keeps paging. */
    private fun withoutLive(p: PageResult): PageResult = PageResult(
        p.items.filterNot { it is Entry.Video && it.item.live },
        p.loadMore?.let { more -> { withContext(Dispatchers.IO) { withoutLive(more()) } } },
    )

    suspend fun search(query: String): PageResult = withContext(Dispatchers.IO) { searchBlocking(query) }

    private fun searchBlocking(query: String): PageResult {
        val handler = yt.searchQHFactory.fromQuery(query, listOf("all"), "")
        val info = SearchInfo.getInfo(yt, handler)
        val first = ListExtractor.InfoItemsPage<InfoItem>(
            info.relatedItems, info.nextPage, info.errors
        )
        return pageOf(first, null) { page -> SearchInfo.getMoreItems(yt, handler, page) }
    }

    class ChannelPage(val channel: ChannelItem, val description: String?, val videos: PageResult)

    suspend fun channel(url: String): ChannelPage = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val tab: ListLinkHandler? =
            info.tabs.firstOrNull { it.contentFilters.contains("videos") } ?: info.tabs.firstOrNull()
        val videos = if (tab == null) PageResult(emptyList()) else {
            val ti = ChannelTabInfo.getInfo(yt, tab)
            val first = ListExtractor.InfoItemsPage<InfoItem>(ti.relatedItems, ti.nextPage, ti.errors)
            pageOf(first, info.name) { page -> ChannelTabInfo.getMoreItems(yt, tab, page) }
        }
        ChannelPage(
            ChannelItem(info.url, info.name, info.avatars.best(), info.subscriberCount),
            info.description,
            videos,
        )
    }

    private val VIDEO_URL = Regex("^https://(?:www\\.|m\\.)?youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})(?:&.*)?$")
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** Returns the canonical watch URL for a video id / watch URL, or null for anything else. */
    fun canonicalVideoUrl(input: String): String? {
        val id = VIDEO_URL.matchEntire(input)?.groupValues?.get(1)
            ?: input.takeIf { VIDEO_ID.matches(it) }
            ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }

    suspend fun streamInfo(url: String): StreamInfo {
        val safe = canonicalVideoUrl(url) ?: throw IllegalArgumentException("Invalid video URL")
        return withContext(Dispatchers.IO) { StreamInfo.getInfo(yt, safe) }
    }
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
