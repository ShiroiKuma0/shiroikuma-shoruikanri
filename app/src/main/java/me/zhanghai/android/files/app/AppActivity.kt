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

    // 白い熊 fork: whether the 白い熊 theme overlay was applied at creation.
    private var isSkThemeApplied = false

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
        if (isSkThemeApplied) {
            theme.applyStyle(R.style.ThemeOverlay_Sk, true)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        // 白い熊 fork: pick up a toggle of the 白い熊 theme from the UI page.
        if (isSkThemeApplied != SkUi.isSkThemeEnabled) {
            recreateCompat()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
