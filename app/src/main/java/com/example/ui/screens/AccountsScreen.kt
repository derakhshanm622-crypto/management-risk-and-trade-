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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.TradingAccount
import com.example.market.MarketSimulator
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

@Composable
fun AccountsScreen(
    uiState: TradeHubUiState,
    onOpenAddAccountDialog: () -> Unit,
    onOpenCTraderAuthScreen: () -> Unit,
    onEnableDemoMode: () -> Unit,
    onResetDemoBalance: () -> Unit,
    onSelectActiveAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        text = "Accounts",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${uiState.visibleAccounts.size} connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                Button(
                    onClick = onOpenAddAccountDialog,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("accounts_add_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Demo Mode Banner
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.settings.demoModeEnabled) RoyalBlueBright else CardBorder
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RoyalBlueBright.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                )
                            }
                            Column {
                                Text(
                                    text = "Demo Practice Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (uiState.settings.demoModeEnabled) {
                                        "Active — Virtual $10,000 practice funds"
                                    } else {
                                        "Disabled — Enable in Settings or here"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }

                        if (uiState.settings.demoModeEnabled) {
                            OutlinedButton(
                                onClick = onResetDemoBalance,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reset demo balance",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", color = TextPrimary)
                            }
                        } else {
                            Button(
                                onClick = onEnableDemoMode,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }

            if (uiState.visibleAccounts.isEmpty()) {
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
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No accounts connected",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connect cTrader via OAuth2 or add a journal account to track equity and risk per trade.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenCTraderAuthScreen,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("accounts_connect_ctrader_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect cTrader OAuth2", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                items(uiState.visibleAccounts, key = { it.id }) { account ->
                    AccountDetailCard(
                        account = account,
                        isActive = account.id == uiState.activeAccount?.id,
                        onSelect = { onSelectActiveAccount(account.id) },
                        onDelete = { onDeleteAccount(account.id) },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDetailCard(
    account: TradingAccount,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDemo = account.accountType == "demo"
    val pnl = account.balance - account.initialBalance

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, if (isActive) RoyalBlueBright else CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CardSurfaceVariant,
                    ) {
                        Text(
                            text = when (account.accountType) {
                                "demo" -> "DEMO"
                                "broker_ref" -> "BROKER REF"
                                else -> "JOURNAL"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Active account",
                            tint = BullGreenBright,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (!isDemo) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove account",
                                tint = TextMuted,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${account.broker} • ${account.currency}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    if (account.apiKeyReference.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Ref: ${account.apiKeyReference}",
                                style = MaterialTheme.typography.labelSmall.merge(MonoPriceStyle),
                                color = TextMuted,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MarketSimulator.formatCurrency(account.balance, account.currency),
                        style = MaterialTheme.typography.titleLarge.merge(MonoPriceStyle),
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
}
