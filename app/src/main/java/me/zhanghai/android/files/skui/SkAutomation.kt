/*
 * 白い熊 fork (skui): the gate of the sister-app 保存復元 automation contract
 * (SkStateExportReceiver and SkAutomationProvider) — a master switch, plus a
 * shared secret that is only asked for when 白い熊 says so.
 *
 * Contract v2 (2026-09-04) turned the gate inside out. v1 shipped every app
 * closed: the switch defaulted off and a caller also had to present a
 * 48-character secret pasted from this app's settings into the caller's. That
 * is the wrong shape for where the family is going — a pasted secret cannot
 * survive a wipe, and the case this whole contract now exists to serve is
 * 応用管理 restoring apps *and their data* onto a clean phone, where nothing
 * has been configured and nobody has pasted anything. So:
 *
 *   automation_enabled        default false → **true**
 *   automation_require_token  new, default **false**
 *   automation_token          unchanged
 *
 * What replaces the token as an identity check is not "nothing": everything
 * that moves data through a caller-supplied descriptor lives behind
 * SkAutomationProvider, which checks the caller's package name, uid and pinned
 * signing certificate (SkAutomationCallers). The token is now an extra 白い熊
 * may switch on, not the door.
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
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"

    private const val TOKEN_BYTES = 24

    private val automationPrefs: SharedPreferences by lazy {
        application.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Nothing of the contract answers until this is on — and in v2 it ships on.
     *
     * It stays a switch rather than being removed because it is the only way to close this app
     * off, and a feature that can be turned on but never off is one 白い熊 cannot retreat from.
     * An install that already wrote `false` here keeps it: only the default moved.
     */
    var isEnabled: Boolean
        get() = automationPrefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            automationPrefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /**
     * Whether a caller must also present [token]. Off by default — see the file header.
     *
     * Off does not mean unguarded: the data door checks the caller's identity and signature
     * either way, and the broadcast half of the contract only ever writes where it was told to.
     */
    var isTokenRequired: Boolean
        get() = automationPrefs.getBoolean(KEY_REQUIRE_TOKEN, false)
        set(value) {
            automationPrefs.edit().putBoolean(KEY_REQUIRE_TOKEN, value).apply()
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
     * The whole gate, in one place.
     *
     * Returns null to proceed, otherwise the exact `ERROR:` string to answer with. Both checks
     * live here on purpose: two of them written out at each entry point is how "disabled" and
     * "bad token" drift apart across forty-two apps, and they must stay distinct because they
     * debug differently.
     *
     * **A token sent to an app that does not require one is ignored, never refused.** Tokens live
     * in task arguments and workspace variables that outlive the setting they were pasted for, and
     * a caller still sending one — because it was configured last year, or because another app on
     * the batch does want one — must be served. Refusing it would turn "白い熊 turned a switch
     * off" into "half the batch mysteriously fails", which is the friction the switch exists to
     * remove.
     */
    fun refuse(candidate: String?): String? =
        when {
            !isEnabled -> "ERROR:automation disabled"
            isTokenRequired && !isTokenValid(candidate) -> "ERROR:bad token"
            else -> null
        }

    /**
     * Constant-time match against the stored secret, for the case where the token *is* required.
     */
    fun isTokenValid(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) {
            return false
        }
        return MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())
    }
}
