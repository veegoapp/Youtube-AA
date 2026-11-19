package dev.ytcar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytcar.data.Entry
import dev.ytcar.data.PageResult
import dev.ytcar.data.VideoItem
import dev.ytcar.data.YtRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Paged, observable list of entries. */
class ListState(private val scope: CoroutineScope, private val first: suspend () -> PageResult) {
    var items by mutableStateOf<List<Entry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var more: (suspend () -> PageResult)? = null
    private var started = false

    fun start() { if (!started) reload() }

    fun reload() {
        started = true
        scope.launch { run(replace = true) { first() } }
    }

    fun loadMore() {
        val m = more ?: return
        if (loading) return
        scope.launch { run(replace = false) { m() } }
    }

    private suspend fun run(replace: Boolean, block: suspend () -> PageResult) {
        loading = true; error = null
        try {
            val r = block()
            items = if (replace) r.items else items + r.items
            more = r.loadMore
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            more = null
        } finally { loading = false }
    }
}

sealed class Route {
    data object Home : Route()
    class Search(scope: CoroutineScope) : Route() {
        var query by mutableStateOf("")
        var list by mutableStateOf<ListState?>(null)
        private val s = scope
        fun submit() {
            val q = query.trim()
            if (q.isEmpty()) return
            list = ListState(s) { YtRepo.search(q) }.also { it.start() }
        }
    }
    class Channel(val url: String, val name: String, scope: CoroutineScope) : Route() {
        var header by mutableStateOf<YtRepo.ChannelPage?>(null)
        var list: ListState = ListState(scope) {
            val page = YtRepo.channel(url)
            header = page
            page.videos
        }
    }
    data object HistoryList : Route()
    class Player(val item: VideoItem) : Route()
}

class MainViewModel : ViewModel() {
    val stack = mutableStateListOf<Route>(Route.Home)
    val home = ListState(viewModelScope) { YtRepo.home() }

    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false

    fun openSearch() = push(Route.Search(viewModelScope))
    fun openChannel(url: String, name: String) = push(Route.Channel(url, name, viewModelScope))
    fun openVideo(v: VideoItem) = push(Route.Player(v))
    fun openHistory() = push(Route.HistoryList)
    fun open(e: Entry) = when (e) {
        is Entry.Video -> openVideo(e.item)
        is Entry.Channel -> openChannel(e.item.url, e.item.name)
    }
}
