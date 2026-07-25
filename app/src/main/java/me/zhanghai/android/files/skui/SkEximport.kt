/*
 * 白い熊 fork (skui): category-based settings export/import, modeled on the
 * sister repos (shiroikuma-kojiki KojikiExport). The export is a ZIP of plain
 * type-tagged JSON files — one per category — plus the user-imported font
 * files as real files under fonts/, and a manifest.json listing format,
 * version and the categories present. Import merges per key (never clears),
 * skips categories absent from the ZIP, and ignores unknown keys, so exports
 * round-trip safely across app versions.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.defaultSharedPreferences
import me.zhanghai.android.files.compat.PreferenceManagerCompat
import me.zhanghai.android.files.provider.common.getLastModifiedTime
import me.zhanghai.android.files.provider.common.newDirectoryStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SkEximport {
    const val FORMAT = "shiroikuma-shoruikanri-export"
    const val VERSION = 1

    // Doubles as the "is this one of ours" filter for the last-export scan.
    private const val EXPORT_PREFIX = "shiroikuma-shoruikanri-"

    // Device-local store for the export directory; deliberately not a category,
    // so an import from another device never installs a dead directory.
    private const val EXIMPORT_PREFS = "sk_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    // Ephemeral / device-local keys never worth exporting from the main app prefs.
    private val APP_SETTINGS_EXCLUDE = setOf("key_version_code")

    // Keys carved out of the default prefs into their own categories.
    private val STORAGE_KEYS = setOf(
        "key_storages", "key_bookmark_directories", "key_standard_directories",
        "key_file_list_default_directory"
    )
    private const val OPEN_TABS_KEY = "key_sk_open_tabs"

    /** A selectable export/import category. [id] is its JSON file name (`<id>.json`) in the ZIP. */
    enum class Cat(val id: String, @StringRes val labelRes: Int) {
        UI_THEME("ui_theme", R.string.sk_eximport_cat_ui),
        SEPARATORS_GRID("separators_grid", R.string.sk_eximport_cat_separators),
        APP_SETTINGS("app_settings", R.string.sk_eximport_cat_settings),
        STORAGES("storages", R.string.sk_eximport_cat_storages),
        FOLDER_VIEWS("folder_views", R.string.sk_eximport_cat_folders),
        OPEN_TABS("open_tabs", R.string.sk_eximport_cat_tabs),
        SHARE("share", R.string.sk_eximport_cat_share),
        OPEN_WITH("open_with", R.string.sk_eximport_cat_open_with);

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    // --- The persisted export directory ---

    private val eximportPrefs: SharedPreferences by lazy {
        application.getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE)
    }

    var exportDirPath: Path?
        get() =
            eximportPrefs.getString(KEY_DIR_URI, null)
                ?.let { runCatching { Paths.get(URI(it)) }.getOrNull() }
        set(value) {
            eximportPrefs.edit().putString(KEY_DIR_URI, value?.toUri()?.toString()).apply()
        }

    /** The newest export in [dir] as (file name, last modified millis), or null. */
    fun latestExport(dir: Path): Pair<String, Long>? =
        runCatching {
            dir.newDirectoryStream().use { stream ->
                stream
                    .mapNotNull { path ->
                        val name = path.fileName?.toString() ?: return@mapNotNull null
                        if (!name.startsWith(EXPORT_PREFIX) || !name.endsWith(".zip")) {
                            return@mapNotNull null
                        }
                        name to runCatching { path.getLastModifiedTime().toMillis() }
                            .getOrDefault(0L)
                    }
                    .maxByOrNull { it.second }
            }
        }.getOrNull()

    fun newExportFileName(): String =
        EXPORT_PREFIX + BuildConfig.VERSION_NAME + "-export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // --- Export ---

    /** Write a ZIP of the selected categories to [out]. Returns the category count. */
    fun export(cats: Set<Cat>, out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", application.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2))
            for (cat in Cat.entries) {
                if (cat !in cats) {
                    continue
                }
                writeEntry(zip, "${cat.id}.json", exportCategory(cat).toString(2))
                if (cat == Cat.UI_THEME) {
                    exportFonts(zip)
                }
                ++count
            }
        }
        return count
    }

    private fun exportCategory(cat: Cat): JSONObject =
        when (cat) {
            Cat.UI_THEME -> prefsToJson(namedPrefs("sk_ui"))
            Cat.SEPARATORS_GRID ->
                JSONObject()
                    .put("separators", prefsToJson(namedPrefs("sk_separators")))
                    .put("grid_styles", prefsToJson(namedPrefs("sk_grid_styles")))
            Cat.APP_SETTINGS ->
                prefsToJson(defaultSharedPreferences) {
                    it !in STORAGE_KEYS && it != OPEN_TABS_KEY && it !in APP_SETTINGS_EXCLUDE
                }
            Cat.STORAGES -> prefsToJson(defaultSharedPreferences) { it in STORAGE_KEYS }
            Cat.FOLDER_VIEWS -> prefsToJson(pathPrefs())
            Cat.OPEN_TABS -> prefsToJson(defaultSharedPreferences) { it == OPEN_TABS_KEY }
            Cat.SHARE ->
                JSONObject()
                    .put("termux", prefsToJson(namedPrefs("sk_termux")))
                    .put("share", prefsToJson(namedPrefs("sk_share")))
            Cat.OPEN_WITH -> prefsToJson(namedPrefs("sk_open_with"))
        }

    private fun exportFonts(zip: ZipOutputStream) {
        skFontsDir().listFiles()?.forEach { file ->
            if (!file.isFile) {
                return@forEach
            }
            zip.putNextEntry(ZipEntry("fonts/${file.name}"))
            zip.write(file.readBytes())
            zip.closeEntry()
        }
    }

    // --- Import ---

    /** Read every ZIP entry into memory keyed by entry name. */
    fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    stream.copyTo(buffer)
                    files[entry.name] = buffer.toByteArray()
                }
                entry = stream.nextEntry
            }
        }
        return files
    }

    /** Categories present in a ZIP (from its manifest, else the `<id>.json` files found). */
    fun categoriesIn(files: Map<String, ByteArray>): Set<Cat> {
        files["manifest.json"]?.let { manifest ->
            val ids = runCatching {
                JSONObject(manifest.decodeToString()).optJSONArray("categories")
            }.getOrNull()
            if (ids != null) {
                val cats = (0 until ids.length()).mapNotNull { Cat.byId(ids.optString(it)) }.toSet()
                if (cats.isNotEmpty()) {
                    return cats
                }
            }
        }
        return Cat.entries.filter { files.containsKey("${it.id}.json") }.toSet()
    }

    /**
     * Apply the selected categories from an already-read ZIP; categories absent from it are
     * skipped. Returns a per-category "label: key count" summary for the result dialog.
     */
    fun import(context: Context, files: Map<String, ByteArray>, cats: Set<Cat>): String {
        val parts = mutableListOf<String>()
        for (cat in Cat.entries) {
            if (cat !in cats) {
                continue
            }
            val data = files["${cat.id}.json"] ?: continue
            val label = context.getString(cat.labelRes)
            parts +=
                try {
                    val count = importCategory(cat, JSONObject(data.decodeToString()))
                    if (cat == Cat.UI_THEME) {
                        importFonts(files)
                    }
                    "$label: $count"
                } catch (e: Exception) {
                    "$label: ✗ (${e.message})"
                }
        }
        // The stores' in-memory state was swapped underneath — drop caches and
        // nudge every generation-based refresher; shortcuts follow the new scripts.
        skInvalidateFontCache()
        SkUi.notifyChanged()
        SkShareShortcuts.sync(context)
        return parts.joinToString("\n")
    }

    private fun importCategory(cat: Cat, json: JSONObject): Int =
        when (cat) {
            Cat.UI_THEME -> jsonToPrefs(namedPrefs("sk_ui"), json)
            Cat.SEPARATORS_GRID ->
                jsonToPrefs(namedPrefs("sk_separators"), json.optJSONObject("separators")) +
                    jsonToPrefs(namedPrefs("sk_grid_styles"), json.optJSONObject("grid_styles"))
            Cat.APP_SETTINGS ->
                jsonToPrefs(defaultSharedPreferences, json) {
                    it !in STORAGE_KEYS && it != OPEN_TABS_KEY && it !in APP_SETTINGS_EXCLUDE
                }
            Cat.STORAGES -> jsonToPrefs(defaultSharedPreferences, json) { it in STORAGE_KEYS }
            Cat.FOLDER_VIEWS -> jsonToPrefs(pathPrefs(), json)
            Cat.OPEN_TABS -> jsonToPrefs(defaultSharedPreferences, json) { it == OPEN_TABS_KEY }
            Cat.SHARE ->
                jsonToPrefs(namedPrefs("sk_termux"), json.optJSONObject("termux")) +
                    jsonToPrefs(namedPrefs("sk_share"), json.optJSONObject("share"))
            Cat.OPEN_WITH -> jsonToPrefs(namedPrefs("sk_open_with"), json)
        }

    private fun importFonts(files: Map<String, ByteArray>) {
        for ((name, bytes) in files) {
            if (!name.startsWith("fonts/")) {
                continue
            }
            // Basename only — no path traversal.
            val safeName = File(name).name
            if (safeName.isBlank()) {
                continue
            }
            runCatching { File(skFontsDir(), safeName).writeBytes(bytes) }
        }
    }

    /** Restart the whole app so every setting is re-read from the imported prefs. */
    fun restartApp(context: Context) {
        val appContext = context.applicationContext
        val launchIntent =
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        appContext.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
        Runtime.getRuntime().exit(0)
    }

    // --- The type-tagged prefs round-trip ---

    // Android caches SharedPreferences per name, so these are the same instances
    // the stores hold — imports fire their change listeners.
    private fun namedPrefs(name: String): SharedPreferences =
        application.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun pathPrefs(): SharedPreferences =
        application.getSharedPreferences(
            "${PreferenceManagerCompat.getDefaultSharedPreferencesName(application)}_path",
            PreferenceManagerCompat.defaultSharedPreferencesMode
        )

    // Each pref serializes as "key": {"t": <type tag>, "v": <value>}.
    private fun prefsToJson(
        sharedPreferences: SharedPreferences,
        include: (String) -> Boolean = { true }
    ): JSONObject {
        val json = JSONObject()
        for ((key, value) in sharedPreferences.all) {
            if (!include(key)) {
                continue
            }
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            json.put(key, entry)
        }
        return json
    }

    // Merge per key — never clear, so unrelated/device-local keys survive.
    private fun jsonToPrefs(
        sharedPreferences: SharedPreferences,
        json: JSONObject?,
        include: (String) -> Boolean = { true }
    ): Int {
        json ?: return 0
        val editor = sharedPreferences.edit()
        var count = 0
        for (key in json.keys()) {
            if (!include(key)) {
                continue
            }
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "s" -> editor.putString(key, entry.optString("v"))
                "ss" -> {
                    val array = entry.optJSONArray("v") ?: JSONArray()
                    editor.putStringSet(
                        key, (0 until array.length()).mapTo(HashSet()) { array.optString(it) }
                    )
                }
                else -> continue
            }
            ++count
        }
        editor.apply()
        return count
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
