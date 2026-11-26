package io.github.mstajanc.gros

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mstajanc.gros.data.settings.TextSize
import io.github.mstajanc.gros.domain.LockState
import io.github.mstajanc.gros.ui.GrosApp
import io.github.mstajanc.gros.ui.lock.LockScreen
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.launch

/**
 * Jediná Activity appky; celé rozhranie je v Compose. Dedí z `FragmentActivity`, lebo to vyžaduje
 * `BiometricPrompt` z androidx.biometric 1.1.0.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as GrosApplication).container

    /** Widget požiadal o Novú transakciu; rozhranie ju otvorí (po odomknutí) a príznak zruší. */
    private var newTransactionRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Po otočení obrazovky sa pôvodný intent widgetu znova nespracuje.
        if (savedInstanceState == null) handleIntent(intent)

        // So zapnutým zámkom žiadny náhľad v prehľade úloh ani snímky obrazovky.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settingsRepository.lockEnabled.collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        val settings = container.settingsRepository
        val lock = container.appLock
        setContent {
            val textSize by settings.textSize.collectAsStateWithLifecycle(initialValue = TextSize.STANDARD)
            val lockState by lock.state.collectAsStateWithLifecycle()
            // Pri zamknutí sa obsah appky odstráni z kompozície (nič sa nevykreslí), jeho stav
            // (otvorená obrazovka, rozpísaný formulár) sa však uloží a po odomknutí obnoví.
            val savedState = rememberSaveableStateHolder()
            GrosTheme(textScale = textSize.factor) {
                when (lockState) {
                    LockState.LOADING -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    LockState.LOCKED -> LockScreen(lock)
                    LockState.UNLOCKED -> savedState.SaveableStateProvider(APP_STATE_KEY) {
                        GrosApp(
                            newTransactionRequested = newTransactionRequested,
                            onNewTransactionOpened = { newTransactionRequested = false },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Otočenie obrazovky nie je pobyt na pozadí.
        if (!isChangingConfigurations) container.appLock.onBackground()
    }

    /** Activity je singleTop: widget pri bežiacej appke doručí intent sem. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MainActivity.ACTION_NEW_TRANSACTION) newTransactionRequested = true
    }

    companion object {
        const val ACTION_NEW_TRANSACTION = "io.github.mstajanc.gros.action.NEW_TRANSACTION"
        private const val APP_STATE_KEY = "app"
    }
}
