package io.github.mstajanc.gros.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.GrosApplication
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.PluralForm
import io.github.mstajanc.gros.domain.SlovakText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Akcia snackbaru, ktorá namiesto zmeny dát otvorí obrazovku. */
enum class SnackbarAction {
    /** Transakcie s Celým obdobím (snackbar opakovaných platieb, rozhodnutie A4). */
    SHOW_ALL_TRANSACTIONS,
}

/**
 * Správa v snackbare s nepovinnou akciou Späť ([undo]) alebo Zobraziť ([action]). Posiela sa cez
 * [SnackbarBus], aby prežila zatvorenie obrazovky, ktorá ju vyvolala (napr. formulár po uložení).
 */
class SnackbarEvent(
    @StringRes val message: Int,
    val formatArgs: List<Any> = emptyList(),
    val undo: (suspend () -> Unit)? = null,
    val action: SnackbarAction? = null,
)

/**
 * Fronta snackbarov. Správy sa držia, kým ich nezobrazí rozhranie – aj tie, ktoré vzniknú
 * pred jeho vykreslením (napr. generátor opakovaných platieb pri štarte).
 */
class SnackbarBus {
    private val channel = Channel<SnackbarEvent>(Channel.BUFFERED)
    val events: Flow<SnackbarEvent> = channel.receiveAsFlow()

    fun show(event: SnackbarEvent) {
        channel.trySend(event)
    }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as GrosApplication).container

/** ViewModel s ručne dodanými závislosťami z [AppContainer] a so `SavedStateHandle`. */
@Composable
inline fun <reified VM : ViewModel> grosViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } },
    )
}

/** „1 transakcia“, „3 transakcie“, „5 transakcií“. */
@Composable
fun transactionCountText(count: Int): String = stringResource(
    when (SlovakText.pluralForm(count)) {
        PluralForm.ONE -> R.string.transactions_count_one
        PluralForm.FEW -> R.string.transactions_count_few
        PluralForm.MANY -> R.string.transactions_count_many
    },
    count,
)

/** „3 zo 4 účtov“, „1 z 1 účtu“. */
@Composable
fun accountsOfText(included: Int, active: Int): String = stringResource(
    if (active == 1) R.string.accounts_of_one else R.string.accounts_of_other,
    included,
    SlovakText.prepositionFrom(active),
    active,
)
