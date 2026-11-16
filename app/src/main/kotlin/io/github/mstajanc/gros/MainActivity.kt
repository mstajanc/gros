package io.github.mstajanc.gros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mstajanc.gros.ui.preview.ThemePreviewScreen
import io.github.mstajanc.gros.ui.theme.GrosTheme

/** Jediná Activity appky. V M1 ukazuje len dočasnú vzorku témy. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as GrosApplication).container
        setContent {
            GrosTheme {
                val accountCountFlow = remember { container.accountRepository.observeAccountCount() }
                val accountCount by accountCountFlow.collectAsStateWithLifecycle(initialValue = null)
                ThemePreviewScreen(accountCount)
            }
        }
    }
}
