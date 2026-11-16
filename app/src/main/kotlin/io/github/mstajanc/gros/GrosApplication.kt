package io.github.mstajanc.gros

import android.app.Application

class GrosApplication : Application() {

    /** Ručné DI: jediný kontajner závislostí na celý proces. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
