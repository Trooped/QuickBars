package dev.trooped.tvquickbars.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.ui.dialog.WhatsNewDialog
import dev.trooped.tvquickbars.utils.AppUpdateManager
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.TokenTransferHelper
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ComposeMainActivity
 * Main activity for the app, contains a drawer menu.
 * @property fragmentContainerId The ID of the fragment container in the layout.
 * @property isDrawerOpen A mutable state variable to track if the drawer is open.
 */
class ComposeMainActivity : BaseActivity() {
    private val fragmentContainerId = R.id.fragment_container_view
    private var isDrawerOpen = mutableStateOf(false)

    // Add new state for what's new dialog
    private var showWhatsNewDialog = mutableStateOf(false)
    private var currentVersionCode = 0

    // Permission request receiver
    private val permissionRequestReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_OVERLAY_PERMISSION_REQUIRED" -> {
                    // Show the overlay permission explanation
                    PermissionUtils.showOverlayPermissionExplanation(
                        this@ComposeMainActivity,
                        onCancel = {
                            Toast.makeText(
                                this@ComposeMainActivity,
                                R.string.overlay_permission_denied_message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                "ACTION_ACCESSIBILITY_PERMISSION_REQUIRED" -> {
                    // Show the accessibility permission explanation
                    PermissionUtils.showAccessibilityPermissionExplanation(
                        this@ComposeMainActivity,
                        onCancel = {
                            Toast.makeText(
                                this@ComposeMainActivity,
                                R.string.accessibility_permission_denied_message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_main)

        DemoModeManager.checkAndEnableDemoMode(this)

        // Check if app has been updated
        checkForAppUpdate()

        // Register the permission request receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            permissionRequestReceiver,
            IntentFilter().apply {
                addAction("ACTION_OVERLAY_PERMISSION_REQUIRED")
                addAction("ACTION_ACCESSIBILITY_PERMISSION_REQUIRED")
            }
        )

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)

        composeView.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                // Show what's new dialog if needed
                if (showWhatsNewDialog.value) {
                    val versionName = AppUpdateManager.getVersionName(currentVersionCode)
                    val changes = AppUpdateManager.getChangesForVersion(currentVersionCode)

                    WhatsNewDialog(
                        versionName = versionName,
                        changes = changes,
                        onDismiss = {
                            // Update last seen version and dismiss dialog
                            AppPrefs.setLastSeenVersion(
                                this@ComposeMainActivity,
                                currentVersionCode
                            )
                            showWhatsNewDialog.value = false
                        }
                    )
                }

                TVFriendlyNavigation(
                    isDrawerOpen = isDrawerOpen.value,
                    onDrawerStateChange = { isOpen ->
                        isDrawerOpen.value = isOpen
                    },
                    onNavigate = { fragment ->
                        supportFragmentManager.beginTransaction()
                            .replace(fragmentContainerId, fragment)
                            .commitNow()
                    }
                )
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(fragmentContainerId, QuickBarsListFragment())
                .commitNow()  // Use commitNow() for immediate execution
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(permissionRequestReceiver)
    }

    /**
     * Handle key events to open/close the drawer.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // If the left D-pad is pressed AND the drawer is currently closed...
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !isDrawerOpen.value) {
            // ...then open the drawer.
            isDrawerOpen.value = true
            // Return true to indicate we've handled this event.
            return true
        }
        // For all other key presses, let the system handle them as usual.
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Checks if the app has been updated and shows the what's new dialog if needed
     */
    private fun checkForAppUpdate() {
        try {
            // DEBUGGING: Set to true to always show the dialog
            val forceShowDialog = false

            if (forceShowDialog || AppPrefs.isAppUpdated(this)) {
                currentVersionCode = AppPrefs.getCurrentAppVersion(this)

                // For debugging: if forcing dialog, use the most recent version with changes
                if (forceShowDialog && AppUpdateManager.getChangesForVersion(currentVersionCode).isEmpty()) {
                    // Find the most recent version with changes
                    val versionsWithChanges = listOf(16, 15) // Update this list with your version codes
                    for (version in versionsWithChanges) {
                        if (AppUpdateManager.getChangesForVersion(version).isNotEmpty()) {
                            currentVersionCode = version
                            break
                        }
                    }
                }

                val changes = AppUpdateManager.getChangesForVersion(currentVersionCode)

                // Only show dialog if we have changes to display for this version
                if (changes.isNotEmpty()) {
                    showWhatsNewDialog.value = true

                    // When in debug mode, don't update the last seen version
                    if (!forceShowDialog) {
                        AppPrefs.setLastSeenVersion(this, currentVersionCode)
                    }
                } else if (!forceShowDialog) {
                    // If no changes to show, just update the last seen version
                    AppPrefs.setLastSeenVersion(this, currentVersionCode)
                }
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "Error checking for app update", e)
        }
    }
}

/**
 * NavItem class
 * Represents a navigation item in the drawer.
 * @property id The unique ID of the item.
 * @property title The title of the item.
 * @property icon The icon of the item.
 * @property createFragment A lambda to create the corresponding fragment.
 */
sealed class NavItem(
    val id: Int,
    val title: String,
    val icon: Any,
    val createFragment: () -> Fragment
) {
    object QuickBars : NavItem(
        R.id.nav_quickbars,
        "QuickBars",
        R.drawable.ic_quickbars,
        { QuickBarsListFragment() }
    )

    object Entities : NavItem(
        R.id.nav_entities,
        "Entities",
        R.drawable.ic_view_module,
        { EntitiesFragment() }
    )

    object TriggerKeys : NavItem(
        R.id.nav_trigger_keys,
        "Trigger Keys",
        R.drawable.ic_remote,
        { TriggerKeysListFragment() }
    )

    object Settings : NavItem(
        R.id.nav_settings,
        "Settings",
        Icons.Default.Settings,
        { SettingsFragment() }
    )
}

/**
 * TVFriendlyNavigation
 * Main navigation composable.
 * @param isDrawerOpen Whether the drawer is open.
 * @param onDrawerStateChange Callback to update drawer state.
 * @param onNavigate Callback to navigate to a fragment.
 * @property items List of navigation items.
 * @property selectedItem The currently selected item.
 * @property focusRequesters List of focus requesters for each item.
 * @property focusedItemIndex Index of the currently focused item.
 * @property lastFocusedIndex Index of the last focused item.
 */
@Composable
fun TVFriendlyNavigation(
    isDrawerOpen: Boolean,
    onDrawerStateChange: (Boolean) -> Unit,
    onNavigate: (Fragment) -> Unit
) {
    val context = LocalContext.current
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    val backExitWindowMs = 2500L

    val items = listOf(NavItem.QuickBars, NavItem.Entities, NavItem.TriggerKeys, NavItem.Settings)
    var selectedItem by remember { mutableStateOf(items[0]) }
    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }

    // Track which item is currently focused - store it persistently
    var focusedItemIndex by remember { mutableIntStateOf(0) } // Start with first item

    // Store last drawer position to restore focus
    var lastFocusedIndex by remember { mutableIntStateOf(0) }

    /*
    // Auto-select without closing drawer when focus changes
    LaunchedEffect(focusedItemIndex) {
        if (isDrawerOpen && focusedItemIndex >= 0) {
            // Immediately update the selected item and fragment
            val item = items[focusedItemIndex]
            selectedItem = item
            onNavigate(item.createFragment())
            // Note: We don't close the drawer here
        }
    }
     */


    val drawerWidth by animateDpAsState(
        targetValue = if (isDrawerOpen) 230.dp else 0.dp,
        label = "drawerWidth"
    )


    // Update focus when drawer opens/closes
    LaunchedEffect(isDrawerOpen, lastFocusedIndex) {
        if (isDrawerOpen) {
            // Wait until the animated width crosses 0 so the drawer content is composed
            snapshotFlow { drawerWidth }
                .filter { it > 0.dp }
                .first()

            // Wait one frame so the FocusRequester node is attached
            awaitFrame()

            // Safely request focus
            focusRequesters.getOrNull(lastFocusedIndex)?.requestFocus()
            focusedItemIndex = lastFocusedIndex
        } else {
            // Remember where focus was before closing
            lastFocusedIndex = focusedItemIndex
        }
    }

    // Theme colors
    val surfaceColor = colorResource(id = R.color.md_theme_surface)
    val surfaceContainerColor = colorResource(id = R.color.md_theme_surfaceContainer)
    val surfaceContainerHighestColor = colorResource(id = R.color.md_theme_surfaceContainerHighest)
    val onSurfaceColor = colorResource(id = R.color.md_theme_onSurface)
    val onSurfaceVariantColor = colorResource(id = R.color.md_theme_onSurfaceVariant)
    val onSurfaceVariantMediumContrastColor = colorResource(id = R.color.md_theme_onSurfaceVariant_mediumContrast)
    val primaryColor = colorResource(id = R.color.md_theme_primary)
    val surfaceVariantColor = colorResource(id = R.color.md_theme_surfaceVariant)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = surfaceColor,
            background = surfaceColor,
            onSurface = onSurfaceColor,
            primary = primaryColor,
            onSurfaceVariant = onSurfaceVariantColor,
            //onSurfaceVariantMediumContrast = onSurfaceVariantMediumContrastColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusable()
                .onPreviewKeyEvent { event ->
                    // Handle only DPAD keys here
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!isDrawerOpen) {
                                    onDrawerStateChange(true)
                                    return@onPreviewKeyEvent true
                                }
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (isDrawerOpen) {
                                    onDrawerStateChange(false)
                                    return@onPreviewKeyEvent true
                                }
                            }

                            KeyEvent.KEYCODE_BACK -> {
                                if (isDrawerOpen) {
                                    onDrawerStateChange(false)
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
                }
        ) {
            // Handle the back button separately
            BackHandler {
                if (drawerWidth <= 0.dp) {
                    val now = SystemClock.elapsedRealtime()
                    // Second back within 2.5 s exits; otherwise show toast
                    if (now - lastBackPressMs <= backExitWindowMs) {
                        (context as? Activity)?.finish()
                    } else {
                        Toast.makeText(
                            context,
                            "Press BACK again to exit",
                            Toast.LENGTH_SHORT
                        ).show()
                        lastBackPressMs = now
                    }
                }
            }
            // Main content box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = drawerWidth)
            ) {
                if (!isDrawerOpen) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Navigation Drawer",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterStart)
                            .size(36.dp, 24.dp),
                        tint = onSurfaceColor.copy(alpha = 0.8f)
                    )
                }
            }

            // Drawer content
            if (drawerWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(drawerWidth)
                        .fillMaxHeight()
                        .background(surfaceContainerColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, start = 8.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 28.dp, vertical = 16.dp)
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                color = surfaceContainerHighestColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.mipmap.ic_icon_foreground),
                                        contentDescription = "QuickBars",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            // Add a graphicsLayer to manually scale the image up
                                            .graphicsLayer {
                                                // You can experiment with this value. 1.5f means 150% bigger.
                                                scaleX = 1.5f
                                                scaleY = 1.5f
                                            },
                                        // It's still good practice to keep ContentScale.Crop
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "QuickBars",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "for Home Assistant",
                                    style = MaterialTheme.typography.bodySmall, // Smaller style
                                    color = MaterialTheme.colorScheme.onSurfaceVariant // Grayer, less noticeable color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Navigation items
                        items.forEachIndexed { index, item ->
                            val isFocused = index == focusedItemIndex

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .then(
                                        if (isFocused)
                                            Modifier.padding(start = 4.dp)
                                        else
                                            Modifier
                                    )
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        selectedItem = item
                                        onNavigate(item.createFragment())
                                        // Only close drawer when explicitly clicked
                                        onDrawerStateChange(false)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .focusRequester(focusRequesters[index])
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                focusedItemIndex = index
                                            }
                                        }
                                        .focusProperties {
                                            down = if (index < items.size - 1)
                                                focusRequesters[index + 1]
                                            else
                                                FocusRequester.Cancel
                                            up = if (index > 0)
                                                focusRequesters[index - 1]
                                            else
                                                FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                        .then(
                                            if (isFocused)
                                                Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
                                            else
                                                Modifier
                                        ),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = when {
                                            item == selectedItem && isFocused -> primaryColor.copy(alpha = 0.9f)
                                            item == selectedItem -> primaryColor.copy(alpha = 0.7f)
                                            isFocused -> surfaceContainerHighestColor.copy(alpha = 0.9f)
                                            else -> surfaceContainerHighestColor.copy(alpha = 0.7f)
                                        },
                                        contentColor = if (item == selectedItem || isFocused)
                                            Color.White
                                        else
                                            onSurfaceColor
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = if (isFocused) 8.dp else 0.dp,
                                        pressedElevation = 4.dp
                                    ),
                                    contentPadding = PaddingValues(
                                        start = 10.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    Color.Transparent,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (val icon = item.icon) {
                                                is ImageVector -> {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = item.title,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = if (isFocused || item == selectedItem)
                                                            Color.White
                                                        else
                                                            onSurfaceColor
                                                    )
                                                }
                                                is Int -> {
                                                    Icon(
                                                        painter = painterResource(id = icon),
                                                        contentDescription = item.title,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = if (isFocused || item == selectedItem)
                                                            Color.White
                                                        else
                                                            onSurfaceColor
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                if (isFocused) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(40.dp)
                                            .background(primaryColor)
                                            .align(Alignment.CenterStart)
                                    )
                                }
                            }
                        }

                        // Website section
                        Spacer(modifier = Modifier.weight(1f))

                        HorizontalDivider(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .alpha(0.3f),
                            thickness = DividerDefaults.Thickness, color = DividerDefaults.color
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
                                .focusable(false)  // This whole section is not focusable
                                .semantics(mergeDescendants = true) { disabled() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center // Center the QR and Text
                        ) {
                            // Your QR Code
                            Image(
                                painter = painterResource(id = R.drawable.website_qr_code), // <-- FIXED: Use painterResource
                                contentDescription = "Scan to visit quickbars.app",
                                modifier = Modifier
                                    .size(60.dp) // Adjust size as needed
                                    .clip(RoundedCornerShape(8.dp)), // Softens the edges
                                contentScale = ContentScale.Fit
                            )

                            Spacer(Modifier.width(16.dp))

                            // Text Column
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "More info & support",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "quickbars.app",
                                    style = MaterialTheme.typography.bodySmall, // Smaller, subtle text
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            if (DemoModeManager.isInDemoMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DEMO MODE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            /* TODO used only for debugging data
            QuickBarsDiagCardFull(
                quickBarsKey = "quickbar_list",
                modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(10f)
            )
             */
        }
    }
}

@Composable
fun QuickBarsDiagCardFull(
    quickBarsKey: String,
    diagKey: String = "qb_last_load_diag",
    prefsProvider: (Context) -> SharedPreferences = { ctx ->
        ctx.getSharedPreferences("HA_QuickBars", Context.MODE_PRIVATE)
    },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { prefsProvider(context) }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // ---- data
    var diag by remember { mutableStateOf(prefs.getString(diagKey, "no status") ?: "no status") }
    var json by remember { mutableStateOf(prefs.getString(quickBarsKey, null)) }
    var jsonLen by remember { mutableStateOf(json?.length) }
    var exists by remember { mutableStateOf(prefs.contains(quickBarsKey)) }
    var jsonPretty by remember(json) { mutableStateOf(pretty(json)) }

    fun refresh() {
        exists = prefs.contains(quickBarsKey)
        json = prefs.getString(quickBarsKey, null)
        jsonLen = json?.length
        jsonPretty = pretty(json)
        diag = prefs.getString(diagKey, "no status") ?: "no status"
    }

    // Listen for pref changes
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == quickBarsKey || k == diagKey) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        refresh()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    // Attach Android OnKeyListener for DPAD scrolling
    DisposableEffect(view) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()

        val listener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        scope.launch { scroll.animateScrollBy(160f) }; true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        scope.launch { scroll.animateScrollBy(-160f) }; true
                    }
                    KeyEvent.KEYCODE_PAGE_DOWN -> {
                        scope.launch { scroll.animateScrollBy(600f) }; true
                    }
                    KeyEvent.KEYCODE_PAGE_UP -> {
                        scope.launch { scroll.animateScrollBy(-600f) }; true
                    }
                    else -> false
                }
            } else false
        }
        view.setOnKeyListener(listener)
        onDispose { view.setOnKeyListener(null) }
    }

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        color = Color(0xFF0E0E10).copy(alpha = 0.95f),
        contentColor = Color.White,
        modifier = modifier
            .widthIn(max = 1100.dp)
            .heightIn(max = 520.dp)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)   // actual scrolling content
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("QuickBars Debug", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Divider(color = Color(0x33FFFFFF))

            LabelVal("Prefs key") { Mono(quickBarsKey) }
            LabelVal("Exists")    { Text(exists.toString(), fontSize = 18.sp) }
            LabelVal("JSON len")  { Text(jsonLen?.toString() ?: "null", fontSize = 18.sp) }
            LabelVal("JSON head") { Mono(json?.take(120) ?: "null") }
            LabelVal("Last load") { Mono(diag) }

            Divider(color = Color(0x33FFFFFF))

            Text("Full JSON", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8EC9FF))
            SelectionContainer {
                Text(
                    text = jsonPretty ?: (json ?: "null"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                OutlinedButton(onClick = { prefs.edit().remove(diagKey).apply(); refresh() }) { Text("Clear status") }
                OutlinedButton(onClick = {
                    val j = prefs.getString(quickBarsKey, null)
                    if (j != null) {
                        val migrated = j.replace("\"displayTime\"", "\"showTimeOnQuickBar\"")
                        if (migrated != j) {
                            prefs.edit().putString(quickBarsKey, migrated)
                                .putString(diagKey, "${System.currentTimeMillis()}: manual-migrated")
                                .apply()
                            refresh()
                        }
                    }
                }) { Text("Force migrate key") }
            }
        }
    }
}

@Composable private fun LabelVal(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 16.sp, color = Color(0xFF8EC9FF), fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 18.sp, lineHeight = 20.sp, color = Color.White)
}

private fun pretty(raw: String?): String? = try {
    if (raw == null) null else GsonBuilder().setPrettyPrinting()
        .create().toJson(JsonParser.parseString(raw))
} catch (_: Exception) { raw }