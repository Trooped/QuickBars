package dev.trooped.tvquickbars.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecureStore Object
 * This object provides a secure way to store and retrieve sensitive data,
 * such as API tokens and URLs, using encryption and Android's Keystore system.
 */
object SecureStore {

    /* ---------- constants ---------- */

    private const val KEY_ALIAS = "HA_AES_256_GCM_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_MODE = "AES/GCM/NoPadding"

    /* DataStore file name */
    private const val DS_NAME = "ha_secure"

    /* preference keys */
    private val URL_KEY   = stringPreferencesKey("ha_url")
    private val TOKEN_KEY = stringPreferencesKey("ha_token")

    /* ---------- DataStore delegate ---------- */
    private val Context.secureDataStore by preferencesDataStore(name = DS_NAME)

    @Volatile private var cachedToken: String? = null

    /* ---------- public API ---------- */

    fun setHAUrl(ctx: Context, url: String) = runBlocking { setHAUrlAsync(ctx, url) }

    fun setHAToken(ctx: Context, token: String) = runBlocking { setHATokenAsync(ctx, token) }

    fun getHAUrl(ctx: Context): String? = runBlocking { getHAUrlAsync(ctx) }

    fun getHAToken(ctx: Context): String? = runBlocking { getHATokenAsync(ctx) }

    fun clear(ctx: Context) = runBlocking { clearAsync(ctx) }

    /* ---------- one‑time migration ---------- */

    /**
     * Call once early in Application.onCreate() to migrate the
     * legacy EncryptedSharedPreferences into the new store.
     */
    fun migrateFromOldPrefsIfNeeded(ctx: Context) = runBlocking {
        val legacy =
            ctx.getSharedPreferences("ha_secure_prefs", Context.MODE_PRIVATE)

        val oldToken = legacy.getString("ha_token", null) ?: return@runBlocking
        val oldUrl   = legacy.getString("ha_url",   null)

        Log.i("SecureStore", "Migrating credentials from legacy prefs")

        /* write to new store */
        ctx.secureDataStore.edit { prefs ->
            oldUrl?.let   { prefs[URL_KEY]   = it }
            prefs[TOKEN_KEY] = encrypt(oldToken, ctx).base64
        }

        legacy.edit().clear().apply()
    }

    /* =================================================================== */
    /* ===========                internals                      ========= */
    /* =================================================================== */

    /** Simple holder for (IV + cipher‑text) */
    private data class EncryptedBlob(val iv: ByteArray, val cipher: ByteArray) {
        val base64: String
            get() = Base64.encodeToString(
                ByteBuffer.allocate(4 + iv.size + cipher.size).apply {
                    putInt(iv.size)
                    put(iv)
                    put(cipher)
                }.array(), Base64.NO_WRAP)

        companion object {
            fun fromBase64(b64: String): EncryptedBlob {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val bb    = ByteBuffer.wrap(bytes)
                val ivLen = bb.int
                val iv    = ByteArray(ivLen).also { bb.get(it) }
                val ciph  = ByteArray(bb.remaining()).also { bb.get(it) }
                return EncryptedBlob(iv, ciph)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedBlob

            if (!iv.contentEquals(other.iv)) return false
            if (!cipher.contentEquals(other.cipher)) return false
            if (base64 != other.base64) return false

            return true
        }

        override fun hashCode(): Int {
            var result = iv.contentHashCode()
            result = 31 * result + cipher.contentHashCode()
            result = 31 * result + base64.hashCode()
            return result
        }
    }

    /* ---------- encryption helpers ---------- */

    private fun encrypt(plain: String, ctx: Context): EncryptedBlob {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(ctx))
        val iv     = cipher.iv
        val enc    = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return EncryptedBlob(iv, enc)
    }

    private fun decrypt(blob: EncryptedBlob, ctx: Context): String {
        val cipher = Cipher.getInstance(AES_MODE)
        val spec   = GCMParameterSpec(128, blob.iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(ctx), spec)
        val bytes  = cipher.doFinal(blob.cipher)
        return bytes.toString(Charsets.UTF_8)
    }

    /* ---------- key management ---------- */

    private fun getOrCreateKey(ctx: Context): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        ks.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        if (Build.VERSION.SDK_INT < 23)
            error("AES/GCM keystore keys require API 23+")

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)   // no biometrics needed
            .build()

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        ).apply { init(spec) }
            .generateKey()
    }


    /* ---------------- ASYNC Functions --------------------------*/

    suspend fun setHAUrlAsync(ctx: Context, url: String) {
        ctx.secureDataStore.edit { it[URL_KEY] = url }
    }

    suspend fun setHATokenAsync(ctx: Context, token: String) {
        // encrypt + write off main
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val enc = encrypt(token, ctx).base64
            ctx.secureDataStore.edit { prefs -> prefs[TOKEN_KEY] = enc }
        }
    }

    suspend fun getHAUrlAsync(ctx: Context): String? {
        return ctx.secureDataStore.data.first()[URL_KEY]
    }

    suspend fun getHATokenAsync(ctx: Context): String? {
        cachedToken?.let { return it }
        val enc = ctx.secureDataStore.data.first()[TOKEN_KEY] ?: return null
        val plain = withContext(kotlinx.coroutines.Dispatchers.IO) {
            decrypt(EncryptedBlob.fromBase64(enc), ctx)
        }
        cachedToken = plain
        return plain
    }

    suspend fun clearAsync(ctx: Context) {
        ctx.secureDataStore.edit { it.clear() }
        cachedToken = null
    }
}