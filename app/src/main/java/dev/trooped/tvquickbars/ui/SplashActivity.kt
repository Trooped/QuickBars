package dev.trooped.tvquickbars.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.persistence.AppPrefs
import dev.trooped.tvquickbars.persistence.SavedEntitiesManager
import dev.trooped.tvquickbars.persistence.SecurePrefsManager
import dev.trooped.tvquickbars.services.HAConnectionService
import dev.trooped.tvquickbars.utils.DemoModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV SplashActivity
 * Shows the windowBackground (from Theme.MyApp.TvSplash) immediately,
 * then routes to the right screen. No SplashScreen API (not supported on TV).
 */
class SplashActivity : ComponentActivity() {

    // Tune to the total visible animation time you want
    private val ANIM_TOTAL_MS = 2360L
    private val LINGER_MS     = 1500L

    private val FADE_OUT_MS   = 1200L

    @Volatile private var animDone = false
    @Volatile private var nextReady = false
    @Volatile private var nextActivity: Class<*>? = null
    @Volatile private var fadingOut = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content view immediately to show the layout
        setContentView(R.layout.activity_splash)
        val logo = findViewById<ImageView>(R.id.animated_logo)
        val title = findViewById<TextView>(R.id.app_name)

        val surfaceVariantColor = ContextCompat.getColor(this, R.color.md_theme_surfaceContainerHigh)
        val surfaceColor = ContextCompat.getColor(this, R.color.md_theme_surface)

        // Create gradient with alpha for first color
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    surfaceVariantColor,
                    240
                ), // 60% opacity (0.6 * 255 = 153)
                surfaceColor
            )
        )

        // Set gradient as background
        findViewById<View>(R.id.splash_root).background = gradient

        if (!AppPrefs.hasPersistentConnectionFlag(this)) {
            // default OFF
            AppPrefs.setPersistentConnectionEnabled(this, false)
        }

        else if (AppPrefs.isPersistentConnectionEnabled(this)) {
            try {
                Log.d("SplashActivity", "Starting background connection service from SplashActivity")
                val serviceIntent = Intent(applicationContext, HAConnectionService::class.java)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)
            } catch (e: Exception) {
                Log.e("SplashActivity", "Failed to start background service", e)
            }
        }

        DemoModeManager.checkAndEnableDemoMode(this)

        // 1) Start the animated vector drawable AFTER first frame
        logo.post {
            (logo.drawable as? Animatable)?.start()

            title.animate()
                .alpha(1f)
                .setStartDelay(1000)     // waits a bit
                .setDuration(900)
                .start()

            // Mark animation complete after movement+linger
            logo.postDelayed({
                animDone = true
                maybeGo()
            }, ANIM_TOTAL_MS + LINGER_MS) // ≈ 2.2s total on screen
        }

        // 2) Run your existing loading / routing on background
        lifecycleScope.launch {
            val next = withContext(Dispatchers.IO) { determineStartActivity() }
            nextActivity = next
            nextReady = true
            maybeGo()
        }
    }

    private fun maybeGo() {
        if (!animDone || !nextReady || nextActivity == null || fadingOut) return
        fadingOut = true

        val root  = findViewById<View>(R.id.splash_root)
        val scrim = findViewById<View>(R.id.fade_scrim)

        scrim.setBackgroundColor(ContextCompat.getColor(this, R.color.md_theme_onPrimaryContainer_highContrast))

        // slightly dim content while scrim comes in
        root.animate().alpha(0.75f).setDuration(FADE_OUT_MS).start()

        // Fade the scrim to 1.0, then navigate
        scrim.animate()
            .alpha(1f)
            .setDuration(FADE_OUT_MS)
            .withEndAction {
                startActivity(Intent(this, nextActivity).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                // No cross-activity animation (instant cut since we already faded)
                overridePendingTransition(0, 0)
                finish()
            }
            .start()
    }

    private suspend fun determineStartActivity(): Class<*> {
        val url = SecurePrefsManager.getHAUrl(this@SplashActivity)
        val token = SecurePrefsManager.getHAToken(this@SplashActivity)

        return if (url.isNullOrEmpty() || token.isNullOrEmpty()) {
            //SetupActivity::class.java
            OnboardingActivity::class.java
        } else {
            if (AppPrefs.isFirstTimeSetupInProgress(this@SplashActivity)) {
                EntityImporterActivity::class.java
            } else {
                val saved = SavedEntitiesManager(this@SplashActivity).loadEntities()
                if (saved.isEmpty()) {
                    // Start first-time setup and mark it as in progress
                    AppPrefs.setFirstTimeSetupInProgress(this@SplashActivity, true)
                    EntityImporterActivity::class.java
                } else {
                    ComposeMainActivity::class.java
                }
            }
        }
    }
}
