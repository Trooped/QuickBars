package dev.trooped.tvquickbars.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import dev.trooped.tvquickbars.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.trooped.tvquickbars.data.AppInfo
import dev.trooped.tvquickbars.data.TriggerKey
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.ui.adapters.AppSpinnerAdapter
import dev.trooped.tvquickbars.ui.adapters.GenericSpinnerAdapter
import dev.trooped.tvquickbars.utils.ISpinnerItem
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.getLeanbackLaunchables
import java.util.Locale



data class QuickBarSpinnerItem(override val id: String, override val displayText: String) :
    ISpinnerItem {
    override val displayIcon: Drawable? = null // QuickBars have no icon
}

data class EntitySpinnerItem(
    override val id: String,
    override val displayText: String,
    override val displayIcon: Drawable?
) : ISpinnerItem

/**
 * TriggerKeyEditorActivity
 * Activity for editing trigger keys.
 * Allows the user to edit the friendly name and actions for a trigger key.
 * @property triggerKeyManager The TriggerKeyManager for managing trigger keys.
 * @property quickBarManager The QuickBarManager for managing QuickBars.
 * @property entitiesManager The SavedEntitiesManager for managing saved entities.
 * @property currentKey The current trigger key being edited.
 * @property keyCode The key code of the trigger key being edited.
 * @property keyNameText The TextView displaying the key name.
 * @property friendlyNameInput The EditText for entering the friendly name.
 * @property singlePressToggleGroup The MaterialButtonToggleGroup for single press actions.
 * @property singlePressActionSpinner The Spinner for selecting single press actions.
 * @property doublePressToggleGroup The MaterialButtonToggleGroup for double press actions.
 * @property doublePressActionSpinner The Spinner for selecting double press actions.
 * @property longPressToggleGroup The MaterialButtonToggleGroup for long press actions.
 * @property longPressActionSpinner The Spinner for selecting long press actions.
 * @property saveButton The Button for saving the key.
 * @property deleteButton The Button for deleting the key.
 * @property actionTypes The list of action types.
 * @property isInitializingUI Flag to prevent toggle listeners from firing.
 * @property singlePressToggleListener The listener for single press actions.
 * @property doublePressToggleListener The listener for double press actions.
 * @property longPressToggleListener The listener for long press actions.
 */
class TriggerKeyEditorActivity : BaseActivity() {

    private lateinit var triggerKeyManager: TriggerKeyManager
    private lateinit var quickBarManager: QuickBarManager
    private lateinit var entitiesManager: SavedEntitiesManager

    private var currentKey: TriggerKey? = null
    private var keyCode: Int = -1

    // UI components
    private lateinit var keyNameText: TextView
    private lateinit var friendlyNameInput: EditText

    // Single press
    private lateinit var singlePressToggleGroup: MaterialButtonToggleGroup
    private lateinit var singlePressActionSpinner: Spinner
    // Double press
    private lateinit var doublePressToggleGroup: MaterialButtonToggleGroup
    private lateinit var doublePressActionSpinner: Spinner

    // Long press
    private lateinit var longPressToggleGroup: MaterialButtonToggleGroup
    private lateinit var longPressActionSpinner: Spinner

    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private var isInitializingUI = false

    private lateinit var singlePressCameraSpinner: Spinner
    private lateinit var doublePressCameraSpinner: Spinner
    private lateinit var longPressCameraSpinner: Spinner

    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    private val singlePressToggleListener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked && !isInitializingUI) {
            handleSinglePressToggle(checkedId)
        }
    }
    private val doublePressToggleListener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked && !isInitializingUI) {
            handleDoublePressToggle(checkedId)
        }
    }
    private val longPressToggleListener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked && !isInitializingUI) {
            handleLongPressToggle(checkedId)
        }
    }

    private lateinit var singlePressAppSpinner: Spinner
    private lateinit var availableApps: List<AppInfo>

    private lateinit var enabledSwitch: SwitchMaterial

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trigger_key_editor)

        // Initialize managers
        triggerKeyManager = TriggerKeyManager(this)
        quickBarManager = QuickBarManager(this)
        entitiesManager = SavedEntitiesManager(this)

        // Set up toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            showDiscardChangesDialog()
        }
        supportActionBar?.title = "Edit Trigger Key"

        makeToolbarNavIconFocusable(toolbar)
        setToolbarNavigationIconColor(toolbar, R.color.md_theme_onSurface)

        // Get key code from intent
        keyCode = intent.getIntExtra("KEY_CODE", -1)
        if (keyCode == -1) {
            Toast.makeText(this, "Error: No key code provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Find views
        keyNameText = findViewById(R.id.key_name)
        friendlyNameInput = findViewById(R.id.edit_friendly_name)

        // Single press
        singlePressToggleGroup = findViewById(R.id.toggle_single_press_type)
        singlePressActionSpinner = findViewById(R.id.spinner_single_press_action)

        // Double press
        doublePressToggleGroup = findViewById(R.id.toggle_double_press_type)
        doublePressActionSpinner = findViewById(R.id.spinner_double_press_action)

        // Long press
        longPressToggleGroup = findViewById(R.id.toggle_long_press_type)
        longPressActionSpinner = findViewById(R.id.spinner_long_press_action)

        saveButton = findViewById(R.id.btn_save)
        deleteButton = findViewById(R.id.btn_delete)

        enabledSwitch = findViewById(R.id.switch_key_enabled)
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Visual feedback for enabled/disabled state
            updateEnabledStateUI(isChecked)
        }

        singlePressCameraSpinner = findViewById(R.id.spinner_single_press_camera)
        doublePressCameraSpinner = findViewById(R.id.spinner_double_press_camera)
        longPressCameraSpinner = findViewById(R.id.spinner_long_press_camera)

        // Load key data
        currentKey = triggerKeyManager.getTriggerKey(keyCode)
        if (currentKey == null) {
            Toast.makeText(this, "Error: Key not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load available apps
        availableApps = getLeanbackLaunchables()

        // Find app spinner
        singlePressAppSpinner = findViewById(R.id.spinner_single_press_app)

        // Set up app spinner
        val appAdapter = AppSpinnerAdapter(this, availableApps)
        singlePressAppSpinner.adapter = appAdapter

        // Set up toggle groups listeners
        setupToggleGroups()

        // Initialize UI with current key data
        updateUI()

        // Set up save button
        saveButton.setOnClickListener {
            saveKey()
        }

        // Set up delete button
        deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }


        // Update the toggle group listener
        singlePressToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isInitializingUI) {
                handleSinglePressToggle(checkedId)
            }
        }

        checkAccessibilityPermission()
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes")
            .setMessage("Are you sure you want to leave this screen? Changes will not be saved.")
            .setPositiveButton("Leave") { _, _ ->
                // User confirmed - leave without saving
                onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("Stay") { dialog, _ ->
                // User canceled - dismiss the dialog and stay on screen
                dialog.dismiss()
            }
            .show()
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showDiscardChangesDialog()
    }

    private fun validateEntityReferences() {
        val key = currentKey ?: return
        val savedEntities = entitiesManager.loadEntities().map { it.id }.toSet()
        var updated = false
        var updatedKey = key

        // Check single press action
        if (key.singlePressActionType == "entity" && key.singlePressAction != null &&
            !savedEntities.contains(key.singlePressAction)) {
            updatedKey = updatedKey.copy(singlePressAction = null, singlePressActionType = null)
            updated = true
        }

        // Check double press action
        if (key.doublePressActionType == "entity" && key.doublePressAction != null &&
            !savedEntities.contains(key.doublePressAction)) {
            updatedKey = updatedKey.copy(doublePressAction = null, doublePressActionType = null)
            updated = true
        }

        // Check long press action
        if (key.longPressActionType == "entity" && key.longPressAction != null &&
            !savedEntities.contains(key.longPressAction)) {
            updatedKey = updatedKey.copy(longPressAction = null, longPressActionType = null)
            updated = true
        }

        if (updated) {
            // Update the current key reference
            currentKey = updatedKey

            // Save the updated key
            triggerKeyManager.updateTriggerKey(updatedKey)

            // Notify the user
            Toast.makeText(
                this,
                "Some entity references were removed because they no longer exist",
                Toast.LENGTH_SHORT
            ).show()

            // Update the UI to reflect changes
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        validateEntityReferences()
    }

    /**
     * Checks if accessibility service is enabled and shows warning if not
     */
    private fun checkAccessibilityPermission() {
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.accessibility_permission_reminder,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.more_info_button) {
                // Show the detailed explanation dialog instead of direct settings navigation
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.accessibility_permission_title)
                    .setMessage(R.string.accessibility_permission_explanation)
                    .setPositiveButton(R.string.go_to_settings) { _, _ ->
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                        Toast.makeText(
                            this,
                            R.string.accessibility_settings_toast,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .setNegativeButton(R.string.not_now, null)
                    .setNeutralButton(R.string.more_info) { _, _ ->
                        PermissionUtils.showAccessibilityMoreInfo(this)
                    }
                    .show()
            }.show()
        }
    }


    /**
     * Update UI based on enabled state
     */
    private fun updateEnabledStateUI(enabled: Boolean) {
        // Optional visual feedback - adjust opacity for disabled state
        val alpha = if (enabled) 1.0f else 0.4f

        // Apply alpha to the action sections
        findViewById<View>(R.id.card_single_press).alpha = alpha
        findViewById<View>(R.id.card_double_press).alpha = alpha
        findViewById<View>(R.id.card_long_press).alpha = alpha

        // Update the key enabled text to provide more context
        val statusText = if (enabled)
            "Key Enabled"
        else
            "Key Disabled (Custom actions won't trigger)"

        enabledSwitch.text = statusText
    }


    /**
     * Set up the toggle groups listeners.
     */
    private fun setupToggleGroups() {
        // Add the listeners
        singlePressToggleGroup.addOnButtonCheckedListener(singlePressToggleListener)
        doublePressToggleGroup.addOnButtonCheckedListener(doublePressToggleListener)
        longPressToggleGroup.addOnButtonCheckedListener(longPressToggleListener)
    }

    /**
     * Handle the toggle groups for single/double/long press.
     * @param checkedId The ID of the checked button.
     */
    private fun handleSinglePressToggle(checkedId: Int) {
        singlePressActionSpinner.visibility = View.GONE
        singlePressAppSpinner.visibility = View.GONE
        singlePressCameraSpinner.visibility = View.GONE

        when (checkedId) {
            R.id.btn_single_none -> {
            }
            R.id.btn_single_quickbar -> {
                updateActionSpinner(singlePressActionSpinner, false)
                singlePressActionSpinner.visibility = View.VISIBLE
            }
            R.id.btn_single_entity -> {
                updateActionSpinner(singlePressActionSpinner, true)
                singlePressActionSpinner.visibility = View.VISIBLE
            }
            R.id.btn_single_camera_pip -> {
                updateCameraSpinner(singlePressCameraSpinner)
                singlePressCameraSpinner.visibility = View.VISIBLE
            }
            R.id.btn_single_app -> {
                singlePressAppSpinner.visibility = View.VISIBLE
            }
        }
    }
    private fun handleDoublePressToggle(checkedId: Int) {
        doublePressActionSpinner.visibility = View.GONE
        doublePressCameraSpinner.visibility = View.GONE

        when (checkedId) {
            R.id.btn_double_none -> {
                doublePressActionSpinner.visibility = View.GONE
            }
            R.id.btn_double_quickbar -> {
                updateActionSpinner(doublePressActionSpinner, false)
                doublePressActionSpinner.visibility = View.VISIBLE
            }
            R.id.btn_double_entity -> {
                updateActionSpinner(doublePressActionSpinner, true)
                doublePressActionSpinner.visibility = View.VISIBLE
            }
            R.id.btn_double_camera_pip -> {
                updateCameraSpinner(doublePressCameraSpinner)
                doublePressCameraSpinner.visibility = View.VISIBLE
            }
        }
    }
    private fun handleLongPressToggle(checkedId: Int) {
        longPressActionSpinner.visibility = View.GONE
        longPressCameraSpinner.visibility = View.GONE

        when (checkedId) {
            R.id.btn_long_none -> {
                findViewById<Button>(R.id.btn_long_none).nextFocusDownId = R.id.btn_save
            }
            R.id.btn_long_quickbar -> {
                updateActionSpinner(longPressActionSpinner, false)
                longPressActionSpinner.visibility = View.VISIBLE
                findViewById<Button>(R.id.btn_long_quickbar).nextFocusDownId = R.id.spinner_long_press_action
            }
            R.id.btn_long_entity -> {
                updateActionSpinner(longPressActionSpinner, true)
                longPressActionSpinner.visibility = View.VISIBLE
                findViewById<Button>(R.id.btn_long_entity).nextFocusDownId = R.id.spinner_long_press_action
            }
            R.id.btn_long_camera_pip -> {
                updateCameraSpinner(longPressCameraSpinner)
                longPressCameraSpinner.visibility = View.VISIBLE
                findViewById<Button>(R.id.btn_long_camera_pip).nextFocusDownId = R.id.spinner_long_press_camera
            }
        }
    }
    //-------------------------------------------------------------


    private fun updateCameraSpinner(spinner: Spinner) {
        // Get all camera entities
        val entities = entitiesManager.loadEntities().filter { it.id.startsWith("camera.") }
        val spinnerItems = mutableListOf<ISpinnerItem>()

        // Add placeholder
        spinnerItems.add(EntitySpinnerItem("select_camera", "Select Camera", null))

        // Add camera entities
        entities.mapTo(spinnerItems) {
            val iconRes = EntityIconMapper.getResourceIdByName("mdi_cctv")
                ?: R.drawable.cctv

            EntitySpinnerItem(
                id = it.id,
                displayText = "${it.customName.ifEmpty { it.friendlyName }} (${it.id})",
                displayIcon = getTintedDrawable(iconRes)
            )
        }

        spinner.adapter = GenericSpinnerAdapter(this, spinnerItems)
    }

    /**
     * Update the action spinner based on the type of action (the dropdown menu).
     * @param actionSpinner The Spinner to update.
     * @param isEntity Whether the action is for an entity or a QuickBar.
     */
    private fun updateActionSpinner(actionSpinner: Spinner, isEntity: Boolean) {
        if (isEntity) {
            // Set up entity spinner
            val entities = entitiesManager.getActionableEntities()
            val spinnerItems = mutableListOf<ISpinnerItem>()
            // Add the placeholder "Select" item
            spinnerItems.add(EntitySpinnerItem("select_entity", "Select Entity", null))

            entities.mapTo(spinnerItems) {
                // Get icon resource ID from name first, or fall back to defaults
                val iconRes = when {
                    !it.customIconOnName.isNullOrEmpty() -> {
                        val resId = EntityIconMapper.getResourceIdByName(it.customIconOnName)
                        resId ?: EntityIconMapper.getDefaultOnIconForEntity(it.id)
                    }
                    else -> EntityIconMapper.getDefaultOnIconForEntity(it.id)
                }

                EntitySpinnerItem(
                    id = it.id,
                    displayText = "${it.customName.ifEmpty { it.friendlyName }} (${it.id})",
                    displayIcon = getTintedDrawable(iconRes)
                )
            }
            actionSpinner.adapter = GenericSpinnerAdapter(this, spinnerItems)

        } else {
            // Set up QuickBar spinner
            val quickBars = quickBarManager.loadQuickBars()
            val spinnerItems = mutableListOf<ISpinnerItem>()
            // Add the placeholder "Select" item
            spinnerItems.add(QuickBarSpinnerItem("select_quickbar", "Select QuickBar"))

            quickBars.mapTo(spinnerItems) {
                QuickBarSpinnerItem(id = it.id, displayText = it.name)
            }
            actionSpinner.adapter = GenericSpinnerAdapter(this, spinnerItems)
        }
    }

    private fun AppSpinnerAdapter.getPosition(packageName: String): Int {
        for (i in 0 until count) {
            val app = getItem(i)
            if (app?.packageName == packageName) {
                return i
            }
        }
        return -1
    }

    /**
     * Update the UI based on the current Trigger Key data.
     */
    private fun updateUI() {
        val key = currentKey ?: return

        // Flag that we're initializing UI to prevent toggle listeners from firing
        isInitializingUI = true

        try {
            // Update key name display
            keyNameText.text = formatKeyName(key.keyName)

            // Update friendly name input
            friendlyNameInput.setText(key.friendlyName)

            enabledSwitch.isChecked = key.enabled
            updateEnabledStateUI(key.enabled)

            // Clear UI state
            singlePressActionSpinner.visibility = View.GONE
            singlePressAppSpinner.visibility = View.GONE
            singlePressCameraSpinner.visibility = View.GONE
            doublePressActionSpinner.visibility = View.GONE
            doublePressCameraSpinner.visibility = View.GONE
            longPressActionSpinner.visibility = View.GONE
            longPressCameraSpinner.visibility = View.GONE

            // ===== SINGLE PRESS =====
            if (key.singlePressAction != null) {
                when (key.singlePressActionType) {
                    "entity" -> {
                        // Prepare entity spinner
                        updateActionSpinner(singlePressActionSpinner, true)
                        singlePressActionSpinner.visibility = View.VISIBLE
                        singlePressToggleGroup.check(R.id.btn_single_entity)
                        setEntitySelection(singlePressActionSpinner, key.singlePressAction)
                    }
                    "camera_pip" -> {
                        // Prepare camera spinner
                        updateCameraSpinner(singlePressCameraSpinner)
                        singlePressCameraSpinner.visibility = View.VISIBLE
                        singlePressToggleGroup.check(R.id.btn_single_camera_pip)
                        setCameraSelection(singlePressCameraSpinner, key.singlePressAction)
                    }
                    else -> {
                        // Default: assume quickbar
                        updateActionSpinner(singlePressActionSpinner, false)
                        singlePressActionSpinner.visibility = View.VISIBLE
                        singlePressToggleGroup.check(R.id.btn_single_quickbar)
                        setQuickBarSelection(singlePressActionSpinner, key.singlePressAction)
                    }
                }
            } else {
                // No action, show preserve switch
                singlePressToggleGroup.check(R.id.btn_single_none)
            }

            if (key.originalAction?.startsWith("launch_app:") == true) {
                // Parse the package name from originalAction
                val packageName = key.originalAction.substringAfter("launch_app:")

                // Set the toggle to "App"
                singlePressToggleGroup.check(R.id.btn_single_app)

                // Show app spinner, hide action spinner
                singlePressActionSpinner.visibility = View.GONE
                singlePressAppSpinner.visibility = View.VISIBLE

                // Find and select the app in the spinner
                val appAdapter = singlePressAppSpinner.adapter as? AppSpinnerAdapter
                val appPosition = appAdapter?.getPosition(packageName) ?: -1
                if (appPosition >= 0) {
                    singlePressAppSpinner.setSelection(appPosition)
                }
            }

            if (key.singlePressActionType == "camera_pip") {
                // Prepare camera spinner
                updateCameraSpinner(singlePressCameraSpinner)
                singlePressCameraSpinner.visibility = View.VISIBLE

                // Check camera PIP button
                singlePressToggleGroup.check(R.id.btn_single_camera_pip)

                // Select camera
                setCameraSelection(singlePressCameraSpinner, key.singlePressAction ?: "")
            }

            // ===== DOUBLE PRESS =====
            if (key.doublePressAction != null) {
                when (key.doublePressActionType) {
                    "entity" -> {
                        updateActionSpinner(doublePressActionSpinner, true)
                        doublePressActionSpinner.visibility = View.VISIBLE
                        doublePressToggleGroup.check(R.id.btn_double_entity)
                        setEntitySelection(doublePressActionSpinner, key.doublePressAction)
                    }
                    "camera_pip" -> {
                        updateCameraSpinner(doublePressCameraSpinner)
                        doublePressCameraSpinner.visibility = View.VISIBLE
                        doublePressToggleGroup.check(R.id.btn_double_camera_pip)
                        setCameraSelection(doublePressCameraSpinner, key.doublePressAction)
                    }
                    else -> {
                        updateActionSpinner(doublePressActionSpinner, false)
                        doublePressActionSpinner.visibility = View.VISIBLE
                        doublePressToggleGroup.check(R.id.btn_double_quickbar)
                        setQuickBarSelection(doublePressActionSpinner, key.doublePressAction)
                    }
                }
            } else {
                // No action
                doublePressToggleGroup.check(R.id.btn_double_none)
            }

            // ===== LONG PRESS =====
            if (key.longPressAction != null) {
                when (key.longPressActionType) {
                    "entity" -> {
                        updateActionSpinner(longPressActionSpinner, true)
                        longPressActionSpinner.visibility = View.VISIBLE
                        longPressToggleGroup.check(R.id.btn_long_entity)
                        setEntitySelection(longPressActionSpinner, key.longPressAction)
                    }
                    "camera_pip" -> {
                        updateCameraSpinner(longPressCameraSpinner)
                        longPressCameraSpinner.visibility = View.VISIBLE
                        longPressToggleGroup.check(R.id.btn_long_camera_pip)
                        setCameraSelection(longPressCameraSpinner, key.longPressAction)
                    }
                    else -> {
                        updateActionSpinner(longPressActionSpinner, false)
                        longPressActionSpinner.visibility = View.VISIBLE
                        longPressToggleGroup.check(R.id.btn_long_quickbar)
                        setQuickBarSelection(longPressActionSpinner, key.longPressAction)
                    }
                }
            } else {
                // No action
                longPressToggleGroup.check(R.id.btn_long_none)
            }
        } finally {
            // Re-enable listener processing
            isInitializingUI = false
            singlePressActionSpinner.isFocusable = true
            singlePressActionSpinner.isFocusableInTouchMode = true
            doublePressActionSpinner.isFocusable = true
            doublePressActionSpinner.isFocusableInTouchMode = true
            longPressActionSpinner.isFocusable = true
            longPressActionSpinner.isFocusableInTouchMode = true
        }
    }

    private fun setCameraSelection(spinner: Spinner, cameraId: String) {
        val adapter = spinner.adapter as? GenericSpinnerAdapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item?.id == cameraId) {
                spinner.setSelection(i)
                return
            }
        }
    }

    /**
     * Set the QuickBar selection in the spinner.
     * @param spinner The Spinner to update.
     * @param quickBarId The ID of the QuickBar to select.
     */
    private fun setQuickBarSelection(spinner: Spinner, quickBarId: String) {
        val adapter = spinner.adapter as? GenericSpinnerAdapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item?.id == quickBarId) {
                spinner.setSelection(i)
                return
            }
        }
    }

    /**
     * Set the Entity selection in the spinner.
     * @param spinner The Spinner to update.
     * @param entityId The ID of the Entity to select.
     */
    private fun setEntitySelection(spinner: Spinner, entityId: String) {
        val adapter = spinner.adapter as? GenericSpinnerAdapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item?.id == entityId) {
                spinner.setSelection(i)
                return
            }
        }
    }

    /**
     * Save the current trigger key.
     */
    private fun saveKey() {
        val key = currentKey ?: return
        val friendlyName = friendlyNameInput.text.toString().trim()

        if (friendlyName.isEmpty()) {
            Toast.makeText(this, "Please enter a friendly name", Toast.LENGTH_SHORT).show()
            return
        }

        // Get single press action
        val (singleAction, singleActionType) = getSelectedAction(singlePressToggleGroup, singlePressActionSpinner)

        // Get double press action
        val (doubleAction, doubleActionType) = getSelectedAction(doublePressToggleGroup, doublePressActionSpinner)

        // Get long press action
        val (longAction, longActionType) = getSelectedAction(longPressToggleGroup, longPressActionSpinner)

        // getSelectedAction handles the app type.
        val originalAction: String?
        val appLabel: String?

        if (singleActionType == "app_launch") {
            originalAction = singleAction
            val position = singlePressAppSpinner.selectedItemPosition
            appLabel = if (position >= 0 && position < availableApps.size) {
                availableApps[position].label
            } else {
                null
            }
        } else {
            // Keep existing values
            originalAction = null
            appLabel = null
        }

        // Create updated key
        val updatedKey = key.copy(
            friendlyName = friendlyName,
            singlePressAction = singleAction,
            singlePressActionType = singleActionType,
            doublePressAction = doubleAction,
            doublePressActionType = doubleActionType,
            longPressAction = longAction,
            longPressActionType = longActionType,
            originalAction = originalAction,
            appLabel = appLabel,
            enabled = enabledSwitch.isChecked
        )

        // Save the key
        triggerKeyManager.updateTriggerKey(updatedKey)

        // Notify service to reload
        val intent = android.content.Intent(this, QuickBarService::class.java).apply {
            action = "ACTION_RELOAD_TRIGGER_KEYS"
            putExtra("FORCE_FULL_RELOAD", true)
        }
        startService(intent)

        Toast.makeText(this, "Trigger key saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * Get the selected action from the toggle group and spinner.
     */
    private fun getSelectedAction(toggleGroup: MaterialButtonToggleGroup, actionSpinner: Spinner): Pair<String?, String?> {
        val selectedItem = actionSpinner.selectedItem as? ISpinnerItem
        val actionId = selectedItem?.id?.takeIf { !it.startsWith("select_") }

        return when (toggleGroup.checkedButtonId) {
            R.id.btn_single_none, R.id.btn_double_none, R.id.btn_long_none -> {
                Pair(null, null)
            }
            R.id.btn_single_quickbar, R.id.btn_double_quickbar, R.id.btn_long_quickbar -> {
                Pair(actionId, "quickbar")
            }
            R.id.btn_single_entity, R.id.btn_double_entity, R.id.btn_long_entity -> {
                Pair(actionId, "entity")
            }
            R.id.btn_single_camera_pip, R.id.btn_double_camera_pip, R.id.btn_long_camera_pip -> {
                // For camera PIP, the camera entity ID is stored as the action
                val cameraSpinner = when (toggleGroup.id) {
                    R.id.toggle_single_press_type -> singlePressCameraSpinner
                    R.id.toggle_double_press_type -> doublePressCameraSpinner
                    R.id.toggle_long_press_type -> longPressCameraSpinner
                    else -> null
                }

                val selectedCamera = cameraSpinner?.selectedItem as? ISpinnerItem
                val cameraId = selectedCamera?.id?.takeIf { !it.startsWith("select_") }

                Pair(cameraId, "camera_pip")
            }
            R.id.btn_single_app -> {
                val position = singlePressAppSpinner.selectedItemPosition
                if (position >= 0 && position < availableApps.size) {
                    val app = availableApps[position]
                    Pair("launch_app:${app.packageName}", "app_launch")
                } else {
                    Pair(null, null)
                }
            }
            else -> Pair(null, null)
        }
    }

    /**
     * Show a confirmation dialog for deleting the trigger key.
     */
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Trigger Key")
            .setMessage("Are you sure you want to delete this trigger key?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteTriggerKey()
            }
            .show()
    }

    /**
     * Delete the current trigger key.
     */
    private fun deleteTriggerKey() {
        val key = currentKey ?: return

        // Use the dedicated method for key deletion
        val success = triggerKeyManager.deleteTriggerKey(key.keyCode)

        if (!success) {
            Toast.makeText(this, "Failed to delete trigger key", Toast.LENGTH_SHORT).show()
            return
        }

        // Notify service to reload
        val intent = Intent(this, QuickBarService::class.java).apply {
            action = "ACTION_RELOAD_TRIGGER_KEYS"
            putExtra("FORCE_FULL_RELOAD", true)
        }
        startService(intent)

        Toast.makeText(this, "Trigger key deleted", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * Format the key name for display (happens automatically on creation).
     */
    private fun formatKeyName(keyName: String): String {
        return keyName.replace("KEYCODE_", "")
            .split("_")
            .joinToString(" ") {
                it.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    /**
     * Handle the back button being pressed.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // TODO create a different, consistent way to set icon colors inside the app's interfaces, that isn't separated to this method + the iconColorsLists file.
    /**
     * Creates a new Drawable instance from a resource and tints it
     * using the theme's onSurface color.
     */
    private fun getTintedDrawable(@DrawableRes iconRes: Int): Drawable? {
        // Get the base drawable
        val drawable = ContextCompat.getDrawable(this, iconRes) ?: return null

        // Get the color from your theme (e.g., colorOnSurface)
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val color = typedValue.data

        // Create a new, mutable, tinted version of the drawable
        val wrappedDrawable = DrawableCompat.wrap(drawable).mutate()
        DrawableCompat.setTint(wrappedDrawable, color)

        return wrappedDrawable
    }
}