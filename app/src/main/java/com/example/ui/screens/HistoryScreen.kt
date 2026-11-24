package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.TradeRecord
import com.example.market.MarketSimulator
import com.example.ui.components.TradeItemCard
import com.example.ui.components.tradeHubFieldColors
import com.example.ui.theme.BearOrangeRed
import com.example.ui.theme.BullGreenBright
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceVariant
import com.example.ui.theme.DeepSpaceBg
import com.example.ui.theme.HeaderBlueBottom
import com.example.ui.theme.HeaderBlueTop
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradeHubUiState

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    CLOSED("Closed"),
}

@Composable
fun HistoryScreen(
    uiState: TradeHubUiState,
    onCloseTrade: (TradeRecord) -> Unit,
    onUpdateOpenLevels: (tradeId: String, sl: Double, tp: Double, notes: String) -> Unit,
    onLogJournalTrade: (
        symbol: String,
        side: String,
        lots: Double,
        entry: Double,
        exit: Double,
        notes: String,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var editingTrade by remember { mutableStateOf<TradeRecord?>(null) }
    var showLogTradeDialog by remember { mutableStateOf(false) }

    val filteredTrades = remember(uiState.trades, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> uiState.trades
            HistoryFilter.OPEN -> uiState.trades.filter { it.status == "OPEN" }
            HistoryFilter.CLOSED -> uiState.trades.filter { it.status == "CLOSED" }
        }
    }

    val closedTrades = uiState.trades.filter { it.status == "CLOSED" }
    val realizedNet = closedTrades.sumOf { it.realizedPnl }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(colors = listOf(HeaderBlueTop, HeaderBlueBottom))
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Trade History",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${uiState.metrics.openTradesCount} open • ${closedTrades.size} closed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                Button(
                    onClick = { showLogTradeDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("log_journal_trade_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Trade", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Summary Row
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryStatPill(
                        label = "REALIZED P&L",
                        value = MarketSimulator.formatSignedCurrency(realizedNet),
                        valueColor = if (realizedNet >= 0) BullGreenBright else BearOrangeRed,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatPill(
                        label = "FLOATING P&L",
                        value = MarketSimulator.formatSignedCurrency(uiState.metrics.floatingPnl),
                        valueColor = if (uiState.metrics.floatingPnl >= 0) BullGreenBright else BearOrangeRed,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatPill(
                        label = "WIN RATE",
                        value = "${uiState.metrics.winRatePercent}%",
                        valueColor = TextPrimary,
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlueBright,
                                selectedLabelColor = Color.White,
                                containerColor = CardSurface,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }

            if (filteredTrades.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No trades in this view",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Execute trades on the chart terminal or tap Log Trade to record a manual journal entry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(filteredTrades, key = { it.id }) { trade ->
                    TradeItemCard(
                        trade = trade,
                        liveQuote = uiState.quotes[trade.symbol],
                        onCloseClick = if (trade.status == "OPEN") {
                            { onCloseTrade(trade) }
                        } else {
                            null
                        },
                        onEditClick = if (trade.status == "OPEN") {
                            { editingTrade = trade }
                        } else {
                            null
                        },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }

    editingTrade?.let { trade ->
        EditOpenTradeDialog(
            trade = trade,
            onSave = { sl, tp, notes ->
                onUpdateOpenLevels(trade.id, sl, tp, notes)
                editingTrade = null
            },
            onDismiss = { editingTrade = null },
        )
    }

    if (showLogTradeDialog) {
        LogJournalTradeDialog(
            defaultSymbol = uiState.selectedSymbol,
            onSave = { symbol, side, lots, entry, exit, notes ->
                onLogJournalTrade(symbol, side, lots, entry, exit, notes)
                showLogTradeDialog = false
            },
            onDismiss = { showLogTradeDialog = false },
        )
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditOpenTradeDialog(
    trade: TradeRecord,
    onSave: (sl: Double, tp: Double, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var slText by remember { mutableStateOf(if (trade.stopLoss > 0) trade.stopLoss.toString() else "") }
    var tpText by remember { mutableStateOf(if (trade.takeProfit > 0) trade.takeProfit.toString() else "") }
    var notes by remember { mutableStateOf(trade.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        title = { Text("Edit ${trade.symbol} Position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = slText,
                    onValueChange = { slText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Stop Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tpText,
                    onValueChange = { tpText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Take Profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(300) },
                    label = { Text("Journal Notes") },
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        slText.toDoubleOrNull() ?: 0.0,
                        tpText.toDoubleOrNull() ?: 0.0,
                        notes,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun LogJournalTradeDialog(
    defaultSymbol: String,
    onSave: (symbol: String, side: String, lots: Double, entry: Double, exit: Double, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var symbol by remember { mutableStateOf(defaultSymbol) }
    var side by remember { mutableStateOf("BUY") }
    var lotsText by remember { mutableStateOf("0.1") }
    var entryText by remember { mutableStateOf("4164.20") }
    var exitText by remember { mutableStateOf("4168.50") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        title = { Text("Log Journal Trade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BUY", "SELL").forEach { dir ->
                        FilterChip(
                            selected = side == dir,
                            onClick = { side = dir },
                            label = { Text(dir) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (dir == "BUY") BullGreenBright else BearOrangeRed,
                                selectedLabelColor = Color.White,
                                containerColor = CardSurfaceVariant,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase().take(12) },
                        label = { Text("Symbol") },
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lotsText,
                        onValueChange = { lotsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Lots") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(0.8f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Entry Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = exitText,
                        onValueChange = { exitText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Exit Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(300) },
                    label = { Text("Notes / Setup") },
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lots = (lotsText.toDoubleOrNull() ?: 0.1).coerceAtLeast(0.01)
                    val entry = entryText.toDoubleOrNull() ?: 0.0
                    val exit = exitText.toDoubleOrNull() ?: 0.0
                    if (entry > 0 && exit > 0 && symbol.isNotBlank()) {
                        onSave(symbol, side, lots, entry, exit, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
