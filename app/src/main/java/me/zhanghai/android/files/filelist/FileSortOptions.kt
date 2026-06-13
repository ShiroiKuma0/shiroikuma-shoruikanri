/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.compat.reversedCompat
import me.zhanghai.android.files.file.FileItem
import java.text.Collator

@Parcelize
data class FileSortOptions(
    val by: By,
    val order: Order,
    val isDirectoriesFirst: Boolean
) : Parcelable {
    fun createComparator(): Comparator<FileItem> {
        val unimportantPrefixComparator = compareBy<FileItem> {
            NAME_UNIMPORTANT_PREFIXES.any { prefix -> it.name.startsWith(prefix) }
        }
        // NAME uses the numeric-aware filename collation key (2, 3, 10, 227…);
        // NAME_LITERAL compares names character-by-character with a plain collator,
        // so digit runs sort literally (2, 227, 3, 3071, 666) rather than by value.
        var comparator = if (by == By.NAME_LITERAL) {
            val collator = Collator.getInstance()
            unimportantPrefixComparator
                .thenComparing(Comparator { a, b -> collator.compare(a.name, b.name) })
        } else {
            unimportantPrefixComparator.thenBy { it.nameCollationKey }
        }
        when (by) {
            // Nothing to do.
            By.NAME, By.NAME_LITERAL -> {}
            By.TYPE ->
                comparator = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) {
                    it.extension
                }.then(comparator)
            By.SIZE -> comparator = compareBy<FileItem> { it.attributes.size() }.then(comparator)
            By.LAST_MODIFIED ->
                comparator = compareBy<FileItem> { it.attributes.lastModifiedTime() }
                    .then(comparator)
        }
        when (order) {
            Order.ASCENDING -> {}
            Order.DESCENDING -> comparator = comparator.reversedCompat()
        }
        if (isDirectoriesFirst) {
            val isDirectoryComparator = compareBy<FileItem> { it.attributes.isDirectory }
                .reversedCompat()
            comparator = isDirectoryComparator.then(comparator)
        }
        return comparator
    }

    companion object {
        // Same behavior as Nautilus.
        private val NAME_UNIMPORTANT_PREFIXES = listOf(".", "#")
    }

    enum class By {
        NAME,
        TYPE,
        SIZE,
        LAST_MODIFIED,
        // 白い熊 fork: pure character-order name sort (digits compared as characters).
        // Appended last so existing persisted ordinals stay valid.
        NAME_LITERAL
    }

    enum class Order {
        ASCENDING,
        DESCENDING
    }
}
