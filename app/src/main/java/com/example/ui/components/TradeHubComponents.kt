package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveQuote
import com.example.data.model.TradeRecord
import com.example.market.MarketSimulator
import com.example.ui.theme.BearOrangeRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenBright
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceVariant
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TradeItemCard(
    trade: TradeRecord,
    liveQuote: LiveQuote?,
    onCloseClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isOpen = trade.status == "OPEN"
    val pnl = if (isOpen) {
        MarketSimulator.calculateFloatingPnl(trade, liveQuote)
    } else {
        trade.realizedPnl
    }
    val inst = MarketSimulator.getInstrument(trade.symbol)
    val currentDisplayPrice = if (isOpen && liveQuote != null) {
        if (trade.side == "BUY") liveQuote.bid else liveQuote.ask
    } else {
        trade.exitPrice
    }
    val isBuy = trade.side == "BUY"
    val sideColor = if (isBuy) BullGreenBright else BearOrangeRed
    val pnlColor = if (pnl >= 0) BullGreenBright else BearOrangeRed

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = trade.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sideColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = "${trade.side} ${trade.lots}",
                            style = MaterialTheme.typography.labelSmall.merge(MonoPriceStyle),
                            color = sideColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                    if (trade.isDemo) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RoyalBlueBright.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Text(
                    text = MarketSimulator.formatSignedCurrency(pnl),
                    style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                    color = pnlColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    val entryText = MarketSimulator.formatPrice(trade.entryPrice, inst.decimals)
                    val targetText = if (currentDisplayPrice > 0) {
                        MarketSimulator.formatPrice(currentDisplayPrice, inst.decimals)
                    } else {
                        "—"
                    }
                    Text(
                        text = "$entryText → $targetText",
                        style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                        color = TextSecondary,
                    )
                    Text(
                        text = trade.accountName + if (trade.notes.isNotBlank()) " • ${trade.notes}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                    )
                }

                if (isOpen && (onCloseClick != null || onEditClick != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onEditClick != null) {
                            OutlinedButton(
                                onClick = onEditClick,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text(
                                    text = "SL/TP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                )
                            }
                        }
                        if (onCloseClick != null) {
                            Button(
                                onClick = onCloseClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BearOrangeRed.copy(alpha = 0.18f),
                                    contentColor = BearOrangeRed,
                                ),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close position",
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Close",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onCreateAccount: (
        name: String,
        broker: String,
        accountType: String,
        currency: String,
        initialBalance: Double,
        apiKeyReference: String,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var broker by remember { mutableStateOf("cTrader") }
    var accountType by remember { mutableStateOf("journal") }
    var currency by remember { mutableStateOf("USD") }
    var balanceText by remember { mutableStateOf("10000") }
    var apiKeyRef by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = RoyalBlueBright,
            )
        },
        title = {
            Text(
                text = "Add Trading Account",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("journal" to "Journal", "broker_ref" to "Broker Ref").forEach { (type, label) ->
                        FilterChip(
                            selected = accountType == type,
                            onClick = { accountType = type },
                            label = { Text(label) },
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. FTMO Swing 10k") },
                    singleLine = true,
                    colors = tradeHubFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_name_input"),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = broker,
                        onValueChange = { broker = it },
                        label = { Text("Broker / Platform") },
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(1.3f),
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(5) },
                        label = { Text("Currency") },
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.weight(0.7f),
                    )
                }

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Starting Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = tradeHubFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (accountType == "broker_ref") {
                    OutlinedTextField(
                        value = apiKeyRef,
                        onValueChange = { apiKeyRef = it },
                        label = { Text("API Key Reference Alias") },
                        placeholder = { Text("e.g. vault:ctrader-prod-01") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        },
                        supportingText = {
                            Text(
                                text = "Stored as a reference label only — never enter plaintext secrets.",
                                color = TextMuted,
                            )
                        },
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = balanceText.toDoubleOrNull() ?: 0.0
                    onCreateAccount(name, broker, accountType, currency, balance, apiKeyRef)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
                modifier = Modifier.testTag("confirm_add_account_button"),
            ) {
                Text("Connect Account")
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
fun TradeConfirmationDialog(
    symbol: String,
    side: String,
    lots: Double,
    priceText: String,
    riskPercent: Double,
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isBuy = side == "BUY"
    val accent = if (isBuy) BullGreen else BearOrangeRed

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        title = {
            Text("Confirm $side Order", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Verify execution details before routing order:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardSurfaceVariant,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ConfirmDetailRow("Symbol", symbol)
                        ConfirmDetailRow("Direction", side, valueColor = accent)
                        ConfirmDetailRow("Volume", "$lots lots")
                        ConfirmDetailRow("Market Price", priceText)
                        ConfirmDetailRow("Account", accountName)
                        ConfirmDetailRow("Risk Profile", "${riskPercent}% equity")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.testTag("confirm_trade_execution_button"),
            ) {
                Text("Execute $side", fontWeight = FontWeight.Bold)
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
private fun ConfirmDetailRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun tradeHubFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = RoyalBlueBright,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = RoyalBlueBright,
    unfocusedLabelColor = TextSecondary,
    cursorColor = RoyalBlueBright,
    focusedContainerColor = CardSurfaceVariant.copy(alpha = 0.5f),
    unfocusedContainerColor = CardSurfaceVariant.copy(alpha = 0.35f),
)
