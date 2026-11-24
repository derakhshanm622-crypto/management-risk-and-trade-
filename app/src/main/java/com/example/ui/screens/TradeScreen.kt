package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveQuote
import com.example.data.model.MarketInstrument
import com.example.data.model.TradeRecord
import com.example.market.MarketSimulator
import com.example.ui.components.InteractiveCandlestickChart
import com.example.ui.components.StagedOrderDraft
import com.example.ui.components.TradeItemCard
import com.example.ui.components.tradeHubFieldColors
import com.example.ui.theme.BearOrangeRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenBright
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceVariant
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.PureChartBg
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradeHubUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    uiState: TradeHubUiState,
    onSelectSymbol: (String) -> Unit,
    onSelectTimeframe: (String) -> Unit,
    onSetLotSize: (Double) -> Unit,
    onToggleFullscreen: () -> Unit,
    onPlaceOrderRequested: (
        side: String,
        lots: Double,
        stopLoss: Double,
        takeProfit: Double,
        skipConfirmDialog: Boolean,
    ) -> Unit,
    onCloseTrade: (TradeRecord) -> Unit,
    onUpdateOpenTradeLevels: (tradeId: String, stopLoss: Double, takeProfit: Double, notes: String) -> Unit,
    onEnableDemoMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val instrument = MarketSimulator.resolveInstrument(
        uiState.selectedSymbol,
        uiState.symbolSpecs[uiState.selectedSymbol],
    )
    val quote = uiState.quotes[uiState.selectedSymbol]
        ?: remember(uiState.selectedSymbol, uiState.selectedTimeframe, instrument.decimals) {
            MarketSimulator.buildQuoteForInstrument(
                instrument = instrument,
                timeframe = uiState.selectedTimeframe,
                nowEpochSec = System.currentTimeMillis() / 1000L,
            )
        }
    val openTradesForSymbol = uiState.trades.filter {
        it.status == "OPEN" && it.symbol == uiState.selectedSymbol
    }
    val allOpenTrades = uiState.trades.filter { it.status == "OPEN" }

    var showSymbolSheet by remember { mutableStateOf(false) }
    var showTimeframeSheet by remember { mutableStateOf(false) }
    var showLotsDialog by remember { mutableStateOf(false) }
    var showPositionsSheet by remember { mutableStateOf(false) }
    var pendingStopLoss by remember { mutableDoubleStateOf(0.0) }
    var pendingTakeProfit by remember { mutableDoubleStateOf(0.0) }

    // Pre-trade Risk Staging State (Buy/Sell -> drag SL on chart -> Confirm with auto-calculated lots)
    var stagedSide by remember(uiState.selectedSymbol) { mutableStateOf<String?>(null) }
    var stagedStopLoss by remember(uiState.selectedSymbol) { mutableDoubleStateOf(0.0) }
    var stagedTakeProfit by remember(uiState.selectedSymbol) { mutableDoubleStateOf(0.0) }

    BackHandler(enabled = stagedSide != null) {
        stagedSide = null
    }

    val activeAccount = uiState.activeAccount
    val depositCurrency = if (activeAccount != null && activeAccount.ctidTraderAccountId > 0L) {
        uiState.depositCurrency.ifBlank { activeAccount.currency.ifBlank { "USD" } }
    } else {
        activeAccount?.currency?.ifBlank { "USD" } ?: uiState.depositCurrency.ifBlank { "USD" }
    }
    val accountFloating = remember(uiState.trades, uiState.quotes, activeAccount?.id) {
        if (activeAccount == null) {
            uiState.metrics.floatingPnl
        } else {
            uiState.trades
                .filter { it.status == "OPEN" && it.accountId == activeAccount.id }
                .sumOf { MarketSimulator.calculateFloatingPnl(it, uiState.quotes[it.symbol]) }
        }
    }
    val riskCapital = when {
        activeAccount != null && uiState.settings.riskBasis == "equity" ->
            (activeAccount.balance + accountFloating).coerceAtLeast(0.0)
        activeAccount != null -> activeAccount.balance.coerceAtLeast(0.0)
        uiState.settings.riskBasis == "equity" -> uiState.metrics.totalEquity.coerceAtLeast(0.0)
        else -> uiState.metrics.totalBalance.coerceAtLeast(0.0)
    }

    val stagedDraft = stagedSide?.let { side ->
        val entry = if (side == "BUY") quote.ask else quote.bid
        val effectiveSl = if (stagedStopLoss > 0.0) {
            stagedStopLoss
        } else {
            initialStagedStopLoss(side, entry, quote, instrument)
        }
        val convRate = MarketSimulator.resolveQuoteToDepositRate(
            quoteCurrency = instrument.quoteCurrency,
            depositCurrency = depositCurrency,
            quotes = uiState.quotes,
            symbolHint = instrument.symbol,
            referencePrice = entry,
            chainRateOverride = uiState.conversionRates[uiState.selectedSymbol],
        )
        val plan = MarketSimulator.calculateRiskSizedLots(
            accountCapital = riskCapital,
            riskPercent = uiState.settings.defaultRiskPercent,
            entryPrice = entry,
            stopLossPrice = effectiveSl,
            instrument = instrument,
            quoteToDepositRate = convRate,
            fallbackLots = uiState.selectedLots,
        )
        StagedOrderDraft(
            side = side,
            entryPrice = entry,
            stopLoss = effectiveSl,
            takeProfit = stagedTakeProfit,
            calculatedLots = plan.calculatedLots,
            riskAmount = plan.riskAmount,
            actualRiskAmount = plan.actualRiskAmount,
            riskPercent = plan.riskPercent,
            stopDistancePips = plan.stopDistancePips,
            quoteToDepositRate = convRate,
            stepLots = plan.stepLots,
            isBelowMinVolume = plan.isBelowMinVolume,
            depositCurrency = depositCurrency,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureChartBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Candlestick Chart Area (always LTR for TradingView-style Y-axis & price overlays)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    val partialTpTiers = remember(uiState.settings.partialTpTiersSpec) {
                        MarketSimulator.parsePartialTpTiers(uiState.settings.partialTpTiersSpec)
                    }
                    InteractiveCandlestickChart(
                        candles = quote.candles,
                        quote = quote,
                        instrument = instrument,
                        openTrades = openTradesForSymbol,
                        onCloseTrade = onCloseTrade,
                        onUpdateTradeLevels = onUpdateOpenTradeLevels,
                        stagedOrder = stagedDraft,
                        onStagedStopLossChange = { newSl -> stagedStopLoss = newSl },
                        onStagedTakeProfitChange = { newTp -> stagedTakeProfit = newTp },
                        onToggleStagedTakeProfit = {
                            val currentDraft = stagedDraft ?: return@InteractiveCandlestickChart
                            if (stagedTakeProfit > 0.0) {
                                stagedTakeProfit = 0.0
                            } else {
                                val slDist = abs(currentDraft.entryPrice - currentDraft.stopLoss)
                                    .coerceAtLeast(instrument.tickStep * 5.0)
                                val rawTp = if (currentDraft.side == "BUY") {
                                    currentDraft.entryPrice + slDist * 2.0
                                } else {
                                    (currentDraft.entryPrice - slDist * 2.0)
                                        .coerceAtLeast(instrument.tickStep)
                                }
                                stagedTakeProfit = snapLevelPrice(rawTp, instrument.decimals)
                            }
                        },
                        autoPartialCloseEnabled = uiState.settings.autoPartialCloseAtOneToOne,
                        partialTpTiers = partialTpTiers,
                        showFibonacciRrOverlay = uiState.settings.showFibonacciRrOnChart,
                        modifier = Modifier.fillMaxSize(),
                    )

                // Top-left Fullscreen toggle button + Account indicator matching Screenshot 1
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141820).copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF283040), CircleShape)
                            .testTag("chart_fullscreen_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.isChartFullscreen) {
                                Icons.Rounded.FullscreenExit
                            } else {
                                Icons.Rounded.Fullscreen
                            },
                            contentDescription = "Toggle fullscreen chart",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    if (!uiState.isChartFullscreen) {
                        val activeAcc = uiState.activeAccount
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF141820).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFF283040)),
                            modifier = Modifier.clickable {
                                if (activeAcc == null) onEnableDemoMode()
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (activeAcc != null) BullGreenBright else BearOrangeRed),
                                )
                                Text(
                                    text = if (activeAcc != null) {
                                        val prefix = if (activeAcc.ctidTraderAccountId > 0L) {
                                            if (activeAcc.isLive) "cTrader LIVE • " else "cTrader DEMO • "
                                        } else {
                                            "${activeAcc.name} • "
                                        }
                                        "$prefix${MarketSimulator.formatCurrency(activeAcc.balance, activeAcc.currency)}"
                                    } else {
                                        "Tap to enable Demo"
                                    },
                                    style = MaterialTheme.typography.labelMedium.merge(MonoPriceStyle),
                                    color = TextPrimary,
                                )
                            }
                        }
                    }
                }

                // Bottom-of-chart floating pills: Symbol (XAUUSD >), Timeframe (m1), and Positions Drawer (|<)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 64.dp, bottom = 26.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Symbol Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1A1F29).copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, Color(0xFF2D3648)),
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showSymbolSheet = true }
                                .testTag("symbol_selector_pill"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = uiState.selectedSymbol,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Select symbol",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Timeframe Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1A1F29).copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, Color(0xFF2D3648)),
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showTimeframeSheet = true }
                                .testTag("timeframe_selector_pill"),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = uiState.selectedTimeframe,
                                    style = MaterialTheme.typography.labelLarge.merge(MonoPriceStyle),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Positions Drawer Toggle (|<)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1A1F29).copy(alpha = 0.92f),
                        border = BorderStroke(
                            1.dp,
                            if (allOpenTrades.isNotEmpty()) RoyalBlueBright else Color(0xFF2D3648)
                        ),
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showPositionsSheet = true }
                            .testTag("chart_positions_drawer_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Open positions",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            if (allOpenTrades.isNotEmpty()) {
                                Text(
                                    text = allOpenTrades.size.toString(),
                                    style = MaterialTheme.typography.labelMedium.merge(MonoPriceStyle),
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Candle countdown timer in bottom-right corner like Screenshot 1 (00:09)
                val mins = quote.secondsRemainingInCandle / 60
                val secs = quote.secondsRemainingInCandle % 60
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.labelMedium.merge(MonoPriceStyle),
                    color = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 4.dp),
                )
                }
            }

            // Iconic Sell | Lots | Buy Execution Bar (transforms into Confirm / Cancel during SL staging)
            OrderExecutionBar(
                bidPrice = MarketSimulator.formatPrice(quote.bid, instrument.decimals),
                askPrice = MarketSimulator.formatPrice(quote.ask, instrument.decimals),
                lots = stagedDraft?.calculatedLots ?: uiState.selectedLots,
                stepLots = instrument.stepLots,
                stagedOrder = stagedDraft,
                riskPercent = uiState.settings.defaultRiskPercent,
                onSellClick = {
                    val entry = quote.bid
                    stagedStopLoss = if (pendingStopLoss > entry) {
                        pendingStopLoss
                    } else {
                        initialStagedStopLoss("SELL", entry, quote, instrument)
                    }
                    stagedTakeProfit = if (pendingTakeProfit > 0.0 && pendingTakeProfit < entry) {
                        pendingTakeProfit
                    } else {
                        0.0
                    }
                    stagedSide = "SELL"
                },
                onBuyClick = {
                    val entry = quote.ask
                    stagedStopLoss = if (pendingStopLoss > 0.0 && pendingStopLoss < entry) {
                        pendingStopLoss
                    } else {
                        initialStagedStopLoss("BUY", entry, quote, instrument)
                    }
                    stagedTakeProfit = if (pendingTakeProfit > entry) {
                        pendingTakeProfit
                    } else {
                        0.0
                    }
                    stagedSide = "BUY"
                },
                onConfirmStagedClick = {
                    val draft = stagedDraft ?: return@OrderExecutionBar
                    stagedSide = null
                    onPlaceOrderRequested(
                        draft.side,
                        draft.calculatedLots,
                        draft.stopLoss,
                        draft.takeProfit,
                        true,
                    )
                },
                onCancelStagedClick = {
                    stagedSide = null
                },
                onLotsClick = { showLotsDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }

    if (showSymbolSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSymbolSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Select Instrument",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                ) {
                    items(uiState.availableSymbols) { symbolName ->
                        val inst = MarketSimulator.resolveInstrument(
                            symbolName,
                            uiState.symbolSpecs[symbolName],
                        )
                        val q = uiState.quotes[symbolName]
                        val isSelected = symbolName == uiState.selectedSymbol
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) RoyalBlueBright.copy(alpha = 0.16f) else CardSurfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueBright else CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectSymbol(symbolName)
                                    showSymbolSheet = false
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = inst.symbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${inst.displayName} • ${inst.category}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                                if (q != null) {
                                    Text(
                                        text = MarketSimulator.formatPrice(q.bid, inst.decimals),
                                        style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                                        color = BullGreenBright,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTimeframeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTimeframeSheet = false },
            containerColor = CardSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Chart Timeframe",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MarketSimulator.timeframes.forEach { (tf, _) ->
                        val selected = tf == uiState.selectedTimeframe
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onSelectTimeframe(tf)
                                showTimeframeSheet = false
                            },
                            label = {
                                Text(
                                    text = tf,
                                    style = MaterialTheme.typography.labelLarge.merge(MonoPriceStyle),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueBright,
                                selectedLabelColor = Color.White,
                                containerColor = CardSurfaceVariant,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showLotsDialog) {
        val riskBase = if (uiState.settings.riskBasis == "equity") {
            uiState.metrics.totalEquity
        } else {
            uiState.metrics.totalBalance
        }
        LotsAndRiskDialog(
            currentLots = uiState.selectedLots,
            currentSl = pendingStopLoss,
            currentTp = pendingTakeProfit,
            riskPercent = uiState.settings.defaultRiskPercent,
            equity = riskBase,
            instrument = instrument,
            onApply = { lots, sl, tp ->
                onSetLotSize(lots)
                pendingStopLoss = sl
                pendingTakeProfit = tp
                showLotsDialog = false
            },
            onDismiss = { showLotsDialog = false },
        )
    }

    if (showPositionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPositionsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Open Positions (${allOpenTrades.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = MarketSimulator.formatSignedCurrency(uiState.metrics.floatingPnl),
                        style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                        color = if (uiState.metrics.floatingPnl >= 0) BullGreenBright else BearOrangeRed,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (allOpenTrades.isEmpty()) {
                    Text(
                        text = "No open positions. Tap Sell or Buy on the chart to open a trade.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(260.dp),
                    ) {
                        items(allOpenTrades, key = { it.id }) { trade ->
                            TradeItemCard(
                                trade = trade,
                                liveQuote = uiState.quotes[trade.symbol],
                                onCloseClick = { onCloseTrade(trade) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun OrderExecutionBar(
    bidPrice: String,
    askPrice: String,
    lots: Double,
    stepLots: Double,
    stagedOrder: StagedOrderDraft?,
    riskPercent: Double,
    onSellClick: () -> Unit,
    onBuyClick: () -> Unit,
    onConfirmStagedClick: () -> Unit,
    onCancelStagedClick: () -> Unit,
    onLotsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .height(60.dp)
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                when (stagedOrder?.side) {
                    "BUY" -> {
                        // Left becomes Cancel, Right becomes Confirm BUY
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF1F2735))
                                .clickable(onClick = onCancelStagedClick)
                                .padding(horizontal = 12.dp)
                                .testTag("cancel_staged_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel order",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Edit Chart",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(BullGreen)
                                .clickable(onClick = onConfirmStagedClick)
                                .padding(horizontal = 12.dp)
                                .testTag("confirm_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Confirm BUY",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "@ $askPrice",
                                        style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Confirm buy order",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    "SELL" -> {
                        // Left becomes Confirm SELL, Right becomes Cancel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(BearOrangeRed)
                                .clickable(onClick = onConfirmStagedClick)
                                .padding(horizontal = 12.dp)
                                .testTag("confirm_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Confirm sell order",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Confirm SELL",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "@ $bidPrice",
                                        style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF1F2735))
                                .clickable(onClick = onCancelStagedClick)
                                .padding(horizontal = 12.dp)
                                .testTag("cancel_staged_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Edit Chart",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel order",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    else -> {
                        // Normal cTrader SELL / BUY halves
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(BearOrangeRed)
                                .clickable(onClick = onSellClick)
                                .padding(horizontal = 10.dp)
                                .testTag("sell_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Sell",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = bidPrice,
                                            style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(BullGreen)
                                .clickable(onClick = onBuyClick)
                                .padding(horizontal = 10.dp)
                                .testTag("buy_order_button"),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Buy",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = askPrice,
                                            style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Center Lots / Risk-Sized Volume Pill
            val formattedLots = MarketSimulator.formatLots(lots, stepLots)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF161A22),
                border = BorderStroke(1.dp, if (stagedOrder != null) RoyalBlueBright else Color(0xFF2B3242)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(if (stagedOrder != null) 120.dp else 108.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = stagedOrder == null, onClick = onLotsClick)
                    .testTag(if (stagedOrder != null) "staged_lots_pill" else "lots_selector_button"),
            ) {
                if (stagedOrder != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Risk ${String.format(Locale.US, "%.1f", riskPercent)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$formattedLots Lots",
                            style = MaterialTheme.typography.titleSmall.merge(MonoPriceStyle),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Lots",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = formattedLots,
                                style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Change lots",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LotsAndRiskDialog(
    currentLots: Double,
    currentSl: Double,
    currentTp: Double,
    riskPercent: Double,
    equity: Double,
    instrument: MarketInstrument,
    onApply: (lots: Double, sl: Double, tp: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var lotsText by remember { mutableStateOf(MarketSimulator.formatLots(currentLots, instrument.stepLots)) }
    var slText by remember { mutableStateOf(if (currentSl > 0) currentSl.toString() else "") }
    var tpText by remember { mutableStateOf(if (currentTp > 0) currentTp.toString() else "") }

    val recommendedLots = remember(equity, riskPercent, instrument.symbol, instrument.lotSizeInCents) {
        MarketSimulator.calculateRecommendedLots(equity, riskPercent, instrument.symbol)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text("Order Volume & Levels", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Preset Lot Sizes",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0.01, 0.05, 0.1, 0.5, 1.0).forEach { preset ->
                        FilterChip(
                            selected = lotsText.toDoubleOrNull() == preset,
                            onClick = { lotsText = preset.toString() },
                            label = { Text("$preset", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueBright,
                                selectedLabelColor = Color.White,
                                containerColor = CardSurfaceVariant,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }

                OutlinedTextField(
                    value = lotsText,
                    onValueChange = { lotsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Lots") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = { lotsText = recommendedLots.toString() },
                ) {
                    Text(
                        text = "Use Risk-Sized Lots (${recommendedLots} for ${riskPercent}% risk)",
                        color = Color(0xFF60A5FA),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = slText,
                        onValueChange = { slText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Stop Loss (Opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tpText,
                        onValueChange = { tpText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Take Profit (Opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedLots = (lotsText.toDoubleOrNull() ?: currentLots)
                        .coerceIn(instrument.minLots, instrument.maxLots)
                    val parsedSl = slText.toDoubleOrNull() ?: 0.0
                    val parsedTp = tpText.toDoubleOrNull() ?: 0.0
                    onApply(parsedLots, parsedSl, parsedTp)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

private fun initialStagedStopLoss(
    side: String,
    entryPrice: Double,
    quote: LiveQuote,
    instrument: MarketInstrument,
): Double {
    val recent = quote.candles.takeLast(24)
    val recentSpan = if (recent.isNotEmpty()) {
        recent.maxOf { it.high } - recent.minOf { it.low }
    } else {
        0.0
    }
    val step = max(
        instrument.tickStep * 25.0,
        max(entryPrice * 0.0022, recentSpan * 0.25),
    )
    val raw = if (side == "BUY") {
        (entryPrice - step).coerceAtLeast(instrument.tickStep)
    } else {
        entryPrice + step
    }
    return snapLevelPrice(raw, instrument.decimals)
}

private fun snapLevelPrice(price: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals.coerceIn(0, 8))
    return (price * factor).roundToLong() / factor
}

