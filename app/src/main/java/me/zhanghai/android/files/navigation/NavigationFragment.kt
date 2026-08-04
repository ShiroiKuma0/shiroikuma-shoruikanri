/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java8.nio.file.Path
import me.zhanghai.android.files.databinding.NavigationFragmentBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

class NavigationFragment : Fragment(), NavigationItem.Listener {
    private lateinit var binding: NavigationFragmentBinding

    private lateinit var adapter: NavigationListAdapter

    lateinit var listener: Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        NavigationFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.recyclerView.setHasFixedSize(true)
        // TODO: Needed?
        //binding.recyclerView.setItemAnimator(new NoChangeAnimationItemAnimator())
        val context = requireContext()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = NavigationListAdapter(this, context)
        binding.recyclerView.adapter = adapter
        // 白い熊 fork: favorites (bookmarks) are drag-rearrangeable in place; a long-press
        // released without movement opens the edit (rename / delete) dialog instead.
        ItemTouchHelper(BookmarkDragCallback()).attachToRecyclerView(binding.recyclerView)

        val viewLifecycleOwner = viewLifecycleOwner
        NavigationItemListLiveData.observe(viewLifecycleOwner) { onNavigationItemsChanged(it) }
        listener.observeCurrentPath(viewLifecycleOwner) { onCurrentPathChanged(it) }
    }

    private fun onNavigationItemsChanged(navigationItems: List<NavigationItem?>) {
        adapter.replace(navigationItems)
    }

    // 白い熊 fork: re-apply skui styling after the UI page changed something.
    fun refreshSkStyle() {
        if (this::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    // 白い熊 fork: drag-rearrange for the favorites block in the drawer.
    private inner class BookmarkDragCallback : ItemTouchHelper.Callback() {
        private var isDragging = false
        private var hasMoved = false
        private var draggedItem: NavigationItem? = null

        override fun isLongPressDragEnabled(): Boolean = true

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val item = adapter.list.getOrNull(viewHolder.bindingAdapterPosition)
            return if (item?.isDraggable == true) {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            } else {
                0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            if (adapter.list.getOrNull(toPosition)?.isDraggable != true) {
                return false
            }
            adapter.move(fromPosition, toPosition)
            hasMoved = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                isDragging = true
                hasMoved = false
                draggedItem = adapter.list.getOrNull(viewHolder.bindingAdapterPosition)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            if (!isDragging) {
                return
            }
            isDragging = false
            val item = draggedItem
            draggedItem = null
            if (hasMoved) {
                persistBookmarkOrder()
            } else {
                // A long-press released without movement edits the favorite.
                item?.onLongClick(this@NavigationFragment)
            }
        }

        private fun persistBookmarkOrder() {
            val idOrder = adapter.list.filterNotNull().filter { it.isDraggable }.map { it.id }
            val bookmarkDirectories = Settings.BOOKMARK_DIRECTORIES.valueCompat
            val reordered = idOrder.mapNotNull { id -> bookmarkDirectories.find { it.id == id } }
            if (reordered.size == bookmarkDirectories.size && reordered != bookmarkDirectories) {
                Settings.BOOKMARK_DIRECTORIES.putValue(reordered)
            }
        }
    }

    private fun onCurrentPathChanged(path: Path) {
        adapter.notifyCheckedChanged()
    }

    override val currentPath: Path
        get() = listener.currentPath

    override fun navigateTo(path: Path) {
        listener.navigateTo(path)
    }

    override fun navigateToRoot(path: Path) {
        listener.navigateToRoot(path)
    }

    override fun launchIntent(intent: Intent) {
        startActivitySafe(intent)
    }

    override fun closeNavigationDrawer() {
        listener.closeNavigationDrawer()
    }

    // 白い熊 fork: quit outright - finish every activity in this task (all tabs of this window)
    // and drop it from recents, so the next launch starts fresh. A window opened with "New
    // window" is its own task and survives, as do background services: a file job or the FTP
    // server carries on with its own notification.
    override fun exitApp() {
        requireActivity().finishAndRemoveTask()
    }

    interface Listener {
        val currentPath: Path
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun navigateToDefaultRoot()
        fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit)
        fun closeNavigationDrawer()
    }
}
