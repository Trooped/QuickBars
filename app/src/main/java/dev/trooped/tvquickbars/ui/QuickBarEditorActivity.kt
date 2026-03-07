package dev.trooped.tvquickbars.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.trooped.tvquickbars.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.data.QuickBarPosition
import dev.trooped.tvquickbars.data.TriggerKey
import dev.trooped.tvquickbars.data.TriggerType
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.PlusStatusManager
import java.util.Locale

const val MAX_LEN_QUICKBAR_NAME = 20

/**
 * QuickBarEditorActivity
 * Allows the user to edit or create a QuickBar, and configure it based on his preference.
 * @property quickBarManager The manager for saving and loading QuickBars.
 * @property currentQuickBar The current QuickBar being edited.
 * @property isNewBar Whether this is a new QuickBar or an existing one.
 * @property captureDialog The dialog for capturing a key.
 * @property nameInputLayout The input layout for the QuickBar name.
 * @property nameInput The input field for the QuickBar name.
 * @property enabledSwitch The switch to enable/disable the QuickBar.
 * @property showNameSwitch The switch to show/hide the QuickBar name.
 * @property setTriggerButton The button to set the trigger for the QuickBar.
 * @property triggerTypeToggleGroup The toggle group for selecting the trigger type.
 * @property selectEntitiesButton The button to select entities for the QuickBar.
 * @property saveButton The button to save the QuickBar.
 * @property deleteButton The button to delete the QuickBar.
 * @property positionToggleGroup The toggle group for selecting the QuickBar position.
 * @property triggerErrorText The text indicating an error with the trigger.
 * @property editStyleButton The button to edit the QuickBar style.
 * @property gridLayoutLabel The label for the grid layout option.
 * @property gridLayoutSwitch The switch to enable/disable the grid layout.
 * @property triggerKeyManager The manager for saving and loading trigger keys.
 * @property currentTriggerKey The current trigger key.
 * @property currentTriggerType The current trigger type.
 * @property triggerInfoCard The card displaying trigger information.
 * @property triggerSetupCard The card for setting up triggers.
 * @property triggerChipsContainer The container for trigger chips.
 * @property noTriggersMessage The message indicating no triggers.
 */
class QuickBarEditorActivity : BaseActivity() {
    private lateinit var quickBarManager: QuickBarManager
    private var currentQuickBar: QuickBar? = null
    private var isNewBar = false

    private var captureDialog: androidx.appcompat.app.AlertDialog? = null

    private lateinit var nameInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var nameInput: EditText
    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var showNameSwitch: SwitchMaterial
    private lateinit var setTriggerButton: Button
    private lateinit var triggerTypeToggleGroup: MaterialButtonToggleGroup // Updated UI element
    private lateinit var selectEntitiesButton: Button
    private lateinit var reorderEntitiesButton: Button
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button

    private lateinit var positionToggleGroup: MaterialButtonToggleGroup

    private lateinit var triggerErrorText: TextView
    private var hasConflictingTrigger = false

    private lateinit var editStyleButton: Button

    private lateinit var gridLayoutLabel: TextView
    private lateinit var gridLayoutSwitch: SwitchMaterial

    private lateinit var triggerKeyManager: TriggerKeyManager

    private var currentTriggerKey: TriggerKey? = null
    private var currentTriggerType: TriggerType? = null

    private lateinit var triggerInfoCard: View
    private lateinit var triggerSetupCard: View
    private lateinit var triggerChipsContainer: ViewGroup
    private lateinit var noTriggersMessage: TextView

    private lateinit var haTriggerAliasInput: EditText
    private lateinit var haTriggerAliasLayout: com.google.android.material.textfield.TextInputLayout

    private lateinit var showTimeSwitch: SwitchMaterial

    private lateinit var btnAutoCloseDomains: Button
    private lateinit var autoCloseDelayToggleGroup: MaterialButtonToggleGroup

    private val positionButtonListener: MaterialButtonToggleGroup.OnButtonCheckedListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                // Premium positions require Plus
                val isPremiumPosition = checkedId == R.id.btn_position_left ||
                        checkedId == R.id.btn_position_top ||
                        checkedId == R.id.btn_position_bottom

                /*
                if (isPremiumPosition && !PlusStatusManager.isPlus.value && !DemoModeManager.isInDemoMode) {
                    // Block this listener temporarily to avoid recursion
                    group.removeOnButtonCheckedListener(positionButtonListener)

                    // Force check the RIGHT position button
                    group.clearChecked() // First clear all
                    group.check(R.id.btn_position_right)

                    // Re-add the listener
                    group.addOnButtonCheckedListener(positionButtonListener)

                    // Show upgrade screen
                    Intent(this@QuickBarEditorActivity, UpgradeActivity::class.java).also {
                        startActivity(it)
                    }

                    // Make sure our model is updated to RIGHT
                    currentQuickBar?.position = QuickBarPosition.RIGHT
                    updateGridLayoutVisibility()
                    return@OnButtonCheckedListener
                }
                */

                currentQuickBar?.position = when (checkedId) {
                    R.id.btn_position_right -> QuickBarPosition.RIGHT
                    R.id.btn_position_left -> QuickBarPosition.LEFT
                    R.id.btn_position_bottom -> QuickBarPosition.BOTTOM
                    R.id.btn_position_top -> QuickBarPosition.TOP
                    else -> QuickBarPosition.RIGHT
                }

                // Show/hide grid layout options based on position
                updateGridLayoutVisibility()
            }
        }

    /**
     * onCreate
     * Sets up the activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_bar_editor)

        quickBarManager = QuickBarManager(this)

        // Set up toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            showDiscardChangesDialog()
        }
        makeToolbarNavIconFocusable(toolbar)
        setToolbarNavigationIconColor(toolbar, R.color.md_theme_onSurface)


        // Initialize views
        //titleTextView = toolbar
        nameInput = findViewById(R.id.et_quickbar_name)
        setTriggerButton = findViewById(R.id.btn_set_trigger)
        triggerTypeToggleGroup = findViewById(R.id.toggle_trigger_type)
        selectEntitiesButton = findViewById(R.id.btn_select_entities)
        saveButton = findViewById(R.id.btn_save_quickbar)
        deleteButton = findViewById(R.id.btn_delete_quickbar)
        enabledSwitch = findViewById(R.id.switch_enabled)
        showNameSwitch = findViewById(R.id.switch_show_name)
        showTimeSwitch = findViewById(R.id.switch_show_time)
        nameInputLayout = findViewById(R.id.name_input_layout)

        positionToggleGroup = findViewById(R.id.toggle_position)

        // Initialize and set up the reorder button
        reorderEntitiesButton = findViewById(R.id.btn_reorder_entities)
        reorderEntitiesButton.setOnClickListener { launchReorderActivity() }
        // Only enable the reorder button if entities are selected
        reorderEntitiesButton.isEnabled = !(currentQuickBar?.savedEntityIds?.isEmpty() ?: true)

        triggerErrorText = findViewById(R.id.trigger_error_text)
        triggerErrorText.visibility = View.GONE

        editStyleButton = findViewById(R.id.btn_edit_style)
        editStyleButton.setOnClickListener { launchStyleEditor() }
        gridLayoutLabel = findViewById(R.id.tv_grid_layout_label)
        gridLayoutSwitch = findViewById(R.id.switch_grid_layout)

        quickBarManager = QuickBarManager(this)
        triggerKeyManager = TriggerKeyManager(this)

        checkOverlayPermission()

        //triggerInfoText = findViewById(R.id.tv_trigger_info)
        triggerInfoCard = findViewById(R.id.trigger_info_card)
        triggerSetupCard = findViewById(R.id.trigger_setup_card)

        triggerChipsContainer = findViewById(R.id.trigger_chips_container)
        noTriggersMessage = findViewById(R.id.tv_no_triggers_message)

        haTriggerAliasInput = findViewById(R.id.et_ha_trigger_alias)
        haTriggerAliasLayout = findViewById(R.id.ha_trigger_input_layout)

        btnAutoCloseDomains = findViewById(R.id.btn_auto_close_domains)
        btnAutoCloseDomains.setOnClickListener { showAutoCloseDomainDialog() }

        autoCloseDelayToggleGroup = findViewById(R.id.toggle_auto_close_delay)

        // Get the ID passed from the previous activity
        val quickBarId = intent.getStringExtra("QUICKBAR_ID")

        if (quickBarId == null) {
            // This is a new QuickBar
            isNewBar = true
            supportActionBar?.title = "Create QuickBar"
            deleteButton.visibility = View.GONE
            currentQuickBar = QuickBar()

            nameInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            // We are editing an existing QuickBar. Find it in our saved list.
            isNewBar = false
            supportActionBar?.title = "Edit QuickBar"
            deleteButton.visibility = View.VISIBLE
            val allBars = quickBarManager.loadQuickBars()
            currentQuickBar = allBars.find { it.id == quickBarId }

            if (currentQuickBar != null) {
                val (triggerKey, triggerType) = findTriggerForQuickBar(currentQuickBar!!.id)
                currentTriggerKey = triggerKey
                currentTriggerType = triggerType
            }
        }

        if (currentQuickBar == null) {
            // This is an error case, the ID was invalid or not found
            Log.e("QuickBarEditor", "Could not find QuickBar to edit. Finishing activity.")
            finish() // Close the editor
            return
        }

        nameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                currentQuickBar?.name = s?.toString()?.trim() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        updateUI()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            keyCaptureReceiver, IntentFilter("ACTION_KEY_CAPTURED")
        )

        haTriggerAliasInput.apply {
            // Add text watcher to update the current QuickBar
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentQuickBar?.haTriggerAlias = s?.toString()?.trim() ?: ""
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // Replace the entire click listener with a focus change listener
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Check if persistent background connection is enabled
                    if (!AppPrefs.isPersistentConnectionEnabled(this@QuickBarEditorActivity)) {
                        // Show the warning dialog
                        MaterialAlertDialogBuilder(this@QuickBarEditorActivity)
                            .setTitle("Background Connection Required")
                            .setMessage("Please enable persistent background connection in settings to enable triggering QuickBars from Home Assistant.")
                            .setIcon(R.drawable.ic_permission_off)
                            .setNegativeButton("Okay", null)
                            .show()
                    } else {
                        // Show keyboard when focused and background connection is enabled
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }

            // These properties help with keyboard behavior
            isFocusable = true
            isFocusableInTouchMode = true
        }

        updateHaTriggerInputState()

        // Add a listener to the toggle group.
        triggerTypeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                // Update the state variable for the trigger type
                currentTriggerType = when (checkedId) {
                    R.id.btn_single_press -> TriggerType.SINGLE_PRESS
                    R.id.btn_double_press -> TriggerType.DOUBLE_PRESS
                    R.id.btn_long_press -> TriggerType.LONG_PRESS
                    else -> TriggerType.DOUBLE_PRESS // Should not happen with a checked button
                }

                // Update the UI to reflect potential new conflicts
                checkTriggerConflicts()
            }
        }

        // Update position toggle listener to show/hide grid option
        positionToggleGroup.addOnButtonCheckedListener(positionButtonListener)

        gridLayoutSwitch.setOnCheckedChangeListener { _, isChecked ->
            /*
            if (isChecked && !PlusStatusManager.isPlus.value && !DemoModeManager.isInDemoMode) {
                // Reset switch to off position
                gridLayoutSwitch.isChecked = false

                // Show upgrade screen
                Intent(this, UpgradeActivity::class.java).also {
                    startActivity(it)
                }
                return@setOnCheckedChangeListener
            }
            */

            currentQuickBar?.useGridLayout = isChecked
        }

        autoCloseDelayToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val delaySeconds = when (checkedId) {
                R.id.btn_auto_close_never -> 0
                R.id.btn_auto_close_15s   -> 15
                R.id.btn_auto_close_30s   -> 30
                R.id.btn_auto_close_60s   -> 60
                else                      -> 0
            }

            currentQuickBar?.autoCloseDelay = delaySeconds
        }

        setTriggerButton.setOnClickListener {
            // If this is a new QuickBar and there are already trigger keys defined
            if (isNewBar && triggerKeyManager.loadTriggerKeys().size >= 1 && !PlusStatusManager.isPlus.value && !DemoModeManager.isInDemoMode) {
                // User has reached the free limit, show upgrade screen
                Intent(this, UpgradeActivity::class.java).also {
                    startActivity(it)
                }
                return@setOnClickListener
            }

            listenForTriggerKey()
        }
        selectEntitiesButton.setOnClickListener { launchEntitySelector() }
        saveButton.setOnClickListener { saveQuickBar() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }

        updateTriggerUI()

    }

    private fun updateHaTriggerInputState() {
        val isBackgroundEnabled = AppPrefs.isPersistentConnectionEnabled(this)

        // Enable/disable the input field based on background connection status
        haTriggerAliasInput.isEnabled = isBackgroundEnabled

        // Update hint text to reflect status
        if (!isBackgroundEnabled) {
            haTriggerAliasLayout.helperText = "You must enable persistent background connection in settings to enable triggering QuickBars from Home Assistant"
        } else {
            haTriggerAliasLayout.helperText = "Optional: Trigger this QuickBar from Home Assistant with quickbars.open event with this alias"
        }
    }

    // Shows confirmation dialog when clicking back.
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

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionUtils.canDrawOverlays(this)) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.overlay_permission_reminder,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.more_info_button) {
                PermissionUtils.showOverlayPermissionDialog(this)
            }.show()
        }
    }

    /**
     * Broadcast receiver for capturing a key
     */
    private val keyCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_KEY_CAPTURED") {
                val keyCode = intent.getIntExtra("keyCode", -1)
                val keyName = intent.getStringExtra("keyName")

                if (keyCode != -1 && keyName != null) {
                    // Update the currentTriggerKey, creating it if it doesn't exist
                    currentTriggerKey = (currentTriggerKey ?: TriggerKey(keyCode = -1, keyName = ""))

                    // If no trigger type was selected yet, default to double press
                    if (currentTriggerType == null) {
                        currentTriggerType = TriggerType.DOUBLE_PRESS
                    }

                    // Update the trigger key
                    val existing = triggerKeyManager.getTriggerKey(keyCode)
                    currentTriggerKey = (currentTriggerKey ?: TriggerKey(-1, ""))
                        .copy(
                            keyCode       = keyCode,
                            keyName       = keyName,
                            // if the key was already named elsewhere keep it, otherwise null
                            friendlyName  = existing?.friendlyName
                        )
                    updateUI()

                    // Clear the captured key from SharedPreferences
                    getSharedPreferences("HA_QuickBars", MODE_PRIVATE)
                        .edit {
                            remove("captured_keycode")
                                .remove("captured_keyname")
                        }

                    // Dismiss the capture dialog
                    captureDialog?.dismiss()
                    captureDialog = null

                    // Check if we need to ask for a friendly name
                    val triggerKey = triggerKeyManager.getTriggerKey(keyCode)
                    if (triggerKey == null || triggerKey.friendlyName.isNullOrBlank()) {
                        showFriendlyNameDialog(keyCode, keyName)
                    }
                }
            }
        }
    }

    /**
     * Launch the entity selector activity.
     */
    private fun launchEntitySelector() {
        try {
            val intent = Intent(this, QuickBarEntitySelectorActivity::class.java).apply {
                // Change from emptySet() to emptyList()
                val idList = ArrayList(currentQuickBar?.savedEntityIds ?: emptyList())
                putStringArrayListExtra("INITIAL_SELECTED_IDS", idList)
            }
            entitySelectorLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("QuickBarEditor", "Error launching entity selector", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Launcher for the entity selector activity.
     */
    private val entitySelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val returnedIds = result.data?.getStringArrayListExtra("SELECTED_IDS") ?: arrayListOf()
            // Change toMutableSet() to toMutableList()
            currentQuickBar?.savedEntityIds = returnedIds.toMutableList()

            // Update the reorder button's enabled state
            reorderEntitiesButton.isEnabled = !(currentQuickBar?.savedEntityIds?.isEmpty() ?: true)
        }
    }

    /**
     * Launcher for the reorder activity.
     */
    private val reorderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val reorderedIds = result.data?.getStringArrayListExtra("ENTITY_IDS") ?: return@registerForActivityResult
            currentQuickBar?.savedEntityIds = reorderedIds.toMutableList()
            Log.d("QuickBarEditor", "Received reordered entities: ${currentQuickBar?.savedEntityIds}")
        }
    }

    /**
     * Launch the entity reorder activity.
     */
    private fun launchReorderActivity() {
        try {
            val intent = Intent(this, ReorderEntitiesActivity::class.java).apply {
                putStringArrayListExtra("ENTITY_IDS", ArrayList(currentQuickBar?.savedEntityIds ?: mutableListOf()))
            }
            reorderLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("QuickBarEditor", "Error launching reorder activity", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Update the UI based on the current trigger.
     */
    private fun updateTriggerUI() {
        if (isNewBar) {
            // Show the trigger setup UI for new QuickBars
            triggerSetupCard.visibility = View.VISIBLE
            triggerInfoCard.visibility = View.GONE
        } else {
            // Show informational UI for existing QuickBars
            triggerSetupCard.visibility = View.GONE
            triggerInfoCard.visibility = View.VISIBLE

            // Update the info text to show current trigger
            updateTriggerInfoText()
        }
    }

    /**
     * Update the trigger info text based on the current trigger.
     */
    private fun updateTriggerInfoText() {
        // Clear existing trigger chips
        triggerChipsContainer.removeAllViews()

        val allTriggers = findAllTriggersForQuickBar(currentQuickBar?.id ?: "")

        if (allTriggers.isEmpty()) {
            // Show the "no triggers" message
            noTriggersMessage.visibility = View.VISIBLE
            return
        }

        // Hide the "no triggers" message
        noTriggersMessage.visibility = View.GONE

        // Add a chip for each trigger
        for ((key, type) in allTriggers) {
            addTriggerChip(key, type)
        }
    }

    /**
     * Add a chip for a trigger on the existing QuickBar.
     */
    private fun addTriggerChip(key: TriggerKey, type: TriggerType) {
        // Inflate the chip layout
        val chipView = layoutInflater.inflate(R.layout.item_trigger_chip, triggerChipsContainer, false)

        // Get references to views in the chip
        val typeIcon = chipView.findViewById<ImageView>(R.id.trigger_type_icon)
        val typeText = chipView.findViewById<TextView>(R.id.trigger_type_text)
        val keyName = chipView.findViewById<TextView>(R.id.trigger_key_name)

        // Set the icon and type text based on trigger type
        when (type) {
            TriggerType.SINGLE_PRESS -> {
                typeIcon.setImageResource(R.drawable.ic_remote)
                typeText.text = "Single Press"
            }
            TriggerType.DOUBLE_PRESS -> {
                typeIcon.setImageResource(R.drawable.ic_remote)
                typeText.text = "Double Press"
            }
            TriggerType.LONG_PRESS -> {
                typeIcon.setImageResource(R.drawable.ic_remote)
                typeText.text = "Long Press"
            }
        }

        // Set the key name
        val displayName = key.friendlyName ?: formatKeyName(key.keyName)
        keyName.text = displayName

        // No need for complex layout params, just add the view
        triggerChipsContainer.addView(chipView)
    }


    /**
     * Find the one specific trigger for a QuickBar (on a new one).
     */
    private fun findTriggerForQuickBar(quickBarId: String): Pair<TriggerKey?, TriggerType?> {
        val allTriggers = findAllTriggersForQuickBar(quickBarId)
        return if (allTriggers.isEmpty()) {
            Pair(null, null)
        } else {
            val (key, type) = allTriggers[0]
            Pair(key, type)
        }
    }

    /**
     * Find all triggers for a QuickBar (existing one).
     */
    private fun findAllTriggersForQuickBar(quickBarId: String): List<Pair<TriggerKey, TriggerType>> {
        val results = mutableListOf<Pair<TriggerKey, TriggerType>>()
        val allTriggerKeys = triggerKeyManager.loadTriggerKeys()

        for (key in allTriggerKeys) {
            // Check all three press types for this key
            if (key.singlePressAction == quickBarId && key.singlePressActionType == "quickbar") {
                results.add(Pair(key, TriggerType.SINGLE_PRESS))
            }
            if (key.doublePressAction == quickBarId && key.doublePressActionType == "quickbar") {
                results.add(Pair(key, TriggerType.DOUBLE_PRESS))
            }
            if (key.longPressAction == quickBarId && key.longPressActionType === "quickbar") {
                results.add(Pair(key, TriggerType.LONG_PRESS))
            }
        }

        return results
    }

    /**
     * Update the visibility of the grid layout options based on the current QuickBar position.
     */
    private fun updateGridLayoutVisibility() {
        val isVerticalPosition = currentQuickBar?.position == QuickBarPosition.LEFT ||
                currentQuickBar?.position == QuickBarPosition.RIGHT

        gridLayoutLabel.visibility = if (isVerticalPosition) View.VISIBLE else View.GONE
        gridLayoutSwitch.visibility = if (isVerticalPosition) View.VISIBLE else View.GONE

        // If switching to a horizontal position, disable grid layout
        if (!isVerticalPosition) {
            gridLayoutSwitch.isChecked = false
            currentQuickBar?.useGridLayout = false
        }
    }

    /**
     * Style editor activity configurations.
     */
    private val styleEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Get individual properties from result
            val backgroundColor = result.data?.getStringExtra("BACKGROUND_COLOR")
            val backgroundOpacity = result.data?.getIntExtra("BACKGROUND_OPACITY", 90)
            val onStateColor = result.data?.getStringExtra("ON_STATE_COLOR")

            // Update the current QuickBar
            if (backgroundColor != null) {
                currentQuickBar?.backgroundColor = backgroundColor
            }
            if (backgroundOpacity != null) {
                currentQuickBar?.backgroundOpacity = backgroundOpacity
            }
            if (onStateColor != null) {
                currentQuickBar?.onStateColor = onStateColor
            }
        }
    }

    /**
     * Launch the style editor activity.
     */
    private fun launchStyleEditor() {
        val intent = Intent(this, QuickBarStyleActivity::class.java).apply {
            // Pass individual properties instead of JSON
            putExtra("BACKGROUND_COLOR", currentQuickBar?.backgroundColor ?: "colorSurface")
            putExtra("BACKGROUND_OPACITY", currentQuickBar?.backgroundOpacity ?: 90)
            putExtra("ON_STATE_COLOR", currentQuickBar?.onStateColor ?: "colorPrimary")
            if (currentQuickBar?.backgroundColor.equals("custom", true) && currentQuickBar?.customBackgroundColor != null) {
                putExtra("CUSTOM_BG_RGB", currentQuickBar?.customBackgroundColor!!.toIntArray())
            }
            if (currentQuickBar?.onStateColor.equals("custom", true) && currentQuickBar?.customOnStateColor != null) {
                putExtra("CUSTOM_ON_RGB", currentQuickBar?.customOnStateColor!!.toIntArray())
            }
        }
        styleEditorLauncher.launch(intent)
    }

    /**
     * Show a confirmation dialog before deleting the QuickBar.
     */
    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete QuickBar")
            .setMessage("This QuickBar will be permanently deleted.")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { _, _ ->
                deleteQuickBar()
            }
            .show()
    }

    /**
     * Update the UI based on the current QuickBar and it's configuration.
     */
    private fun updateUI() {
        nameInput.setText(currentQuickBar?.name)
        enabledSwitch.isChecked = currentQuickBar?.isEnabled ?: true
        showNameSwitch.isChecked = currentQuickBar?.showNameInOverlay ?: true
        showTimeSwitch.isChecked = currentQuickBar?.showTimeOnQuickBar ?: true
        reorderEntitiesButton.isEnabled = !(currentQuickBar?.savedEntityIds?.isEmpty() ?: true)

        updateTriggerButtonText()
        checkTriggerConflicts()

        haTriggerAliasInput.setText(currentQuickBar?.haTriggerAlias ?: "")

        // Update grid layout switch state and visibility
        gridLayoutSwitch.isChecked = currentQuickBar?.useGridLayout ?: false
        updateGridLayoutVisibility()

        updateAutoCloseButtonText()

        val delay = currentQuickBar?.autoCloseDelay ?: 0
        autoCloseDelayToggleGroup.check(
            when (delay) {
                15 -> R.id.btn_auto_close_15s
                30 -> R.id.btn_auto_close_30s
                60 -> R.id.btn_auto_close_60s
                else -> R.id.btn_auto_close_never  // default / unknown → Never
            }
        )

        // Set the correct button as checked based on the saved trigger type
        triggerTypeToggleGroup.check(
            when (currentTriggerType) {
                TriggerType.SINGLE_PRESS -> R.id.btn_single_press
                TriggerType.DOUBLE_PRESS -> R.id.btn_double_press
                TriggerType.LONG_PRESS -> R.id.btn_long_press
                null -> R.id.btn_double_press // Default if no trigger is set
            }
        )

        positionToggleGroup.check(
            when (currentQuickBar?.position) {
                QuickBarPosition.RIGHT -> R.id.btn_position_right
                QuickBarPosition.LEFT -> R.id.btn_position_left
                QuickBarPosition.BOTTOM -> R.id.btn_position_bottom
                QuickBarPosition.TOP -> R.id.btn_position_top
                null -> R.id.btn_position_right // Default
            }
        )
    }

    /**
     * Update the button text based on the current trigger.
     */
    private fun updateTriggerButtonText() {
        val keyCode = currentTriggerKey?.keyCode
        val keyName = currentTriggerKey?.keyName

        if (keyCode != null && keyCode > 0 && keyName != null) {
            val friendlyName = currentTriggerKey?.friendlyName
            setTriggerButton.text = if (!friendlyName.isNullOrBlank()) {
                "Trigger: $friendlyName"
            } else {
                "Trigger: ${formatKeyName(keyName)}"
            }
        } else {
            setTriggerButton.text = "Set Trigger Button"
        }
    }

    /**
     * Format a key name to be more readable.
     */
    private fun formatKeyName(keyName: String?): String {
        if (keyName == null) return ""
        return keyName.replace("KEYCODE_", "")
            .split("_")
            .joinToString(" ") {
                it.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }


    override fun onResume() {
        super.onResume()
        // Every time this screen becomes visible, check if the service has saved a new key for us.
        checkForKeyCapture()
        updateHaTriggerInputState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to prevent memory leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(keyCaptureReceiver)
    }

    /**
     * Listen for the trigger button being pressed.
     */
    private fun listenForTriggerKey() {
        // First check if we have accessibility permission
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            PermissionUtils.showAccessibilityPermissionDialog(
                this,
                onCancel = {
                    // User declined permission - just show toast and continue
                    Toast.makeText(
                        this,
                        R.string.accessibility_permission_denied_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            return
        }

        // We have permission, proceed with key capture
        showKeyCaptureDialog()

        val intent = Intent(this, QuickBarService::class.java).apply {
            action = "ACTION_CAPTURE_KEY"
        }
        startService(intent)
    }

    /**
     * Show the key capture dialog.
     */
    private fun showKeyCaptureDialog() {
        // Use Material 3's MaterialAlertDialogBuilder instead of custom layout
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Capture Key")
            .setMessage(
                """
    Press a remote button to set the trigger.

    Avoid: DPAD arrows (↑ ↓ ← →), OK/Center, Home, Back, Volume, Power etc.
    ⚠️ The selected button will lose it's original action.

    You can press BACK to cancel.
    """.trimIndent()
            )
            .setIcon(R.drawable.ic_remote)
            .setCancelable(true)
            .setOnCancelListener {
                // Cancel capture mode in service when back button is pressed
                val intent = Intent(this, QuickBarService::class.java).apply {
                    action = "ACTION_CANCEL_CAPTURE"
                }
                startService(intent)
            }
            .create()

        dialog.show()

        // Save dialog reference to dismiss it when key is captured
        captureDialog = dialog
    }

    /**
     * Check if the service has saved a new key for us.
     */
    private fun checkForKeyCapture() {
        val prefs = getSharedPreferences("HA_QuickBars", MODE_PRIVATE)
        val capturedKeyCode = prefs.getInt("captured_keycode", -1)
        val capturedKeyName = prefs.getString("captured_keyname", null)

        if (capturedKeyCode != -1 && capturedKeyName != null) {
            // Update the currentTriggerKey, creating it if it doesn't exist
            currentTriggerKey = (currentTriggerKey ?: TriggerKey(keyCode = -1, keyName = ""))
                .copy(keyCode = capturedKeyCode, keyName = capturedKeyName)

            // If no trigger type was selected yet, default to double press
            if (currentTriggerType == null) {
                currentTriggerType = TriggerType.DOUBLE_PRESS
            }

            updateUI()

            // Clear the captured key
            prefs.edit { remove("captured_keycode").remove("captured_keyname") }
        }
    }

    /**
     * Shows a dialog to change the friendly name of a key, if it's a new triggerkey + new quickbar.
     */
    private fun showFriendlyNameDialog(keyCode: Int, keyName: String) {
        val defaultName = formatKeyName(keyName)

        val layout = layoutInflater.inflate(R.layout.dialog_friendly_name, null)
        val editText = layout.findViewById<EditText>(R.id.et_friendly_name)
        editText.setText(defaultName)

        MaterialAlertDialogBuilder(this)
            .setTitle("Name this button")
            .setView(layout)
            .setMessage("Give this remote button a friendly name to help you remember it")
            .setPositiveButton("Save") { _, _ ->
                val friendlyName = editText.text.toString().trim()
                if (friendlyName.isNotEmpty()) {
                    // Create/update the TriggerKey
                    val triggerKey = TriggerKey(
                        keyCode = keyCode,
                        keyName = keyName,
                        friendlyName = friendlyName
                    )
                    triggerKeyManager.updateTriggerKey(triggerKey)
                    currentTriggerKey = triggerKeyManager.getTriggerKey(keyCode)
                    updateUI()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Saves the current QuickBar to persistent storage.
     */
    private fun saveQuickBar() {
        // Do the validation first
        val name = nameInput.text.toString().trim()

        // VALIDATION
        if (name.isEmpty()) {
            nameInputLayout.error = "Name cannot be empty"
            nameInput.requestFocus()
            return
        }

        if (name.length >= MAX_LEN_QUICKBAR_NAME) {
            nameInputLayout.error = "QuickBar name cannot exceed $MAX_LEN_QUICKBAR_NAME characters."
            nameInput.requestFocus()
            return
        }

        if (isNameAlreadyTaken(name)) {
            nameInputLayout.error = "QuickBar name is already taken. Please choose another one."
            nameInput.requestFocus()
            return
        }

        nameInputLayout.error = null // Clear previous errors

        // Validation passed, proceed to save
        finalizeSaveQuickBar()
    }

    /**
     * Finalizes the saving of the QuickBar by updating its properties and saving it to persistent storage.
     */
    private fun finalizeSaveQuickBar(){
        val barToSave = currentQuickBar ?: return // Safety check
        val name = nameInput.text.toString().trim()

        // VALIDATION
        if (name.isEmpty()) {
            nameInputLayout.error = "Name cannot be empty"
            nameInput.requestFocus()
            return
        }

        if (name.length >= MAX_LEN_QUICKBAR_NAME) {
            nameInputLayout.error = "QuickBar name cannot exceed $MAX_LEN_QUICKBAR_NAME characters."
            nameInput.requestFocus()
            return
        }

        if (isNameAlreadyTaken(name)) {
            nameInputLayout.error = "QuickBar name is already taken. Please choose another one."
            nameInput.requestFocus()
            return
        }
        nameInputLayout.error = null // Clear previous errors.

        // UPDATE AND SAVE THE QUICKBAR OBJECT
        barToSave.name = name
        barToSave.isEnabled = enabledSwitch.isChecked
        barToSave.showNameInOverlay = showNameSwitch.isChecked
        barToSave.position = when (positionToggleGroup.checkedButtonId) {
            R.id.btn_position_left -> QuickBarPosition.LEFT
            R.id.btn_position_right -> QuickBarPosition.RIGHT
            R.id.btn_position_top -> QuickBarPosition.TOP
            R.id.btn_position_bottom -> QuickBarPosition.BOTTOM
            else -> QuickBarPosition.RIGHT
        }
        barToSave.useGridLayout = gridLayoutSwitch.isChecked
        barToSave.backgroundColor = currentQuickBar?.backgroundColor ?: "colorSurface"
        barToSave.backgroundOpacity = currentQuickBar?.backgroundOpacity ?: 90
        barToSave.onStateColor = currentQuickBar?.onStateColor ?: "colorPrimary"
        barToSave.haTriggerAlias = haTriggerAliasInput.text.toString().trim()
        barToSave.showTimeOnQuickBar = showTimeSwitch.isChecked
        barToSave.autoCloseQuickBarDomains = currentQuickBar?.autoCloseQuickBarDomains ?: mutableListOf()
        barToSave.autoCloseDelay = currentQuickBar?.autoCloseDelay ?: 0

        val allBars = quickBarManager.loadQuickBars().toMutableList()
        if (isNewBar) {
            allBars.add(barToSave)
        } else {
            val index = allBars.indexOfFirst { it.id == barToSave.id }
            if (index != -1) {
                allBars[index] = barToSave
            } else {
                allBars.add(barToSave) // Safety check
            }
        }
        quickBarManager.saveQuickBars(allBars)

        // If it's a new bar - we also need to save the new trigger key!
        if (isNewBar) {
            // --- 2. GATHER TRIGGER INFO FROM UI ---
            val newKeyCode = currentTriggerKey?.keyCode
            val newKeyName = currentTriggerKey?.keyName
            val newTriggerType = when (triggerTypeToggleGroup.checkedButtonId) {
                R.id.btn_single_press -> TriggerType.SINGLE_PRESS
                R.id.btn_double_press -> TriggerType.DOUBLE_PRESS
                R.id.btn_long_press -> TriggerType.LONG_PRESS
                else -> null // No trigger is selected
            }

            // --- 3. CONFLICT CHECK (Now using TriggerKeyManager) ---
            if (newKeyCode != null && newTriggerType != null) {
                val existingKey = triggerKeyManager.getTriggerKey(newKeyCode)
                if (existingKey != null) {
                    val conflictingActionId = when (newTriggerType) {
                        TriggerType.SINGLE_PRESS -> existingKey.singlePressAction
                        TriggerType.DOUBLE_PRESS -> existingKey.doublePressAction
                        TriggerType.LONG_PRESS -> existingKey.longPressAction
                    }

                    // Check if the conflicting action is for an entity or QuickBar
                    val actionType = when (newTriggerType) {
                        TriggerType.SINGLE_PRESS -> existingKey.singlePressActionType
                        TriggerType.DOUBLE_PRESS -> existingKey.doublePressActionType
                        TriggerType.LONG_PRESS -> existingKey.longPressActionType
                        else -> "none"
                    }

                    // A conflict exists if an action is already assigned AND it's not for the current QuickBar
                    if (conflictingActionId != null && conflictingActionId != barToSave.id) {
                        // Create appropriate message based on action type
                        val conflictTypeMessage: String
                        val conflictName: String

                        when (actionType) {
                            "entity" -> {
                                conflictTypeMessage = "an entity"
                                conflictName = conflictingActionId // Entity ID
                            }
                            "quickbar" -> {
                                conflictTypeMessage = "a QuickBar"
                                conflictName = quickBarManager.loadQuickBars()
                                    .find { it.id == conflictingActionId }?.name ?: "another QuickBar"
                            }
                            "app" -> {
                                conflictTypeMessage = "an app"
                                conflictName = "another app" // You can add logic to get app name later if needed
                            }
                            else -> {
                                conflictTypeMessage = "another item"
                                conflictName = ""
                            }
                        }

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Trigger Conflict")
                            .setMessage("This trigger combination is already used by $conflictTypeMessage  \"$conflictName\". Please choose a different trigger or trigger type.")
                            .setPositiveButton("OK", null)
                            .show()
                        return // Prevent saving
                    }
                }
            }

            // Remove any *old* trigger assignment for this QuickBar ID.
            // This is crucial to handle cases where the user changes the key or trigger type.
            triggerKeyManager.removeQuickBarReference(barToSave.id)

            // Now, if a new trigger was defined, create or update its TriggerKey
            if (newKeyCode != null && newKeyName != null && newTriggerType != null) {
                val keyToUpdate = triggerKeyManager.getTriggerKey(newKeyCode)
                    ?: TriggerKey(
                        keyCode = newKeyCode,
                        keyName = newKeyName
                    ) // Create if it doesn't exist

                val friendlyName = keyToUpdate.friendlyName ?: currentTriggerKey?.friendlyName

                // Create the final version of the key, assigning the new action
                val finalKey = keyToUpdate.copy(
                    friendlyName = friendlyName,
                    singlePressAction = if (newTriggerType == TriggerType.SINGLE_PRESS) barToSave.id else keyToUpdate.singlePressAction,
                    doublePressAction = if (newTriggerType == TriggerType.DOUBLE_PRESS) barToSave.id else keyToUpdate.doublePressAction,
                    longPressAction = if (newTriggerType == TriggerType.LONG_PRESS) barToSave.id else keyToUpdate.longPressAction,
                    // Ensure the 'isEntity' flag is false for the action we just set
                    singlePressActionType = if (newTriggerType == TriggerType.SINGLE_PRESS) "quickbar" else keyToUpdate.singlePressActionType,
                    doublePressActionType = if (newTriggerType == TriggerType.DOUBLE_PRESS) "quickbar" else keyToUpdate.doublePressActionType,
                    longPressActionType = if (newTriggerType == TriggerType.LONG_PRESS) "quickbar" else keyToUpdate.longPressActionType
                )

                triggerKeyManager.updateTriggerKey(finalKey)
            }
        }


        // FINALIZE
        Toast.makeText(this, "QuickBar Saved!", Toast.LENGTH_SHORT).show()

        val reloadIntent = Intent(this, QuickBarService::class.java).apply {
            action = "ACTION_RELOAD_TRIGGER_KEYS"
        }
        startService(reloadIntent)

        setResult(RESULT_OK)
        finish()
    }

    /**
     * Deletes the current QuickBar.
     */
    private fun deleteQuickBar() {
        val barToDelete = currentQuickBar ?: return

        // This function now correctly handles un-assigning the trigger
        triggerKeyManager.removeQuickBarReference(barToDelete.id)

        // The rest of the function remains the same
        val allBars = quickBarManager.loadQuickBars()
        allBars.removeAll { it.id == barToDelete.id }
        quickBarManager.saveQuickBars(allBars)

        Toast.makeText(this, "QuickBar Deleted", Toast.LENGTH_SHORT).show()

        // It's good practice to also reload triggers in the service after deletion
        val reloadIntent = Intent(this, QuickBarService::class.java).apply {
            action = "ACTION_RELOAD_TRIGGER_KEYS"
        }
        startService(reloadIntent)

        setResult(RESULT_OK)
        finish()
    }

    /**
     * Check if a name is already taken by another QuickBar.
     */
    private fun isNameAlreadyTaken(name: String): Boolean {
        val allBars = quickBarManager.loadQuickBars()
        return allBars.any {
            it.id != currentQuickBar?.id && // Skip the current bar being edited
                    it.name.equals(name, ignoreCase = true) // Case-insensitive comparison
        }
    }

    /**
     * Check if there are any conflicts with existing triggers (taken by other QuickBars/Entities).
     */
    private fun checkTriggerConflicts() {
        val barId = currentQuickBar?.id ?: return
        val keyCode = currentTriggerKey?.keyCode
        val triggerType = when (triggerTypeToggleGroup.checkedButtonId) {
            R.id.btn_single_press -> TriggerType.SINGLE_PRESS
            R.id.btn_double_press -> TriggerType.DOUBLE_PRESS
            R.id.btn_long_press -> TriggerType.LONG_PRESS
            else -> null
        }

        // Only check for conflicts if a key and type are actually selected
        if (keyCode == null || keyCode <= 0 || triggerType == null) {
            triggerErrorText.visibility = View.GONE
            hasConflictingTrigger = false
            return
        }

        val existingKey = triggerKeyManager.getTriggerKey(keyCode)
        var conflictFound = false
        if (existingKey != null) {
            val conflictingActionId = when (triggerType) {
                TriggerType.SINGLE_PRESS -> existingKey.singlePressAction
                TriggerType.DOUBLE_PRESS -> existingKey.doublePressAction
                TriggerType.LONG_PRESS -> existingKey.longPressAction
            }

            // Check if it's an entity conflict
            val actionType = when (triggerType) {
                TriggerType.SINGLE_PRESS -> existingKey.singlePressActionType
                TriggerType.DOUBLE_PRESS -> existingKey.doublePressActionType
                TriggerType.LONG_PRESS -> existingKey.longPressActionType
            }

            val isEntityAction = (actionType == "entity")

            // A conflict exists if an action is assigned AND it's not for the bar we are currently editing
            if (conflictingActionId != null && conflictingActionId != barId) {
                // Create appropriate message based on action type
                val conflictType = if (isEntityAction) "entity" else "QuickBar"
                val conflictName = if (isEntityAction) {
                    // For entity conflicts, show the entity ID
                    conflictingActionId
                } else {
                    // For QuickBar conflicts, show the QuickBar name
                    val allBars = quickBarManager.loadQuickBars()
                    allBars.find { it.id == conflictingActionId }?.name ?: "another item"
                }

                triggerErrorText.text = "This trigger is already used by $conflictType \"$conflictName\""
                triggerErrorText.visibility = View.VISIBLE
                hasConflictingTrigger = true
                conflictFound = true
            }
        }

        if (!conflictFound) {
            triggerErrorText.visibility = View.GONE
            hasConflictingTrigger = false
        }
    }

    private fun showAutoCloseDomainDialog() {
        // List of available domains for auto-close
        val availableDomains = listOf(
            "light", "switch", "button", "input_boolean",
            "input_button", "script", "scene", "camera", "automation"
        )

        // Create readable names for the domains
        val domainLabels = listOf(
            "Light", "Switch", "Button", "Input Boolean",
            "Input Button", "Script", "Scene", "Camera", "Automation"
        )

        // Create boolean array for the current selection state
        val selectedDomains = BooleanArray(availableDomains.size) { i ->
            currentQuickBar?.autoCloseQuickBarDomains?.contains(availableDomains[i]) ?: false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Auto-Close on These Domains")
            .setMultiChoiceItems(domainLabels.toTypedArray(), selectedDomains) { _, which, isChecked ->
                selectedDomains[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                // Clear and update the list
                currentQuickBar?.autoCloseQuickBarDomains?.clear()

                for (i in availableDomains.indices) {
                    if (selectedDomains[i]) {
                        currentQuickBar?.autoCloseQuickBarDomains?.add(availableDomains[i])
                    }
                }

                // Update the button text to show selected count
                updateAutoCloseButtonText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add this function to update the button text
    private fun updateAutoCloseButtonText() {
        val count = currentQuickBar?.autoCloseQuickBarDomains?.size ?: 0
        if (count > 0) {
            btnAutoCloseDomains.text = "Auto-Close: $count domain${if (count > 1) "s" else ""} selected"
        } else {
            btnAutoCloseDomains.text = "Select Domains for Auto-Close"
        }
    }

}
