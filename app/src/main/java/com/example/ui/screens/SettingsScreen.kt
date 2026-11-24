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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StackedLineChart
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ctrader.CTraderConnectionState
import com.example.data.model.PartialTpTier
import com.example.market.MarketSimulator
import com.example.ui.auth.BiometricHelper
import com.example.ui.components.tradeHubFieldColors
import com.example.ui.theme.BearOrangeRed
import com.example.ui.theme.BullGreenBright
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceVariant
import com.example.ui.theme.DeepSpaceBg
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.HeaderBlueBottom
import com.example.ui.theme.HeaderBlueTop
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.RoyalBlueBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradeHubUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: TradeHubUiState,
    onOpenCTraderAuthScreen: () -> Unit,
    onSelectCTraderEnv: (String) -> Unit,
    onDisconnectCTrader: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onSaveRiskPercent: (Double) -> Unit,
    onSelectRiskBasis: (String) -> Unit,
    onToggleAutoPartialClose: (Boolean) -> Unit,
    onUpdatePartialTpTiers: (List<PartialTpTier>) -> Unit,
    onApplyQuickFiftyOneToOnePreset: () -> Unit,
    onToggleFibonacciRrOnChart: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleTradeConfirmation: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val biometricAvailable = remember(context) {
        BiometricHelper.isBiometricHardwareAvailable(context)
    }

    var selectedEnv by remember { mutableStateOf(uiState.settings.ctraderEnv.ifBlank { "demo" }) }
    LaunchedEffect(uiState.settings.ctraderEnv) {
        selectedEnv = uiState.settings.ctraderEnv.ifBlank { "demo" }
    }

    var riskInput by remember { mutableStateOf(uiState.settings.defaultRiskPercent.toString()) }
    var riskSlider by remember { mutableFloatStateOf(uiState.settings.defaultRiskPercent.toFloat()) }
    LaunchedEffect(uiState.settings.defaultRiskPercent) {
        riskInput = uiState.settings.defaultRiskPercent.toString()
        riskSlider = uiState.settings.defaultRiskPercent.toFloat().coerceIn(0.1f, 5.0f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBg),
    ) {
        // Royal Blue Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(colors = listOf(HeaderBlueTop, HeaderBlueBottom))
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterStart),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // CTRADER OPEN API ENTRY CARD -> OPENS DEDICATED CTraderAuthScreen
            item {
                SettingsSection(
                    title = stringResource(R.string.section_ctrader_api),
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val (statusColor, statusLabel) = when {
                                uiState.isOAuthWaiting -> Color(0xFFFBBF24) to "Waiting for cTrader login..."
                                uiState.ctraderConnectionState == CTraderConnectionState.CONNECTED ->
                                    BullGreenBright to "Connected (${selectedEnv.uppercase()})"
                                uiState.ctraderConnectionState == CTraderConnectionState.CONNECTING ||
                                    uiState.ctraderConnectionState == CTraderConnectionState.AUTHORIZING ->
                                    Color(0xFF60A5FA) to "Connecting..."
                                uiState.ctraderConnectionState == CTraderConnectionState.ERROR ->
                                    BearOrangeRed to "Connection Error"
                                else -> TextMuted to "Not Connected"
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(statusColor),
                                )
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(CardSurfaceVariant)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf("live" to "Live", "demo" to "Demo").forEach { (envKey, label) ->
                                    val isSelected = selectedEnv == envKey
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) RoyalBlueBright else Color.Transparent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                selectedEnv = envKey
                                                onSelectCTraderEnv(envKey)
                                            }
                                            .testTag("ctrader_env_$envKey"),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        val savedClientId = uiState.settings.ctraderClientId
                        Text(
                            text = if (savedClientId.isNotBlank()) {
                                "Client ID configured (${savedClientId.take(8)}...)"
                            } else {
                                "Configure your cTrader Client ID & Client Secret to sign in and stream live quotes."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onOpenCTraderAuthScreen,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalBlueBright,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(50.dp)
                                    .testTag("open_ctrader_auth_screen_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.ctrader_open_auth_screen),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            if (uiState.settings.ctraderAccessToken.isNotBlank() ||
                                uiState.ctraderConnectionState == CTraderConnectionState.CONNECTED
                            ) {
                                OutlinedButton(
                                    onClick = onDisconnectCTrader,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(50.dp)
                                        .testTag("ctrader_disconnect_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ctrader_logout_button),
                                        color = BearOrangeRed,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RISK MANAGEMENT (with slider + Balance / Equity basis matching screenshot)
            item {
                SettingsSection(
                    title = stringResource(R.string.section_risk_management),
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Rounded.Shield,
                                tint = Color(0xFF60A5FA),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.default_risk_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.default_risk_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2f%%", riskSlider),
                                style = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = riskSlider,
                            onValueChange = { raw ->
                                val snapped = ((raw * 4f).roundToInt() / 4f).coerceIn(0.25f, 5.0f)
                                riskSlider = snapped
                                riskInput = snapped.toString()
                            },
                            onValueChangeFinished = {
                                onSaveRiskPercent(riskSlider.toDouble())
                            },
                            valueRange = 0.25f..5.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = RoyalBlueBright,
                                activeTrackColor = RoyalBlueBright,
                                inactiveTrackColor = CardSurfaceVariant,
                            ),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = riskInput,
                                onValueChange = { riskInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.titleMedium.merge(MonoPriceStyle),
                                colors = tradeHubFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("risk_percent_input"),
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                            )
                            Button(
                                onClick = {
                                    val value = riskInput.toDoubleOrNull() ?: 1.0
                                    onSaveRiskPercent(value)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalBlueBright,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("save_risk_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.save),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0.5, 1.0, 2.0).forEach { preset ->
                                val isSelected = uiState.settings.defaultRiskPercent == preset
                                val label = if (preset == 0.5) "0.5%" else "${preset.toInt()}%"
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) RoyalBlueBright else CardSurfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) RoyalBlueBright else CardBorder
                                    ),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            riskInput = preset.toString()
                                            riskSlider = preset.toFloat()
                                            onSaveRiskPercent(preset)
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge.merge(MonoPriceStyle),
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = DividerSubtle)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.risk_basis_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("balance" to "Balance", "equity" to "Equity").forEach { (basis, label) ->
                                    val selected = uiState.settings.riskBasis == basis
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) RoyalBlueBright else CardSurfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) RoyalBlueBright else CardBorder
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSelectRiskBasis(basis) },
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) Color.White else TextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DividerSubtle)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Rounded.PieChart,
                                    tint = BullGreenBright,
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.auto_partial_close_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_partial_close_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.settings.autoPartialCloseAtOneToOne,
                                onCheckedChange = onToggleAutoPartialClose,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BullGreenBright,
                                    uncheckedThumbColor = Color(0xFFCBD5E1),
                                    uncheckedTrackColor = Color(0xFF334155),
                                ),
                                modifier = Modifier.testTag("auto_partial_close_switch"),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Multi-step TP Partial Close configuration card
                        val tiers = remember(uiState.settings.partialTpTiersSpec) {
                            MarketSimulator.parsePartialTpTiers(uiState.settings.partialTpTiersSpec)
                        }
                        val totalPartialPct = tiers.sumOf { it.closePercent }.roundToInt().coerceIn(0, 100)
                        val remainingForFinalTpPct = (100 - totalPartialPct).coerceAtLeast(0)
                        val isQuickFiftyPreset = uiState.settings.autoPartialCloseAtOneToOne &&
                            tiers.size == 1 &&
                            abs(tiers.first().rrMultiple - 1.0) < 0.05 &&
                            abs(tiers.first().closePercent - 50.0) < 0.5

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardSurfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(
                                1.dp,
                                if (uiState.settings.autoPartialCloseAtOneToOne) {
                                    BullGreenBright.copy(alpha = 0.45f)
                                } else {
                                    CardBorder
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.partial_tp_steps_title)} (${tiers.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isQuickFiftyPreset) {
                                            BullGreenBright.copy(alpha = 0.22f)
                                        } else {
                                            Color(0xFF1E293B)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isQuickFiftyPreset) BullGreenBright else CardBorder,
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onApplyQuickFiftyOneToOnePreset() }
                                            .testTag("preset_fifty_one_to_one_button"),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.preset_fifty_one_to_one),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isQuickFiftyPreset) BullGreenBright else Color(0xFF93C5FD),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }

                                tiers.forEachIndexed { idx, tier ->
                                    val otherPct = tiers.filterIndexed { i, _ -> i != idx }.sumOf { it.closePercent }
                                    val maxThisPct = (100.0 - otherPct).coerceAtLeast(5.0)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0F2F2E),
                                            border = BorderStroke(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.5f)),
                                        ) {
                                            Text(
                                                text = "TP${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall.merge(MonoPriceStyle),
                                                color = Color(0xFF2DD4BF),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            )
                                        }

                                        // R:R Multiple stepper
                                        TierStepperPill(
                                            label = "1:${MarketSimulator.formatRrMultiple(tier.rrMultiple)}",
                                            onDecrement = {
                                                val nextRr = (tier.rrMultiple - 0.5).coerceAtLeast(0.5)
                                                val updated = tiers.toMutableList()
                                                updated[idx] = tier.copy(rrMultiple = nextRr)
                                                onUpdatePartialTpTiers(updated)
                                            },
                                            onIncrement = {
                                                val nextRr = (tier.rrMultiple + 0.5).coerceAtMost(10.0)
                                                val updated = tiers.toMutableList()
                                                updated[idx] = tier.copy(rrMultiple = nextRr)
                                                onUpdatePartialTpTiers(updated)
                                            },
                                            accentColor = Color(0xFF60A5FA),
                                            modifier = Modifier.weight(1f),
                                        )

                                        // Close Percentage stepper
                                        TierStepperPill(
                                            label = "${tier.closePercent.roundToInt()}%",
                                            onDecrement = {
                                                val nextPct = (tier.closePercent - 10.0).coerceAtLeast(5.0)
                                                val updated = tiers.toMutableList()
                                                updated[idx] = tier.copy(closePercent = nextPct)
                                                onUpdatePartialTpTiers(updated)
                                            },
                                            onIncrement = {
                                                val nextPct = (tier.closePercent + 10.0).coerceAtMost(maxThisPct)
                                                val updated = tiers.toMutableList()
                                                updated[idx] = tier.copy(closePercent = nextPct)
                                                onUpdatePartialTpTiers(updated)
                                            },
                                            accentColor = BullGreenBright,
                                            modifier = Modifier.weight(1f),
                                        )

                                        if (tiers.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val updated = tiers.filterIndexed { i, _ -> i != idx }
                                                    onUpdatePartialTpTiers(updated)
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("remove_tp_step_$idx"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Remove TP step",
                                                    tint = BearOrangeRed,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (remainingForFinalTpPct > 0) {
                                            "Auto: $totalPartialPct% • Runs to your TP: $remainingForFinalTpPct%"
                                        } else {
                                            "Auto: 100% closed across ${tiers.size} steps"
                                        },
                                        style = MaterialTheme.typography.bodySmall.merge(MonoPriceStyle),
                                        color = TextSecondary,
                                    )

                                    if (tiers.size < 4 && totalPartialPct < 100) {
                                        val nextRr = ((tiers.maxOfOrNull { it.rrMultiple } ?: 1.0) + 1.0)
                                            .coerceAtMost(10.0)
                                        val nextPct = (100 - totalPartialPct).coerceAtMost(25).toDouble()
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = RoyalBlueBright.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, RoyalBlueBright),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onUpdatePartialTpTiers(
                                                        tiers + PartialTpTier(
                                                            rrMultiple = nextRr,
                                                            closePercent = nextPct,
                                                        )
                                                    )
                                                }
                                                .testTag("add_tp_step_button"),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.add_tp_step),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color(0xFF93C5FD),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DividerSubtle)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Rounded.StackedLineChart,
                                    tint = Color(0xFF2DD4BF),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.fibonacci_rr_chart_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.fibonacci_rr_chart_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.settings.showFibonacciRrOnChart,
                                onCheckedChange = onToggleFibonacciRrOnChart,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF14B8A6),
                                    uncheckedThumbColor = Color(0xFFCBD5E1),
                                    uncheckedTrackColor = Color(0xFF334155),
                                ),
                                modifier = Modifier.testTag("fibonacci_rr_chart_switch"),
                            )
                        }
                    }
                }
            }

            // LOCAL PRACTICE MODE
            item {
                SettingsSection(
                    title = stringResource(R.string.section_trading_mode),
                    modifier = Modifier.widthIn(max = 600.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Rounded.School,
                                tint = Color(0xFF60A5FA),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.demo_mode_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (uiState.settings.demoModeEnabled) {
                                        stringResource(R.string.demo_mode_enabled)
                                    } else {
                                        stringResource(R.string.demo_mode_disabled)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }

                        Switch(
                            checked = uiState.settings.demoModeEnabled,
                            onCheckedChange = onToggleDemoMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RoyalBlueBright,
                                uncheckedThumbColor = Color(0xFFCBD5E1),
                                uncheckedTrackColor = Color(0xFF334155),
                            ),
                            modifier = Modifier.testTag("demo_mode_switch"),
                        )
                    }
                }
            }

            // SECURITY
            item {
                SettingsSection(
                    title = stringResource(R.string.section_security),
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Rounded.Fingerprint,
                                    tint = Color(0xFF60A5FA),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.biometric_auth_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (biometricAvailable) {
                                            stringResource(R.string.biometric_available)
                                        } else {
                                            stringResource(R.string.biometric_fallback)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.settings.biometricAuthEnabled,
                                onCheckedChange = onToggleBiometric,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BullGreenBright,
                                    uncheckedThumbColor = Color(0xFFCBD5E1),
                                    uncheckedTrackColor = Color(0xFF334155),
                                ),
                                modifier = Modifier.testTag("biometric_switch"),
                            )
                        }

                        HorizontalDivider(color = DividerSubtle)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Rounded.Lock,
                                    tint = Color(0xFF60A5FA),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.trade_confirmation_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.trade_confirmation_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (uiState.settings.tradeConfirmationAlwaysOn) {
                                    RoyalBlueBright.copy(alpha = 0.22f)
                                } else {
                                    CardSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onToggleTradeConfirmation(!uiState.settings.tradeConfirmationAlwaysOn)
                                    },
                            ) {
                                Text(
                                    text = if (uiState.settings.tradeConfirmationAlwaysOn) {
                                        stringResource(R.string.always_on)
                                    } else {
                                        "Optional"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ABOUT
            item {
                SettingsSection(
                    title = stringResource(R.string.section_about),
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Rounded.Bolt,
                                tint = Color(0xFF60A5FA),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.about_version),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }

                        HorizontalDivider(color = DividerSubtle)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Rounded.VerifiedUser,
                                tint = Color(0xFF60A5FA),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.about_security_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.about_security_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
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
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder),
            content = content,
        )
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TierStepperPill(
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxSize()
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease $label",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.merge(MonoPriceStyle),
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxSize()
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $label",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
