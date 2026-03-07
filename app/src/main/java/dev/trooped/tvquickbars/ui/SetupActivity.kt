package dev.trooped.tvquickbars.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import dev.trooped.tvquickbars.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
 * SetupActivity
 * serves as the initial setup/onboarding screen for the application.
 *
 * It guides the user through the following steps:
 * 1.  Inputting and saving Home Assistant URL and Access Token credentials.
 * 2.  Prompting the user to enable the application's Accessibility Service if it's not
 *     already active. This service is required for the app's core QuickBar functionality.
 * 3.  Prompting the user to grant the "Draw over other apps" (Overlay) permission,
 *     which is necessary for displaying QuickBars on screen.
 *
 * The activity checks these prerequisites in sequence. Once all configurations are correctly
 * set and permissions are granted, it automatically navigates the user to the
 * [QuickBarsListActivity] (or a similar main functional screen of the app) and finishes itself,
 * preventing users from returning to this setup flow via the back button.
 *
 * The state of these checks is re-evaluated in `onResume` to handle cases where the user
 * returns to the app after granting a permission in the system settings.
 *
 * @property settingsView The view containing the input fields for the Home Assistant URL and Token.
 * @property accessibilityView The view for the Accessibility Service prompt.
 * @property overlayPermissionView The view for the Overlay Permission prompt.
 * @property urlInput The input field for the Home Assistant URL.
 * @property tokenInput The input field for the Access Token.
 * @property saveButton The button to save the credentials.
 * @property tokenHelper The tokenTransferHelper instance for the local HTTP server.
 * @property pasteFromPhoneButton The button to paste the token from the phone.
 * @property howToButton The button to show the "How do I get Credentials" dialog.
 * @property savedEntitiesManager The manager for saving and loading entities.
 */
class SetupActivity : BaseActivity() {

    private lateinit var settingsView: NestedScrollView
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var saveButton: Button

    private var tokenHelper: TokenTransferHelper? = null
    private var overlayClearListener: (() -> Unit)? = null
    private lateinit var howToButton: Button
    private lateinit var savedEntitiesManager: SavedEntitiesManager

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Initialize views for welcome screen
        val welcomeView = findViewById<ConstraintLayout>(R.id.welcome_view)
        val serverAddressText = findViewById<TextView>(R.id.tv_server_address)
        val qrCodeImage = findViewById<ImageView>(R.id.img_qr_code)
        val enterManuallyButton = findViewById<Button>(R.id.btn_enter_manually)
        val progressContainer = findViewById<View>(R.id.progress_container)
        val howToEasyButton = findViewById<Button>(R.id.btn_how_to_credentials)
        val howItWorksButton = findViewById<Button>(R.id.btn_how_it_works)

        progressContainer.visibility = View.GONE

        // Initialize views for settings screen
        settingsView = findViewById(R.id.settings_view)
        urlInput = findViewById(R.id.et_ha_url)
        tokenInput = findViewById(R.id.et_ha_token)
        saveButton = findViewById(R.id.btn_save)
        howToButton = findViewById(R.id.btn_how_to)
        val backToEasyButton = findViewById<Button>(R.id.btn_back_to_easy)

        tokenHelper = TokenTransferHelper(this)

        if (!AppPrefs.hasPersistentConnectionFlag(this)) {
            // default OFF
            AppPrefs.setPersistentConnectionEnabled(this, false)
        }

        // TODO used only for debugging!
        //clearHaCredentials()

        savedEntitiesManager = SavedEntitiesManager(this)

        // Set up navigation between screens
        enterManuallyButton.setOnClickListener {
            // When switching to manual mode, stop the server to free up the port
            tokenHelper?.stopServer()
            welcomeView.visibility = View.GONE
            settingsView.visibility = View.VISIBLE
        }

        backToEasyButton.setOnClickListener {
            // When returning to easy setup, restart the token server
            settingsView.visibility = View.GONE
            welcomeView.visibility = View.VISIBLE
            ensureTokenServerRunning(progressContainer, serverAddressText, qrCodeImage)
        }

        // Set up the "How to" buttons - both screens use the same dialog
        howToButton.setOnClickListener { showHowToDialog() }
        howToEasyButton.setOnClickListener { showHowToDialog() }
        howItWorksButton.setOnClickListener { showServerExplanationDialog() }

        // Start the token server for the initial easy setup
        ensureTokenServerRunning(progressContainer, serverAddressText, qrCodeImage)

        // Set up the manual path buttons
        saveButton.setOnClickListener {
            saveSettingsAndProceed()
        }

        urlInput.addTextChangedListener(createTextWatcher())
        tokenInput.addTextChangedListener(createTextWatcher())

        updateSaveButtonState()
    }

    /**
     * Create a TextWatcher to handle changes in the input fields.
     */
    private fun createTextWatcher(): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSaveButtonState()
            }
        }
    }

    /**
     * Update the state of the "Save" button based on the input fields (enabled only if both are non-empty).
     */
    private fun updateSaveButtonState() {
        val urlText = urlInput.text.toString().trim()
        val tokenText = tokenInput.text.toString().trim()
        saveButton.isEnabled = urlText.isNotEmpty() && tokenText.isNotEmpty()
    }

    override fun onStart() {
        super.onStart()
        findViewById<View>(R.id.progress_container)?.visibility = View.GONE

        // When HA integration succeeds (pair confirm OR /api/ha/credentials saved),
        // the server fires PairingUiEvents.clear() → OverlayDispatcher.onClear().
        overlayClearListener = {
            runOnUiThread {
                // if creds exist now, go forward like the token-server path
                checkAppState()
            }
        }.also { listener ->
            //OverlayDispatcher.observeClear(listener)
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Check the state every time the user returns to this screen (e.g., after enabling the service)
        checkAppState()
    }

    /**
     * Show a dialog with instructions on how to get the credentials.
     */
    private fun showHowToDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_how_to_credentials, null)
        MaterialAlertDialogBuilder(this)
            .setTitle("How to Get Credentials")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showServerExplanationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_local_server_how_it_works, null)
        MaterialAlertDialogBuilder(this)
            .setTitle("How It Works")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    /**
     * Check the application's state in terms of credentials, accessibility, and overlay permission.
     */
    private fun checkAppState() {
        val url = SecurePrefsManager.getHAUrl(this)
        val token = SecurePrefsManager.getHAToken(this)

        if (url.isNullOrEmpty() || token.isNullOrEmpty()) {
            val welcomeView = findViewById<ConstraintLayout>(R.id.welcome_view)

            // Decide which view to show based on whether we can get an IP address
            val ipAddress = tokenHelper?.getLocalIpAddress()
            if (ipAddress != null) {
                // We can get an IP, so show welcome view and hide settings view
                welcomeView.visibility = View.VISIBLE
                settingsView.visibility = View.GONE
            } else {
                // Can't get IP, so show settings view and hide welcome view
                welcomeView.visibility = View.GONE
                settingsView.visibility = View.VISIBLE
            }
        }
        else {
            // Credentials are saved, check if we have any entities
            val savedEntities = savedEntitiesManager.loadEntities()

            if (savedEntities.isEmpty()) {
                // No entities yet, direct to EntityImporterActivity
                AppPrefs.setFirstTimeSetupInProgress(this, true)
                val intent = Intent(this, EntityImporterActivity::class.java)
                intent.putExtra("FIRST_TIME_SETUP", true)
                startActivity(intent)
                finish() // Close this setup activity
            } else {
                // We have entities, proceed to main activity
                val intent = Intent(this, ComposeMainActivity::class.java)
                startActivity(intent)
                finish() // Close this setup activity
            }
        }
    }

    /**
     * Save the credentials and proceed.
     */
    private fun saveSettingsAndProceed() {
        val userInput = urlInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()

        if (userInput.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Please enter both URL and Token", Toast.LENGTH_SHORT).show()
            return
        }

        validateAndSaveCredentials(userInput, token)
    }

    /**
     * Validate and save the credentials
     */
    private fun validateAndSaveCredentials(url: String, token: String) {
        if (DemoModeManager.isDemoCredentials(url, token)) {
            // Enable demo mode
            DemoModeManager.enableDemoMode()

            // Save demo credentials
            SecurePrefsManager.saveHAUrl(this@SetupActivity, DemoModeManager.DEMO_WEBSOCKET_URL)
            SecurePrefsManager.saveHAToken(this@SetupActivity, DemoModeManager.DEMO_TOKEN)

            // Show success message
            Toast.makeText(this@SetupActivity, "Demo mode activated!", Toast.LENGTH_SHORT).show()

            // Proceed to entity selection
            checkAppState()
            return
        }

        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Connecting")
            .setMessage("Attempting to connect to Home Assistant…")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            // Use the new validateWithFallback function instead
            val result = HomeAssistantClient.validateWithFallback(url, token)

            loadingDialog.dismiss()

            when (result) {
                is ValidationResult.Success -> {
                    runOnUiThread {
                        SecurePrefsManager.saveHAUrl(this@SetupActivity, url)
                        SecurePrefsManager.saveHAToken(this@SetupActivity, token)
                        Toast.makeText(this@SetupActivity, "Connection successful! Settings saved.", Toast.LENGTH_SHORT).show()
                        checkAppState()
                    }
                }
                is ValidationResult.Error -> {
                    val errorInfo = getErrorInfo(result.reason)
                    runOnUiThread {
                        showConnectionError(errorInfo.first, errorInfo.second)
                    }
                }
            }
        }
    }

    // Add a new method to restart the token server
    @RequiresApi(Build.VERSION_CODES.P)
    private fun ensureTokenServerRunning(progressContainer: View, serverAddressText: TextView, qrCodeImage: ImageView) {
        try {
            // First stop any existing server
            tokenHelper?.stopServer()

            // Re-initialize the token helper
            tokenHelper = TokenTransferHelper(this)

            // Get IP address
            val ipAddress = tokenHelper?.getLocalIpAddress()
            if (ipAddress != null) {
                // Show progress indicator
                progressContainer.visibility = View.GONE

                try {
                    // Start the token server with error handling
                    tokenHelper?.startTokenTransfer { haUrl, token ->
                        runOnUiThread {
                            progressContainer.visibility = View.GONE

                            // Process received credentials
                            if (haUrl.isNotEmpty() && token.isNotEmpty()) {
                                urlInput.setText(haUrl)
                                tokenInput.setText(token)
                                validateAndSaveCredentials(haUrl, token)
                            } else if (token.isNotEmpty()) {
                                tokenInput.setText(token)
                                // Show the manual screen if we only got the token
                                findViewById<ConstraintLayout>(R.id.welcome_view).visibility = View.GONE
                                settingsView.visibility = View.VISIBLE
                                Toast.makeText(this, "Token received! Please enter your Home Assistant URL.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    // Update UI with the server URL (might be different from default if fallback port was used)
                    val serverUrl = "http://$ipAddress:8765" // This will be approximate - the actual port might be different
                    serverAddressText.text = serverUrl

                    // Generate and display QR code using helper
                    qrCodeImage.setImageBitmap(tokenHelper?.generateQRCode(serverUrl))

                } catch (e: Exception) {
                    // Handle server start error
                    progressContainer.visibility = View.GONE
                    Toast.makeText(this, "Could not start token server: ${e.message}. Please use manual setup.", Toast.LENGTH_LONG).show()
                    findViewById<ConstraintLayout>(R.id.welcome_view).visibility = View.GONE
                    settingsView.visibility = View.VISIBLE
                }
            } else {
                // If we can't get IP address, show an error and go to manual setup
                Toast.makeText(this, "Could not determine IP address. Please use manual setup.", Toast.LENGTH_LONG).show()
                findViewById<ConstraintLayout>(R.id.welcome_view).visibility = View.GONE
                settingsView.visibility = View.VISIBLE
                progressContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Handle any unexpected errors
            progressContainer.visibility = View.GONE
            Toast.makeText(this, "Setup error: ${e.message}. Please use manual setup.", Toast.LENGTH_LONG).show()
            findViewById<ConstraintLayout>(R.id.welcome_view).visibility = View.GONE
            settingsView.visibility = View.VISIBLE
        }
    }


    /**
     * Stop the server when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        tokenHelper?.stopServer()
    }

    /**
     * Clear the Home Assistant credentials from local storage. Used for Debugging purposes currently.
     */
    private fun clearHaCredentials() {
        SecurePrefsManager.clearCredentials(this)

        //urlInput.setText("")
        //tokenInput.setText("")
        Toast.makeText(this, "Home Assistant credentials cleared", Toast.LENGTH_SHORT).show()
    }

    /**
     * Get user-friendly error title and message based on connection error reason
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
                "Invalid Server Address",
                "The provided URL is invalid. Please check it and try again."
            )
        }
    }

    /**
     * Show an error dialog with detailed information
     */
    private fun showConnectionError(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}


class Settings {
}