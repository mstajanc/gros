package io.github.mstajanc.gros.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.ui.accounts.AccountFormScreen
import io.github.mstajanc.gros.ui.accounts.AccountFormViewModel
import io.github.mstajanc.gros.ui.accounts.AccountsScreen
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SnackbarAction
import io.github.mstajanc.gros.ui.common.appContainer
import io.github.mstajanc.gros.ui.onboarding.OnboardingScreen
import io.github.mstajanc.gros.ui.overview.OverviewScreen
import io.github.mstajanc.gros.ui.plan.PlanScreen
import io.github.mstajanc.gros.ui.plan.RuleFormScreen
import io.github.mstajanc.gros.ui.plan.RuleFormViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.transaction.TransactionFormScreen
import io.github.mstajanc.gros.ui.transaction.TransactionFormViewModel
import io.github.mstajanc.gros.ui.transactions.TransactionsScreen
import io.github.mstajanc.gros.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private object Routes {
    const val OVERVIEW = "overview"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTIONS_PATTERN = "transactions?allPeriod={allPeriod}"
    const val TRANSACTIONS_ALL_PERIOD = "transactions?allPeriod=true"

    /** Pohyby jedného účtu: samostatná obrazovka, nie záložka Transakcie. */
    const val ACCOUNT_TRANSACTIONS_PATTERN = "accountTransactions/{accountId}"
    const val PLAN = "plan"
    const val REPORTS = "reports"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_PATTERN = "account?id={id}"
    const val RULE_PATTERN = "rule?id={id}"
    const val TRANSACTION_PATTERN = "transaction?id={id}&duplicate={duplicate}&type={type}"

    fun transactionsOfAccount(accountId: Long) = "accountTransactions/$accountId"
    fun account(id: Long? = null) = "account?id=${id ?: -1}"
    fun rule(id: Long? = null) = "rule?id=${id ?: -1}"
    fun transaction(id: Long? = null, duplicate: Boolean = false, type: TransactionType? = null) =
        "transaction?id=${id ?: -1}&duplicate=$duplicate&type=${type?.name.orEmpty()}"
}

/** Ako dlho je vidieť snackbar s akciou Späť. */
private const val SNACKBAR_MILLIS = 5_000L

/** Koreň appky: kým neexistuje žiadny účet, beží sprievodca prvým spustením. */
@Composable
fun GrosApp() {
    val container = appContainer()
    val accountCountFlow = remember { container.accountRepository.observeAccountCount() }
    val accountCount by accountCountFlow.collectAsStateWithLifecycle(initialValue = null)

    when (accountCount) {
        null -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        0 -> OnboardingScreen()
        else -> MainScreen()
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    // Formuláre zaberajú celú obrazovku; lišta je len na prehľadových obrazovkách.
    val showBar = !route.startsWith("transaction?") && !route.startsWith("account?") && !route.startsWith("rule?")
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEvents(snackbarHostState) { action ->
        when (action) {
            // Nová položka (nie launchSingleTop), aby sa Transakcie otvorili s Celým obdobím.
            SnackbarAction.SHOW_ALL_TRANSACTIONS -> navController.navigate(Routes.TRANSACTIONS_ALL_PERIOD) {
                popUpTo(navController.graph.findStartDestination().id)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                BottomBar(
                    route = route,
                    onTab = navController::navigateToTab,
                    onAdd = { navController.navigate(Routes.transaction()) },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = GrosShapes.cardSmall,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    contentPadding = padding,
                    onManageAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onAllTransactions = { navController.navigateToTab(Routes.TRANSACTIONS) },
                    onAccount = { navController.navigate(Routes.transactionsOfAccount(it)) },
                    onEdit = { navController.navigate(Routes.transaction(id = it)) },
                )
            }
            composable(
                Routes.TRANSACTIONS_PATTERN,
                arguments = listOf(
                    navArgument(TransactionsViewModel.ARG_ALL_PERIOD) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) {
                TransactionsScreen(
                    contentPadding = padding,
                    onEdit = { navController.navigate(Routes.transaction(id = it)) },
                )
            }
            composable(
                Routes.ACCOUNT_TRANSACTIONS_PATTERN,
                arguments = listOf(navArgument(TransactionsViewModel.ARG_ACCOUNT_ID) { type = NavType.LongType }),
            ) {
                TransactionsScreen(
                    contentPadding = padding,
                    onEdit = { navController.navigate(Routes.transaction(id = it)) },
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.PLAN) {
                PlanScreen(
                    contentPadding = padding,
                    onAddRule = { navController.navigate(Routes.rule()) },
                    onEditRule = { navController.navigate(Routes.rule(it)) },
                )
            }
            composable(
                Routes.RULE_PATTERN,
                arguments = listOf(
                    navArgument(RuleFormViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                RuleFormScreen(contentPadding = padding, onClose = navController::popBackStack)
            }
            composable(Routes.REPORTS) {
                PlaceholderScreen(R.string.nav_reports, R.string.placeholder_reports, padding)
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    contentPadding = padding,
                    onBack = navController::popBackStack,
                    onAdd = { navController.navigate(Routes.account()) },
                    onEdit = { navController.navigate(Routes.account(it)) },
                    onAccount = { navController.navigate(Routes.transactionsOfAccount(it)) },
                    onTransfer = { navController.navigate(Routes.transaction(type = TransactionType.TRANSFER)) },
                )
            }
            composable(
                Routes.ACCOUNT_PATTERN,
                arguments = listOf(
                    navArgument(AccountFormViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                AccountFormScreen(contentPadding = padding, onClose = navController::popBackStack)
            }
            composable(
                Routes.TRANSACTION_PATTERN,
                arguments = listOf(
                    navArgument(TransactionFormViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(TransactionFormViewModel.ARG_DUPLICATE) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument(TransactionFormViewModel.ARG_TYPE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong(TransactionFormViewModel.ARG_ID) ?: -1L
                TransactionFormScreen(
                    onClose = navController::popBackStack,
                    onDuplicate = {
                        // Duplikát nahradí otvorenú úpravu predvyplneným formulárom novej transakcie.
                        navController.navigate(Routes.transaction(id = id, duplicate = true)) {
                            popUpTo(Routes.TRANSACTION_PATTERN) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Prepnutie záložky: zásobník sa vráti na Prehľad a záložka sa otvorí nad ním (Prehľad sám sebou).
 * Stav záložiek sa zámerne neobnovuje: obnovený zásobník Prehľadu vedel vrátiť naposledy otvorenú
 * obrazovku a záložka Prehľad sa potom nedala otvoriť.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@Composable
private fun SnackbarEvents(hostState: SnackbarHostState, onAction: (SnackbarAction) -> Unit) {
    val bus = appContainer().snackbarBus
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(bus, hostState) {
        // Nová správa nahradí predošlú; každá je vidieť 5 sekúnd.
        bus.events.collectLatest { event ->
            val actionLabel = when {
                event.undo != null -> resources.getString(R.string.action_undo)
                event.action != null -> resources.getString(R.string.action_show)
                else -> null
            }
            val result = withTimeoutOrNull(SNACKBAR_MILLIS) {
                hostState.showSnackbar(
                    message = resources.getString(event.message, *event.formatArgs.toTypedArray()),
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                val undo = event.undo
                val action = event.action
                if (undo != null) scope.launch { undo() }
                if (action != null) onAction(action)
            }
        }
    }
}

private class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

private val LEFT_TABS = listOf(
    Tab(Routes.OVERVIEW, R.string.nav_overview, R.drawable.ic_house),
    Tab(Routes.TRANSACTIONS, R.string.nav_transactions, R.drawable.ic_list),
)
private val RIGHT_TABS = listOf(
    Tab(Routes.PLAN, R.string.nav_plan, R.drawable.ic_calendar),
    Tab(Routes.REPORTS, R.string.nav_reports, R.drawable.ic_chart_no_axes_column),
)

/** Spodná lišta: Prehľad · Transakcie · + · Plán · Reporty. */
@Composable
private fun BottomBar(route: String, onTab: (String) -> Unit, onAdd: () -> Unit) {
    // Účty a pohyby účtu sa otvárajú z Prehľadu, preto patria pod jeho záložku.
    val selected = when {
        route.startsWith(Routes.TRANSACTIONS) -> Routes.TRANSACTIONS
        route == Routes.PLAN || route.startsWith("rule?") -> Routes.PLAN
        route == Routes.REPORTS -> Routes.REPORTS
        else -> Routes.OVERVIEW
    }
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LEFT_TABS.forEach { TabItem(it, it.route == selected, onTab, Modifier.weight(1f)) }
            val addLabel = stringResource(R.string.nav_add)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(GrosShapes.cardMedium)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClickLabel = addLabel, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    GrosIcon(R.drawable.ic_plus, addLabel, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            RIGHT_TABS.forEach { TabItem(it, it.route == selected, onTab, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(GrosShapes.cardSmall)
            .selectable(selected = selected, role = Role.Tab) { onTab(tab.route) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        GrosIcon(tab.icon, contentDescription = null, tint = color)
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Dočasný obsah záložiek, ktoré pribudnú v neskorších míľnikoch. */
@Composable
private fun PlaceholderScreen(@StringRes title: Int, @StringRes text: Int, contentPadding: PaddingValues) {
    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(title))
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.semantics { }.fillMaxWidth(),
            )
        }
    }
}
