package dev.trooped.tvquickbars.persistence

import android.content.Context
import dev.trooped.tvquickbars.utils.SecureStore
import kotlinx.coroutines.runBlocking


/**
 * SecurePrefsManager
 *
 * Acts as a wrapper for the SecureStore file.
 */
object SecurePrefsManager {

    /* --------  synchronous façade used by the rest of the app -------- */

    fun saveHAUrl(ctx: Context, url: String) = runBlocking {
        SecureStore.setHAUrl(ctx, url)
    }

    fun getHAUrl(ctx: Context): String? = runBlocking {
        SecureStore.getHAUrl(ctx)
    }

    fun saveHAToken(ctx: Context, token: String) = runBlocking {
        SecureStore.setHAToken(ctx, token)
    }

    fun getHAToken(ctx: Context): String? = runBlocking {
        SecureStore.getHAToken(ctx)
    }

    fun clearCredentials(ctx: Context) = runBlocking {
        SecureStore.clear(ctx)
    }
}