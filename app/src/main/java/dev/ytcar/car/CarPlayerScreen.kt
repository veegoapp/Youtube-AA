package dev.ytcar.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.ytcar.data.VideoItem
import dev.ytcar.player.PlayerHub

/**
 * Full-screen video on the car display.
 */
class CarPlayerScreen(ctx: CarContext, item: VideoItem) : Screen(ctx) {
    private val renderer = VideoSurfaceRenderer(ctx)
    private val handler = Handler(Looper.getMainLooper())

    init {
        PlayerHub.play(item)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                renderer.videoAllowed = true
                invalidate()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacksAndMessages(null)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                renderer.release()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val player = PlayerHub.player
        fun act(title: String, onClick: () -> Unit) =
            Action.Builder().setTitle(title).setOnClickListener(onClick).build()

        val strip = ActionStrip.Builder()
            .addAction(act("Close") { screenManager.pop() })
            .addAction(act(if (player.playWhenReady) "Pause" else "Play") {
                player.playWhenReady = !player.playWhenReady; invalidate()
            })
            .addAction(act("-10s") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) })
            .addAction(act("+10s") { player.seekTo(player.currentPosition + 10_000) })
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setBackgroundColor(CarColor.createCustom(0xFF000000.toInt(), 0xFF000000.toInt()))
            .build()
    }
}
