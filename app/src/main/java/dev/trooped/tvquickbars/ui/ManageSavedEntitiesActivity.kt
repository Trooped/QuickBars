package dev.trooped.tvquickbars.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.trooped.tvquickbars.R
import com.google.android.material.appbar.MaterialToolbar
import dev.trooped.tvquickbars.data.EntityItem
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.ui.adapters.ManageSavedEntitiesAdapter

/**
 * ManageSavedEntitiesActivity
 * Activity for managing saved entities.
 * Allows users to edit saved entities' names (and in the future - icons).
 * @property recyclerView The RecyclerView for displaying saved entities.
 * @property emptyView The TextView indicating no entities are available.
 * @property savedEntitiesManager The manager for saving and loading entities.
 * @property adapter The adapter for displaying saved entities.
 * @property savedEntities The list of saved entities.
 */
class ManageSavedEntitiesActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var savedEntitiesManager: SavedEntitiesManager
    private lateinit var adapter: ManageSavedEntitiesAdapter
    private var savedEntities: MutableList<EntityItem> = mutableListOf()
    private val manageEntityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            // Handle entity updates
            val updated = data?.getBooleanExtra("updated", false) ?: false
            if (updated) {
                val position = data.getIntExtra("position", -1)
                val updatedEntityJson = data.getStringExtra("updatedEntity")

                if (position >= 0 && updatedEntityJson != null) {
                    try {
                        val updatedEntity = savedEntitiesManager.gson.fromJson(updatedEntityJson, EntityItem::class.java)
                        // Update the specific entity in the list
                        if (position < savedEntities.size) {
                            savedEntities[position] = updatedEntity
                            // Just update that one item
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this, "Entity updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("ManageSavedEntitiesActivity", "Invalid position: $position")
                            loadEntities()
                            setupRecyclerView()
                        }
                    } catch (e: Exception) {
                        Log.e("ManageSavedEntitiesActivity", "Error parsing updated entity: ${e.message}")
                        loadEntities()
                        setupRecyclerView()
                    }
                } else {
                    val updatedEntityId = data?.getStringExtra("updatedEntityId") ?: return@registerForActivityResult
                    loadEntities()
                    setupRecyclerView()
                    Toast.makeText(this, "Entity updated", Toast.LENGTH_SHORT).show()
                }
            }

            // Handle entity deletion
            val deleted = data?.getBooleanExtra("deleted", false) ?: false
            if (deleted) {
                loadEntities()
                setupRecyclerView()
                Toast.makeText(this, "Entity deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_saved_entities)

        // Set up toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        makeToolbarNavIconFocusable(toolbar)
        setToolbarNavigationIconColor(toolbar, R.color.md_theme_onSurface)

        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)

        savedEntitiesManager = SavedEntitiesManager(this)

        loadEntities()
        setupRecyclerView()
    }

    private fun loadEntities() {
        savedEntities = savedEntitiesManager.loadEntities()

        savedEntities.forEachIndexed { index, entity ->
            val isToggleable = EntityIconMapper.isEntityToggleable(entity.id)

            if (entity.customIconOnName == null) {
                savedEntities[index] = entity.copy(
                    isActionable = isToggleable,
                    customName = entity.customName, // Preserve the custom name
                    friendlyName = entity.friendlyName, // Preserve the friendly name too
                    customIconOnName = EntityIconMapper.getDefaultOnIconForEntityName(entity.id),
                    customIconOffName = if (isToggleable) EntityIconMapper.getDefaultOffIconForEntityName(entity.id) else null
                )
            }
        }
    }

    private fun setupRecyclerView() {
        if (savedEntities.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        savedEntities.sortWith(compareBy { it.isAvailable })

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ManageSavedEntitiesAdapter(savedEntities) { entity, position ->
            launchEntityManageActivity(entity, position)
        }
        recyclerView.adapter = adapter
    }

    /**
     * Launch ManageEntityActivity
     * @param entity The entity to edit.
     * @param position The position of the entity in the list.
     */
    private fun launchEntityManageActivity(entity: EntityItem, position: Int) {
        val intent = Intent(this, ManageEntityActivity::class.java).apply {
            putExtra("entityId", entity.id)
            putExtra("position", position)
        }
        manageEntityLauncher.launch(intent)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}