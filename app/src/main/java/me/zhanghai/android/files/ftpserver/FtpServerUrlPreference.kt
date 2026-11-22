/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.View
import android.view.View.OnCreateContextMenuListener
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.valueCompat

class FtpServerUrlPreference : Preference {
    private val observer = Observer<Any> { updateUrl() }
    private val receiver = FtpServerUrl.createChangeReceiver(context) { updateUrl() }

    private var url: String? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isPersistent = false
        updateUrl()
    }

    override fun onAttached() {
        super.onAttached()

        Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeForever(observer)
        Settings.FTP_SERVER_USERNAME.observeForever(observer)
        Settings.FTP_SERVER_PORT.observeForever(observer)
        receiver.register()
    }

    override fun onDetached() {
        super.onDetached()

        Settings.FTP_SERVER_ANONYMOUS_LOGIN.removeObserver(observer)
        Settings.FTP_SERVER_USERNAME.removeObserver(observer)
        Settings.FTP_SERVER_PORT.removeObserver(observer)
        receiver.unregister()
    }

    private fun updateUrl() {
        url = FtpServerUrl.getUrl()
        summary = url ?: context.getString(R.string.ftp_server_url_summary_no_local_inet_address)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // 白い熊 fork: a pill popup in place of the platform's context menu, whose rows the
        // theme cannot reach.
        holder.itemView.setOnLongClickListener { view ->
            val url = url ?: return@setOnLongClickListener false
            me.zhanghai.android.files.skui.SkPopupMenu(context, view).apply {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, url).isEnabled = false
                menu.add(
                    Menu.NONE, R.id.action_sk_copy_url, Menu.NONE,
                    R.string.ftp_server_url_menu_copy_url
                ).setOnMenuItemClickListener {
                    clipboardManager.copyText(url, context)
                    true
                }
                if (!Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
                    val password = Settings.FTP_SERVER_PASSWORD.valueCompat
                    if (password.isNotEmpty()) {
                        menu.add(
                            Menu.NONE, R.id.action_sk_copy_password, Menu.NONE,
                            R.string.ftp_server_url_menu_copy_password
                        ).setOnMenuItemClickListener {
                            clipboardManager.copyText(password, context)
                            true
                        }
                    }
                }
            }.show()
            true
        }
    }
}
