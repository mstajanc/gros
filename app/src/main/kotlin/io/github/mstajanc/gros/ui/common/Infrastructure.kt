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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Správa v snackbare s nepovinnou akciou Späť. Posiela sa cez [SnackbarBus], aby prežila
 * zatvorenie obrazovky, ktorá ju vyvolala (napr. formulár po uložení).
 */
class SnackbarEvent(
    @StringRes val message: Int,
    val undo: (suspend () -> Unit)? = null,
)

class SnackbarBus {
    private val _events = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SnackbarEvent> = _events

    fun show(event: SnackbarEvent) {
        _events.tryEmit(event)
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
