package io.github.mstajanc.gros.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.settings.TextSize
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.ui.backup.rememberBackupFlow
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.ConfirmDialog
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SwitchRow
import io.github.mstajanc.gros.ui.lock.Biometric
import io.github.mstajanc.gros.ui.lock.findFragmentActivity
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository

    val textSize: StateFlow<TextSize> =
        settings.textSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TextSize.STANDARD)

    fun setTextSize(size: TextSize) {
        viewModelScope.launch { settings.setTextSize(size) }
    }

    val lockEnabled: StateFlow<Boolean> =
        settings.lockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Zapína sa až po úspešnom overení (G5); vypína sa hneď. */
    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockEnabled(enabled) }
    }

    val lastBackup: StateFlow<LocalDate?> = settings.lastBackupEpochDay
        .map { day -> day?.let(LocalDate::ofEpochDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminderEnabled: StateFlow<Boolean> =
        settings.backupReminderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBackupReminderEnabled(enabled) }
    }
}

/** Nastavenia: kategórie, vzhľad, zabezpečenie, záloha a o aplikácii. */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onBack: () -> Unit, onCategories: () -> Unit) {
    val viewModel = grosViewModel { container, _ -> SettingsViewModel(container) }
    val textSize by viewModel.textSize.collectAsStateWithLifecycle()
    val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val backup = rememberBackupFlow()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.lock_enable_prompt)
    var noCredential by rememberSaveable { mutableStateOf(false) }

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(R.string.settings_title), onBack = onBack)

            SettingsRow(
                title = stringResource(R.string.settings_categories),
                subtitle = stringResource(R.string.settings_categories_hint),
                onClick = onCategories,
            )

            SectionTitle(stringResource(R.string.settings_appearance))
            Text(
                stringResource(R.string.settings_text_size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.settings_text_small),
                    stringResource(R.string.settings_text_standard),
                    stringResource(R.string.settings_text_large),
                ),
                selectedIndex = textSize.ordinal,
                onSelect = { viewModel.setTextSize(TextSize.entries[it]) },
            )

            SectionTitle(stringResource(R.string.settings_security))
            SwitchRow(
                label = stringResource(R.string.settings_lock),
                checked = lockEnabled,
                onCheckedChange = { enable ->
                    when {
                        !enable -> viewModel.setLockEnabled(false)
                        !Biometric.isAvailable(context) -> noCredential = true
                        else -> context.findFragmentActivity()?.let { activity ->
                            Biometric.authenticate(activity, promptTitle, onSuccess = { viewModel.setLockEnabled(true) })
                        }
                    }
                },
            )
            Hint(stringResource(R.string.settings_lock_hint))

            SectionTitle(stringResource(R.string.settings_backup))
            Hint(
                lastBackup?.let { stringResource(R.string.settings_last_backup, SlovakDates.fullDate(it)) }
                    ?: stringResource(R.string.settings_no_backup),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    stringResource(R.string.settings_export),
                    onClick = backup.export,
                    modifier = Modifier.weight(1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                SecondaryButton(stringResource(R.string.settings_import), onClick = backup.import, modifier = Modifier.weight(1f))
            }
            Hint(stringResource(R.string.settings_backup_hint))
            SwitchRow(
                label = stringResource(R.string.settings_backup_reminder),
                checked = reminderEnabled,
                onCheckedChange = viewModel::setReminderEnabled,
            )

            SectionTitle(stringResource(R.string.settings_about))
            AboutCard()
        }
    }

    if (noCredential) {
        ConfirmDialog(
            title = stringResource(R.string.settings_lock),
            text = stringResource(R.string.settings_lock_unavailable),
            confirmLabel = stringResource(R.string.action_close),
            onConfirm = { noCredential = false },
            onDismiss = { noCredential = false },
            destructive = false,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(GrosShapes.cardMedium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GrosIcon(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, GrosShapes.cardMedium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.titleMedium)
        listOf(
            R.string.settings_about_offline,
            R.string.settings_about_license,
            R.string.settings_about_third_party,
        ).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
