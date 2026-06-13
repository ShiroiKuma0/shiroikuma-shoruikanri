/*
 * 白い熊 fork (skui): receives system-share intents (ACTION_SEND / SEND_MULTIPLE)
 * and routes them to a Termux script — the inbound counterpart of SkShareDialog.
 *
 * Two entry points, both landing here:
 * - A per-script Direct-Share shortcut (SkShareShortcuts): the system passes the
 *   shortcut id in EXTRA_SHORTCUT_ID, so the matching script runs straight away
 *   (the one-tap path 白い熊 asked for — screenshot → Share → «script»).
 * - The always-present «書類管理» entry (this activity's SEND intent-filter): no
 *   shortcut id, so we show a small black/yellow chooser of the script targets.
 *
 * Termux runs in its own sandbox and can't read content:// URIs, so each shared
 * item is resolved to a real filesystem path Termux can read: a readable path on
 * shared storage when one exists (e.g. a screenshot), otherwise a copy staged
 * under external storage. Those absolute paths become the script's arguments.
 */

package me.zhanghai.android.files.skui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.showToast
import java.io.File
import kotlin.concurrent.thread

class SkShareReceiverActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = incomingUris()
        if (uris.isEmpty()) {
            showToast(R.string.sk_inbound_no_files)
            finish()
            return
        }
        if (!SkTermux.isInstalled(this)) {
            showToast(R.string.sk_termux_missing)
            finish()
            return
        }

        // One-tap path: launched via a per-script Direct-Share shortcut or a static
        // share slot (activity-alias). Either resolves to a script index → run it.
        val scripts = SkTermux.scripts
        val directIndex = directScriptIndex(scripts.size)
        if (directIndex != null) {
            runWith(scripts[directIndex], uris)
            return
        }

        // Fallback entry («白い熊 書類管理»): pick a target.
        if (scripts.isEmpty()) {
            showToast(R.string.sk_inbound_no_targets)
            finish()
            return
        }
        val labels: Array<CharSequence> = Array(scripts.size) { scripts[it].label }
        SkMaterialAlertDialogBuilder(this)
            .setTitle(R.string.sk_inbound_choose_target)
            .setItems(labels) { _, which -> runWith(scripts[which], uris) }
            .setOnCancelListener { finish() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun runWith(script: SkTermuxScript, uris: List<Uri>) {
        showToast(getString(R.string.sk_inbound_running_format, script.label))
        thread {
            val paths = uris.mapNotNull { resolveToTermuxPath(it) }
            runOnUiThread {
                when {
                    paths.isEmpty() -> showToast(R.string.sk_inbound_resolve_failed)
                    !SkTermux.run(this, script, paths) -> showToast(R.string.sk_termux_failed)
                }
                finish()
            }
        }
    }

    // --- Which script to run for a one-tap launch ---

    // Resolve a direct script index from either the Direct-Share shortcut id or the
    // static share slot (activity-alias) that launched us. Returns null (→ chooser)
    // when neither applies or the index is out of range.
    private fun directScriptIndex(count: Int): Int? {
        val fromShortcut = SkShareShortcuts.scriptIndexForShortcutId(
            intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        )
        if (fromShortcut != null && fromShortcut in 0 until count) return fromShortcut
        val fromSlot = slotIndexFromComponent()
        if (fromSlot != null && fromSlot in 0 until count) return fromSlot
        return null
    }

    // A SkShareSlot<N> alias maps to the Nth Termux script (1-based label → 0-based).
    private fun slotIndexFromComponent(): Int? {
        val className = intent.component?.className ?: componentName.className
        val digits = className.substringAfterLast(SkShareShortcuts.SLOT_CLASS_PREFIX, "")
        return digits.toIntOrNull()?.let { it - 1 }
    }

    // --- Shared URIs ---

    private fun incomingUris(): List<Uri> {
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.let { uris += it }
            }
        }
        if (uris.isEmpty()) {
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris += it }
                }
            }
        }
        return uris
    }

    // --- Resolving a shared item to a Termux-readable real path ---

    private fun resolveToTermuxPath(uri: Uri): String? {
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty() && File(path).canRead()) return path
        }
        realPathOnSharedStorage(uri)?.let { if (File(it).canRead()) return it }
        return copyToStaging(uri)
    }

    private fun realPathOnSharedStorage(uri: Uri): String? {
        // externalstorage DocumentsProvider: docId like "primary:Pictures/Screenshots/x.jpg".
        try {
            if (DocumentsContract.isDocumentUri(this, uri) &&
                uri.authority == AUTHORITY_EXTERNAL_STORAGE) {
                val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true)) {
                    return "${externalRoot().absolutePath}/${parts[1]}"
                }
            }
        } catch (e: Exception) {
            // Fall through to the MediaStore query.
        }
        // MediaStore (and many ROMs' DocumentsProviders) still expose the real _data path.
        try {
            contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotEmpty() }?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            // No usable path; the caller copies to staging instead.
        }
        return null
    }

    private fun copyToStaging(uri: Uri): String? =
        try {
            val dir = stagingDir().apply { mkdirs() }
            // Keep the original name (transfer scripts want it); only de-collide so we
            // never clobber an existing file in what may be a shared scratch folder.
            val dest = uniqueDest(dir, displayName(uri))
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }?.let { dest.absolutePath }
        } catch (e: Exception) {
            null
        }

    private fun uniqueDest(dir: File, name: String): File {
        val base = File(dir, name)
        if (!base.exists()) return base
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(dir, "${stem}_$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun displayName(uri: Uri): String {
        var name: String? = null
        try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            // Fall back to the URI's last path segment.
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')
        if (name.isNullOrBlank()) name = "shared"
        return name!!.replace('/', '_').replace(' ', '_')
    }

    // Settable on the 白い熊 UI page; defaults to /storage/emulated/0/tmp. Not
    // auto-pruned — it may be a shared scratch folder, so cleanup is left to 白い熊.
    private fun stagingDir(): File = File(SkTermux.stagingDir)

    private fun externalRoot(): File = Environment.getExternalStorageDirectory()

    companion object {
        private const val AUTHORITY_EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    }
}
