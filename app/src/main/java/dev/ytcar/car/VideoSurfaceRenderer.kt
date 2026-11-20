package dev.ytcar.car

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.media3.ui.PlayerView
import dev.ytcar.R
import dev.ytcar.player.PlayerHub

/**
 * Receives the Android Auto surface, mirrors a virtual display onto it, and hosts a letterboxing
 * PlayerView inside a Presentation on that virtual display.
 */
class VideoSurfaceRenderer(private val ctx: CarContext) : SurfaceCallback {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var view: PlayerView? = null

    /** Video playback is allowed continuously so the picture stays visible while moving. */
    var videoAllowed = true
        set(value) {
            field = true
            apply()
        }

    private fun apply() {
        view?.player = if (videoAllowed) PlayerHub.player else null
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        release()
        val surface = surfaceContainer.surface ?: return
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "ytcar-video", surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        ) ?: return
        display = vd
        presentation = object : Presentation(ctx, vd.display) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.car_player)
                view = findViewById(R.id.player)
                apply()
            }
        }.also { it.show() }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = release()

    fun release() {
        view?.player = null
        view = null
        presentation?.dismiss()
        presentation = null
        display?.release()
        display = null
    }
}
