/*
 * 白い熊 fork (skui): per-element fonts — user-importable font files (shared
 * across all elements), with per-slot family / weight / size. Ported from the
 * sister repos (shiroikuma-denwa Fonts.kt).
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import java.io.File

private const val SEMIBOLD_WEIGHT = 600
private const val BOLD_WEIGHT = 700
private val FONT_EXTENSIONS = setOf("ttf", "otf")

// Built-in family sentinel that can't be a real filename.
const val SK_MONOSPACE_FONT = "@monospace"

// A selectable font weight. value 0 = leave the family's own default weight.
enum class SkFontWeight(val value: Int, @StringRes val labelRes: Int) {
    DEFAULT(0, R.string.sk_font_weight_default),
    THIN(100, R.string.sk_font_weight_thin),
    LIGHT(300, R.string.sk_font_weight_light),
    REGULAR(400, R.string.sk_font_weight_regular),
    MEDIUM(500, R.string.sk_font_weight_medium),
    SEMIBOLD(600, R.string.sk_font_weight_semibold),
    BOLD(700, R.string.sk_font_weight_bold),
    BLACK(900, R.string.sk_font_weight_black);

    companion object {
        fun fromValue(value: Int): SkFontWeight = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

// One pickable font; an empty fileName means "system default".
class SkFontOption(val displayName: String, val fileName: String)

private val typefaceCache = HashMap<String, Typeface>()

fun skFontsDir(): File = File(application.filesDir, "fonts").apply { if (!exists()) mkdirs() }

/** Drop the in-memory typeface cache (e.g. after an import replaced font files). */
fun skInvalidateFontCache() {
    typefaceCache.clear()
}

/** Built-in families + every font the user has imported. */
fun Context.skAvailableFontOptions(): List<SkFontOption> {
    val options = mutableListOf(
        SkFontOption(getString(R.string.sk_font_system_default), ""),
        SkFontOption(getString(R.string.sk_font_monospace), SK_MONOSPACE_FONT)
    )
    skFontsDir().listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
        ?.sortedBy { it.name.lowercase() }
        ?.forEach { options.add(SkFontOption(it.nameWithoutExtension, it.name)) }
    return options
}

/** Human-readable name for a stored family value. */
fun Context.skFontDisplayName(fileName: String): String =
    when {
        fileName.isEmpty() -> getString(R.string.sk_font_system_default)
        fileName == SK_MONOSPACE_FONT -> getString(R.string.sk_font_monospace)
        else -> File(fileName).nameWithoutExtension
    }

/** Typeface for a stored family value, cached; a bad font file falls back to the default. */
fun skFontTypeface(fileName: String): Typeface =
    when {
        fileName.isEmpty() -> Typeface.DEFAULT
        fileName == SK_MONOSPACE_FONT -> Typeface.MONOSPACE
        else ->
            typefaceCache.getOrPut(fileName) {
                try {
                    Typeface.createFromFile(File(skFontsDir(), fileName))
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            }
    }

/** Combine a family + weight with a base text style (e.g. a bold list header). */
fun skTypeface(family: String, weight: Int, baseStyle: Int = Typeface.NORMAL): Typeface {
    val base = skFontTypeface(family)
    if (weight <= 0) {
        return Typeface.create(base, baseStyle)
    }
    val italic = baseStyle == Typeface.ITALIC || baseStyle == Typeface.BOLD_ITALIC
    val bold = baseStyle == Typeface.BOLD || baseStyle == Typeface.BOLD_ITALIC
    val effectiveWeight = if (bold) maxOf(weight, BOLD_WEIGHT) else weight
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, effectiveWeight, italic)
    } else {
        Typeface.create(base, if (effectiveWeight >= SEMIBOLD_WEIGHT) Typeface.BOLD else Typeface.NORMAL)
    }
}

/**
 * Apply a slot's configured color + family + weight + size to a real text view
 * (size only overrides when explicitly set).
 */
fun TextView.applySkSlot(slot: SkThemeSlot, baseStyle: Int = Typeface.NORMAL) {
    setTextColor(skColor(slot))
    typeface = skTypeface(SkUi.getFontFamily(slot.key), SkUi.getFontWeight(slot.key), baseStyle)
    val sizeSp = SkUi.getFontSize(slot.key)
    if (sizeSp > 0) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    }
}

private const val DEFAULT_SAMPLE_TEXT_SP = 16f

/** Render the live sample line in an explicit family/weight/size/color (the on-page preview). */
fun TextView.showSkFontSample(family: String, weight: Int, sizeSp: Int, color: Int) {
    text = context.getString(R.string.sk_font_sample_text)
    typeface = skTypeface(family, weight)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (sizeSp > 0) sizeSp.toFloat() else DEFAULT_SAMPLE_TEXT_SP)
    setTextColor(color)
}

/** Copy a picked font file into the shared app fonts dir; returns its filename, or null on failure. */
fun Context.skImportFont(uri: Uri): String? {
    val name = skFontFileName(uri) ?: return null
    if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) {
        return null
    }
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    try {
        File(skFontsDir(), name).writeBytes(bytes)
    } catch (e: Exception) {
        return null
    }
    typefaceCache.remove(name)
    return name
}

private fun Context.skFontFileName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
    return uri.lastPathSegment?.substringAfterLast('/')
}
