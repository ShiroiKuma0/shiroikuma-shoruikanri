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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commitNow
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.tabs.TabLayout
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.SkFileListActivityBinding
import me.zhanghai.android.files.databinding.SkFileListTabItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.valueCompat

class FileListActivity : AppActivity() {
    private lateinit var binding: SkFileListActivityBinding

    private val tabs = mutableListOf<TabInfo>()
    private var selectedTabIndex = -1
    private var nextTabId = 1
    // Suppresses TabLayout selection callbacks while we mutate its tabs ourselves.
    private var isUpdatingTabViews = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SkFileListActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.tabBarLayout.updatePadding(
                left = systemBarInsets.left, right = systemBarInsets.right,
                bottom = systemBarInsets.bottom
            )
            insets
        }
        // When the tab bar is visible it sits between the fragment and the bottom of the window
        // and takes over the bottom system bar inset, so don't let the fragment apply it again.
        ViewCompat.setOnApplyWindowInsetsListener(binding.tabFragmentContainer) { _, insets ->
            if (binding.tabBarLayout.isVisible) {
                val bottomInset = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                ).bottom
                insets.inset(0, 0, 0, bottomInset)
            } else {
                insets
            }
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isUpdatingTabViews) {
                    return
                }
                selectTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.addTabButton.setOnClickListener {
            openInNewTab(
                tabs.getOrNull(selectedTabIndex)?.path
                    ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            )
        }
        onBackPressedDispatcher.addCallback(this, closeTabOnBackPressedCallback)

        if (savedInstanceState == null) {
            addTab(FileListFragment.Args(intent), null)
        } else {
            val state = savedInstanceState.getState<State>()
            nextTabId = state.nextTabId
            isUpdatingTabViews = true
            for (tabState in state.tabs) {
                val tabInfo = TabInfo(tabState.id, tabState.path)
                tabs += tabInfo
                binding.tabLayout.addTab(createTabView(tabInfo), false)
            }
            selectedTabIndex = state.selectedTabIndex
            binding.tabLayout.getTabAt(selectedTabIndex)?.select()
            isUpdatingTabViews = false
            // The tab fragments themselves are restored by the fragment manager, with only the
            // selected one attached.
            currentFragment?.let { observeCurrentPath(it) }
            updateTabBar()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(
            State(tabs.map { TabState(it.id, it.path) }, selectedTabIndex, nextTabId)
        )
    }

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
        addTab(FileListFragment.Args(createViewIntent(path)), path)
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
        isUpdatingTabViews = true
        binding.tabLayout.addTab(createTabView(tabInfo), true)
        isUpdatingTabViews = false
        observeCurrentPath(fragment)
        updateTabBar()
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
        isUpdatingTabViews = true
        binding.tabLayout.getTabAt(index)?.select()
        isUpdatingTabViews = false
        observeCurrentPath(fragment)
    }

    private fun closeTab(tabInfo: TabInfo) {
        closeTab(tabs.indexOf(tabInfo))
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
        isUpdatingTabViews = true
        binding.tabLayout.removeTabAt(index)
        isUpdatingTabViews = false
        fragment?.let { supportFragmentManager.commitNow { remove(it) } }
        if (wasSelected) {
            selectedTabIndex = -1
            selectTab(index.coerceAtMost(tabs.lastIndex))
        }
        updateTabBar()
    }

    private fun createTabView(tabInfo: TabInfo): TabLayout.Tab {
        val tab = binding.tabLayout.newTab()
        val itemBinding = SkFileListTabItemBinding.inflate(layoutInflater)
        itemBinding.nameText.text = getTabTitle(tabInfo.path)
        itemBinding.closeButton.setOnClickListener { closeTab(tabInfo) }
        tab.customView = itemBinding.root
        return tab
    }

    private fun onCurrentTabPathChanged(path: Path) {
        val tabInfo = tabs.getOrNull(selectedTabIndex) ?: return
        tabInfo.path = path
        val customView = binding.tabLayout.getTabAt(selectedTabIndex)?.customView ?: return
        SkFileListTabItemBinding.bind(customView).nameText.text = getTabTitle(path)
    }

    private fun getTabTitle(path: Path?): String {
        path ?: return getString(R.string.loading)
        return NavigationRootMapLiveData.value?.get(path)?.getName(this) ?: path.name
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

    private fun updateTabBar() {
        val isTabBarVisible = tabs.size > 1
        if (binding.tabBarLayout.isVisible != isTabBarVisible) {
            binding.tabBarLayout.isVisible = isTabBarVisible
            ViewCompat.requestApplyInsets(binding.root)
        }
        closeTabOnBackPressedCallback.isEnabled = tabs.size > 1
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
