/*
 * 白い熊 fork (skui): the gate of the sister-app 保存復元 automation contract
 * (SkStateExportReceiver) — a master switch plus a shared secret that every
 * automation broadcast must carry, the same model as the renrakusaki fork's
 * Config and 自由作業盤's AutomationAuth.
 *
 * Device-local by design: the prefs file below belongs to no export category,
 * so the token never travels in a backup ZIP and never leaves the phone.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.SharedPreferences
import me.zhanghai.android.files.app.application
import java.security.MessageDigest
import java.security.SecureRandom

object SkAutomation {
    // Deliberately its own prefs file, and deliberately not in SkEximport.Cat.
    private const val AUTOMATION_PREFS = "sk_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"

    private const val TOKEN_BYTES = 24

    private val automationPrefs: SharedPreferences by lazy {
        application.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE)
    }

    /** Nothing of the contract answers until this is on. */
    var isEnabled: Boolean
        get() = automationPrefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            automationPrefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** The shared secret, generated on first read so the settings row always shows a value. */
    val token: String
        get() =
            automationPrefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
                ?: regenerateToken()

    fun regenerateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        automationPrefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    /** Head and tail only — what the settings row shows. */
    fun abbreviatedToken(): String = token.let { "${it.take(8)}…${it.takeLast(8)}" }

    /**
     * Constant-time match against the stored secret. [isEnabled] is checked separately so a
     * caller can be told "automation disabled" and "bad token" apart — they debug differently.
     */
    fun isTokenValid(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) {
            return false
        }
        return MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())
    }
}
