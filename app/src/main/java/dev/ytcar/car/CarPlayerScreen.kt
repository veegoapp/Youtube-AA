package dev.ytcar.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.ytcar.data.VideoItem
import dev.ytcar.player.PlayerHub

/**
 * Full-screen video on the car display.
 *
 * Safety: the picture is shown ONLY while the car reports a speed of 0 for [STILL_MS]. As soon as
 * the car moves (or reports no speed at all) the video is detached and playback continues as
 * audio only. There is deliberately no override.
 */
class CarPlayerScreen(ctx: CarContext, item: VideoItem) : Screen(ctx) {
    private val renderer = VideoSurfaceRenderer(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private var stillSince = 0L
    private var speedKnown = false
    private var allowed = false

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val v = speed.displaySpeedMetersPerSecond.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value
            ?: speed.rawSpeedMetersPerSecond.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value
        speedKnown = v != null
        if (v == null || v > MOVING_MPS) {
            stillSince = 0L
        } else if (stillSince == 0L) {
            stillSince = SystemClock.elapsedRealtime()
            handler.postDelayed(::evaluate, STILL_MS + 100)
        }
        evaluate()
    }

    init {
        PlayerHub.play(item)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                try {
                    val hw = carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
                    hw.carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
                } catch (e: Exception) { // speed API unavailable -> stay audio-only
                    speedKnown = false
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacksAndMessages(null)
                try {
                    (carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager)
                        .carInfo.removeSpeedListener(speedListener)
                } catch (_: Exception) {}
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                renderer.release()
            }
        })
    }

    private fun evaluate() {
        val now = stillSince != 0L && SystemClock.elapsedRealtime() - stillSince >= STILL_MS
        if (now != allowed) {
            allowed = now
            renderer.videoAllowed = now
            invalidate()
        }
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

        val b = NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setBackgroundColor(CarColor.createCustom(0xFF000000.toInt(), 0xFF000000.toInt()))
        if (!allowed) {
            b.setNavigationInfo(
                MessageInfo.Builder(
                    if (speedKnown) "Audio only while the car is moving" else "Audio only (waiting for car speed)"
                ).build()
            )
        }
        return b.build()
    }

    private companion object {
        const val MOVING_MPS = 0.3f
        const val STILL_MS = 3000L
    }
}
