package dev.ytcar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local watch history. Stored only in this app's private storage on the phone (never sent anywhere,
 * excluded from backups). Newest first, max [MAX] videos. [clear] wipes it.
 */
object History {
    private const val MAX = 200
    private const val KEY = "items"
    private lateinit var sp: SharedPreferences

    val items = MutableStateFlow<List<VideoItem>>(emptyList())

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
        items.value = load()
    }

    fun add(v: VideoItem) {
        items.value = (listOf(v) + items.value.filter { it.url != v.url }).take(MAX)
        save()
    }

    fun clear() {
        items.value = emptyList()
        sp.edit().remove(KEY).apply()
    }

    private fun save() {
        val arr = JSONArray()
        items.value.forEach {
            arr.put(
                JSONObject()
                    .put("url", it.url).put("title", it.title).put("channel", it.channel)
                    .put("channelUrl", it.channelUrl ?: "").put("thumb", it.thumb ?: "")
                    .put("duration", it.durationSec).put("live", it.live)
            )
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<VideoItem> = try {
        val arr = JSONArray(sp.getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            VideoItem(
                url = o.getString("url"),
                title = o.getString("title"),
                channel = o.optString("channel"),
                channelUrl = o.optString("channelUrl").ifEmpty { null },
                thumb = o.optString("thumb").ifEmpty { null },
                durationSec = o.optLong("duration"),
                live = o.optBoolean("live"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
