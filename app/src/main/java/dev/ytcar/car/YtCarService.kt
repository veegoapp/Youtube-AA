package dev.ytcar.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class YtCarService : CarAppService() {
    // Sideloaded app: Android Auto only lists it when "Unknown sources" is on, so any host is accepted.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = HomeScreen(carContext)
    }
}
