/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.recreateCompat
import me.zhanghai.android.files.skui.SkUi
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.night.NightModeHelper

abstract class AppActivity : AppCompatActivity() {
    private var isDelegateCreated = false

    // 白い熊 fork: whether the 白い熊 theme overlay was applied at creation, and the colours
    // this activity was built from.
    private var isSkThemeApplied = false
    private var skPaletteGeneration = 0

    // 白い熊 fork: the colour roles are laid over the resources before anything — the theme
    // included — is read from them. See SkPalette.
    override fun getTheme(): android.content.res.Resources.Theme {
        try {
            me.zhanghai.android.files.skui.SkPalette.apply(this)
        } catch (e: Exception) {
            // Asked for before the base context is attached: the next call lays them.
        }
        return super.getTheme()
    }

    override fun getDelegate(): AppCompatDelegate {
        val delegate = super.getDelegate()

        if (!isDelegateCreated) {
            isDelegateCreated = true
            NightModeHelper.apply(this)
        }
        return delegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CustomThemeHelper.apply(this)
        // 白い熊 fork: black background, yellow text/icons/borders everywhere.
        isSkThemeApplied = SkUi.isSkThemeEnabled
        skPaletteGeneration = me.zhanghai.android.files.skui.SkPalette.generation
        if (isSkThemeApplied) {
            theme.applyStyle(R.style.ThemeOverlay_Sk, true)
        }

        super.onCreate(savedInstanceState)

        if (isSkThemeApplied) {
            skKeepTranslucentWindowClear()
            skPaintSystemBars()
        }
    }

    // 白い熊 fork: the 白い熊 overlay gives every window the background role as its ground —
    // also the see-through windows that only host a dialog (file job conflict, error and
    // progress dialogs), which then blacked out the screen behind the dialog. A translucent
    // window gets its clear ground back.
    private fun skKeepTranslucentWindowClear() {
        val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
        val isTranslucent = try {
            attributes.getBoolean(0, false)
        } finally {
            attributes.recycle()
        }
        if (isTranslucent) {
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
        }
    }

    // 白い熊 fork: the system bars are upstream's (a scrim over the content) until 白い熊
    // gives one of them a colour of its own on the UI page.
    @Suppress("DEPRECATION")
    private fun skPaintSystemBars() {
        val statusBar = me.zhanghai.android.files.skui.SkThemeSlot.STATUS_BAR
        if (SkUi.getColorOverride(statusBar.key) != SkUi.UNSET) {
            window.statusBarColor = me.zhanghai.android.files.skui.skColor(statusBar)
        }
        val navigationBar = me.zhanghai.android.files.skui.SkThemeSlot.NAVIGATION_BAR
        if (SkUi.getColorOverride(navigationBar.key) != SkUi.UNSET) {
            window.navigationBarColor = me.zhanghai.android.files.skui.skColor(navigationBar)
        }
    }

    override fun onResume() {
        super.onResume()

        // 白い熊 fork: pick up a toggle of the 白い熊 theme from the UI page.
        if (isSkThemeApplied != SkUi.isSkThemeEnabled
            || (isSkThemeApplied && skRecreatesOnColorChange
                && skPaletteGeneration != me.zhanghai.android.files.skui.SkPalette.generation)) {
            recreateCompat()
        }
    }

    // 白い熊 fork: false for the UI page, which repaints itself while its colours are edited.
    protected open val skRecreatesOnColorChange: Boolean
        get() = true

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
