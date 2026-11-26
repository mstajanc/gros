package io.github.mstajanc.gros.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.AppLock
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.theme.GrosShapes

/**
 * Obrazovka zámku (G4): pozadie appky, logo „G“ a tlačidlo Odomknúť. Systémový dialóg sa ukáže sám;
 * tlačidlo ho vyvolá znova. Obsah appky sa pred overením nevykreslí.
 */
@Composable
fun LockScreen(lock: AppLock) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)

    fun prompt() {
        val activity = context.findFragmentActivity() ?: return
        if (!Biometric.isAvailable(context)) {
            lock.unlockWithoutCredential()
            return
        }
        Biometric.authenticate(activity, title, onSuccess = lock::unlock)
    }

    LaunchedEffect(Unit) { prompt() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = ScreenPadding, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary, GrosShapes.cardLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text("G", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.lock_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        PrimaryButton(stringResource(R.string.lock_unlock), onClick = ::prompt, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
