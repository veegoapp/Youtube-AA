package dev.ytcar.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dev.ytcar.R

class YtCarService : CarAppService() {
    // Only the genuine Android Auto host (package + signing-cert SHA-256) may bind to this service.
    override fun createHostValidator(): HostValidator =
        HostValidator.Builder(applicationContext).addAllowedHosts(R.array.hosts_allowlist).build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = HomeScreen(carContext)
    }
}
