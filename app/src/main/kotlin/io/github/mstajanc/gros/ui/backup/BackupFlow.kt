package io.github.mstajanc.gros.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.backup.BackupData
import io.github.mstajanc.gros.data.backup.BackupError
import io.github.mstajanc.gros.data.backup.BackupException
import io.github.mstajanc.gros.data.backup.BackupSummary
import io.github.mstajanc.gros.ui.common.SnackbarEvent
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Krok toku zálohy; heslo ani obsah súboru sa nikde neukladajú, žijú len v pamäti ViewModelu. */
sealed interface BackupStep {
    data object Idle : BackupStep
    data object ExportPassword : BackupStep
    data object Exporting : BackupStep
    data class ImportPassword(val error: BackupError? = null) : BackupStep
    data object Opening : BackupStep
    data class ImportConfirm(val summary: BackupSummary) : BackupStep
    data object Restoring : BackupStep

    /** Chyba, ktorá tok ukončí (cudzí súbor, novšia verzia, chyba čítania či zápisu). */
    data class Failed(val message: Int) : BackupStep
}

internal enum class Launch { CREATE, OPEN }

class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _step = MutableStateFlow<BackupStep>(BackupStep.Idle)
    val step: StateFlow<BackupStep> = _step

    private val _launch = Channel<Launch>(Channel.BUFFERED)
    internal val launch = _launch.receiveAsFlow()

    private var exportPassword: CharArray? = null
    private var importFile: ByteArray? = null
    private var opened: BackupData? = null

    fun startExport() {
        _step.value = BackupStep.ExportPassword
    }

    /** Heslo je overené dialógom; teraz si používateľ vyberie, kam zálohu uloží. */
    fun onExportPassword(password: CharArray) {
        exportPassword = password
        _step.value = BackupStep.Idle
        _launch.trySend(Launch.CREATE)
    }

    fun fileName(): String = "gros-${container.today()}.grosbak"

    fun onExportTarget(uri: Uri?) {
        val password = exportPassword ?: return
        exportPassword = null
        if (uri == null) {
            password.fill(' ')
            return
        }
        _step.value = BackupStep.Exporting
        viewModelScope.launch {
            try {
                val bytes = container.backupRepository.export(password)
                withContext(Dispatchers.IO) {
                    container.appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException()
                }
                container.settingsRepository.setLastBackupEpochDay(container.today().toEpochDay())
                _step.value = BackupStep.Idle
                container.snackbarBus.show(SnackbarEvent(R.string.backup_exported))
            } catch (_: IOException) {
                _step.value = BackupStep.Failed(R.string.backup_error_write)
            } catch (_: SecurityException) {
                _step.value = BackupStep.Failed(R.string.backup_error_write)
            } finally {
                password.fill(' ')
            }
        }
    }

    fun startImport() {
        _launch.trySend(Launch.OPEN)
    }

    fun onImportSource(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                importFile = withContext(Dispatchers.IO) {
                    container.appContext.contentResolver.openInputStream(uri)?.use { input ->
                        // Záloha osobných financií má stovky kB; obrovský súbor nie je záloha Groša.
                        input.readNBytes(MAX_FILE_BYTES + 1).also { if (it.size > MAX_FILE_BYTES) throw IOException() }
                    } ?: throw IOException()
                }
                _step.value = BackupStep.ImportPassword()
            } catch (_: IOException) {
                _step.value = BackupStep.Failed(R.string.backup_error_read)
            } catch (_: SecurityException) {
                _step.value = BackupStep.Failed(R.string.backup_error_read)
            }
        }
    }

    fun onImportPassword(password: CharArray) {
        val file = importFile ?: return
        _step.value = BackupStep.Opening
        viewModelScope.launch {
            try {
                val data = container.backupRepository.open(file, password)
                opened = data
                _step.value = BackupStep.ImportConfirm(container.backupRepository.summary(data))
            } catch (e: BackupException) {
                _step.value = when (e.error) {
                    BackupError.WRONG_PASSWORD_OR_CORRUPTED -> BackupStep.ImportPassword(e.error)
                    else -> {
                        clear()
                        BackupStep.Failed(e.error.message())
                    }
                }
            } finally {
                password.fill(' ')
            }
        }
    }

    fun confirmImport() {
        val data = opened ?: return
        _step.value = BackupStep.Restoring
        viewModelScope.launch {
            try {
                container.restoreBackup(data)
                _step.value = BackupStep.Idle
                container.snackbarBus.show(SnackbarEvent(R.string.backup_restored))
            } catch (e: BackupException) {
                _step.value = BackupStep.Failed(e.error.message())
            } finally {
                clear()
            }
        }
    }

    fun cancel() {
        exportPassword?.fill(' ')
        exportPassword = null
        clear()
        _step.value = BackupStep.Idle
    }

    private fun clear() {
        importFile = null
        opened = null
    }

    override fun onCleared() {
        exportPassword?.fill(' ')
    }

    private companion object {
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}

/** Hlásenia chýb importu (G6). */
fun BackupError.message(): Int = when (this) {
    BackupError.NOT_A_BACKUP -> R.string.backup_error_not_backup
    BackupError.UNSUPPORTED_FORMAT, BackupError.NEWER_SCHEMA -> R.string.backup_error_newer
    BackupError.WRONG_PASSWORD_OR_CORRUPTED -> R.string.backup_error_password
    BackupError.INVALID_CONTENT -> R.string.backup_error_invalid
}

/** Ovládanie toku zálohy pre obrazovku, ktorá ho spúšťa (Nastavenia, sprievodca). */
class BackupActions(val export: () -> Unit, val import: () -> Unit)

/**
 * Dialógy a systémové výbery súboru toku zálohy. Pred otvorením výberu sa zámok upozorní,
 * aby sa návrat z neho nepočítal ako pobyt na pozadí.
 */
@Composable
fun rememberBackupFlow(): BackupActions {
    val viewModel = grosViewModel { container, _ -> BackupViewModel(container) }
    val container = io.github.mstajanc.gros.ui.common.appContainer()
    val step by viewModel.step.collectAsStateWithLifecycle()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        viewModel::onExportTarget,
    )
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onImportSource)

    LaunchedEffect(viewModel) {
        viewModel.launch.collect { launch ->
            container.appLock.beginExternalActivity()
            when (launch) {
                Launch.CREATE -> createLauncher.launch(viewModel.fileName())
                Launch.OPEN -> openLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    when (val current = step) {
        BackupStep.Idle -> Unit
        BackupStep.ExportPassword -> ExportPasswordDialog(onConfirm = viewModel::onExportPassword, onDismiss = viewModel::cancel)
        BackupStep.Exporting -> ProgressDialog(stringResource(R.string.backup_exporting))
        is BackupStep.ImportPassword -> ImportPasswordDialog(
            error = current.error,
            onConfirm = viewModel::onImportPassword,
            onDismiss = viewModel::cancel,
        )
        BackupStep.Opening -> ProgressDialog(stringResource(R.string.backup_opening))
        is BackupStep.ImportConfirm -> InfoDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            text = stringResource(R.string.backup_import_confirm_text, current.summary.accounts, current.summary.transactions),
            confirmLabel = stringResource(R.string.backup_import_confirm),
            destructive = true,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancel,
        )
        BackupStep.Restoring -> ProgressDialog(stringResource(R.string.backup_restoring))
        is BackupStep.Failed -> InfoDialog(
            title = stringResource(R.string.backup_error_title),
            text = stringResource(current.message),
            confirmLabel = stringResource(R.string.action_close),
            onConfirm = viewModel::cancel,
            onDismiss = viewModel::cancel,
            showCancel = false,
        )
    }

    return remember(viewModel) { BackupActions(export = viewModel::startExport, import = viewModel::startImport) }
}

@Composable
private fun ExportPasswordDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val longEnough = first.length >= MIN_PASSWORD_LENGTH
    val matches = first == second
    PasswordDialog(
        title = stringResource(R.string.backup_export_title),
        confirmLabel = stringResource(R.string.action_continue),
        confirmEnabled = longEnough && matches,
        onConfirm = { onConfirm(first.toCharArray()) },
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.backup_export_warning), style = MaterialTheme.typography.bodyMedium)
        PasswordField(first, { first = it }, stringResource(R.string.backup_password))
        PasswordField(second, { second = it }, stringResource(R.string.backup_password_again))
        val hint = when {
            first.isNotEmpty() && !longEnough -> stringResource(R.string.backup_password_short, MIN_PASSWORD_LENGTH)
            second.isNotEmpty() && !matches -> stringResource(R.string.backup_password_mismatch)
            else -> null
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = GrosTheme.colors.warning)
    }
}

@Composable
private fun ImportPasswordDialog(error: BackupError?, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    PasswordDialog(
        title = stringResource(R.string.backup_import_title),
        confirmLabel = stringResource(R.string.action_continue),
        confirmEnabled = password.isNotEmpty(),
        onConfirm = { onConfirm(password.toCharArray()) },
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.backup_import_text), style = MaterialTheme.typography.bodyMedium)
        PasswordField(password, { password = it }, stringResource(R.string.backup_password))
        if (error != null) {
            Text(stringResource(error.message()), style = MaterialTheme.typography.bodyMedium, color = GrosTheme.colors.warning)
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmLabel,
                    color = if (confirmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = GrosShapes.cardLarge,
        // Ťuknutie mimo dialógu nesmie omylom zahodiť rozpísané heslo.
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
        shape = GrosShapes.cardSmall,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = GrosShapes.cardLarge,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

@Composable
private fun InfoDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    showCancel: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) GrosTheme.colors.warning else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = if (showCancel) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        } else {
            null
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = GrosShapes.cardLarge,
    )
}

private const val MIN_PASSWORD_LENGTH = 10
