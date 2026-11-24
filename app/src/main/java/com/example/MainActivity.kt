package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ctrader.CTraderOAuthManager
import com.example.data.repository.TradeHubRepository
import com.example.market.MarketSimulator
import com.example.ui.auth.BiometricHelper
import com.example.ui.components.AddAccountDialog
import com.example.ui.components.TradeConfirmationDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.CTraderAuthScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TradeScreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DeepSpaceBg
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TradeHubTheme
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.TradeHubViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(DeepSpaceBg.toArgb()),
        )
        setContent {
            TradeHubTheme {
                TradeHubAppContent()
            }
        }
    }
}

private data class PendingTradeOrder(
    val side: String,
    val lots: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val priceText: String,
)

@Composable
fun TradeHubAppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: TradeHubViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                TradeHubViewModel(TradeHubRepository(app))
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var pendingTradeOrder by remember { mutableStateOf<PendingTradeOrder?>(null) }

    LaunchedEffect(uiState.statusBanner, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Handle Back Navigation on CTraderAuthScreen, chart fullscreen, or secondary tabs
    BackHandler(
        enabled = uiState.isCTraderAuthScreenOpen ||
            uiState.isChartFullscreen ||
            uiState.selectedTab != BottomNavTab.HOME
    ) {
        when {
            uiState.isCTraderAuthScreenOpen -> viewModel.closeCTraderAuthScreen()
            uiState.isChartFullscreen -> viewModel.toggleChartFullscreen()
            else -> viewModel.selectTab(BottomNavTab.HOME)
        }
    }

    val showBottomBar = !uiState.isCTraderAuthScreenOpen &&
        !(uiState.selectedTab == BottomNavTab.TRADE && uiState.isChartFullscreen)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepSpaceBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                TradeHubBottomBar(
                    selectedTab = uiState.selectedTab,
                    onSelectTab = viewModel::selectTab,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isCTraderAuthScreenOpen) {
                CTraderAuthScreen(
                    uiState = uiState,
                    onBack = viewModel::closeCTraderAuthScreen,
                    onSelectEnv = viewModel::setCTraderEnvironment,
                    onStartAuthentication = { clientId, clientSecret, env ->
                        viewModel.startCTraderOAuthLogin(
                            clientId = clientId,
                            clientSecret = clientSecret,
                            env = env,
                            onOpenBrowserUrl = { url ->
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to open browser for cTrader login")
                                    }
                                }
                            },
                        )
                    },
                    onCompleteManualCode = viewModel::completeCTraderOAuthManually,
                    onCancelOAuth = viewModel::cancelCTraderOAuthWaiting,
                    onDisconnect = viewModel::disconnectCTrader,
                    onOpenCTraderPortal = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(CTraderOAuthManager.CTRADER_PORTAL_URL),
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar("Unable to open cTrader portal")
                            }
                        }
                    },
                    onCopyRedirectUri = { uri ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Copied Redirect URI: $uri")
                        }
                    },
                )
            } else {
                when (uiState.selectedTab) {
                    BottomNavTab.HOME -> HomeScreen(
                        uiState = uiState,
                        onOpenTradeTab = { viewModel.selectTab(BottomNavTab.TRADE) },
                        onOpenAccountsTab = { viewModel.selectTab(BottomNavTab.ACCOUNTS) },
                        onOpenHistoryTab = { viewModel.selectTab(BottomNavTab.HISTORY) },
                        onOpenAddAccountDialog = { showAddAccountDialog = true },
                        onEnableDemoMode = { viewModel.setDemoMode(true) },
                        onSelectAccount = viewModel::setActiveAccount,
                        onCloseTrade = viewModel::closeOpenTrade,
                    )

                    BottomNavTab.TRADE -> TradeScreen(
                        uiState = uiState,
                        onSelectSymbol = viewModel::selectSymbol,
                        onSelectTimeframe = viewModel::selectTimeframe,
                        onSetLotSize = viewModel::setLotSize,
                        onToggleFullscreen = viewModel::toggleChartFullscreen,
                        onPlaceOrderRequested = { side, lots, sl, tp, skipConfirmDialog ->
                            val activeAccount = uiState.activeAccount
                            if (activeAccount == null) {
                                viewModel.executeMarketOrder(side, sl, tp, lotsOverride = lots)
                                return@TradeScreen
                            }
                            val quote = uiState.quotes[uiState.selectedSymbol]
                            val inst = MarketSimulator.getInstrument(uiState.selectedSymbol)
                            val price = if (side == "BUY") quote?.ask ?: 0.0 else quote?.bid ?: 0.0
                            val priceText = MarketSimulator.formatPrice(price, inst.decimals)

                            val needsDialog = !skipConfirmDialog && uiState.settings.tradeConfirmationAlwaysOn
                            val needsBiometric = uiState.settings.biometricAuthEnabled

                            if (!needsDialog && !needsBiometric) {
                                viewModel.executeMarketOrder(side, sl, tp, lotsOverride = lots)
                            } else {
                                val activity = context as? FragmentActivity
                                if (needsBiometric && activity != null) {
                                    BiometricHelper.authenticateTrade(
                                        activity = activity,
                                        title = "Confirm $side ${uiState.selectedSymbol}",
                                        subtitle = "$lots lots @ $priceText",
                                        onSuccess = {
                                            viewModel.executeMarketOrder(side, sl, tp, lotsOverride = lots)
                                        },
                                        onFallbackRequired = {
                                            if (skipConfirmDialog) {
                                                viewModel.executeMarketOrder(side, sl, tp, lotsOverride = lots)
                                            } else {
                                                pendingTradeOrder = PendingTradeOrder(side, lots, sl, tp, priceText)
                                            }
                                        },
                                        onCancelled = { /* User dismissed prompt */ },
                                    )
                                } else if (needsDialog) {
                                    pendingTradeOrder = PendingTradeOrder(side, lots, sl, tp, priceText)
                                } else {
                                    viewModel.executeMarketOrder(side, sl, tp, lotsOverride = lots)
                                }
                            }
                        },
                        onCloseTrade = viewModel::closeOpenTrade,
                        onUpdateOpenTradeLevels = viewModel::updateOpenTradeLevels,
                        onEnableDemoMode = { viewModel.setDemoMode(true) },
                    )

                    BottomNavTab.HISTORY -> HistoryScreen(
                        uiState = uiState,
                        onCloseTrade = viewModel::closeOpenTrade,
                        onUpdateOpenLevels = viewModel::updateOpenTradeLevels,
                        onLogJournalTrade = viewModel::logClosedJournalTrade,
                    )

                    BottomNavTab.ACCOUNTS -> AccountsScreen(
                        uiState = uiState,
                        onOpenAddAccountDialog = { showAddAccountDialog = true },
                        onOpenCTraderAuthScreen = viewModel::openCTraderAuthScreen,
                        onEnableDemoMode = { viewModel.setDemoMode(true) },
                        onResetDemoBalance = viewModel::resetDemoAccount,
                        onSelectActiveAccount = viewModel::setActiveAccount,
                        onDeleteAccount = viewModel::deleteAccount,
                    )

                    BottomNavTab.SETTINGS -> SettingsScreen(
                        uiState = uiState,
                        onOpenCTraderAuthScreen = viewModel::openCTraderAuthScreen,
                        onSelectCTraderEnv = viewModel::setCTraderEnvironment,
                        onDisconnectCTrader = viewModel::disconnectCTrader,
                        onToggleDemoMode = viewModel::setDemoMode,
                        onSaveRiskPercent = viewModel::saveDefaultRiskPercent,
                        onSelectRiskBasis = viewModel::setRiskBasis,
                        onToggleAutoPartialClose = viewModel::setAutoPartialCloseAtOneToOne,
                        onUpdatePartialTpTiers = viewModel::updatePartialTpTiers,
                        onApplyQuickFiftyOneToOnePreset = viewModel::applyQuickOneToOneFiftyPreset,
                        onToggleFibonacciRrOnChart = viewModel::setShowFibonacciRrOnChart,
                        onToggleBiometric = viewModel::setBiometricAuthEnabled,
                        onToggleTradeConfirmation = viewModel::setTradeConfirmationAlwaysOn,
                    )
                }
            }
        }
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onCreateAccount = { name, broker, type, currency, balance, apiRef ->
                viewModel.createAccount(name, broker, type, currency, balance, apiRef)
                showAddAccountDialog = false
            },
        )
    }

    pendingTradeOrder?.let { pending ->
        TradeConfirmationDialog(
            symbol = uiState.selectedSymbol,
            side = pending.side,
            lots = pending.lots,
            priceText = pending.priceText,
            riskPercent = uiState.settings.defaultRiskPercent,
            accountName = uiState.activeAccount?.name ?: "Account",
            onConfirm = {
                viewModel.executeMarketOrder(
                    side = pending.side,
                    stopLoss = pending.stopLoss,
                    takeProfit = pending.takeProfit,
                    lotsOverride = pending.lots,
                )
                pendingTradeOrder = null
            },
            onDismiss = { pendingTradeOrder = null },
        )
    }
}

@Composable
private fun TradeHubBottomBar(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit,
) {
    NavigationBar(
        containerColor = CardSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        val items = listOf(
            Triple(BottomNavTab.HOME, R.string.nav_home, Icons.Rounded.Home to Icons.Outlined.Home),
            Triple(BottomNavTab.TRADE, R.string.nav_trade, Icons.Rounded.Bolt to Icons.Rounded.Bolt),
            Triple(BottomNavTab.HISTORY, R.string.nav_history, Icons.AutoMirrored.Rounded.ReceiptLong to Icons.AutoMirrored.Outlined.ReceiptLong),
            Triple(BottomNavTab.ACCOUNTS, R.string.nav_accounts, Icons.Rounded.AccountBalanceWallet to Icons.Outlined.AccountBalanceWallet),
            Triple(BottomNavTab.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings to Icons.Outlined.Settings),
        )

        items.forEach { (tab, labelRes, icons) ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) icons.first else icons.second,
                        contentDescription = stringResource(labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF60A5FA),
                    selectedTextColor = Color(0xFF60A5FA),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = RoyalBlueBright.copy(alpha = 0.16f),
                ),
                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
            )
        }
    }
}
