package dev.ytcar

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.ytcar.net.Net
import dev.ytcar.data.OkDownloader
import dev.ytcar.player.PlayerHub
import org.schabi.newpipe.extractor.NewPipe

class App : Application(), ImageLoaderFactory {
    // Thumbnails/avatars use the same host-policy HTTP client as everything else.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).okHttpClient(Net.client).build()

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkDownloader)
        PlayerHub.init(this)
    }
}
