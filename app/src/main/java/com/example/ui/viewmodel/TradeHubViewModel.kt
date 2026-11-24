package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ctrader.CTraderConnectionState
import com.example.ctrader.CTraderOAuthManager
import com.example.ctrader.CTraderOpenApiClient
import com.example.ctrader.CTraderStreamEvent
import com.example.ctrader.CTraderSymbolInfo
import com.example.data.model.Candle
import com.example.data.model.LiveQuote
import com.example.data.model.PartialTpTier
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import com.example.data.model.UserSettings
import com.example.data.repository.TradeHubRepository
import com.example.market.MarketSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

enum class BottomNavTab {
    HOME,
    TRADE,
    HISTORY,
    ACCOUNTS,
    SETTINGS,
}

data class PortfolioMetrics(
    val totalEquity: Double = 0.0,
    val totalBalance: Double = 0.0,
    val todaysPnl: Double = 0.0,
    val floatingPnl: Double = 0.0,
    val openTradesCount: Int = 0,
    val winRatePercent: Int = 0,
    val totalClosedTrades: Int = 0,
)

data class TradeHubUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val statusBanner: String? = null,
    val selectedTab: BottomNavTab = BottomNavTab.HOME,
    val settings: UserSettings = UserSettings(),
    val accounts: List<TradingAccount> = emptyList(),
    val visibleAccounts: List<TradingAccount> = emptyList(),
    val activeAccount: TradingAccount? = null,
    val trades: List<TradeRecord> = emptyList(),
    val quotes: Map<String, LiveQuote> = emptyMap(),
    val availableSymbols: List<String> = MarketSimulator.instruments.map { it.symbol },
    val selectedSymbol: String = "XAUUSD",
    val selectedTimeframe: String = "m1",
    val selectedLots: Double = 0.1,
    val isChartFullscreen: Boolean = false,
    val isCTraderAuthScreenOpen: Boolean = false,
    val ctraderConnectionState: CTraderConnectionState = CTraderConnectionState.DISCONNECTED,
    val isOAuthWaiting: Boolean = false,
    val symbolSpecs: Map<String, CTraderSymbolInfo> = emptyMap(),
    val depositCurrency: String = "USD",
    val depositAssetId: Long = 0L,
    val conversionRates: Map<String, Double> = emptyMap(),
    val metrics: PortfolioMetrics = PortfolioMetrics(),
)

class TradeHubViewModel(
    private val repository: TradeHubRepository,
    private val oauthManager: CTraderOAuthManager = CTraderOAuthManager(),
    private val ctraderClient: CTraderOpenApiClient = CTraderOpenApiClient(),
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(BottomNavTab.HOME)
    private val _selectedSymbol = MutableStateFlow("XAUUSD")
    private val _selectedTimeframe = MutableStateFlow("m1")
    private val _selectedLots = MutableStateFlow(0.1)
    private val _isChartFullscreen = MutableStateFlow(false)
    private val _isCTraderAuthScreenOpen = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isOAuthWaiting = MutableStateFlow(false)
    private val _availableSymbols = MutableStateFlow(MarketSimulator.instruments.map { it.symbol })
    private val _quotes = MutableStateFlow(
        MarketSimulator.generateInitialQuotes(System.currentTimeMillis() / 1000L)
    )

    private val liveCTraderSymbols = ConcurrentHashMap.newKeySet<String>()
    private val partialCloseInFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private var oauthJob: Job? = null
    private var reconnectTriggered = false

    private val settingsFlow: StateFlow<UserSettings?> = repository.observeSettings()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed observing settings", error)
            _errorMessage.value = error.message ?: "Failed to load settings"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val accountsFlow: StateFlow<List<TradingAccount>> = repository.observeAccounts()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed observing accounts", error)
            _errorMessage.value = error.message ?: "Failed to load accounts"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val tradesFlow: StateFlow<List<TradeRecord>> = repository.observeTrades()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed observing trades", error)
            _errorMessage.value = error.message ?: "Failed to load trades"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private data class UiLocalPrefs(
        val tab: BottomNavTab,
        val symbol: String,
        val timeframe: String,
        val lots: Double,
        val fullscreen: Boolean,
        val authScreenOpen: Boolean = false,
        val status: String?,
        val error: String?,
        val oauthWaiting: Boolean,
        val connState: CTraderConnectionState,
        val symbols: List<String>,
        val symbolSpecs: Map<String, CTraderSymbolInfo> = emptyMap(),
        val depositCurrency: String = "USD",
        val depositAssetId: Long = 0L,
    )

    private val localPrefsFlow = combine(
        _selectedTab,
        _selectedSymbol,
        _selectedTimeframe,
        _selectedLots,
        _isChartFullscreen,
    ) { tab, sym, tf, lots, fs ->
        UiLocalPrefs(
            tab = tab,
            symbol = sym,
            timeframe = tf,
            lots = lots,
            fullscreen = fs,
            status = null,
            error = null,
            oauthWaiting = false,
            connState = CTraderConnectionState.DISCONNECTED,
            symbols = emptyList(),
        )
    }.combine(_isCTraderAuthScreenOpen) { prefs, authOpen ->
        prefs.copy(authScreenOpen = authOpen)
    }.combine(_statusBanner) { prefs, status ->
        prefs.copy(status = status)
    }.combine(_errorMessage) { prefs, err ->
        prefs.copy(error = err)
    }.combine(_isOAuthWaiting) { prefs, waiting ->
        prefs.copy(oauthWaiting = waiting)
    }.combine(ctraderClient.connectionState) { prefs, conn ->
        prefs.copy(connState = conn)
    }.combine(_availableSymbols) { prefs, syms ->
        prefs.copy(symbols = syms)
    }.combine(ctraderClient.symbolSpecs) { prefs, specs ->
        prefs.copy(symbolSpecs = specs)
    }.combine(ctraderClient.depositCurrency) { prefs, depCur ->
        prefs.copy(depositCurrency = depCur)
    }.combine(ctraderClient.depositAssetId) { prefs, depId ->
        prefs.copy(depositAssetId = depId)
    }

    val uiState: StateFlow<TradeHubUiState> = combine(
        settingsFlow,
        accountsFlow,
        tradesFlow,
        _quotes,
        localPrefsFlow,
    ) { maybeSettings, accounts, trades, quotes, local ->
        val settings = maybeSettings ?: UserSettings()
        val hasCTraderAccounts = accounts.any { it.ctidTraderAccountId > 0L }

        val visibleAccounts = when {
            hasCTraderAccounts && !settings.demoModeEnabled -> accounts.filter {
                it.accountType != "demo"
            }
            settings.demoModeEnabled -> accounts
            else -> accounts.filter { it.accountType != "demo" }
        }

        val activeAccount = visibleAccounts.firstOrNull { it.id == settings.activeAccountId }
            ?: visibleAccounts.firstOrNull { it.isActive }
            ?: visibleAccounts.firstOrNull()

        val relevantTrades = if (settings.demoModeEnabled) {
            trades
        } else {
            trades.filter { it.accountId != TradeHubRepository.DEMO_ACCOUNT_ID }
        }

        val openTrades = relevantTrades.filter { it.status == "OPEN" }
        val closedTrades = relevantTrades.filter { it.status == "CLOSED" }

        val floatingPnl = openTrades.sumOf { trade ->
            MarketSimulator.calculateFloatingPnl(trade, quotes[trade.symbol])
        }

        val startOfDayMillis = startOfTodayEpochMillis()
        val realizedToday = closedTrades
            .filter { (it.closedAtMillis ?: it.createdAtMillis) >= startOfDayMillis }
            .sumOf { it.realizedPnl }

        val totalBalance = visibleAccounts.sumOf { it.balance }
        val totalEquity = totalBalance + floatingPnl
        val wins = closedTrades.count { it.realizedPnl > 0.0 }
        val winRate = if (closedTrades.isNotEmpty()) (wins * 100) / closedTrades.size else 0

        val liveConversionRates = buildMap<String, Double> {
            if (local.depositAssetId > 0L) {
                local.symbols.forEach { symName ->
                    val spec = local.symbolSpecs[symName]
                    if (spec != null && spec.quoteAssetId > 0L) {
                        val chainRate = ctraderClient.computeConversionRateFromChain(
                            spec.quoteAssetId,
                            local.depositAssetId,
                        )
                        if (chainRate != null && chainRate > 0.0) {
                            put(symName, chainRate)
                        }
                    }
                }
            }
        }

        TradeHubUiState(
            isLoading = false,
            errorMessage = local.error,
            statusBanner = local.status,
            selectedTab = local.tab,
            settings = settings,
            accounts = accounts,
            visibleAccounts = visibleAccounts,
            activeAccount = activeAccount,
            trades = relevantTrades,
            quotes = quotes,
            availableSymbols = local.symbols,
            selectedSymbol = local.symbol,
            selectedTimeframe = local.timeframe,
            selectedLots = local.lots,
            isChartFullscreen = local.fullscreen,
            isCTraderAuthScreenOpen = local.authScreenOpen,
            ctraderConnectionState = local.connState,
            isOAuthWaiting = local.oauthWaiting,
            symbolSpecs = local.symbolSpecs,
            depositCurrency = local.depositCurrency,
            depositAssetId = local.depositAssetId,
            conversionRates = liveConversionRates,
            metrics = PortfolioMetrics(
                totalEquity = totalEquity,
                totalBalance = totalBalance,
                todaysPnl = realizedToday + floatingPnl,
                floatingPnl = floatingPnl,
                openTradesCount = openTrades.size,
                winRatePercent = winRate,
                totalClosedTrades = closedTrades.size,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TradeHubUiState(),
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultSettings().onSuccess { loaded ->
                _selectedSymbol.value = loaded.selectedSymbol.ifBlank { "XAUUSD" }
                _selectedTimeframe.value = loaded.selectedTimeframe.ifBlank { "m1" }
                if (!reconnectTriggered &&
                    loaded.ctraderAccessToken.isNotBlank() &&
                    loaded.ctraderClientId.isNotBlank() &&
                    loaded.ctraderClientSecret.isNotBlank()
                ) {
                    reconnectTriggered = true
                    connectCTraderSession(loaded)
                }
            }
        }
        observeCTraderEvents()
        observeAutoPartialClose()
        startMarketTicker()
    }

    override fun onCleared() {
        super.onCleared()
        oauthManager.stopCallbackServer()
        ctraderClient.disconnect()
    }

    private fun observeAutoPartialClose() {
        viewModelScope.launch {
            uiState.collect { state ->
                if (!state.settings.autoPartialCloseAtOneToOne) return@collect
                val openCandidates = state.trades.filter { it.status == "OPEN" && it.stopLoss > 0.0 }
                if (openCandidates.isEmpty()) return@collect

                val tiers = MarketSimulator.parsePartialTpTiers(state.settings.partialTpTiersSpec)
                if (tiers.isEmpty()) return@collect

                for (trade in openCandidates) {
                    val quote = state.quotes[trade.symbol] ?: continue
                    val instrument = MarketSimulator.resolveInstrument(
                        trade.symbol,
                        state.symbolSpecs[trade.symbol],
                    )
                    val account = state.accounts.firstOrNull { it.id == trade.accountId }
                        ?: state.activeAccount
                    val depositCurrency = if (account != null && account.ctidTraderAccountId > 0L) {
                        state.depositCurrency.ifBlank { account.currency.ifBlank { "USD" } }
                    } else {
                        account?.currency?.ifBlank { "USD" } ?: state.depositCurrency.ifBlank { "USD" }
                    }
                    val convRate = MarketSimulator.resolveQuoteToDepositRate(
                        quoteCurrency = instrument.quoteCurrency,
                        depositCurrency = depositCurrency,
                        quotes = state.quotes,
                        symbolHint = instrument.symbol,
                        referencePrice = trade.entryPrice,
                        chainRateOverride = state.conversionRates[trade.symbol],
                    )
                    val plan = MarketSimulator.evaluatePartialTpTiers(
                        trade = trade,
                        quote = quote,
                        instrument = instrument,
                        tiers = tiers,
                        quoteToDepositRate = convRate,
                    ) ?: continue

                    val flightKey = "${trade.id}:${plan.tierKey}"
                    if (!partialCloseInFlightKeys.add(flightKey)) continue
                    executeAutoPartialClose(
                        trade = trade,
                        account = account,
                        instrument = instrument,
                        plan = plan,
                        flightKey = flightKey,
                        exitPrice = if (trade.side.equals("BUY", true)) quote.bid else quote.ask,
                        depositCurrency = depositCurrency,
                        ctraderConnected = state.ctraderConnectionState == CTraderConnectionState.CONNECTED,
                    )
                }
            }
        }
    }

    private fun executeAutoPartialClose(
        trade: TradeRecord,
        account: TradingAccount?,
        instrument: com.example.data.model.MarketInstrument,
        plan: com.example.market.PartialClosePlan,
        flightKey: String,
        exitPrice: Double,
        depositCurrency: String,
        ctraderConnected: Boolean,
    ) {
        viewModelScope.launch {
            val lotsStr = MarketSimulator.formatLots(plan.closeLots, instrument.stepLots)
            val rrLabel = "1:${MarketSimulator.formatRrMultiple(plan.rrMultiple)}"
            val pctStr = "${plan.closePercent.toInt()}%"
            val tierNote = "$pctStr @ $rrLabel RR"

            if (trade.ctraderPositionId > 0L &&
                account != null &&
                account.ctidTraderAccountId > 0L &&
                ctraderConnected
            ) {
                val symInfo = ctraderClient.getSymbolInfo(trade.symbol)
                val closeTargetLots = if (plan.isFullClose) trade.lots else plan.closeLots
                ctraderClient.closePosition(
                    ctidTraderAccountId = account.ctidTraderAccountId,
                    positionId = trade.ctraderPositionId,
                    symbolInfo = symInfo,
                    lots = closeTargetLots,
                ).onSuccess {
                    if (plan.isFullClose) {
                        repository.closeTrade(
                            trade = trade,
                            exitPrice = exitPrice,
                            realizedPnl = plan.realizedPnlEstimate,
                            currentAccountBalance = account.balance,
                        )
                    } else {
                        repository.markPartialCloseExecuted(
                            tradeId = trade.id,
                            tierKey = plan.tierKey,
                            existingTiersCsv = trade.executedPartialTiers,
                        )
                    }
                    refreshActiveCTraderAccountData(account)
                    _statusBanner.value = "$rrLabel RR reached: closed $pctStr ($lotsStr lots) on ${trade.symbol}"
                }.onFailure { err ->
                    delay(5_000L)
                    partialCloseInFlightKeys.remove(flightKey)
                    _errorMessage.value = err.localizedMessage ?: "Auto $pctStr close failed"
                }
            } else {
                val result = if (plan.isFullClose) {
                    repository.closeTrade(
                        trade = trade,
                        exitPrice = exitPrice,
                        realizedPnl = plan.realizedPnlEstimate,
                        currentAccountBalance = account?.balance,
                    )
                } else {
                    repository.partialCloseTrade(
                        trade = trade,
                        closedLots = plan.closeLots,
                        remainingLots = plan.remainingLots,
                        exitPrice = exitPrice,
                        realizedPnl = plan.realizedPnlEstimate,
                        currentAccountBalance = account?.balance,
                        tierKey = plan.tierKey,
                        tierLabel = tierNote,
                    )
                }
                result.onSuccess {
                    _statusBanner.value = "$rrLabel RR reached: closed $pctStr ($lotsStr ${trade.symbol} • ${
                        MarketSimulator.formatSignedCurrency(plan.realizedPnlEstimate, depositCurrency)
                    })"
                }.onFailure { err ->
                    partialCloseInFlightKeys.remove(flightKey)
                    _errorMessage.value = err.localizedMessage ?: "Auto $pctStr close failed"
                }
            }
        }
    }

    private fun observeCTraderEvents() {
        viewModelScope.launch {
            ctraderClient.events.collect { event ->
                when (event) {
                    is CTraderStreamEvent.SpotUpdate -> {
                        val tick = event.tick
                        liveCTraderSymbols.add(tick.symbolName)
                        applyCTraderSpotTick(
                            symbol = tick.symbolName,
                            newBid = tick.bid,
                            newAsk = tick.ask,
                        )
                    }

                    is CTraderStreamEvent.ExecutionUpdate -> {
                        val active = uiState.value.activeAccount
                        if (active != null && active.ctidTraderAccountId == event.ctidTraderAccountId) {
                            refreshActiveCTraderAccountData(active)
                        }
                    }

                    is CTraderStreamEvent.ErrorNotice -> {
                        _errorMessage.value = if (event.description.isNotBlank()) {
                            "${event.code}: ${event.description}"
                        } else {
                            event.code
                        }
                    }
                }
            }
        }
    }

    private fun applyCTraderSpotTick(
        symbol: String,
        newBid: Double?,
        newAsk: Double?,
    ) {
        val nowSec = System.currentTimeMillis() / 1000L
        val tf = _selectedTimeframe.value
        val tfSec = MarketSimulator.timeframeSeconds(tf)
        val bucket = (nowSec / tfSec) * tfSec
        val secondsRemaining = (tfSec - (nowSec % tfSec)).toInt().coerceAtLeast(1)

        _quotes.update { map ->
            val prev = map[symbol]
            val bid = newBid ?: prev?.bid ?: newAsk ?: return@update map
            val ask = newAsk ?: prev?.ask ?: bid
            val mid = (bid + ask) / 2.0
            val candles = prev?.candles?.toMutableList() ?: mutableListOf()
            if (candles.isEmpty()) {
                candles.add(Candle(bucket, mid, mid, mid, mid))
            } else {
                val last = candles.last()
                if (bucket > last.timeEpochSec) {
                    candles.add(Candle(bucket, last.close, max(last.close, mid), min(last.close, mid), mid))
                    if (candles.size > 110) candles.removeAt(0)
                } else {
                    candles[candles.lastIndex] = last.copy(
                        high = max(last.high, mid),
                        low = min(last.low, mid),
                        close = mid,
                    )
                }
            }
            val firstOpen = candles.firstOrNull()?.open ?: mid
            val changePct = if (firstOpen > 0.0) ((mid - firstOpen) / firstOpen) * 100.0 else 0.0
            map + (symbol to LiveQuote(
                symbol = symbol,
                bid = bid,
                ask = ask,
                changePercent = changePct,
                secondsRemainingInCandle = secondsRemaining,
                candles = candles,
            ))
        }
    }

    private fun startMarketTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val nowSec = System.currentTimeMillis() / 1000L
                val tf = _selectedTimeframe.value
                val tfSec = MarketSimulator.timeframeSeconds(tf)
                val secondsRemaining = (tfSec - (nowSec % tfSec)).toInt().coerceAtLeast(1)

                _quotes.update { currentMap ->
                    currentMap.mapValues { (symbol, quote) ->
                        if (liveCTraderSymbols.contains(symbol)) {
                            quote.copy(secondsRemainingInCandle = secondsRemaining)
                        } else {
                            val effectiveTf = if (symbol == _selectedSymbol.value) tf else "m1"
                            MarketSimulator.tickQuote(quote, effectiveTf, nowSec)
                        }
                    }
                }
            }
        }
    }

    fun startCTraderOAuthLogin(
        clientId: String,
        clientSecret: String,
        env: String,
        onOpenBrowserUrl: (String) -> Unit,
    ) {
        val trimmedId = clientId.trim()
        val trimmedSecret = clientSecret.trim()
        if (trimmedId.isBlank() || trimmedSecret.isBlank()) {
            _errorMessage.value = "Please enter both Client ID and Client Secret first."
            return
        }

        oauthJob?.cancel()
        oauthManager.stopCallbackServer()

        oauthJob = viewModelScope.launch {
            repository.updateSettings(
                ctraderEnv = env,
                ctraderClientId = trimmedId,
                ctraderClientSecret = trimmedSecret,
            )
            _isOAuthWaiting.value = true

            val authUrl = oauthManager.buildAuthorizationUrl(trimmedId)
            // Start local callback listener before opening browser
            val waitTask = launch {
                oauthManager.awaitAuthorizationCode()
                    .onSuccess { code ->
                        exchangeAndConnect(
                            code = code,
                            clientId = trimmedId,
                            clientSecret = trimmedSecret,
                            env = env,
                        )
                    }
                    .onFailure { err ->
                        _isOAuthWaiting.value = false
                        if (err !is CancellationException) {
                            _errorMessage.value = safeErrorMessage(err, "cTrader authorization cancelled")
                        }
                    }
            }
            delay(150L)
            onOpenBrowserUrl(authUrl)
            waitTask.join()
        }
    }

    fun completeCTraderOAuthManually(
        rawCodeOrUrl: String,
        clientId: String,
        clientSecret: String,
        env: String,
    ) {
        val code = oauthManager.extractCodeFromInput(rawCodeOrUrl)
        if (code == null) {
            _errorMessage.value = "Invalid redirect URL or authorization code."
            return
        }
        val trimmedId = clientId.trim()
        val trimmedSecret = clientSecret.trim()
        if (trimmedId.isBlank() || trimmedSecret.isBlank()) {
            _errorMessage.value = "Client ID and Client Secret are required."
            return
        }
        oauthJob?.cancel()
        oauthManager.stopCallbackServer()
        viewModelScope.launch {
            _isOAuthWaiting.value = true
            repository.updateSettings(
                ctraderEnv = env,
                ctraderClientId = trimmedId,
                ctraderClientSecret = trimmedSecret,
            )
            exchangeAndConnect(code, trimmedId, trimmedSecret, env)
        }
    }

    fun cancelCTraderOAuthWaiting() {
        oauthJob?.cancel()
        oauthManager.stopCallbackServer()
        _isOAuthWaiting.value = false
    }

    private suspend fun exchangeAndConnect(
        code: String,
        clientId: String,
        clientSecret: String,
        env: String,
    ) {
        oauthManager.exchangeAuthorizationCode(code, clientId, clientSecret)
            .onSuccess { tokens ->
                val expiresAt = System.currentTimeMillis() + (tokens.expiresInSeconds * 1000L)
                repository.updateSettings(
                    ctraderEnv = env,
                    ctraderClientId = clientId,
                    ctraderClientSecret = clientSecret,
                    ctraderAccessToken = tokens.accessToken,
                    ctraderRefreshToken = tokens.refreshToken,
                    ctraderTokenExpiresAtMillis = expiresAt,
                )
                _isOAuthWaiting.value = false
                val refreshedSettings = UserSettings(
                    ctraderEnv = env,
                    ctraderClientId = clientId,
                    ctraderClientSecret = clientSecret,
                    ctraderAccessToken = tokens.accessToken,
                    ctraderRefreshToken = tokens.refreshToken,
                    ctraderTokenExpiresAtMillis = expiresAt,
                    selectedSymbol = _selectedSymbol.value,
                    selectedTimeframe = _selectedTimeframe.value,
                )
                connectCTraderSession(refreshedSettings)
            }
            .onFailure { err ->
                _isOAuthWaiting.value = false
                _errorMessage.value = safeErrorMessage(err, "Token exchange failed")
            }
    }

    fun setCTraderEnvironment(env: String) {
        val current = uiState.value.settings
        if (current.ctraderEnv == env) return
        viewModelScope.launch {
            repository.updateSettings(ctraderEnv = env)
            if (current.ctraderAccessToken.isNotBlank() &&
                current.ctraderClientId.isNotBlank() &&
                current.ctraderClientSecret.isNotBlank()
            ) {
                connectCTraderSession(current.copy(ctraderEnv = env))
            }
        }
    }

    fun saveCTraderCredentials(clientId: String, clientSecret: String, env: String) {
        viewModelScope.launch {
            repository.updateSettings(
                ctraderEnv = env,
                ctraderClientId = clientId.trim(),
                ctraderClientSecret = clientSecret.trim(),
            )
            _statusBanner.value = "cTrader credentials saved"
        }
    }

    fun disconnectCTrader() {
        oauthJob?.cancel()
        oauthManager.stopCallbackServer()
        _isOAuthWaiting.value = false
        liveCTraderSymbols.clear()
        ctraderClient.disconnect()
        viewModelScope.launch {
            repository.clearCTraderSession()
            _statusBanner.value = "Disconnected from cTrader"
        }
    }

    private suspend fun connectCTraderSession(settings: UserSettings) {
        liveCTraderSymbols.clear()
        var activeSettings = settings

        // Refresh token if within 10 minutes of expiry and refreshToken exists
        val now = System.currentTimeMillis()
        if (activeSettings.ctraderTokenExpiresAtMillis in 1..(now + 600_000L) &&
            activeSettings.ctraderRefreshToken.isNotBlank()
        ) {
            oauthManager.refreshAccessToken(
                refreshToken = activeSettings.ctraderRefreshToken,
                clientId = activeSettings.ctraderClientId,
                clientSecret = activeSettings.ctraderClientSecret,
            ).onSuccess { refreshed ->
                val newExpiry = now + (refreshed.expiresInSeconds * 1000L)
                repository.updateSettings(
                    ctraderAccessToken = refreshed.accessToken,
                    ctraderRefreshToken = refreshed.refreshToken.ifBlank { activeSettings.ctraderRefreshToken },
                    ctraderTokenExpiresAtMillis = newExpiry,
                )
                activeSettings = activeSettings.copy(
                    ctraderAccessToken = refreshed.accessToken,
                    ctraderRefreshToken = refreshed.refreshToken.ifBlank { activeSettings.ctraderRefreshToken },
                    ctraderTokenExpiresAtMillis = newExpiry,
                )
            }
        }

        val env = activeSettings.ctraderEnv.ifBlank { "demo" }
        ctraderClient.connectAndAuthorizeApp(
            env = env,
            clientId = activeSettings.ctraderClientId,
            clientSecret = activeSettings.ctraderClientSecret,
        ).onFailure { err ->
            _errorMessage.value = "cTrader connection failed: ${safeErrorMessage(err, "Unknown error")}"
            return
        }

        val accountsResult = ctraderClient.getAccountsByAccessToken(activeSettings.ctraderAccessToken)
        val allAccounts = accountsResult.getOrElse { err ->
            _errorMessage.value = "Failed loading cTrader accounts: ${safeErrorMessage(err, "Unknown error")}"
            return
        }

        val matchingEnvAccounts = allAccounts.filter { acc ->
            if (env == "live") acc.isLive else !acc.isLive
        }.ifEmpty { allAccounts }

        if (matchingEnvAccounts.isEmpty()) {
            _errorMessage.value = "No cTrader accounts found on this token."
            return
        }

        val chosen = matchingEnvAccounts.firstOrNull { it.id == activeSettings.activeAccountId }
            ?: matchingEnvAccounts.first()

        val chosenEnv = if (chosen.isLive) "live" else "demo"
        if (chosenEnv != env) {
            repository.updateSettings(ctraderEnv = chosenEnv)
            ctraderClient.connectAndAuthorizeApp(
                env = chosenEnv,
                clientId = activeSettings.ctraderClientId,
                clientSecret = activeSettings.ctraderClientSecret,
            ).onFailure { err ->
                _errorMessage.value = "cTrader ${chosenEnv.uppercase()} connection failed: ${safeErrorMessage(err, "Unknown error")}"
                return
            }
        }

        repository.syncCTraderAccounts(matchingEnvAccounts, chosen.id)
        authorizeAndLoadAccount(chosen, activeSettings.ctraderAccessToken)
        _statusBanner.value = "Connected to ${chosen.name} (${if (chosen.isLive) "LIVE" else "DEMO"})"
    }

    private fun safeErrorMessage(error: Throwable, fallback: String): String {
        val msg = error.localizedMessage ?: error.message
        return if (msg.isNullOrBlank() || msg.equals("null", ignoreCase = true)) {
            fallback
        } else {
            msg
        }
    }

    private suspend fun authorizeAndLoadAccount(
        account: TradingAccount,
        accessToken: String,
    ) {
        val ctid = account.ctidTraderAccountId
        if (ctid <= 0L) return

        ctraderClient.authorizeAccount(ctid, accessToken)
            .onFailure { err ->
                _errorMessage.value = "Account auth failed: ${err.localizedMessage}"
                return
            }

        ctraderClient.fetchAssetsList(ctid)

        ctraderClient.fetchTraderBalance(ctid).onSuccess { balance ->
            repository.updateCTraderAccountBalance(account.id, balance)
        }

        ctraderClient.fetchSymbolsList(ctid).onSuccess { list ->
            if (list.isNotEmpty()) {
                val defaults = MarketSimulator.instruments.map { it.symbol }
                val merged = (defaults + list.map { it.symbolName }).distinct().take(40)
                _availableSymbols.value = merged
                if (!list.any { it.symbolName == _selectedSymbol.value }) {
                    _selectedSymbol.value = list.firstOrNull { it.symbolName == "XAUUSD" }?.symbolName
                        ?: list.firstOrNull { it.symbolName == "EURUSD" }?.symbolName
                        ?: list.first().symbolName
                }
            }
        }

        refreshActiveCTraderAccountData(account)
        loadCTraderChartAndSpot(_selectedSymbol.value, _selectedTimeframe.value, ctid)
    }

    private suspend fun refreshActiveCTraderAccountData(account: TradingAccount) {
        val ctid = account.ctidTraderAccountId
        if (ctid <= 0L) return
        ctraderClient.fetchTraderBalance(ctid).onSuccess { balance ->
            repository.updateCTraderAccountBalance(account.id, balance)
        }
        ctraderClient.fetchOpenPositions(
            ctidTraderAccountId = ctid,
            accountId = account.id,
            accountName = account.name,
            isDemo = !account.isLive,
        ).onSuccess { positions ->
            repository.syncCTraderOpenPositions(account.id, positions)
            val posSymbolIds = positions.mapNotNull { pos ->
                if (pos.symbolId > 0L) pos.symbolId else ctraderClient.getSymbolInfo(pos.symbol)?.symbolId
            }
            if (posSymbolIds.isNotEmpty()) {
                ctraderClient.subscribeSpots(ctid, posSymbolIds)
            }
        }
    }

    private suspend fun loadCTraderChartAndSpot(
        symbolName: String,
        timeframe: String,
        ctid: Long,
    ) {
        val symInfo = ctraderClient.getSymbolInfo(symbolName) ?: return
        val detailed = ctraderClient.ensureSymbolDetails(ctid, symInfo.symbolId) ?: symInfo
        val depAssetId = ctraderClient.depositAssetId.value
        if (detailed.quoteAssetId > 0L && depAssetId > 0L && detailed.quoteAssetId != depAssetId) {
            ctraderClient.ensureConversionChainSubscribed(ctid, detailed.quoteAssetId, depAssetId)
        }
        ctraderClient.fetchTrendbars(
            ctidTraderAccountId = ctid,
            symbolId = symInfo.symbolId,
            timeframe = timeframe,
            count = 90,
        ).onSuccess { candles ->
            if (candles.isNotEmpty()) {
                val lastClose = candles.last().close
                val inst = MarketSimulator.resolveInstrument(symInfo.symbolName, detailed)
                val halfSpread = inst.spread / 2.0
                val nowSec = System.currentTimeMillis() / 1000L
                val tfSec = MarketSimulator.timeframeSeconds(timeframe)
                val rem = (tfSec - (nowSec % tfSec)).toInt().coerceAtLeast(1)
                val firstOpen = candles.first().open
                val changePct = if (firstOpen > 0) ((lastClose - firstOpen) / firstOpen) * 100.0 else 0.0
                _quotes.update { map ->
                    val existing = map[symInfo.symbolName]
                    val bid = existing?.bid?.takeIf { liveCTraderSymbols.contains(symInfo.symbolName) }
                        ?: (lastClose - halfSpread)
                    val ask = existing?.ask?.takeIf { liveCTraderSymbols.contains(symInfo.symbolName) }
                        ?: (lastClose + halfSpread)
                    map + (symInfo.symbolName to LiveQuote(
                        symbol = symInfo.symbolName,
                        bid = bid,
                        ask = ask,
                        changePercent = changePct,
                        secondsRemainingInCandle = rem,
                        candles = candles,
                    ))
                }
            }
        }
        ctraderClient.subscribeSpots(ctid, listOf(symInfo.symbolId))
    }

    fun selectTab(tab: BottomNavTab) {
        _isCTraderAuthScreenOpen.value = false
        _selectedTab.value = tab
    }

    fun openCTraderAuthScreen() {
        _isCTraderAuthScreenOpen.value = true
    }

    fun closeCTraderAuthScreen() {
        _isCTraderAuthScreenOpen.value = false
    }

    fun selectSymbol(symbol: String) {
        _selectedSymbol.value = symbol
        viewModelScope.launch {
            repository.updateSettings(selectedSymbol = symbol)
            val active = uiState.value.activeAccount
            if (active != null && active.ctidTraderAccountId > 0L &&
                ctraderClient.connectionState.value == CTraderConnectionState.CONNECTED
            ) {
                loadCTraderChartAndSpot(symbol, _selectedTimeframe.value, active.ctidTraderAccountId)
            }
        }
    }

    fun selectTimeframe(timeframe: String) {
        _selectedTimeframe.value = timeframe
        val sym = _selectedSymbol.value
        val active = uiState.value.activeAccount
        if (active != null && active.ctidTraderAccountId > 0L &&
            ctraderClient.connectionState.value == CTraderConnectionState.CONNECTED
        ) {
            viewModelScope.launch {
                repository.updateSettings(selectedTimeframe = timeframe)
                loadCTraderChartAndSpot(sym, timeframe, active.ctidTraderAccountId)
            }
        } else {
            val nowSec = System.currentTimeMillis() / 1000L
            _quotes.update { currentMap ->
                val currentClose = currentMap[sym]?.candles?.lastOrNull()?.close
                val updated = MarketSimulator.buildQuoteForInstrument(
                    instrument = MarketSimulator.getInstrument(sym),
                    timeframe = timeframe,
                    nowEpochSec = nowSec,
                    anchorClose = currentClose,
                )
                currentMap + (sym to updated)
            }
            viewModelScope.launch {
                repository.updateSettings(selectedTimeframe = timeframe)
            }
        }
    }

    fun setLotSize(lots: Double) {
        _selectedLots.value = lots.coerceIn(0.01, 100.0)
    }

    fun toggleChartFullscreen() {
        _isChartFullscreen.update { !it }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
        _errorMessage.value = null
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val demoId = repository.ensureDemoAccount().getOrNull()
                repository.updateSettings(
                    demoModeEnabled = true,
                    activeAccountId = demoId ?: TradeHubRepository.DEMO_ACCOUNT_ID,
                )
                if (demoId != null) {
                    repository.setActiveAccount(demoId)
                }
                _statusBanner.value = "Local Practice Mode enabled ($10,000 virtual funds)"
            } else {
                val realAcc = uiState.value.accounts.firstOrNull { it.accountType != "demo" }
                repository.updateSettings(
                    demoModeEnabled = false,
                    activeAccountId = realAcc?.id ?: "",
                )
                _statusBanner.value = "Local Practice Mode disabled"
            }
        }
    }

    fun resetDemoAccount() {
        viewModelScope.launch {
            repository.resetDemoBalance()
                .onSuccess { _statusBanner.value = "Demo balance reset to $10,000.00" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to reset demo" }
        }
    }

    fun saveDefaultRiskPercent(percent: Double) {
        val clamped = percent.coerceIn(0.1, 100.0)
        viewModelScope.launch {
            repository.updateSettings(defaultRiskPercent = clamped)
                .onSuccess { _statusBanner.value = "Default risk saved (${clamped}%)" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save risk %" }
        }
    }

    fun setRiskBasis(basis: String) {
        viewModelScope.launch {
            repository.updateSettings(riskBasis = basis)
                .onSuccess {
                    _statusBanner.value = "Risk basis set to ${basis.replaceFirstChar { it.uppercase() }}"
                }
        }
    }

    fun setAutoPartialCloseAtOneToOne(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(autoPartialCloseAtOneToOne = enabled)
                .onSuccess {
                    _statusBanner.value = if (enabled) {
                        "Auto partial TP enabled"
                    } else {
                        "Auto partial TP disabled"
                    }
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed updating partial TP setting" }
        }
    }

    fun updatePartialTpTiers(tiers: List<PartialTpTier>) {
        val encoded = MarketSimulator.encodePartialTpTiers(tiers)
        viewModelScope.launch {
            repository.updateSettings(partialTpTiersSpec = encoded)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed saving TP steps" }
        }
    }

    fun applyQuickOneToOneFiftyPreset() {
        viewModelScope.launch {
            repository.updateSettings(
                autoPartialCloseAtOneToOne = true,
                partialTpTiersSpec = "1.0:50",
            ).onSuccess {
                _statusBanner.value = "Preset active: 50% closed @ 1:1 R:R, 50% runs to your TP"
            }
        }
    }

    fun setShowFibonacciRrOnChart(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(showFibonacciRrOnChart = enabled)
        }
    }

    fun setBiometricAuthEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(biometricAuthEnabled = enabled)
                .onSuccess {
                    _statusBanner.value = if (enabled) {
                        "Biometric confirmation enabled"
                    } else {
                        "Biometric confirmation disabled"
                    }
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update security" }
        }
    }

    fun setTradeConfirmationAlwaysOn(alwaysOn: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(tradeConfirmationAlwaysOn = alwaysOn)
        }
    }

    fun createAccount(
        name: String,
        broker: String,
        accountType: String,
        currency: String,
        initialBalance: Double,
        apiKeyReference: String,
    ) {
        if (name.isBlank()) {
            _errorMessage.value = "Account name is required"
            return
        }
        viewModelScope.launch {
            repository.createAccount(
                name = name,
                broker = broker.ifBlank { "Manual Journal" },
                accountType = accountType,
                currency = currency.ifBlank { "USD" },
                initialBalance = initialBalance,
                apiKeyReference = apiKeyReference,
                setAsActive = true,
            ).onSuccess {
                _statusBanner.value = "Account \"$name\" connected"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to create account"
            }
        }
    }

    fun setActiveAccount(accountId: String) {
        viewModelScope.launch {
            repository.setActiveAccount(accountId)
                .onSuccess {
                    val account = uiState.value.accounts.firstOrNull { it.id == accountId }
                    val token = uiState.value.settings.ctraderAccessToken
                    if (account != null && account.ctidTraderAccountId > 0L && token.isNotBlank()) {
                        val targetEnv = if (account.isLive) "live" else "demo"
                        if (!ctraderClient.isConnectedFor(targetEnv)) {
                            connectCTraderSession(uiState.value.settings.copy(ctraderEnv = targetEnv, activeAccountId = accountId))
                        } else {
                            authorizeAndLoadAccount(account, token)
                        }
                    }
                    _statusBanner.value = "Active account updated"
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to switch account" }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
                .onSuccess { _statusBanner.value = "Account removed" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete account" }
        }
    }

    fun executeMarketOrder(
        side: String,
        stopLoss: Double = 0.0,
        takeProfit: Double = 0.0,
        lotsOverride: Double? = null,
        notes: String = "",
    ) {
        val state = uiState.value
        val account = state.activeAccount
        if (account == null) {
            _errorMessage.value = "Connect cTrader in Settings or enable Practice Mode first."
            return
        }
        val effectiveLots = (lotsOverride ?: state.selectedLots).coerceIn(0.001, 1000.0)
        if (lotsOverride != null) {
            _selectedLots.value = effectiveLots
        }

        val referenceEntryPrice = state.quotes[state.selectedSymbol]?.let {
            if (side.equals("BUY", true)) it.ask else it.bid
        } ?: 0.0

        if (account.ctidTraderAccountId > 0L &&
            state.ctraderConnectionState == CTraderConnectionState.CONNECTED
        ) {
            val symInfo = ctraderClient.getSymbolInfo(state.selectedSymbol)
            if (symInfo == null) {
                _errorMessage.value = "Symbol ${state.selectedSymbol} not available on this cTrader account"
                return
            }
            viewModelScope.launch {
                val detailed = ctraderClient.ensureSymbolDetails(account.ctidTraderAccountId, symInfo.symbolId)
                    ?: symInfo
                val sentLots = CTraderOpenApiClient.alignVolumeToSymbol(effectiveLots, detailed).toDouble() /
                    detailed.lotSizeInCents.coerceAtLeast(100L).toDouble()
                ctraderClient.placeMarketOrder(
                    ctidTraderAccountId = account.ctidTraderAccountId,
                    symbolInfo = detailed,
                    side = side,
                    lots = effectiveLots,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    referenceEntryPrice = referenceEntryPrice,
                ).onSuccess {
                    refreshActiveCTraderAccountData(account)
                    _statusBanner.value = "cTrader $side ${MarketSimulator.formatLots(sentLots)} ${state.selectedSymbol} executed"
                }.onFailure { err ->
                    _errorMessage.value = err.localizedMessage ?: "cTrader order rejected"
                }
            }
            return
        }

        val quote = state.quotes[state.selectedSymbol]
        if (quote == null) {
            _errorMessage.value = "Market quote unavailable"
            return
        }
        val entryPrice = if (side == "BUY") quote.ask else quote.bid
        viewModelScope.launch {
            repository.openTrade(
                account = account,
                symbol = state.selectedSymbol,
                side = side,
                lots = effectiveLots,
                entryPrice = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                notes = notes,
            ).onSuccess {
                val formattedPrice = MarketSimulator.formatPrice(
                    entryPrice,
                    MarketSimulator.getInstrument(state.selectedSymbol).decimals
                )
                _statusBanner.value = "$side $effectiveLots ${state.selectedSymbol} @ $formattedPrice"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Order execution failed"
            }
        }
    }

    fun closeOpenTrade(trade: TradeRecord) {
        val state = uiState.value
        val account = state.accounts.firstOrNull { it.id == trade.accountId }

        if (trade.ctraderPositionId > 0L && account != null && account.ctidTraderAccountId > 0L &&
            state.ctraderConnectionState == CTraderConnectionState.CONNECTED
        ) {
            viewModelScope.launch {
                val symInfo = ctraderClient.getSymbolInfo(trade.symbol)
                ctraderClient.closePosition(
                    ctidTraderAccountId = account.ctidTraderAccountId,
                    positionId = trade.ctraderPositionId,
                    symbolInfo = symInfo,
                    lots = trade.lots,
                ).onSuccess {
                    refreshActiveCTraderAccountData(account)
                    _statusBanner.value = "Closed cTrader position ${trade.symbol}"
                }.onFailure { err ->
                    _errorMessage.value = err.localizedMessage ?: "Failed closing cTrader position"
                }
            }
            return
        }

        val quote = state.quotes[trade.symbol] ?: return
        val exitPrice = if (trade.side == "BUY") quote.bid else quote.ask
        val pnl = MarketSimulator.calculateFloatingPnl(trade, quote)
        viewModelScope.launch {
            repository.closeTrade(
                trade = trade,
                exitPrice = exitPrice,
                realizedPnl = pnl,
                currentAccountBalance = account?.balance,
            ).onSuccess {
                _statusBanner.value = "Closed ${trade.symbol} (${MarketSimulator.formatSignedCurrency(pnl)})"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to close position"
            }
        }
    }

    fun updateOpenTradeLevels(tradeId: String, stopLoss: Double, takeProfit: Double, notes: String) {
        val state = uiState.value
        val trade = state.trades.firstOrNull { it.id == tradeId }
        val account = trade?.let { t -> state.accounts.firstOrNull { it.id == t.accountId } }

        if (trade != null && trade.ctraderPositionId > 0L && account != null &&
            account.ctidTraderAccountId > 0L &&
            state.ctraderConnectionState == CTraderConnectionState.CONNECTED
        ) {
            val symbolDigits = ctraderClient.getSymbolInfo(trade.symbol)?.digits
                ?: MarketSimulator.getInstrument(trade.symbol).decimals
            viewModelScope.launch {
                ctraderClient.amendPositionSlTp(
                    ctidTraderAccountId = account.ctidTraderAccountId,
                    positionId = trade.ctraderPositionId,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    digits = symbolDigits,
                ).onSuccess {
                    repository.updateOpenTradeLevels(tradeId, stopLoss, takeProfit, notes)
                    _statusBanner.value = "cTrader SL/TP updated"
                }.onFailure { err ->
                    _errorMessage.value = err.localizedMessage ?: "Failed updating cTrader SL/TP"
                }
            }
            return
        }

        viewModelScope.launch {
            repository.updateOpenTradeLevels(tradeId, stopLoss, takeProfit, notes)
                .onSuccess { _statusBanner.value = "Position updated" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update position" }
        }
    }

    fun logClosedJournalTrade(
        symbol: String,
        side: String,
        lots: Double,
        entryPrice: Double,
        exitPrice: Double,
        notes: String,
    ) {
        val account = uiState.value.activeAccount
        if (account == null) {
            _errorMessage.value = "Please add or select an account first."
            return
        }
        val inst = MarketSimulator.getInstrument(symbol)
        val diff = if (side == "BUY") exitPrice - entryPrice else entryPrice - exitPrice
        val pnl = diff * lots * inst.contractSize
        viewModelScope.launch {
            repository.logClosedJournalTrade(
                account = account,
                symbol = symbol,
                side = side,
                lots = lots,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                realizedPnl = pnl,
                notes = notes,
            ).onSuccess {
                _statusBanner.value = "Logged $symbol trade (${MarketSimulator.formatSignedCurrency(pnl)})"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to log journal trade"
            }
        }
    }

    fun deleteTrade(tradeId: String) {
        viewModelScope.launch {
            repository.deleteTrade(tradeId)
                .onSuccess { _statusBanner.value = "Trade deleted" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete trade" }
        }
    }

    private fun startOfTodayEpochMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private companion object {
        const val TAG = "TradeHubVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
