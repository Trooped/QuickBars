package dev.trooped.tvquickbars.data

import android.content.Context
import java.security.SecureRandom
import android.content.SharedPreferences

/**
 * Stable, human-typeable app ID for local targeting.
 * - 8 chars, Crockford Base32 (no I/L/O/U), ~40 bits entropy.
 * - Created once on first access, persisted in SharedPreferences.
 * - Cached in memory for fast subsequent calls.
 */
object AppIdProvider {
    private const val PREFS_NAME = "qb.appid.prefs"
    private const val KEY_APP_ID  = "app_id"

    @Volatile private var cached: String? = null
    private val rng = SecureRandom()
    private val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val VALID_8_CHARS = Regex("^[0-9A-HJKMNP-TV-Z]{8}$") // Crockford, no I/L/O/U

    /** Ensure an ID exists (call from Application.onCreate). */
    fun ensure(ctx: Context) { get(ctx) }

    /** Get the stable app id; create & persist it if missing. */
    fun get(ctx: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            readFromPrefs(ctx)?.let { existing ->
                cached = existing
                return existing
            }
            val fresh = generateId8()
            writeToPrefs(ctx, fresh)
            cached = fresh
            return fresh
        }
    }

    /**
     * Set a specific app id (e.g., from user input or migration).
     * Accepts hyphens/underscores/spaces; validates 8 Crockford chars after normalization.
     */
    fun set(ctx: Context, input: String) {
        val norm = normalizeUserId(input)
        require(VALID_8_CHARS.matches(norm)) {
            "App ID must be 8 Crockford Base32 chars (0-9 A-Z, no I/L/O/U)."
        }
        synchronized(this) {
            writeToPrefs(ctx, norm)
            cached = norm
        }
    }

    /** Clear the stored id (debug/tools). Next get() will generate a new one. */
    fun reset(ctx: Context) {
        synchronized(this) {
            prefs(ctx).edit().remove(KEY_APP_ID).apply()
            cached = null
        }
    }

    /** Normalize user-typed input to strict Crockford Base32: uppercase, strip -, _, spaces. */
    fun normalizeUserId(input: String): String =
        input.uppercase()
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

    // ---- Internal helpers ----

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readFromPrefs(ctx: Context): String? =
        prefs(ctx).getString(KEY_APP_ID, null)

    private fun writeToPrefs(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_APP_ID, id).apply()
    }

    private fun generateId8(): String {
        // 40 random bits -> 8 Base32 symbols (5 bits each)
        val bytes = ByteArray(5)
        rng.nextBytes(bytes)
        var v = 0L
        for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)

        val out = CharArray(8)
        for (i in 7 downTo 0) {
            out[i] = CROCKFORD_ALPHABET[(v and 31L).toInt()]
            v = v ushr 5
        }
        return String(out)
    }
}
