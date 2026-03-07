package dev.trooped.tvquickbars.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.adapters.TVFriendlyReorderAdapter

/**
 * ReorderEntitiesActivity
 * An activity that allows the user to reorder the entities on his QuickBar (launched from the QuickBar editor).
 * @property recyclerView The RecyclerView to display the entities.
 * @property emptyView A TextView to display when there are no entities.
 * @property instructionsView A TextView to display instructions.
 * @property saveButton A Button to save the order of the entities.
 * @property entityIds A mutable list of entity IDs.
 * @property adapter The adapter for the RecyclerView.
 * @property moveMode A flag indicating whether move mode is active.
 * @property selectedPosition The position of the currently selected item.
 */
class ReorderEntitiesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var instructionsView: TextView
    private lateinit var saveButton: Button
    private var entityIds = mutableListOf<String>()
    private lateinit var adapter: TVFriendlyReorderAdapter

    // Track if we're in move mode
    private var moveMode = false
    private var selectedPosition = -1

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reorder_entities)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            showDiscardChangesDialog()
        }

        makeToolbarNavIconFocusable(toolbar)
        setToolbarNavigationIconColor(toolbar, R.color.md_theme_onSurface)


        recyclerView = findViewById(R.id.reorder_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        instructionsView = findViewById(R.id.instructions_view)
        saveButton = findViewById(R.id.btn_save_order)

        // Get entity IDs in their current order
        val passedEntityIds = intent.getStringArrayListExtra("ENTITY_IDS") ?: arrayListOf()
        entityIds.addAll(passedEntityIds)

        setupRecyclerView()

        saveButton.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && moveMode) {
                // Automatically exit move mode when Save button gets focus
                moveMode = false
                updateInstructions(false)
                adapter.setMoveMode(selectedPosition, false)
            }
        }

        saveButton.setOnClickListener {
            // Exit move mode if active
            if (moveMode) {
                moveMode = false
                adapter.setMoveMode(selectedPosition, false)
                updateInstructions(false)
            }

            val resultIntent = Intent().apply {
                putStringArrayListExtra("ENTITY_IDS", ArrayList(entityIds))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes")
            .setMessage("Are you sure you want to leave this screen? Changes will not be saved.")
            .setPositiveButton("Leave") { _, _ ->
                // User confirmed - leave without saving
                super.onBackPressed()
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
        // Show dialog first, super.onBackPressed() will be called if user confirms
        showDiscardChangesDialog()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setupRecyclerView() {
        // Get entity display names
        val savedEntitiesManager = SavedEntitiesManager(this)
        val savedEntities = savedEntitiesManager.loadEntities()

        val displayNames = savedEntities.associateBy({ it.id }, {
            if (it.customName.isNotEmpty()) it.customName else it.friendlyName
        })

        val entityIcons = savedEntities.associateBy(
            { it.id },
            {
                if (it.isActionable) {
                    EntityIconMapper.getDisplayIconForEntity(it)
                } else {
                    EntityIconMapper.getFinalIconForEntity(it)
                }
            }
        )

        if (entityIds.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            instructionsView.visibility = View.GONE
            emptyView.text = "No entities selected for this QuickBar"
            return
        }

        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        instructionsView.visibility = View.VISIBLE

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = TVFriendlyReorderAdapter(
            entityIds,
            displayNames,
            entityIcons,
            moveMode = false,
            onItemSelected = { position ->
                selectedPosition = position
            }
        )

        recyclerView.adapter = adapter

        // Initial instruction
        updateInstructions(false)
    }

    /**
     * Updates the instructions view based on whether we are in move mode or not.
     * @param inMoveMode Whether we are currently in move mode.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateInstructions(inMoveMode: Boolean) {
        if (inMoveMode) {
            instructionsView.text = "MOVE MODE: Press UP/DOWN to move item, CENTER to exit"
            instructionsView.setBackgroundColor(getColor(R.color.md_theme_primaryContainer))
            instructionsView.setTextColor(getColor(R.color.md_theme_onPrimaryContainer))
        } else {
            instructionsView.text = "Select an item and press CENTER to toggle move mode"
            instructionsView.setBackgroundColor(getColor(R.color.md_theme_secondaryContainer))
            instructionsView.setTextColor(getColor(R.color.md_theme_onSecondaryContainer))
        }
    }

    /**
     * Handles key events for moving items in the RecyclerView.
     * Allows moving items up/down in the list when in move mode.
     * @param event The KeyEvent to handle.
     * @return True if the event was handled, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Special case for save button - make sure we can click it
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {

            if (currentFocus == saveButton) {
                // Let the button handle the click
                return super.dispatchKeyEvent(event)
            }
        }

        val isRecyclerViewFocused = recyclerView.findFocus() != null

        // Handle all other cases as before
        if (isRecyclerViewFocused && event.action == KeyEvent.ACTION_DOWN && selectedPosition >= 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // Toggle move mode
                    moveMode = !moveMode
                    adapter.setMoveMode(selectedPosition, moveMode)
                    updateInstructions(moveMode)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (moveMode && selectedPosition > 0) {
                        // Move item up
                        val item = entityIds.removeAt(selectedPosition)
                        entityIds.add(selectedPosition - 1, item)
                        adapter.notifyItemMoved(selectedPosition, selectedPosition - 1)
                        selectedPosition--
                        adapter.setMoveMode(selectedPosition, true)
                        recyclerView.scrollToPosition(selectedPosition)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (moveMode && selectedPosition < entityIds.size - 1) {
                        // Move item down
                        val item = entityIds.removeAt(selectedPosition)
                        entityIds.add(selectedPosition + 1, item)
                        adapter.notifyItemMoved(selectedPosition, selectedPosition + 1)
                        selectedPosition++
                        adapter.setMoveMode(selectedPosition, true)
                        recyclerView.scrollToPosition(selectedPosition)
                        return true
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }
}