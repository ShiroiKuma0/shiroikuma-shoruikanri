/*
 * 白い熊 fork (skui): granular UI theming for 白い熊 書類管理, modeled on the
 * sister repos (shiroikuma-denwa, shiroikuma-messeji): per-element color slots
 * with two-tier inheritance from a small foundation, plus per-text-element
 * fonts (family / weight / size) with user-importable font files.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.SharedPreferences
import me.zhanghai.android.files.app.application

object SkUi {
    const val PALETTE_BLACK = 0xFF000000.toInt()
    const val PALETTE_YELLOW = 0xFFFFFF00.toInt()

    // A slot with this stored value follows its inherited default.
    const val UNSET = Int.MIN_VALUE

    // RGB of the material yellow (0xFFEB3B) that PALETTE_YELLOW used to be.
    private const val LEGACY_YELLOW_RGB = 0xFFEB3B

    private const val KEY_SK_THEME_ENABLED = "sk_theme_enabled"
    private const val KEY_PURE_YELLOW_MIGRATED = "sk_pure_yellow_migrated"
    private const val KEY_FILE_ICON_SIZE = "sk_file_icon_size"
    private const val KEY_FILE_PADDING = "sk_file_padding"
    private const val FONT_FAMILY_PREFIX = "font_family_"
    private const val FONT_WEIGHT_PREFIX = "font_weight_"
    private const val FONT_SIZE_PREFIX = "font_size_"

    // The stock list look: 24dp mime icons, no extra space between files.
    const val DEFAULT_FILE_ICON_SIZE_DP = 24
    const val DEFAULT_FILE_PADDING_DP = 0

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_ui", Context.MODE_PRIVATE)
    }

    // Bumped on every change so screens can cheaply re-apply styling in onResume/onStart.
    @Volatile
    var generation: Long = 0
        private set

    private fun touch() {
        ++generation
    }

    // The 白い熊 black/yellow theme overlay (black background, yellow
    // text/icons/borders); applied to every activity when enabled.
    var isSkThemeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SK_THEME_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SK_THEME_ENABLED, value).apply()
            touch()
        }

    // One-time migration: PALETTE_YELLOW changed from the material yellow to pure
    // yellow, so persisted overrides still carrying the old RGB are rewritten to
    // the new one (alpha preserved). Runs at app start, before slot colors are read.
    fun migrateToPureYellow() {
        if (prefs.getBoolean(KEY_PURE_YELLOW_MIGRATED, false)) {
            return
        }
        val editor = prefs.edit()
        for (slot in SkThemeSlot.entries) {
            val color = prefs.getInt(slot.key, UNSET)
            if (color != UNSET && color and 0xFFFFFF == LEGACY_YELLOW_RGB) {
                editor.putInt(slot.key, (color and 0xFF000000.toInt()) or (PALETTE_YELLOW and 0xFFFFFF))
            }
        }
        editor.putBoolean(KEY_PURE_YELLOW_MIGRATED, true).apply()
    }

    // File list appearance: mime icon size and vertical padding around each file row
    // (0 = rows touch each other).
    var fileIconSizeDp: Int
        get() = prefs.getInt(KEY_FILE_ICON_SIZE, DEFAULT_FILE_ICON_SIZE_DP)
        set(value) {
            prefs.edit().putInt(KEY_FILE_ICON_SIZE, value).apply()
            touch()
        }

    var filePaddingDp: Int
        get() = prefs.getInt(KEY_FILE_PADDING, DEFAULT_FILE_PADDING_DP)
        set(value) {
            prefs.edit().putInt(KEY_FILE_PADDING, value).apply()
            touch()
        }

    fun getColorOverride(key: String): Int = prefs.getInt(key, UNSET)

    fun setColorOverride(key: String, color: Int) {
        prefs.edit().putInt(key, color).apply()
        touch()
    }

    fun clearColorOverride(key: String) {
        prefs.edit().remove(key).apply()
        touch()
    }

    // Per-element fonts: family (filename, "" = default), weight (0 = default),
    // size (sp, 0 = default).
    fun getFontFamily(slotKey: String): String =
        prefs.getString(FONT_FAMILY_PREFIX + slotKey, "")!!

    fun setFontFamily(slotKey: String, value: String) {
        prefs.edit().putString(FONT_FAMILY_PREFIX + slotKey, value).apply()
        touch()
    }

    fun getFontWeight(slotKey: String): Int = prefs.getInt(FONT_WEIGHT_PREFIX + slotKey, 0)

    fun setFontWeight(slotKey: String, value: Int) {
        prefs.edit().putInt(FONT_WEIGHT_PREFIX + slotKey, value).apply()
        touch()
    }

    fun getFontSize(slotKey: String): Int = prefs.getInt(FONT_SIZE_PREFIX + slotKey, 0)

    fun setFontSize(slotKey: String, value: Int) {
        prefs.edit().putInt(FONT_SIZE_PREFIX + slotKey, value).apply()
        touch()
    }
}
