package dev.ytcar

import android.app.Application
import dev.ytcar.data.OkDownloader
import dev.ytcar.player.PlayerHub
import org.schabi.newpipe.extractor.NewPipe

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkDownloader)
        PlayerHub.init(this)
    }
}
