package io.github.mstajanc.gros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mstajanc.gros.ui.GrosApp
import io.github.mstajanc.gros.ui.theme.GrosTheme

/** Jediná Activity appky; celé rozhranie je v Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GrosTheme {
                GrosApp()
            }
        }
    }
}
