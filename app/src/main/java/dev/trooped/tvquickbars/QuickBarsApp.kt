package dev.trooped.tvquickbars

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.data.AppIdProvider
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.utils.DemoModeManager
import dev.trooped.tvquickbars.utils.PlusStatusManager
import dev.trooped.tvquickbars.utils.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class that provides global context access.
 * Does not change app navigation flow - SetupActivity will still be the first screen shown.
 */
class QuickBarsApp : Application(), Application.ActivityLifecycleCallbacks {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentActivity: Activity? = null

    /**
     * Called when the application is starting, before any activity, service, or receiver objects
     * (excluding content providers) have been created.
     *
     * This implementation initializes the application singleton, sets up default preferences,
     * warms up secure storage, and checks for demo mode activation. It also configures
     * RevenueCat for subscription management and ensures a unique application ID is generated.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        AppPrefs.ensureShowToastOnEntityTriggerDefault(this)

        appScope.launch {
            // warm up keystore + cache token once
            SecureStore.getHAToken(this@QuickBarsApp)

            // Run demo mode check in background
            DemoModeManager.checkAndEnableDemoMode(this@QuickBarsApp)
        }
        registerActivityLifecycleCallbacks(this)

        // TODO uncomment when debugging connection
        //clearHaCredentials()
        //IntegrationPrefs.clearPairing(ctx = this)

        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )

        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info: CustomerInfo ->
                PlusStatusManager.update(info)
            }

        AppIdProvider.ensure(applicationContext) // Create a QuickBars ID instance
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) {
    }

    override fun onTerminate() {
        Purchases.sharedInstance.updatedCustomerInfoListener = null
        super.onTerminate()
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    companion object {
        private lateinit var instance: QuickBarsApp

        var showToastOnEntityTrigger: Boolean
            get() = AppPrefs.isShowToastOnEntityTriggerEnabled(instance.applicationContext)
            set(value) = AppPrefs.setShowToastOnEntityTriggerEnabled(instance.applicationContext, value)

        fun getAppContext(): Context {
            return instance.applicationContext
        }
    }

    /**
     * Utility function that helps debug the connection to Home Assistant
     */
    private fun clearHaCredentials() {
        SecurePrefsManager.clearCredentials(this)

        //urlInput.setText("")
        //tokenInput.setText("")
        Toast.makeText(this, "Home Assistant credentials cleared", Toast.LENGTH_SHORT).show()
    }
}