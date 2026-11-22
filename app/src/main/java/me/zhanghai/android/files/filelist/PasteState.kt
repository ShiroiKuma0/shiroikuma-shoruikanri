/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

// TODO: Make immutable?
class PasteState(
    var copy: Boolean = false,
    // 白い熊 fork: the files wait to be packed into an archive where "Pack here" is tapped,
    // not to be copied or moved.
    var archive: Boolean = false,
    val files: FileItemSet = fileItemSetOf()
)
