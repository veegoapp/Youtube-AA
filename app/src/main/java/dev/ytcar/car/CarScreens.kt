package dev.ytcar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.coroutineScope
import dev.ytcar.data.ChannelItem
import dev.ytcar.data.Entry
import dev.ytcar.data.VideoItem
import dev.ytcar.data.YtRepo
import dev.ytcar.data.formatDuration
import kotlinx.coroutines.launch

/** Shared helper: builds an item list of videos/channels that respects the host's row limit. */
private fun CarContext.limit(): Int =
    getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

private fun Screen.entryList(entries: List<Entry>, empty: String): ItemList {
    val b = ItemList.Builder().setNoItemsMessage(empty)
    entries.take(carContext.limit()).forEach { e ->
        when (e) {
            is Entry.Video -> b.addItem(
                Row.Builder().setTitle(e.item.title)
                    .addText(listOf(e.item.channel, if (e.item.live) "LIVE" else formatDuration(e.item.durationSec))
                        .filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { " " })
                    .setOnClickListener { screenManager.push(CarPlayerScreen(carContext, e.item)) }
                    .build()
            )
            is Entry.Channel -> b.addItem(
                Row.Builder().setTitle(e.item.name).addText("Channel")
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(ChannelScreen(carContext, e.item)) }
                    .build()
            )
        }
    }
    return b.build()
}

class HomeScreen(ctx: CarContext) : Screen(ctx) {
    private var entries: List<Entry>? = null
    private var error: String? = null

    init {
        lifecycle.coroutineScope.launch {
            try { entries = YtRepo.home().items } catch (e: Exception) { error = e.message ?: "Failed to load" }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle("YouTube").setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder().setTitle("Search")
                        .setOnClickListener { screenManager.push(CarSearchScreen(carContext)) }.build()
                ).build()
            )
        val e = entries
        if (e == null && error == null) b.setLoading(true)
        else b.setSingleList(entryList(e.orEmpty(), error ?: "Nothing here"))
        return b.build()
    }
}

class CarSearchScreen(ctx: CarContext) : Screen(ctx) {
    private var results: List<Entry>? = null
    private var loading = false
    private var error: String? = null

    private fun run(q: String) {
        if (q.isBlank()) return
        loading = true; error = null; invalidate()
        lifecycle.coroutineScope.launch {
            try { results = YtRepo.search(q).items } catch (e: Exception) { results = null; error = e.message ?: "Search failed" }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) = run(searchText)
        }).setHeaderAction(Action.BACK).setSearchHint("Search YouTube").setShowKeyboardByDefault(true)
        if (loading) b.setLoading(true)
        else results?.let { b.setItemList(entryList(it, "No results")) }
        error?.let { b.setItemList(ItemList.Builder().setNoItemsMessage(it).build()) }
        return b.build()
    }
}

class ChannelScreen(ctx: CarContext, private val channel: ChannelItem) : Screen(ctx) {
    private var entries: List<Entry>? = null
    private var error: String? = null

    init {
        lifecycle.coroutineScope.launch {
            try { entries = YtRepo.channel(channel.url).videos.items } catch (e: Exception) { error = e.message ?: "Failed to load" }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle(channel.name).setHeaderAction(Action.BACK)
        val e = entries
        if (e == null && error == null) b.setLoading(true)
        else b.setSingleList(entryList(e.orEmpty(), error ?: "No videos"))
        return b.build()
    }
}
