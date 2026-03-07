package dev.trooped.tvquickbars.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.trooped.tvquickbars.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.trooped.tvquickbars.data.AppIdProvider
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetStoreProductsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import dev.trooped.tvquickbars.data.CategoryItem
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ha.HomeAssistantClient
import dev.trooped.tvquickbars.ha.HomeAssistantListener
import dev.trooped.tvquickbars.ha.ValidationResult
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.PlusStatusManager
import dev.trooped.tvquickbars.utils.TokenTransferHelper
import kotlinx.coroutines.launch

/**
 * SettingsFragment
 * Fragment for the settings screen.
 * Allows the user to change his HA ip/token, and see the current state of his app.
 * @property haUrlTextView The TextView to display the Home Assistant URL.
 * @property overlayStatusIcon The ImageView to display the overlay permission status.
 * @property accessibilityStatusIcon The ImageView to display the accessibility permission status.
 * @property testHaClient The HomeAssistantClient for testing the connection.
 * @property validationClient The HomeAssistantClient for validating the token.
 * @property tokenHelper The TokenTransferHelper for handling token transfer.
 * @property tokenBeingValidated The token being validated.
 * @property tokenValidationSource The source of the token being validated.
 */
class SettingsFragment : Fragment(), HomeAssistantListener {

    private lateinit var haUrlTextView: TextView
    private lateinit var overlayStatusIcon: ImageView
    private lateinit var accessibilityStatusIcon: ImageView

    private var testHaClient: HomeAssistantClient? = null
    private var validationClient: HomeAssistantClient? = null
    private var tokenHelper: TokenTransferHelper? = null
    private var tokenBeingValidated: String? = null
    private var tokenValidationSource: String = ""

    private lateinit var backupButton: Button

    private lateinit var plusStatusText: TextView
    private lateinit var upgradeButton: Button
    private lateinit var coffeeButton: Button
    private lateinit var cookieButton: Button
    private lateinit var cakeButton: Button

    private lateinit var exportBackupButton: Button
    private lateinit var restoreBackupButton: Button

    private lateinit var appIdTextView: TextView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextThemeWrapper = ContextThemeWrapper(requireContext(), R.style.Theme_HAQuickBars)
        // Clone inflater with the theme applied
        val themedInflater = inflater.cloneInContext(contextThemeWrapper)

        // Inflate the layout for this fragment
        return themedInflater.inflate(R.layout.fragment_settings, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        haUrlTextView = view.findViewById(R.id.tv_ha_url)
        overlayStatusIcon = view.findViewById(R.id.icon_overlay_status)
        accessibilityStatusIcon = view.findViewById(R.id.icon_accessibility_status)

        appIdTextView = view.findViewById(R.id.tv_app_id)
        val appIdRow: View = view.findViewById(R.id.app_id_container)

        val appIdRaw = AppIdProvider.get(requireContext())
        appIdTextView.text = appIdRaw

        val updateUrlButton: Button = view.findViewById(R.id.btn_update_url)
        val updateTokenButton: Button = view.findViewById(R.id.btn_update_token)
        val testConnectionButton: Button = view.findViewById(R.id.btn_test_connection)

        val overlayContainer: View = view.findViewById(R.id.overlay_permission_container)
        val accessibilityContainer: View = view.findViewById(R.id.accessibility_permission_container)

        plusStatusText = view.findViewById(R.id.tv_plus_status)
        upgradeButton = view.findViewById(R.id.btn_upgrade_plus)
        coffeeButton = view.findViewById(R.id.btn_coffee)
        cookieButton = view.findViewById(R.id.btn_coffee_cookie)
        cakeButton = view.findViewById(R.id.btn_coffee_cake)

        updateUrlButton.setOnClickListener { showUpdateDialog("url") }
        updateTokenButton.setOnClickListener { showTokenUpdateOptions() }
        testConnectionButton.setOnClickListener { testConnection() }

        // Set up button click listeners
        upgradeButton.setOnClickListener {
            startActivity(Intent(requireContext(), UpgradeActivity::class.java))
        }

        // Set up coffee buttons
        coffeeButton.setOnClickListener { purchaseDonation("donation_coffee") }
        cookieButton.setOnClickListener { purchaseDonation("donation_cookie") }
        cakeButton.setOnClickListener { purchaseDonation("donation_cake") }

        exportBackupButton = view.findViewById(R.id.btn_export_backup)
        restoreBackupButton = view.findViewById(R.id.btn_restore_backup)


        exportBackupButton.setOnClickListener {
            val intent = Intent(requireContext(), BackupActivity::class.java)
            intent.putExtra("restore_mode", false)  // Use the parameter BackupActivity expects
            startActivity(intent)
        }

        restoreBackupButton.setOnClickListener {
            val intent = Intent(requireContext(), BackupActivity::class.java)
            intent.putExtra("restore_mode", true)  // Use the parameter BackupActivity expects
            startActivity(intent)
        }

        // Observe the Plus status
        viewLifecycleOwner.lifecycleScope.launch {
            PlusStatusManager.isPlus.collect { isPlusActive ->
                updatePlusUI(isPlusActive)
            }
        }

        val rateAppButton: Button = view.findViewById(R.id.btn_rate_app)
        rateAppButton.setOnClickListener {
            // Build the dialog first so we can set an OnShowListener before showing
            val dlg = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rate & review QuickBars?")
                .setMessage("This opens Google Play so you can leave a rating or review.")
                .setNegativeButton("Cancel", null) // Cancel first for DPAD users
                .setPositiveButton("Open Google Play") { _, _ ->
                    openPlayStoreForRating()
                }
                .create()

            // Default focus to Cancel on TV (use AppCompat's constant, not Compose)
            dlg.setOnShowListener {
                dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
            }

            dlg.show()
        }

        val persistentRow = view.findViewById<View>(R.id.persistent_conn_container)
        val persistentSwitch = view.findViewById<MaterialSwitch>(R.id.switch_persistent_connection)

        val enabled = AppPrefs.isPersistentConnectionEnabled(requireContext())
        persistentSwitch.isChecked = enabled

        fun applyPersistentChange(checked: Boolean) {
            if (DemoModeManager.isInDemoMode && checked) {
                // Allow the switch to remain checked for UI consistency
                AppPrefs.setPersistentConnectionEnabled(requireContext(), true)

                // Show a message that it doesn't actually do anything in demo mode
                Toast.makeText(
                    requireContext(),
                    "Persistent connection is visually enabled in demo mode, but no actual connection is established.",
                    Toast.LENGTH_LONG
                ).show()

                // Don't start the service in demo mode
                return
            }

            AppPrefs.setPersistentConnectionEnabled(requireContext(), checked)

            // Start/stop your background HA connection service (if you created it)
            // Replace HAConnectionService with your actual service class when ready.
            val ctx = requireContext().applicationContext
            val svc = android.content.Intent(ctx, dev.trooped.tvquickbars.services.HAConnectionService::class.java)

            if (checked) {
                // If your service is a ForegroundService, prefer:
                // androidx.core.content.ContextCompat.startForegroundService(ctx, svc)
                ctx.startService(svc)
            } else {
                ctx.stopService(svc)
            }
        }

        persistentRow.setOnClickListener {
            persistentSwitch.toggle()
        }
        persistentSwitch.setOnCheckedChangeListener { _, checked ->
            applyPersistentChange(checked)
        }

        overlayContainer.setOnClickListener {
            if (!PermissionUtils.canDrawOverlays(requireContext())) {
                PermissionUtils.showOverlayPermissionDialog(
                    this,
                    onCancel = {
                        // User declined, that's okay
                        updateUi()  // Update UI to show current permission status
                    }
                )
            } else {
                // Already granted, show a toast
                Toast.makeText(requireContext(), "Show over other apps permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        accessibilityContainer.setOnClickListener {
            if (!PermissionUtils.isAccessibilityServiceEnabled(requireContext())) {
                activity?.let { activity ->
                    PermissionUtils.showAccessibilityPermissionDialog(
                        activity,
                        onCancel = {
                            // User declined, that's okay
                            updateUi()  // Update UI to show current permission status
                        }
                    )
                }
            } else {
                // Already granted, show a toast
                Toast.makeText(requireContext(), "Accessibility permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        // Toast on entity trigger setting (More Settings card)
        val toastRow: View = view.findViewById(R.id.toast_on_trigger_container)
        val toastSwitch: MaterialSwitch = view.findViewById(R.id.switch_show_toast_on_trigger)

        // Initialize from prefs (defaults handled in AppPrefs / Application)
        val isToastEnabled = AppPrefs.isShowToastOnEntityTriggerEnabled(requireContext())
        toastSwitch.isChecked = isToastEnabled

        fun applyToastOnTriggerChange(checked: Boolean) {
            AppPrefs.setShowToastOnEntityTriggerEnabled(requireContext(), checked)
        }

        toastRow.setOnClickListener {
            toastSwitch.toggle()
        }
        toastSwitch.setOnCheckedChangeListener { _, checked ->
            applyToastOnTriggerChange(checked)
        }

        // 1. Find the TextView from your layout
        val aboutVersionTextView: TextView = view.findViewById(R.id.about_version_textview)

        // 2. Get the version name automatically
        val versionName = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0" // A fallback version
        }

        // 3. Format your string resource with the version name
        //    (Make sure you've updated the string in strings.xml to "Version %1$s")
        val formattedVersionString = getString(R.string.about_app_version, versionName)

        // 4. Set the text
        aboutVersionTextView.text = formattedVersionString
    }

    private fun updatePlusUI(isPlus: Boolean) {
        if (isPlus) {
            plusStatusText.text = "Premium features active"
            upgradeButton.text = "Active"
            upgradeButton.isEnabled = false
            upgradeButton.alpha = 0.6f
        } else {
            plusStatusText.text = "Unlock premium features"
            upgradeButton.text = "Upgrade"
            upgradeButton.isEnabled = true
            upgradeButton.alpha = 1f
        }
    }

    /**
     * Opens the app's Play Store page for rating and reviewing
     */
    private fun openPlayStoreForRating() {
        val ctx = requireContext()
        val pm  = ctx.packageManager

        // Hardcoded for your debug testing
        val packageName = "dev.trooped.tvquickbars"

        fun Intent.withCommonFlags() = apply {
            // Safe, simple back stack behavior from fragments/activities
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Try Play Store app first (force package), then any market handler, then HTTPS
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .setPackage("com.android.vending")   // Prefer Google Play app (works on Android TV too)
                .withCommonFlags(),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .withCommonFlags(),                  // Any market-capable app
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                .withCommonFlags()                   // Browser fallback
        )

        for (i in intents) {
            val canHandle = i.resolveActivity(pm) != null
            if (!canHandle) continue
            try {
                startActivity(i)
                return
            } catch (_: ActivityNotFoundException) {
                // Try next
            } catch (_: Throwable) {
                // Defensive: try next candidate
            }
        }

        Toast.makeText(ctx, "Couldn't open Google Play", Toast.LENGTH_SHORT).show()
    }


    private fun purchaseDonation(productId: String) {
        Purchases.sharedInstance.getProducts(
            listOf(productId),
            object : GetStoreProductsCallback {
                override fun onReceived(storeProducts: List<StoreProduct>) {
                    val product = storeProducts.firstOrNull()
                    if (product != null) {
                        Purchases.sharedInstance.purchaseProduct(
                            requireActivity(),
                            product,
                            object : PurchaseCallback {
                                override fun onCompleted(
                                    storeTransaction: StoreTransaction,
                                    customerInfo: CustomerInfo
                                ) {
                                    Toast.makeText(requireContext(), "Thank you for your support!", Toast.LENGTH_LONG).show()
                                }

                                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                                    if (!userCancelled) {
                                        Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Donation product not available right now.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(error: PurchasesError) {
                    Toast.makeText(
                        requireContext(),
                        "Error loading product: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /**
     * Show options for updating the token.
     * This includes manual entry and QR code transfer.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun showTokenUpdateOptions() {
        val options = arrayOf("Enter Manually", "Paste from phone using QR code")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Access Token")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUpdateDialog("token") // Existing manual entry
                    1 -> startTokenTransfer() // New QR code option
                }
            }
            .show()
    }

    /**
     * Starts the token transfer helper server.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun startTokenTransfer() {
        tokenHelper = TokenTransferHelper(requireContext())

        // Get the local IP address
        val ipAddress = tokenHelper?.getLocalIpAddress()
        if (ipAddress == null) {
            Toast.makeText(requireContext(), "Could not determine IP address", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate the server URL
        val serverUrl = "http://$ipAddress:8765"

        // Create the dialog with QR code
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_token, null)
        val qrImageView = dialogView.findViewById<ImageView>(R.id.img_qr_code)
        val urlTextView = dialogView.findViewById<TextView>(R.id.tv_qr_url)

        // Set the server URL and generate QR code
        urlTextView.text = serverUrl
        tokenHelper = TokenTransferHelper(requireContext())
        qrImageView.setImageBitmap(tokenHelper?.generateQRCode(serverUrl))

        // Show the dialog
        val qrDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Access Token")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ ->
                tokenHelper?.stopServer()
                dialog.dismiss()
            }
            .show()

        // Start the token server with token-only mode ENABLED
        tokenHelper?.startTokenTransfer(tokenOnlyMode = true) { haUrl, token ->
            requireActivity().runOnUiThread {
                // Dismiss the QR code dialog
                qrDialog.dismiss()

                if (token.isEmpty()) {
                    Toast.makeText(requireContext(), "No token received", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                validateAndSaveToken(token, "qr")
            }
        }
    }

    /**
     * Update the UI with the current settings.
     */
    fun updateUi() {
        val fullUrl = SecurePrefsManager.getHAUrl(requireContext())

        val displayUrl = if (fullUrl.isNullOrEmpty()) {
            "Not Set"
        } else {
            // Remove http:// or https:// from display, but keep the rest of the URL
            fullUrl.replace("https://", "").replace("http://", "")
        }

        haUrlTextView.text = displayUrl.ifEmpty { "Not Set" }

        // Update permission status with more information
        if (isOverlayPermissionGranted()) {
            overlayStatusIcon.setImageResource(R.drawable.ic_permission_on)
            view?.findViewById<TextView>(R.id.overlay_status_text)?.text = "Enabled"
        } else {
            overlayStatusIcon.setImageResource(R.drawable.ic_permission_off)
            view?.findViewById<TextView>(R.id.overlay_status_text)?.text = "Click to enable"
        }

        if (isAccessibilityServiceEnabled()) {
            accessibilityStatusIcon.setImageResource(R.drawable.ic_permission_on)
            view?.findViewById<TextView>(R.id.accessibility_status_text)?.text = "Enabled"
        } else {
            accessibilityStatusIcon.setImageResource(R.drawable.ic_permission_off)
            view?.findViewById<TextView>(R.id.accessibility_status_text)?.text = "Click to enable"
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the UI every time the fragment becomes visible
        updateUi()
    }

    /**
     * Test the connection to the Home Assistant server.
     */
    private fun testConnection() {
        val url = SecurePrefsManager.getHAUrl(requireContext())
        val token = SecurePrefsManager.getHAToken(requireContext())

        if (url == null || token == null) {
            Snackbar.make(requireView(), "URL or Token not set", Snackbar.LENGTH_SHORT).show()
            return
        }

        val loadingSnackbar = Snackbar.make(requireView(), "Testing connection...", Snackbar.LENGTH_INDEFINITE)
        loadingSnackbar.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = HomeAssistantClient.validateCredentials(url, token)
            loadingSnackbar.dismiss()

            when (result) {
                is ValidationResult.Success -> {
                    // Show auto-dismissing success dialog
                    if (isAdded) { // Make sure fragment is still attached
                        requireActivity().runOnUiThread {
                            val successDialog = MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Connection Test")
                                .setMessage("Connection Successful! Your Home Assistant server is properly configured.")
                                .setPositiveButton("OK", null)
                                .show()

                            // Auto-dismiss after 3 seconds
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (successDialog.isShowing && isAdded) {
                                    successDialog.dismiss()
                                }
                            }, 3000) // 3 seconds
                        }
                    }
                }
                is ValidationResult.Error -> {
                    val errorInfo = getErrorInfo(result.reason)
                    showError(errorInfo.first, errorInfo.second)
                }
            }
        }
    }

    /**
     * Callback for when entities are fetched from the Home Assistant server.
     */
    override fun onEntitiesFetched(categories: List<CategoryItem>) {
        // This is called after a successful test connection.
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Connection Successful!", Toast.LENGTH_LONG).show()
            testHaClient?.disconnect()
        }
    }


    override fun onEntityStateChanged(entityId: String, newState: String) {
        // Doesn't do anything
    }

    /**
     * Show a dialog for updating the token.
     */
    private fun showUpdateDialog(type: String) {
        val title = if (type == "url") "Update Home Assistant URL" else "Update Access Token"
        val hint  = if (type == "url") "URL or IP Address" else "Long-lived access token"

        // Use the library style, not app R.style.*
        val til = TextInputLayout(
            ContextThemeWrapper(
                requireContext(),
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox
            )
        ).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            isHintEnabled = true
            this.hint = hint

            val primary = androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_primary)
            boxStrokeColor = primary
            setHintTextColor(android.content.res.ColorStateList.valueOf(primary))
        }

        val edit = TextInputEditText(til.context).apply {
            if (type == "url") {
                setText(SecurePrefsManager.getHAUrl(requireContext()) ?: "")
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            } else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            val h = (16 * resources.displayMetrics.density).toInt()
            val v = (12 * resources.displayMetrics.density).toInt()
            setPadding(h, v, h, v)
        }
        til.addView(edit)

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(til, lp)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val userInput = edit.text.toString().trim()
                if (userInput.isEmpty()) {
                    Snackbar.make(requireView(), "Please enter a value", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val loading = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Validating")
                    .setMessage("Testing connection...")
                    .setCancelable(false)
                    .show()

                viewLifecycleOwner.lifecycleScope.launch {
                    if (type == "url") {
                        val token = SecurePrefsManager.getHAToken(requireContext())
                        if (token == null) {
                            loading.dismiss()
                            Snackbar.make(requireView(), "Token not set", Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }

                        // Let validateWithFallback try http/https if missing
                        val urlToValidate = userInput
                        when (val result = HomeAssistantClient.validateWithFallback(urlToValidate, token)) {
                            is ValidationResult.Success -> {
                                loading.dismiss()
                                SecurePrefsManager.saveHAUrl(requireContext(), userInput)
                                Snackbar.make(requireView(), "Home Assistant URL updated", Snackbar.LENGTH_SHORT).show()
                                updateUi()
                            }
                            is ValidationResult.Error -> {
                                loading.dismiss()
                                val (t, m) = getErrorInfo(result.reason)
                                showError(t, m)
                            }
                        }
                    } else {
                        val url = SecurePrefsManager.getHAUrl(requireContext())
                        if (url == null) {
                            loading.dismiss()
                            Snackbar.make(requireView(), "URL not set", Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }

                        when (val result = HomeAssistantClient.validateCredentials(url, userInput)) {
                            is ValidationResult.Success -> {
                                loading.dismiss()
                                SecurePrefsManager.saveHAToken(requireContext(), userInput)
                                Snackbar.make(requireView(), "Token validated and saved!", Snackbar.LENGTH_SHORT).show()
                                updateUi()
                            }
                            is ValidationResult.Error -> {
                                loading.dismiss()
                                val (t, m) = getErrorInfo(result.reason)
                                showError(t, m)
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     *  Permission Checking Logic (copied from SetupActivity)
     */
    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }
    }

    /**
     * AccessibilityService permission checking
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "${requireActivity().packageName}/${QuickBarService::class.java.canonicalName}"
        try {
            val enabledServices = Settings.Secure.getString(
                requireActivity().contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(serviceId) == true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Validates the token (by checking connection with HA) and saves it.
     * This is now refactored to use the central validation logic.
     */
    private fun validateAndSaveToken(token: String, source: String) {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Validating")
            .setMessage("Testing connection with this token...")
            .setCancelable(false)
            .show()

        val url = SecurePrefsManager.getHAUrl(requireContext())
        if (url == null) {
            loadingDialog.dismiss()
            Snackbar.make(requireView(), "Please set a Home Assistant URL first", Snackbar.LENGTH_LONG).show()
            return
        }

        // ✅ Launch a coroutine to use the central validation function
        viewLifecycleOwner.lifecycleScope.launch {
            val result = HomeAssistantClient.validateCredentials(url, token)
            loadingDialog.dismiss()

            when (result) {
                is ValidationResult.Success -> {
                    // Token works! Save it.
                    SecurePrefsManager.saveHAToken(requireContext(), token)

                    // Show different success message based on the source (manual vs. QR)
                    if (source == "qr") {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Token Updated")
                            .setMessage("Your Home Assistant access token has been successfully validated and saved.")
                            .setPositiveButton("OK") { _, _ -> updateUi() }
                            .show()
                    } else {
                        Snackbar.make(requireView(), "Token validated and saved!", Snackbar.LENGTH_SHORT).show()
                        updateUi()
                    }
                }
                is ValidationResult.Error -> {
                    // Show a specific error message if validation fails
                    val errorInfo = getErrorInfo(result.reason)
                    showError(errorInfo.first, errorInfo.second)
                }
            }
        }
    }

    /**
     * Get user-friendly error messages based on connection error reason
     */
    private fun getErrorInfo(reason: ConnectionState.Reason): Pair<String, String> {
        return when (reason) {
            ConnectionState.Reason.CANNOT_RESOLVE_HOST -> Pair(
                "Host Not Found",
                "Could not find Home Assistant at the specified address. Please check:\n\n" +
                        "• Is the IP address correct?\n" +
                        "• Is Home Assistant running?\n" +
                        "• Are you connected to the same network?"
            )
            ConnectionState.Reason.AUTH_FAILED -> Pair(
                "Authentication Failed",
                "The token was rejected by Home Assistant. Please enter your Long-Lived Access Token that was generated in Home Assistant."
            )
            ConnectionState.Reason.BAD_TOKEN -> Pair(
                "Invalid Token Format",
                "The provided token appears to be invalid. Please make sure you're using a Long-Lived Access Token."
            )
            ConnectionState.Reason.SSL_HANDSHAKE -> Pair(
                "SSL Certificate Error",
                "There was a problem with the SSL certificate. Try using HTTP instead of HTTPS for local connections."
            )
            ConnectionState.Reason.TIMEOUT -> Pair(
                "Connection Timeout",
                "The connection attempt timed out. Please check:\n\n" +
                        "• Is it a valid Home Assistant server IP address?\n" +
                        "• Is Home Assistant running?\n"
            )
            ConnectionState.Reason.NETWORK_IO -> Pair(
                "Network Error",
                "A network error occurred while connecting to Home Assistant. Please check your network connection."
            )
            ConnectionState.Reason.UNKNOWN -> Pair(
                "Unknown Error",
                "An unknown error occurred while connecting to Home Assistant. Please check your settings and try again."
            )

            ConnectionState.Reason.BAD_URL -> Pair(
                "Invalid URL",
                "The provided URL appears to be invalid. Please check the URL and try again."
            )
        }
    }

    /**
     * Show an error dialog with detailed information
     */
    private fun showError(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }


    override fun onDestroy() {
        super.onDestroy()
        tokenHelper?.stopServer()
        validationClient?.disconnect()
        validationClient = null
    }
}