package dev.trooped.tvquickbars.services

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A custom lifecycle owner for hosting Jetpack Compose UI in a WindowManager view.
 * This class provides the necessary LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
 * that Compose needs to function outside of a standard Activity or Fragment.
 * @property lifecycleRegistry A LifecycleRegistry that handles the lifecycle events.
 * @property viewModelStoreStore A ViewModelStore that stores ViewModels.
 * @property savedStateRegistryController A SavedStateRegistryController that handles saving and restoring state.
 */

class ComposeViewLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // --- Member variables to hold the state ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // --- Interface Implementations ---

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // --- Lifecycle Management Methods ---

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun pause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreStore.clear()
    }

    /**
     * Attaches this LifecycleOwner to the root of a view hierarchy using
     * the correct Kotlin extension functions.
     */
    fun attachToView(rootView: View) {
        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeViewModelStoreOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)
    }
}
