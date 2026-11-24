package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ctrader.CTraderConnectionState
import com.example.ctrader.CTraderOAuthManager
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
import com.example.ui.theme.RoyalBlueDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradeHubUiState

@Composable
fun CTraderAuthScreen(
    uiState: TradeHubUiState,
    onBack: () -> Unit,
    onSelectEnv: (String) -> Unit,
    onStartAuthentication: (clientId: String, clientSecret: String, env: String) -> Unit,
    onCompleteManualCode: (codeOrUrl: String, clientId: String, clientSecret: String, env: String) -> Unit,
    onCancelOAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenCTraderPortal: () -> Unit,
    onCopyRedirectUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var clientId by remember { mutableStateOf(uiState.settings.ctraderClientId) }
    var clientSecret by remember { mutableStateOf(uiState.settings.ctraderClientSecret) }
    var secretVisible by remember { mutableStateOf(false) }
    var selectedEnv by remember { mutableStateOf(uiState.settings.ctraderEnv.ifBlank { "demo" }) }
    var showManualCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.settings.ctraderClientId) {
        if (uiState.settings.ctraderClientId.isNotBlank() && clientId.isBlank()) {
            clientId = uiState.settings.ctraderClientId
        }
    }
    LaunchedEffect(uiState.settings.ctraderClientSecret) {
        if (uiState.settings.ctraderClientSecret.isNotBlank() && clientSecret.isBlank()) {
            clientSecret = uiState.settings.ctraderClientSecret
        }
    }
    LaunchedEffect(uiState.settings.ctraderEnv) {
        selectedEnv = uiState.settings.ctraderEnv.ifBlank { "demo" }
    }

    val isConnected = uiState.ctraderConnectionState == CTraderConnectionState.CONNECTED
    val hasToken = uiState.settings.ctraderAccessToken.isNotBlank()
    val canAuthenticate = !uiState.isOAuthWaiting &&
        clientId.isNotBlank() &&
        clientSecret.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBg),
    ) {
        // Atmospheric Royal-Blue Top App Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(colors = listOf(HeaderBlueTop, HeaderBlueBottom))
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("ctrader_auth_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.ctrader_auth_back_cd),
                        tint = Color.White,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ctrader_auth_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.ctrader_auth_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedEnv == "live") {
                        BullGreenBright.copy(alpha = 0.25f)
                    } else {
                        Color.White.copy(alpha = 0.18f)
                    },
                ) {
                    Text(
                        text = selectedEnv.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Connection & Session Status Hero Card
            item {
                AuthStatusCard(
                    connectionState = uiState.ctraderConnectionState,
                    isOAuthWaiting = uiState.isOAuthWaiting,
                    selectedEnv = selectedEnv,
                    hasToken = hasToken,
                    ctraderAccountCount = uiState.accounts.count { it.ctidTraderAccountId > 0L },
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }

            // Main OAuth2 Credentials Form Card
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Header + Demo/Live Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ctrader_auth_credentials_header),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.ctrader_auth_env_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(CardSurfaceVariant)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf("live" to "Live", "demo" to "Demo").forEach { (envKey, label) ->
                                    val isSelected = selectedEnv == envKey
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isSelected) RoyalBlueBright else Color.Transparent,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable {
                                                selectedEnv = envKey
                                                onSelectEnv(envKey)
                                            }
                                            .testTag("ctrader_auth_env_$envKey"),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = DividerSubtle)

                        // Client ID Input Field
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text(stringResource(R.string.ctrader_client_id_label)) },
                            placeholder = { Text(stringResource(R.string.ctrader_auth_client_id_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                            colors = tradeHubFieldColors(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ctrader_auth_client_id_input"),
                        )

                        // Client Secret Input Field
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text(stringResource(R.string.ctrader_client_secret_label)) },
                            placeholder = { Text(stringResource(R.string.ctrader_auth_client_secret_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { secretVisible = !secretVisible },
                                    modifier = Modifier.testTag("ctrader_auth_toggle_secret_visibility"),
                                ) {
                                    Icon(
                                        imageVector = if (secretVisible) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = stringResource(R.string.ctrader_auth_toggle_secret_cd),
                                        tint = TextSecondary,
                                    )
                                }
                            },
                            visualTransformation = if (secretVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (canAuthenticate) {
                                        onStartAuthentication(clientId, clientSecret, selectedEnv)
                                    }
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                            colors = tradeHubFieldColors(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ctrader_auth_client_secret_input"),
                        )

                        // Registered Redirect URI Card
                        Text(
                            text = stringResource(R.string.ctrader_redirect_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardSurfaceVariant,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(CTraderOAuthManager.REDIRECT_URI))
                                    onCopyRedirectUri(CTraderOAuthManager.REDIRECT_URI)
                                }
                                .testTag("ctrader_auth_copy_redirect_button"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = CTraderOAuthManager.REDIRECT_URI,
                                    style = MaterialTheme.typography.bodyMedium.merge(MonoPriceStyle),
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.ctrader_auth_copy_redirect_cd),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Primary Button to Initiate OAuth2 Flow
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onStartAuthentication(clientId, clientSecret, selectedEnv)
                            },
                            enabled = canAuthenticate,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalBlueBright,
                                contentColor = Color.White,
                                disabledContainerColor = RoyalBlueBright.copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.65f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("ctrader_auth_start_button"),
                        ) {
                            if (uiState.isOAuthWaiting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.ctrader_auth_waiting_button),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.ctrader_auth_initiate_button),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.isOAuthWaiting) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TextButton(
                                    onClick = { showManualCodeDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ctrader_auth_manual_code_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ctrader_auth_manual_fallback),
                                        color = Color(0xFF60A5FA),
                                    )
                                }
                            }
                        }

                        // Secondary Actions: Cancel / Disconnect & Portal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (uiState.isOAuthWaiting || hasToken || isConnected) {
                                OutlinedButton(
                                    onClick = {
                                        if (uiState.isOAuthWaiting) {
                                            onCancelOAuth()
                                        } else {
                                            onDisconnect()
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("ctrader_auth_disconnect_button"),
                                ) {
                                    Text(
                                        text = if (uiState.isOAuthWaiting) {
                                            stringResource(R.string.ctrader_auth_cancel)
                                        } else {
                                            stringResource(R.string.ctrader_logout_button)
                                        },
                                        color = if (uiState.isOAuthWaiting) TextSecondary else BearOrangeRed,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onOpenCTraderPortal,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("ctrader_auth_portal_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInBrowser,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.ctrader_portal_button),
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // OAuth2 Step-by-Step Guide Card
            item {
                AuthGuideCard(modifier = Modifier.widthIn(max = 600.dp))
            }
        }
    }

    if (showManualCodeDialog) {
        var manualInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualCodeDialog = false },
            containerColor = CardSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(stringResource(R.string.ctrader_auth_manual_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ctrader_auth_manual_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text(stringResource(R.string.ctrader_auth_manual_dialog_label)) },
                        singleLine = true,
                        colors = tradeHubFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ctrader_auth_manual_code_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManualCodeDialog = false
                        onCompleteManualCode(manualInput, clientId, clientSecret, selectedEnv)
                    },
                    enabled = manualInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlueBright),
                    modifier = Modifier.testTag("ctrader_auth_manual_confirm_button"),
                ) {
                    Text(stringResource(R.string.ctrader_auth_connect_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualCodeDialog = false }) {
                    Text(stringResource(R.string.ctrader_auth_cancel), color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AuthStatusCard(
    connectionState: CTraderConnectionState,
    isOAuthWaiting: Boolean,
    selectedEnv: String,
    hasToken: Boolean,
    ctraderAccountCount: Int,
    modifier: Modifier = Modifier,
) {
    val (accentColor, statusTitle, statusDetail) = when {
        isOAuthWaiting -> Triple(
            Color(0xFFFBBF24),
            "Waiting for Browser Approval",
            "Complete sign-in in your browser. You will be redirected back automatically.",
        )
        connectionState == CTraderConnectionState.CONNECTED -> Triple(
            BullGreenBright,
            "Connected to cTrader (${selectedEnv.uppercase()})",
            "$ctraderAccountCount trading account(s) synced with live market streaming.",
        )
        connectionState == CTraderConnectionState.CONNECTING ||
            connectionState == CTraderConnectionState.AUTHORIZING -> Triple(
            Color(0xFF60A5FA),
            "Authorizing Session...",
            "Connecting to cTrader Open API WebSocket and verifying access token.",
        )
        connectionState == CTraderConnectionState.ERROR -> Triple(
            BearOrangeRed,
            "Authentication / Connection Issue",
            "Verify your Client ID, Client Secret, and Redirect URI, then try again.",
        )
        hasToken -> Triple(
            Color(0xFF60A5FA),
            "Saved Session Ready",
            "Reconnect or re-authenticate below to refresh your cTrader session.",
        )
        else -> Triple(
            TextMuted,
            "Not Authenticated",
            "Enter your cTrader Open API Client ID and Client Secret below to connect.",
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RoyalBlueDeep.copy(alpha = 0.5f),
                        CardSurface,
                    )
                )
            )
            .padding(1.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (connectionState == CTraderConnectionState.CONNECTED) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Security
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthGuideCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.ctrader_auth_guide_title),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                letterSpacing = 1.sp,
            )
            GuideStepRow(
                step = "1",
                text = "Register http://127.0.0.1:8080/oauth as Redirect URI in your cTrader Open API application.",
            )
            GuideStepRow(
                step = "2",
                text = "Paste your Client ID and Client Secret above and select Demo or Live.",
            )
            GuideStepRow(
                step = "3",
                text = "Tap Authenticate with cTrader to approve trading scope in the browser and sync your accounts.",
            )
        }
    }
}

@Composable
private fun GuideStepRow(
    step: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(RoyalBlueBright.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF93C5FD),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
