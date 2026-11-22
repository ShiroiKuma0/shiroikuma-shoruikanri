/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import me.zhanghai.android.files.skui.SkMaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.asExternalStorageUri
import me.zhanghai.android.files.provider.document.resolver.ExternalStorageProviderHacks
import me.zhanghai.android.files.skui.SkAction
import me.zhanghai.android.files.skui.SkIconRole
import me.zhanghai.android.files.skui.createWithActions
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

class AddStorageDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        SkMaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_add_storage_title)
            // 白い熊 fork: the list as icon pills (black-yellow theme), not as plain lines.
            .createWithActions(
                STORAGE_TYPES.map { (titleRes, intent) ->
                    SkAction(
                        getString(titleRes), R.drawable.add_icon_white_24dp, SkIconRole.WRITE,
                        opens = true
                    ) {
                        startActivitySafe(intent)
                        finish()
                    }
                }
            )

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    companion object {
        private val STORAGE_TYPES = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.storage_add_storage_android_data to
                    AddExternalStorageShortcutActivity ::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_data,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA
                                .asExternalStorageUri()
                        )
                    )
            } else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.storage_add_storage_android_obb to
                    AddExternalStorageShortcutActivity ::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_obb,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB
                                .asExternalStorageUri()
                        )
                    )
            } else null,
            R.string.storage_add_storage_document_tree to
                AddDocumentTreeActivity::class.createIntent(),
            R.string.storage_add_storage_ftp_server to
                EditFtpServerActivity::class.createIntent().putArgs(EditFtpServerFragment.Args()),
            R.string.storage_add_storage_sftp_server to
                EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerFragment.Args()),
            R.string.storage_add_storage_smb_server to
                AddLanSmbServerActivity::class.createIntent(),
            R.string.storage_add_storage_webdav_server to
                EditWebDavServerActivity::class.createIntent()
                    .putArgs(EditWebDavServerFragment.Args()),
        )
    }
}
