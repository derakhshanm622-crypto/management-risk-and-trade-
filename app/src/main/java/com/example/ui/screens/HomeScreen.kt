package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import com.example.market.MarketSimulator
import com.example.ui.components.TradeItemCard
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
import com.example.ui.theme.RoyalBlueDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradeHubUiState

@Composable
fun HomeScreen(
    uiState: TradeHubUiState,
    onOpenTradeTab: () -> Unit,
    onOpenAccountsTab: () -> Unit,
    onOpenHistoryTab: () -> Unit,
    onOpenAddAccountDialog: () -> Unit,
    onEnableDemoMode: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onCloseTrade: (TradeRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBg),
    ) {
        // Royal Blue Command Header matching Screenshot 3
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HeaderBlueTop, HeaderBlueBottom)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (uiState.settings.demoModeEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "DEMO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.subtitle_command_center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                IconButton(
                    onClick = onOpenTradeTab,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .testTag("header_quick_trade_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropSquare,
                        contentDescription = "Open Trade Terminal",
                        tint = Color.White,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Total Portfolio Equity Card matching Screenshot 3
            item {
                PortfolioEquityCard(
                    uiState = uiState,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }

            // ACCOUNTS Section
            item {
                SectionHeaderRow(
                    title = stringResource(R.string.section_accounts),
                    actionLabel = stringResource(R.string.manage_arrow),
                    onActionClick = onOpenAccountsTab,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }

            if (uiState.visibleAccounts.isEmpty()) {
                item {
                    EmptyAccountsCard(
                        onAddAccountClick = onOpenAddAccountDialog,
                        onEnableDemoClick = onEnableDemoMode,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            } else {
                items(uiState.visibleAccounts.take(3), key = { it.id }) { account ->
                    HomeAccountSummaryCard(
                        account = account,
                        isSelected = account.id == uiState.activeAccount?.id,
                        onClick = { onSelectAccount(account.id) },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }

            // RECENT TRADES Section
            item {
                SectionHeaderRow(
                    title = stringResource(R.string.section_recent_trades),
                    actionLabel = stringResource(R.string.see_all_arrow),
                    onActionClick = onOpenHistoryTab,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }

            if (uiState.trades.isEmpty()) {
                item {
                    EmptyRecentTradesCard(
                        onOpenTradeTab = onOpenTradeTab,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            } else {
                items(uiState.trades.take(5), key = { it.id }) { trade ->
                    TradeItemCard(
                        trade = trade,
                        liveQuote = uiState.quotes[trade.symbol],
                        onCloseClick = if (trade.status == "OPEN") {
                            { onCloseTrade(trade) }
                        } else {
                            null
                        },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioEquityCard(
    uiState: TradeHubUiState,
    modifier: Modifier = Modifier,
) {
    val metrics = uiState.metrics
    val pnlColor = if (metrics.todaysPnl >= 0) Color.White else Color(0xFFFCA5A5)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(RoyalBlueBright, RoyalBlueDeep)
                )
            )
            .padding(22.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.total_portfolio_equity),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 1.2.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = MarketSimulator.formatCurrency(metrics.totalEquity),
                style = MaterialTheme.typography.displaySmall.merge(MonoPriceStyle),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EquityMetricColumn(
                    value = MarketSimulator.formatSignedCurrency(metrics.todaysPnl),
                    label = stringResource(R.string.todays_pnl),
                    valueColor = pnlColor,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                EquityMetricColumn(
                    value = metrics.openTradesCount.toString(),
                    label = stringResource(R.string.open_trades),
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                EquityMetricColumn(
                    value = "${metrics.winRatePercent}%",
                    label = stringResource(R.string.win_rate),
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EquityMetricColumn(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF60A5FA),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onActionClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyAccountsCard(
    onAddAccountClick: () -> Unit,
    onEnableDemoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.no_accounts_connected),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.no_accounts_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onAddAccountClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurfaceVariant,
                        contentColor = TextPrimary,
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_add_account_button"),
                ) {
                    Text(
                        text = stringResource(R.string.add_account),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = onEnableDemoClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBlueBright.copy(alpha = 0.22f),
                        contentColor = Color(0xFF93C5FD),
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_enable_demo_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Demo Mode", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HomeAccountSummaryCard(
    account: TradingAccount,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDemo = account.accountType == "demo"
    val pnl = account.balance - account.initialBalance

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, if (isSelected) RoyalBlueBright else CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDemo) RoyalBlueBright.copy(alpha = 0.2f) else BullGreenBright.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (isDemo) "DEMO" else account.broker.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDemo) Color(0xFF93C5FD) else BullGreenBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (isSelected) "Active trading account" else "Tap to select",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color(0xFF60A5FA) else TextMuted,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MarketSimulator.formatCurrency(account.balance, account.currency),
                    style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = MarketSimulator.formatSignedCurrency(pnl, account.currency),
                    style = MaterialTheme.typography.labelSmall.merge(MonoPriceStyle),
                    color = if (pnl >= 0) BullGreenBright else BearOrangeRed,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecentTradesCard(
    onOpenTradeTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No recent trades yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Open the candlestick chart to place an order or log a journal trade.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onOpenTradeTab,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlueBright,
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CandlestickChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Chart Terminal", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
