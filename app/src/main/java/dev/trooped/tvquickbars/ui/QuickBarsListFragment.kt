package dev.trooped.tvquickbars.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.trooped.tvquickbars.data.QuickBar
import dev.trooped.tvquickbars.persistence.QuickBarManager
import dev.trooped.tvquickbars.ui.adapters.QuickBarListAdapter
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PermissionUtils
import dev.trooped.tvquickbars.utils.PlusStatusManager

/**
 * QuickBarsListFragment
 * Contains the list of QuickBars and the button to create a new one.
 * @property quickBarManager The QuickBarManager used to load and save QuickBars.
 * @property quickBarsList A list of QuickBars.
 * @property recyclerView The RecyclerView that displays the QuickBars.
 * @property adapter The QuickBarListAdapter used to populate the RecyclerView.
 * @property emptyView The TextView that is displayed when the list is empty.
 * @property pendingQuickBarId The ID of the QuickBar to edit after returning from settings.
 * @property permissionBanner The banner that prompts the user to grant overlay permission.
 */
class QuickBarsListFragment : Fragment() {
    private lateinit var quickBarManager: QuickBarManager
    private val quickBarsList: MutableList<QuickBar> = mutableListOf()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: QuickBarListAdapter // We will create this adapter next
    private lateinit var emptyView: TextView
    private var pendingQuickBarId: String? = null
    private var permissionBanner: View? = null

    // The onCreateView method is where the fragment's UI is created.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_quick_bars_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        quickBarManager = QuickBarManager(requireContext())

        // Set up toolbar
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)

        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)


        // Find views
        recyclerView = view.findViewById(R.id.quickbars_recycler_view)
        emptyView = view.findViewById(R.id.empty_list_view)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Add permission banner container (make sure this view exists in your layout)
        val bannerContainer = view.findViewById<ViewGroup>(R.id.permission_banner_container)
            ?: throw IllegalStateException("Permission banner container not found. Add it to your layout.")
        updatePermissionBanner(bannerContainer)

        // Create and set the adapter with improved click handling
        adapter = QuickBarListAdapter(quickBarsList) { quickBar ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !PermissionUtils.canDrawOverlays(requireContext()) &&
                !PermissionUtils.hasExplainedOverlayPermission(requireContext())) {

                // First time: show dialog before launching editor
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.overlay_permission_title)
                    .setMessage(R.string.overlay_permission_explanation)
                    .setPositiveButton(R.string.go_to_settings) { _, _ ->
                        PermissionUtils.markOverlayPermissionExplained(requireContext())
                        PermissionUtils.navigateToOverlaySettings(requireActivity())
                        pendingQuickBarId = quickBar.id
                    }
                    .setNegativeButton(R.string.not_now) { _, _ ->
                        PermissionUtils.markOverlayPermissionExplained(requireContext())
                        launchEditorFor(quickBar.id) // Launch editor anyway if they decline
                    }
                    .setCancelable(true)
                    .setOnCancelListener {
                        // Don't launch anything if they cancel
                    }
                    .show()
            } else {
                // Already asked before: just launch editor without snackbar
                launchEditorFor(quickBar.id)
            }
        }
        recyclerView.adapter = adapter

        // FAB click listener - similar logic to adapter click handler
        val fab = view.findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fab_add_quickbar)
        fab.setOnClickListener {
            refreshList()
            // Check if user already has 1 QuickBar and is not Plus
            if (quickBarsList.size >= 1 && !PlusStatusManager.isPlus.value && !DemoModeManager.isInDemoMode) {
                // User has reached the free limit, show upgrade screen
                Intent(requireContext(), UpgradeActivity::class.java).also {
                    startActivity(it)
                }
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !PermissionUtils.canDrawOverlays(requireContext()) &&
                !PermissionUtils.hasExplainedOverlayPermission(requireContext())) {

                // First time: show dialog before launching editor
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.overlay_permission_title)
                    .setMessage(R.string.overlay_permission_explanation)
                    .setPositiveButton(R.string.go_to_settings) { _, _ ->
                        PermissionUtils.markOverlayPermissionExplained(requireContext())
                        PermissionUtils.navigateToOverlaySettings(requireActivity())
                        pendingQuickBarId = null // null means new QuickBar
                    }
                    .setNegativeButton(R.string.not_now) { _, _ ->
                        PermissionUtils.markOverlayPermissionExplained(requireContext())
                        launchEditorFor(null) // Launch editor anyway if they decline
                    }
                    .setCancelable(true)
                    .setOnCancelListener {
                        // Don't launch anything if they cancel
                    }
                    .show()
            } else {
                // Already asked before: just launch editor without snackbar
                launchEditorFor(null)
            }
        }
    }

    /**
     * Updates the permission banner visibility.
     * @param container The container to add the banner to.
     */
    private fun updatePermissionBanner(container: ViewGroup) {
        // Remove existing banner if any
        if (permissionBanner != null) {
            container.removeView(permissionBanner)
            permissionBanner = null
        }

        // Only show banner if permission is missing and we have QuickBars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !PermissionUtils.canDrawOverlays(requireContext()) &&
            quickBarsList.isNotEmpty()) {

            permissionBanner = PermissionUtils.createPermissionBanner(
                requireContext(),
                requireView(),
                true, // true for overlay (not accessibility)
                {
                    // Use the new consistent dialog method
                    PermissionUtils.showOverlayPermissionDialog(this)
                }
            )
            container.addView(permissionBanner)
        }
    }

    // onResume and the other functions work the same way,
    // but we use requireActivity() to launch a new activity.
    override fun onResume() {
        super.onResume()
        refreshList()
        // Update permission banner whenever we return to this fragment
        view?.findViewById<ViewGroup>(R.id.permission_banner_container)?.let {
            updatePermissionBanner(it)
        }

        // Fix the infinite loop condition - only launch if pendingQuickBarId is not null
        if (pendingQuickBarId != null) {
            // Store the ID before clearing it to prevent recursion
            val id = pendingQuickBarId
            pendingQuickBarId = null // Clear the pending ID

            // Launch the editor only if we have a valid ID
            launchEditorFor(id)
        }
    }

    /**
     * Launches the QuickBar editor activity for the given QuickBar ID.
     * @param quickBarId The ID of the QuickBar to edit, or null to create a new one.
     */
    private fun launchEditorFor(quickBarId: String?) {
        val intent = Intent(requireActivity(), QuickBarEditorActivity::class.java).apply {
            putExtra("QUICKBAR_ID", quickBarId)
        }
        startActivity(intent)
    }

    /**
     * Refreshes the QuickBars list from the QuickBarManager and updates the UI.
     * This method is called when the fragment is resumed or when QuickBars are modified.
     */
    private fun refreshList() {
        val loadedBars = quickBarManager.loadQuickBars()
        quickBarsList.clear()
        quickBarsList.addAll(loadedBars)
        adapter.notifyDataSetChanged()
        updateEmptyViewVisibility()
    }

    /**
     * Updates the visibility of the empty view based on the QuickBars list.
     * If the list is empty, the empty view is shown; otherwise, the RecyclerView is displayed.
     */
    private fun updateEmptyViewVisibility() {
        if (quickBarsList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.isFocusable = true
            emptyView.requestFocus()
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            emptyView.isFocusable = false
        }
    }
}