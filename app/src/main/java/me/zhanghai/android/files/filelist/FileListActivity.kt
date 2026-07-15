/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.commitNow
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.settings.ParcelValueSettingLiveData
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat

/**
 * 白い熊 fork: hosts one full FileListFragment per tab, switched via
 * FragmentManager attach/detach. The activity owns the tab model; the
 * folder-style tab bar itself is rendered by the attached fragment between
 * its toolbar and breadcrumb bar (see SkFolderTabBar).
 */
class FileListActivity : AppActivity() {
    private val tabs = mutableListOf<TabInfo>()
    private var selectedTabIndex = -1
    private var nextTabId = 1

    private var observedCurrentPathLiveData: LiveData<Path>? = null
    private val currentPathObserver = Observer<Path> { onCurrentTabPathChanged(it) }

    private val closeTabOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeTab(selectedTabIndex)
        }
    }

    // File picking is modal, so it stays single-tab.
    private val isInPickMode: Boolean
        get() =
            when (intent.action) {
                Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE -> true
                else -> false
            }

    private val currentFragment: FileListFragment?
        get() = tabs.getOrNull(selectedTabIndex)?.let { findTabFragment(it.id) }

    // The items the attached fragment's SkFolderTabBar renders.
    val skTabItems: List<SkTabItem>
        get() =
            if (isInPickMode) {
                emptyList()
            } else {
                tabs.mapIndexed { index, tabInfo ->
                    SkTabItem(tabInfo.id, getTabTitle(tabInfo.path), index == selectedTabIndex)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.sk_file_list_activity)
        onBackPressedDispatcher.addCallback(this, closeTabOnBackPressedCallback)
        // Tab titles can come from bookmark names; repaint the strip when a
        // bookmark is renamed (or added/removed) while its tab sits open.
        Settings.BOOKMARK_DIRECTORIES.observe(this) { notifyTabStrip() }

        if (savedInstanceState == null) {
            // Restore the persisted tab set on a cold start (survives reboots and app
            // updates); an explicit intent (e.g. a VIEW of a directory) opens on top of it.
            val persistedState = if (!isInPickMode) openTabsSetting.value else null
            if (persistedState != null && persistedState.tabs.isNotEmpty()) {
                restoreTabs(persistedState)
                if (intent.action != Intent.ACTION_MAIN) {
                    addTab(FileListFragment.Args(intent), null)
                }
            } else {
                addTab(FileListFragment.Args(intent), null)
            }
        } else {
            val state = savedInstanceState.getState<State>()
            nextTabId = state.nextTabId
            for (tabState in state.tabs) {
                tabs += tabState.toTabInfo()
            }
            selectedTabIndex = state.selectedTabIndex
            // The tab fragments themselves are restored by the fragment manager, with only the
            // selected one attached; it rebinds the tab bar itself when its view is recreated.
            currentFragment?.let { observeCurrentPath(it) }
            updateTabState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(
            State(tabs.map { it.toTabState() }, selectedTabIndex, nextTabId)
        )
    }

    private fun TabInfo.toTabState(): TabState =
        TabState(id, path)

    private fun TabState.toTabInfo(): TabInfo =
        TabInfo(id, path)

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (currentFragment?.onKeyShortcut(keyCode, event) == true) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    fun openInNewTab(path: Path) {
        if (isInPickMode) {
            return
        }
        // 白い熊 fork: the listing view is per-folder, so a new tab simply picks up
        // whatever view its destination folder already remembers (or the default).
        addTab(FileListFragment.Args(createViewIntent(path)), path)
    }

    // The tab bar's "+" button: duplicate the current location into a new tab.
    fun openCurrentPathInNewTab() {
        openInNewTab(
            tabs.getOrNull(selectedTabIndex)?.path
                ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
        )
    }

    fun selectTabById(id: Int) {
        selectTab(tabs.indexOfFirst { it.id == id })
    }

    fun closeTabById(id: Int) {
        closeTab(tabs.indexOfFirst { it.id == id })
    }

    // Drag-rearranging the folder tabs.
    fun moveTabById(id: Int, toIndex: Int) {
        val fromIndex = tabs.indexOfFirst { it.id == id }
        if (fromIndex == -1 || toIndex !in tabs.indices || fromIndex == toIndex) {
            return
        }
        val selectedId = tabs.getOrNull(selectedTabIndex)?.id
        tabs.add(toIndex, tabs.removeAt(fromIndex))
        selectedTabIndex = tabs.indexOfFirst { it.id == selectedId }
        updateTabState()
    }

    // A long-press on a folder tab adds it to the favorites in the drawer.
    fun addTabToFavorites(id: Int) {
        val path = tabs.firstOrNull { it.id == id }?.path ?: return
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        showToast(R.string.file_add_bookmark_success)
    }

    // Horizontal swipes in the folder body, wrapping around at both ends.
    fun selectAdjacentTab(direction: Int) {
        if (tabs.size < 2) {
            return
        }
        selectTab((selectedTabIndex + direction + tabs.size) % tabs.size)
    }

    private fun addTab(args: FileListFragment.Args, path: Path?) {
        val tabInfo = TabInfo(nextTabId++, path)
        val fragment = FileListFragment().putArgs(args)
        val oldFragment = currentFragment
        stopObservingCurrentPath()
        supportFragmentManager.commitNow {
            oldFragment?.let { detach(it) }
            add(R.id.tabFragmentContainer, fragment, getTabFragmentTag(tabInfo.id))
        }
        tabs += tabInfo
        selectedTabIndex = tabs.lastIndex
        observeCurrentPath(fragment)
        updateTabState()
    }

    private fun selectTab(index: Int) {
        if (index == selectedTabIndex || index !in tabs.indices) {
            return
        }
        val oldFragment = currentFragment
        stopObservingCurrentPath()
        selectedTabIndex = index
        val fragment = findTabFragment(tabs[index].id)!!
        supportFragmentManager.commitNow {
            oldFragment?.let { detach(it) }
            attach(fragment)
        }
        observeCurrentPath(fragment)
        updateTabState()
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1 || index !in tabs.indices) {
            return
        }
        val wasSelected = index == selectedTabIndex
        val fragment = findTabFragment(tabs[index].id)
        if (wasSelected) {
            stopObservingCurrentPath()
        }
        tabs.removeAt(index)
        if (index < selectedTabIndex) {
            selectedTabIndex--
        }
        fragment?.let { supportFragmentManager.commitNow { remove(it) } }
        if (wasSelected) {
            selectedTabIndex = -1
            selectTab(index.coerceAtMost(tabs.lastIndex))
        } else {
            updateTabState()
        }
    }

    private fun restoreTabs(state: State) {
        nextTabId = state.nextTabId
        val selectedIndex = state.selectedTabIndex.coerceIn(0, state.tabs.lastIndex)
        supportFragmentManager.commitNow {
            state.tabs.forEachIndexed { index, tabState ->
                val path = tabState.path ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
                val fragment = FileListFragment()
                    .putArgs(FileListFragment.Args(createViewIntent(path)))
                add(R.id.tabFragmentContainer, fragment, getTabFragmentTag(tabState.id))
                if (index != selectedIndex) {
                    detach(fragment)
                }
                tabs += tabState.toTabInfo()
            }
        }
        selectedTabIndex = selectedIndex
        currentFragment?.let { observeCurrentPath(it) }
        updateTabState()
    }

    private fun persistTabs() {
        if (isInPickMode) {
            return
        }
        openTabsSetting.putValue(
            State(tabs.map { it.toTabState() }, selectedTabIndex, nextTabId)
        )
    }

    private fun onCurrentTabPathChanged(path: Path) {
        val tabInfo = tabs.getOrNull(selectedTabIndex) ?: return
        tabInfo.path = path
        notifyTabStrip()
        persistTabs()
    }

    private fun getTabTitle(path: Path?): String {
        path ?: return getString(R.string.loading)
        NavigationRootMapLiveData.value?.get(path)?.getName(this)?.let { return it }
        // A tab sitting exactly at a bookmarked path shows the bookmark's (custom)
        // name; several bookmarks may share a path, so take the first match.
        return Settings.BOOKMARK_DIRECTORIES.valueCompat
            .firstOrNull { it.path == path }?.name
            ?: path.name
    }

    private fun observeCurrentPath(fragment: FileListFragment) {
        observedCurrentPathLiveData = fragment.currentPathLiveData.also {
            it.observe(this, currentPathObserver)
        }
    }

    private fun stopObservingCurrentPath() {
        observedCurrentPathLiveData?.removeObserver(currentPathObserver)
        observedCurrentPathLiveData = null
    }

    private fun updateTabState() {
        closeTabOnBackPressedCallback.isEnabled = tabs.size > 1
        notifyTabStrip()
        persistTabs()
    }

    private fun notifyTabStrip() {
        currentFragment
            ?.takeIf { it.isAdded && !it.isDetached }
            ?.updateSkTabStrip()
    }

    private fun findTabFragment(id: Int): FileListFragment? =
        supportFragmentManager.findFragmentByTag(getTabFragmentTag(id)) as FileListFragment?

    private fun getTabFragmentTag(id: Int): String = "tab_$id"

    private class TabInfo(val id: Int, var path: Path?)

    @Parcelize
    private class TabState(
        val id: Int,
        val path: @WriteWith<ParcelableParceler> Path?
    ) : Parcelable

    @Parcelize
    private class State(
        val tabs: List<TabState>,
        val selectedTabIndex: Int,
        val nextTabId: Int
    ) : ParcelableState

    companion object {
        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }

        // 白い熊 fork: the open-tab set persists across restarts, reboots and app
        // updates (decode failures after an update just fall back to a fresh tab).
        private val openTabsSetting: SettingLiveData<State?> =
            ParcelValueSettingLiveData(R.string.sk_pref_key_open_tabs, null)
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}
