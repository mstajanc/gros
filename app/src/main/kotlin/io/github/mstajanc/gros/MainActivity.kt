package io.github.mstajanc.gros

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mstajanc.gros.data.settings.TextSize
import io.github.mstajanc.gros.ui.GrosApp
import io.github.mstajanc.gros.ui.theme.GrosTheme

/** Jediná Activity appky; celé rozhranie je v Compose. */
class MainActivity : ComponentActivity() {

    /** Widget požiadal o Novú transakciu; rozhranie ju otvorí a príznak zruší. */
    private var newTransactionRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Po otočení obrazovky sa pôvodný intent widgetu znova nespracuje.
        if (savedInstanceState == null) handleIntent(intent)
        val settings = (application as GrosApplication).container.settingsRepository
        setContent {
            val textSize by settings.textSize.collectAsStateWithLifecycle(initialValue = TextSize.STANDARD)
            GrosTheme(textScale = textSize.factor) {
                GrosApp(
                    newTransactionRequested = newTransactionRequested,
                    onNewTransactionOpened = { newTransactionRequested = false },
                )
            }
        }
    }

    /** Activity je singleTop: widget pri bežiacej appke doručí intent sem. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NEW_TRANSACTION) newTransactionRequested = true
    }

    companion object {
        const val ACTION_NEW_TRANSACTION = "io.github.mstajanc.gros.action.NEW_TRANSACTION"
    }
}
