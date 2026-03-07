package dev.trooped.tvquickbars.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ha.ValidationResult
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.TokenTransferHelper
import kotlinx.coroutines.launch

/**
 * Onboarding/setup (Compose) – wired Manual + Easy flows.
 * - Manual: validate via HomeAssistantClient.validateWithFallback; on success → navigate as in SetupActivity
 * - Easy: start/stop TokenTransfer server when selected; show local URL + QR; on receive → validate + navigate
 *
 * Theme palette matches existing screens.
 */
class OnboardingActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = colorResource(id = R.color.md_theme_primary),
                    onPrimary = colorResource(id = R.color.md_theme_onPrimary),
                    primaryContainer = colorResource(id = R.color.md_theme_primaryContainer),
                    onPrimaryContainer = colorResource(id = R.color.md_theme_onPrimaryContainer),
                    secondary = colorResource(id = R.color.md_theme_secondary),
                    onSecondary = colorResource(id = R.color.md_theme_onSecondary),
                    secondaryContainer = colorResource(id = R.color.md_theme_secondaryContainer),
                    onSecondaryContainer = colorResource(id = R.color.md_theme_onSecondaryContainer),
                    tertiary = colorResource(id = R.color.md_theme_tertiary),
                    onTertiary = colorResource(id = R.color.md_theme_onTertiary),
                    tertiaryContainer = colorResource(id = R.color.md_theme_tertiaryContainer),
                    onTertiaryContainer = colorResource(id = R.color.md_theme_onTertiaryContainer),
                    error = colorResource(id = R.color.md_theme_error),
                    onError = colorResource(id = R.color.md_theme_onError),
                    errorContainer = colorResource(id = R.color.md_theme_errorContainer),
                    onErrorContainer = colorResource(id = R.color.md_theme_onErrorContainer),
                    background = colorResource(id = R.color.md_theme_background),
                    onBackground = colorResource(id = R.color.md_theme_onBackground),
                    surface = colorResource(id = R.color.md_theme_surface),
                    onSurface = colorResource(id = R.color.md_theme_onSurface),
                    surfaceVariant = colorResource(id = R.color.md_theme_surfaceVariant),
                    onSurfaceVariant = colorResource(id = R.color.md_theme_onSurfaceVariant),
                    outline = colorResource(id = R.color.md_theme_outline),
                    inverseSurface = colorResource(id = R.color.md_theme_inverseSurface),
                    inverseOnSurface = colorResource(id = R.color.md_theme_inverseOnSurface),
                    inversePrimary = colorResource(id = R.color.md_theme_inversePrimary),
                    surfaceTint = colorResource(id = R.color.md_theme_primary)
                )
            ) {
                OnboardingScreen()
            }
        }
    }
}

private enum class Phase { Intro, ModeSelect, Detail }
private enum class SetupChoice { Manual, Easy }

@Composable
private fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // high-level state
    var phase by remember { mutableStateOf(Phase.Intro) }
    var currentChoice by remember { mutableStateOf(SetupChoice.Easy) } // center by default
    var showButtons by remember { mutableStateOf(false) }

    // Manual/Easy shared state
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    // Easy server state
    val tokenHelper = remember { TokenTransferHelper(context) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // dialogs
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Help dialog button
    var showHelpDialog by remember { mutableStateOf(false) }


    // animation/layout state
    val welcomeVisible = phase != Phase.Detail
    val buttonsPinnedToTop = phase == Phase.Detail

    val welcomeScale by animateFloatAsState(
        targetValue = if (phase == Phase.Intro) 1f else 0.85f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "welcomeScale"
    )
    val targetButtonsTop: Dp = when {
        buttonsPinnedToTop -> 48.dp
        else -> 325.dp
    }
    val buttonsTop by animateDpAsState(
        targetValue = targetButtonsTop,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "buttonsTop"
    )
    val targetWelcomeTop: Dp = when (phase) {
        Phase.Intro -> 72.dp
        Phase.ModeSelect -> 8.dp
        Phase.Detail -> 0.dp
    }
    val welcomeTop by animateDpAsState(
        targetValue = targetWelcomeTop,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "welcomeTop"
    )

    // after entering ModeSelect → wait slide → reveal buttons
    val middleRequester = remember { FocusRequester() }
    LaunchedEffect(phase) {
        if (phase == Phase.ModeSelect) {
            showButtons = false
            kotlinx.coroutines.delay(520)
            showButtons = true
        }
    }
    LaunchedEffect(showButtons, phase) {
        if (showButtons && phase == Phase.ModeSelect) {
            kotlinx.coroutines.delay(60)
            middleRequester.requestFocus()
        }
    }

    // Start/Stop token server when Easy screen is selected in Detail
    DisposableEffect(phase, currentChoice) {
        if (phase == Phase.Detail && currentChoice == SetupChoice.Easy) {
            // (re)start server
            try {
                tokenHelper.stopServer()
            } catch (_: Exception) {}

            val ip = tokenHelper.getLocalIpAddress()
            if (ip != null) {
                // Start; capture URL/token
                tokenHelper.startTokenTransfer { haUrl, haToken ->
                    // Update fields and validate
                    url = haUrl
                    token = haToken
                    if (haUrl.isNotEmpty() && haToken.isNotEmpty()) {
                        scope.launch { validateAndProceed(context, haUrl, haToken, setLoading = { loading = it }, onError = { error = it }) }
                    }
                }
                // UI bits
                val addr = "http://$ip:8765" // approximate; helper may choose different port internally
                serverUrl = addr
                qrBitmap = tokenHelper.generateQRCode(addr)
            } else {
                error = "Could not determine IP address" to "Use Manual Setup instead or check your network connection."
            }
        }
        onDispose {
            try { tokenHelper.stopServer() } catch (_: Exception) {}
            serverUrl = null
            qrBitmap = null
        }
    }

    // First-time persistent flag as in SetupActivity
    if (!AppPrefs.hasPersistentConnectionFlag(context)) {
        AppPrefs.setPersistentConnectionEnabled(context, false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .let { base -> if (phase == Phase.Intro) base.clickable { phase = Phase.ModeSelect } else base }
            .padding(horizontal = 48.dp)
    ) {
        // (1) WELCOME
        AnimatedVisibility(
            visible = welcomeVisible,
            enter = fadeIn(tween(300)) + expandVertically(),
            exit = fadeOut(tween(250)) + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = welcomeTop),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icon_svg),
                    contentDescription = "QuickBars Icon",
                    // size animates with welcomeScale, so layout height tracks the icon
                    modifier = Modifier
                        .size(280.dp * welcomeScale)
                        .align(Alignment.CenterHorizontally),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Welcome to QuickBars for Home Assistant",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 44.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(visible = phase == Phase.Intro, enter = fadeIn(), exit = fadeOut()) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val a by pulse.animateFloat(
                        0.6f, 1f,
                        animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                        label = "a"
                    )
                    Text(
                        text = "Click anywhere to start",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(a)
                    )
                }
            }
        }

        // (2) BUTTONS + EXPLANATION
        AnimatedVisibility(
            visible = (phase == Phase.ModeSelect && showButtons) || phase == Phase.Detail,
            enter = fadeIn(tween(280)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = buttonsTop)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ChoiceButtonsRow(
                    currentChoice = currentChoice,
                    onFocused = { currentChoice = it },
                    onChosen = {
                        currentChoice = it
                        if (phase == Phase.ModeSelect) phase = Phase.Detail
                    },
                    middleRequester = middleRequester,
                )
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(visible = phase == Phase.ModeSelect, enter = fadeIn(), exit = fadeOut()) {
                    ExplanationBox(choice = currentChoice)
                }
            }
        }

        // (3) DETAIL AREA (forms)
        AnimatedVisibility(
            visible = phase == Phase.Detail,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
                when (currentChoice) {
                    SetupChoice.Manual -> ManualSetupForm(
                        url = url,
                        token = token,
                        onUrlChange = { url = it },
                        onTokenChange = { token = it },
                        onConnect = {
                            scope.launch { validateAndProceed(context, url, token, setLoading = { loading = it }, onError = { error = it }) }
                        }
                    )
                    SetupChoice.Easy -> EasySetupForm(
                        serverUrl = serverUrl,
                        qr = qrBitmap,
                        onRestart = {
                            // force restart by flipping choice quickly
                            currentChoice = SetupChoice.Manual
                            currentChoice = SetupChoice.Easy
                        }
                    )
                }
            }
        }

        // Loading dialog
        if (loading) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Connecting") }, text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Attempting to connect to Home Assistant…")
                }
            })
        }

        // Error dialog
        error?.let { (t, m) ->
            AlertDialog(
                onDismissRequest = { error = null },
                confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
                title = { Text(t) },
                text = { Text(m) }
            )
        }

        AnimatedVisibility(
            visible = phase == Phase.Detail,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 0.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icon_svg),
                contentDescription = "QuickBars Icon",
                // size animates with welcomeScale, so layout height tracks the icon
                modifier = Modifier
                    .size(72.dp),
                tint = androidx.compose.ui.graphics.Color.Unspecified
            )
        }

        AnimatedVisibility(
            visible = phase == Phase.Detail,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = (-4).dp)
        ) {
            OutlinedIconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier
                    .size(48.dp)                 // overall circle size (tweak 44–56dp)
                    .padding(0.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Help,
                    contentDescription = "Help",
                    modifier = Modifier.size(22.dp) // icon size inside the circle
                )
            }
        }

        val context = LocalContext.current

        // Help dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("Need more help?") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            "For more in-depth explanation, visit quickbars.app/guide or scan this QR code:",
                            textAlign = TextAlign.Center
                        )

                        // QR code for the help website
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val helpQr = remember {
                                TokenTransferHelper(context).generateQRCode("https://quickbars.app/guide")
                            }

                            if (helpQr != null) {
                                Image(
                                    bitmap = helpQr.asImageBitmap(),
                                    contentDescription = "Help QR Code",
                                    modifier = Modifier
                                        .size(180.dp)
                                        .padding(4.dp)
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showHelpDialog = false }) {
                        Text("Close")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(16.dp),
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            )
        }
    }
}

// ---------- Sub-UI ----------
@Composable
private fun ChoiceButtonsRow(
    currentChoice: SetupChoice,
    onFocused: (SetupChoice) -> Unit,
    onChosen: (SetupChoice) -> Unit,
    middleRequester: FocusRequester,
    easierEnabled: Boolean = true
) {
    val spacing = 48.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChoiceButton(
            text = "Manual Setup",
            selected = currentChoice == SetupChoice.Manual,
            onFocus = { onFocused(SetupChoice.Manual) },
            onClick = { onChosen(SetupChoice.Manual) }
        )
        Spacer(Modifier.width(spacing))
        ChoiceButton(
            text = "Easy Setup",
            selected = currentChoice == SetupChoice.Easy,
            onFocus = { onFocused(SetupChoice.Easy) },
            onClick = { onChosen(SetupChoice.Easy) },
            focusRequester = middleRequester
        )
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1.0f, tween(150), label = "btnScale")
    val border = if (focused || selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        border = border,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (focused) 12.dp else 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .height(60.dp)
            .widthIn(min = 220.dp)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() }
    ) { Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) }
}

@Composable
private fun ExplanationBox(choice: SetupChoice) {
    val explanation = when (choice) {
        SetupChoice.Manual -> "Enter your URL and Long-Lived Token manually, using the TV remote."
        SetupChoice.Easy -> "Enter your URL and Long-Lived Token using your phone, with a QR code and a local website."
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth(0.85f),
    ) { Text(explanation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(18.dp)) }
}

@Composable
private fun ManualSetupForm(
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(value = url, onValueChange = onUrlChange, label = { Text("Home Assistant URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.85f))
        OutlinedTextField(value = token, onValueChange = onTokenChange, label = { Text("Long-Lived Access Token") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.85f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onConnect, enabled = url.isNotBlank() && token.isNotBlank()) { Text("Connect") }
            TextButton(onClick = { onUrlChange(""); onTokenChange("") }) { Text("Clear") }
        }
        HelperCard("Tip: You can try using homeassistant.local, or the specific URL on your local network. Tokens are created in your HA profile → Long-Lived Access Tokens.")
    }
}

@Composable
private fun EasySetupForm(
    serverUrl: String?,
    qr: Bitmap?,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp) // Fixed square size
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(150.dp) // Slightly smaller than container to add minimal padding
                        .padding(4.dp)
                )
            } else {
                CircularProgressIndicator()
                Text(
                    "Generating QR…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
        Text(text = serverUrl?.let { "Or open manually: $it" } ?: "Starting local server…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRestart) { Text("Restart QR Server") }
        }
        HelperCard("Scan the QR with your phone to enter your credentials. We’ll securely transfer your URL + token over your local network (nothing leaves your LAN).")
    }
}

@Composable
private fun EasierSetupForm() {
    val context = LocalContext.current

    // Get the device's IP address
    val deviceIp = remember {
        TokenTransferHelper(context).getLocalIpAddress() ?: "IP not available"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "How to add your TV to Home Assistant",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(2.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepText(number = 1, text = "Open Home Assistant")
                    StepText(number = 2, text = "Go to Settings → Devices & Services")
                    StepText(number = 3, text = "Your TV should appear on \"Discovered\". Click on it and follow the configuration steps.")
                }

                Spacer(Modifier.height(4.dp))

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Note: If your TV doesn't appear automatically",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            "Click on \"+ Add Integration\", search for \"QuickBars\", and enter this device's IP address:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            deviceIp,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepText(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelperCard(text: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
}

// ---------- Logic helpers ----------
suspend fun validateAndProceed(
    context: android.content.Context,
    inputUrl: String,
    inputToken: String,
    setLoading: (Boolean) -> Unit,
    onError: (Pair<String, String>) -> Unit
) {
    if (DemoModeManager.isDemoCredentials(inputUrl, inputToken)) {
        DemoModeManager.enableDemoMode()
        SecurePrefsManager.saveHAUrl(context, DemoModeManager.DEMO_WEBSOCKET_URL)
        SecurePrefsManager.saveHAToken(context, DemoModeManager.DEMO_TOKEN)
        navigateNext(context)
        return
    }

    setLoading(true)
    when (val result = HomeAssistantClient.validateWithFallback(inputUrl, inputToken)) {
        is ValidationResult.Success -> {
            SecurePrefsManager.saveHAUrl(context, inputUrl)
            SecurePrefsManager.saveHAToken(context, inputToken)
            setLoading(false)
            navigateNext(context)
        }
        is ValidationResult.Error -> {
            setLoading(false)
            onError(getErrorInfo(result.reason))
        }
    }
}

private fun navigateNext(context: android.content.Context) {
    val saved = SavedEntitiesManager(context).loadEntities()
    if (saved.isEmpty()) {
        AppPrefs.setFirstTimeSetupInProgress(context, true)
        context.startActivity(Intent(context, EntityImporterActivity::class.java).apply {
            putExtra("FIRST_TIME_SETUP", true)
        })
    } else {
        context.startActivity(Intent(context, ComposeMainActivity::class.java))
    }
}

private fun getErrorInfo(reason: ConnectionState.Reason): Pair<String, String> = when (reason) {
    ConnectionState.Reason.CANNOT_RESOLVE_HOST -> "Host Not Found" to "Could not find Home Assistant at the specified address. Check IP/host, that HA is running, and that you are on the same network."
    ConnectionState.Reason.AUTH_FAILED -> "Authentication Failed" to "The token was rejected by Home Assistant. Please enter a valid Long‑Lived Access Token."
    ConnectionState.Reason.BAD_TOKEN -> "Invalid Token Format" to "The provided token appears invalid. Make sure it’s a Long‑Lived Access Token."
    ConnectionState.Reason.SSL_HANDSHAKE -> "SSL Certificate Error" to "There was a problem with the SSL certificate. Try HTTP for local connections."
    ConnectionState.Reason.TIMEOUT -> "Connection Timeout" to "The connection attempt timed out. Verify the server address and that HA is running."
    ConnectionState.Reason.NETWORK_IO -> "Network Error" to "A network error occurred while connecting to Home Assistant."
    ConnectionState.Reason.UNKNOWN -> "Unknown Error" to "An unknown error occurred while connecting to Home Assistant."
    ConnectionState.Reason.BAD_URL -> "Invalid Server Address" to "The provided URL is invalid. Please check it and try again."
}
