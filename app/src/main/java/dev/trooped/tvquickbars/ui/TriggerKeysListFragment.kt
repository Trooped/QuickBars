package dev.trooped.tvquickbars.ui

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dev.trooped.tvquickbars.data.TriggerKey
import dev.trooped.tvquickbars.persistence.TriggerKeyManager
import dev.trooped.tvquickbars.services.QuickBarService
import dev.trooped.tvquickbars.ui.adapters.TriggerKeyListAdapter
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.PlusStatusManager
import java.util.Locale


/**
 * TriggerKeysListFragment
 * Fragment for displaying a list of trigger keys, as well as creating new ones.
 * @property triggerKeyManager The TriggerKeyManager for managing trigger keys.
 * @property triggerKeysList The list of trigger keys.
 * @property recyclerView The RecyclerView for displaying the list.
 * @property adapter The adapter for the RecyclerView.
 * @property emptyView The TextView to display when the list is empty.
 * @property fabAddTriggerKey The ExtendedFloatingActionButton for adding new trigger keys.
 * @property captureDialog The Dialog for capturing a key.
 */
class TriggerKeysListFragment : Fragment() {

    private lateinit var triggerKeyManager: TriggerKeyManager
    private val triggerKeysList = mutableListOf<TriggerKey>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TriggerKeyListAdapter
    private lateinit var emptyView: TextView
    private lateinit var fabAddTriggerKey: ExtendedFloatingActionButton
    private var captureDialog: Dialog? = null

    private var permissionBanner: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_trigger_keys_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        triggerKeyManager = TriggerKeyManager(requireContext())

        // Find views
        recyclerView = view.findViewById(R.id.trigger_keys_recycler_view)
        emptyView = view.findViewById(R.id.empty_list_view)
        fabAddTriggerKey = view.findViewById(R.id.fab_add_trigger_key)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(keyCaptureReceiver, IntentFilter("ACTION_KEY_CAPTURED"))

        // Create and set the adapter
        adapter = TriggerKeyListAdapter(triggerKeysList) { triggerKey ->
            openTriggerKeyEditor(triggerKey.keyCode)
        }
        recyclerView.adapter = adapter

        val bannerContainer = view.findViewById<ViewGroup>(R.id.permission_banner_container)
        updatePermissionBanner(bannerContainer)

        // Set up FAB for adding new trigger key
        fabAddTriggerKey.setOnClickListener {
            listenForTriggerKey()
        }

        // Load initial data
        refreshList()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister the broadcast receiver
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(keyCaptureReceiver)
        } catch (e: Exception) {
            Log.e("TriggerKeysListFragment", "Error unregistering receiver", e)
        }

        // Dismiss dialog if it's showing
        captureDialog?.dismiss()
        captureDialog = null
    }

    private fun updatePermissionBanner(container: ViewGroup) {
        // Remove existing banner if any
        if (permissionBanner != null) {
            container.removeView(permissionBanner)
            permissionBanner = null
        }

        // Only show banner if permission is missing and we have trigger keys
        if (!PermissionUtils.isAccessibilityServiceEnabled(requireContext()) && triggerKeysList.isNotEmpty()) {
            permissionBanner = PermissionUtils.createPermissionBanner(
                requireContext(),
                requireView(),
                false, // false for accessibility (not overlay)
                {
                    // Use the proper dialog flow function instead of creating a new dialog directly
                    // This will ensure the "More Info" dialog returns to the main dialog
                    activity?.let { activity ->
                        PermissionUtils.showAccessibilityPermissionDialog(activity, {})
                    }
                }
            )
            container.addView(permissionBanner)
        }
    }

    override fun onResume() {
        super.onResume()
        triggerKeyManager = TriggerKeyManager(requireContext())
        refreshList()
        view?.findViewById<ViewGroup>(R.id.permission_banner_container)?.let {
            updatePermissionBanner(it)
        }
    }

    private fun refreshList() {
        val loadedKeys = triggerKeyManager.loadTriggerKeys()
        triggerKeysList.clear()
        triggerKeysList.addAll(loadedKeys)
        adapter.notifyDataSetChanged()
        updateEmptyViewVisibility()
    }

    private fun updateEmptyViewVisibility() {
        if (triggerKeysList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    /**
     * Listen for a trigger key to be pressed.
     */
    private fun listenForTriggerKey() {
        // Check if user already has 1 trigger key and is not Plus
        if (triggerKeysList.size >= 1 && !PlusStatusManager.isPlus.value && !DemoModeManager.isInDemoMode) {
            // User has reached the free limit, show upgrade screen
            Intent(requireContext(), UpgradeActivity::class.java).also {
                startActivity(it)
            }
            return
        }

        // Check accessibility permission before attempting to capture keys
        if (!PermissionUtils.isAccessibilityServiceEnabled(requireContext())) {
            PermissionUtils.showAccessibilityPermissionExplanation(
                this,
                onCancel = {
                    // Permission denied - can't proceed with key capture
                }
            )
            return
        }

        // If we already have permission, proceed directly
        showKeyCaptureDialog()

        val intent = Intent(requireContext(), QuickBarService::class.java).apply {
            action = "ACTION_CAPTURE_KEY"
        }
        requireContext().startService(intent)
    }

    /**
     * Show the dialog for capturing a key.
     */
    private fun showKeyCaptureDialog() {
        captureDialog = MaterialAlertDialogBuilder(requireContext())
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
                val intent = Intent(requireContext(), QuickBarService::class.java).apply {
                    action = "ACTION_CANCEL_CAPTURE"
                }
                requireContext().startService(intent)
            }
            .create()

        captureDialog?.show()
    }

    /**
     * Open the editor for a trigger key.
     */
    private fun openTriggerKeyEditor(keyCode: Int) {
        val intent = Intent(requireActivity(), TriggerKeyEditorActivity::class.java).apply {
            putExtra("KEY_CODE", keyCode)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        captureDialog?.dismiss()
    }

    /**
     * Broadcast receiver for when a key is captured.
     */
    private val keyCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_KEY_CAPTURED") {
                val keyCode = intent.getIntExtra("keyCode", -1)
                val keyName = intent.getStringExtra("keyName")

                if (keyCode != -1 && keyName != null) {
                    // Dismiss the dialog first
                    captureDialog?.dismiss()
                    captureDialog = null

                    // Force reload the TriggerKeyManager to get fresh data
                    triggerKeyManager = TriggerKeyManager(requireContext())

                    // Now check if this key exists after the fresh reload
                    val existingKey = triggerKeyManager.getTriggerKey(keyCode)
                    if (existingKey != null) {
                        // Key still exists after a fresh reload
                        Toast.makeText(requireContext(),
                            "This trigger key is already saved",
                            Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Save initial key
                    saveInitialKeyAndLaunchEditor(keyCode, keyName)
                }
            }
        }
    }

    /**
     * Save the initial trigger key and launch the editor.
     * @param keyCode The key code of the trigger key.
     * @param keyName The name of the trigger key.
     */
    private fun saveInitialKeyAndLaunchEditor(keyCode: Int, keyName: String) {
        val formattedName = formatKeyName(keyName)
        val newTriggerKey = TriggerKey(
            keyCode = keyCode,
            keyName = keyName,
            friendlyName = formattedName // Use formatted name as initial friendly name
        )

        // Save the basic key
        triggerKeyManager.updateTriggerKey(newTriggerKey)

        // Now open the editor for the newly created key
        openTriggerKeyEditor(keyCode)
    }

    /**
     * Format a key name for display (happens automatically for the given Android name for the key).
     */
    private fun formatKeyName(keyName: String): String {
        return keyName.replace("KEYCODE_", "")
            .split("_")
            .joinToString(" ") {
                it.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }
}