package io.github.mstajanc.gros

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrosApplication : Application() {

    /** Ručné DI: jediný kontajner závislostí na celý proces. */
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            // Pripomienka zálohy bez akejkoľvek zálohy sa ráta od prvého spustenia (B12, G7).
            container.settingsRepository.recordFirstLaunch(container.today().toEpochDay())
            container.settingsRepository.lockEnabled.collect(container.appLock::onEnabledChanged)
        }
        // Generátor opakovaných platieb beží pri štarte appky a pri každom návrate do popredia.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appScope.launch { container.runRecurring() }
                }
            },
        )
    }
}
