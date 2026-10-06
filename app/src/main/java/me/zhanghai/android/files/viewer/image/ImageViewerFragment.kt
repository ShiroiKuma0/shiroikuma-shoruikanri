/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.image

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.ImageViewerFragmentBinding
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.provider.common.delete
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.applySkChrome
import me.zhanghai.android.files.ui.DepthPageTransformer
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createSendImageIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.mediumAnimTime
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.systemuihelper.SystemUiHelper
import java.io.IOException

class ImageViewerFragment : Fragment(), ConfirmDeleteDialogFragment.Listener {
    private val args by args<Args>()
    private val argsPaths by lazy { args.intent.extraPathList }

    private lateinit var paths: MutableList<Path>

    private lateinit var binding: ImageViewerFragmentBinding

    private lateinit var systemUiHelper: SystemUiHelper

    private lateinit var adapter: ImageViewerAdapter

    // 白い熊 fork: grid mode — pinch in on an image to see its neighbours side by side.
    private lateinit var gridBackCallback: OnBackPressedCallback
    private var isGridMode = false
    private var isGridModeRestored = false
    private var gridInsets = Insets.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = savedInstanceState?.getState<State>()
        paths = (state?.paths ?: argsPaths).toMutableList()
        isGridModeRestored = state?.isGridMode ?: false

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        ImageViewerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (paths.isEmpty()) {
            // TODO: Show a toast.
            finish()
            return
        }

        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        // 白い熊 fork: yellow toolbar chrome (back arrow, title, menu icons).
        binding.toolbar.post {
            binding.toolbar.applySkChrome(
                SkThemeSlot.TOOLBAR_BACKGROUND, SkThemeSlot.TOOLBAR_TITLE,
                SkThemeSlot.TOOLBAR_SUBTITLE, SkThemeSlot.TOOLBAR_ICONS
            )
        }
        // Our app bar will draw the status bar background.
        activity.window.statusBarColor = Color.TRANSPARENT
        binding.appBarLayout.applySystemWindowInsetsToPadding(left = true, top = true, right = true)
        systemUiHelper = SystemUiHelper(
            activity, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY
        ) { visible: Boolean ->
            binding.appBarLayout.animate()
                .alpha(if (visible) 1f else 0f)
                .translationY(if (visible) 0f else -binding.appBarLayout.bottom.toFloat())
                .setDuration(mediumAnimTime.toLong())
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
        // This will set up window flags.
        systemUiHelper.show()
        adapter = ImageViewerAdapter(viewLifecycleOwner) { systemUiHelper.toggle() }.apply {
            replace(paths)
        }
        binding.viewPager.apply {
            // 1 is the default for the old androidx.viewpager.widget.ViewPager.
            offscreenPageLimit = 1
            adapter = this@ImageViewerFragment.adapter
            // ViewPager saves its position and will restore it later.
            setCurrentItem(args.position, false)
            setPageTransformer(DepthPageTransformer)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTitle()
                }
            })
        }
        setUpGrid()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        if (paths.isEmpty()) {
            // We did finish the activity in onActivityCreated(), however we will still be called
            // here before the activity is actually finished.
            return
        }

        updateTitle()
        if (isGridModeRestored) {
            isGridModeRestored = false
            showGrid()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(paths, isGridMode))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.image_viewer, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            R.id.action_share -> {
                share()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun confirmDelete() {
        ConfirmDeleteDialogFragment.show(currentPath, this)
    }

    override fun delete(path: Path) {
        try {
            path.delete()
        } catch (e: IOException) {
            e.printStackTrace()
            showToast(e.toString())
            return
        }
        paths.removeAll(listOf(path))
        if (paths.isEmpty()) {
            finish()
            return
        }
        adapter.replace(paths)
        // ViewPager only asynchronously sets current item to 0, which isn't a desirable behavior
        // for us and will make updateTitle() crash for index out of bounds.
        if (binding.viewPager.currentItem > paths.lastIndex) {
            binding.viewPager.currentItem = paths.lastIndex
        }
        if (isGridMode) {
            binding.wall.replacePaths(paths)
            binding.wall.highlightedIndex = binding.viewPager.currentItem
        }
        updateTitle()
        // Work around blank screen due to ViewPager2.PageTransformer not being called (and thus the
        // next item keeps its 0 alpha) when we have offscreenPageLimit = 1.
        binding.viewPager.doOnPreDraw { binding.viewPager.requestTransform() }
    }

    private fun updateTitle() {
        val path = currentPath
        requireActivity().title = path.fileName.toString()
        val size = paths.size
        binding.toolbar.subtitle = if (isGridMode) {
            getString(R.string.sk_image_viewer_grid_subtitle, size)
        } else if (size > 1) {
            getString(
                R.string.image_viewer_subtitle_format, binding.viewPager.currentItem + 1, size
            )
        } else {
            null
        }
    }

    private fun share() {
        val path = currentPath
        val intent = path.fileProviderUri.createSendImageIntent()
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }

    private val currentPath: Path
        get() = paths[binding.viewPager.currentItem]

    private fun setUpGrid() {
        binding.wall.onImageTap = { showPager(it) }
        binding.wall.onZoomPastImage = { showPager(it) }
        binding.pagerContainer.canPinchToGrid = { isCurrentImageAtMinimumScale() }
        binding.pagerContainer.onPinchToGrid = { showGrid() }
        binding.pagerContainer.onPinchContinue = { scaleFactor, focusX, focusY ->
            if (isGridMode) {
                binding.wall.applyExternalScale(scaleFactor, focusX, focusY)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.wall) { _, insets ->
            gridInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updateWallInsets()
            insets
        }
        binding.appBarLayout.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _,
            oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                updateWallInsets()
            }
        }
        gridBackCallback = requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, false
        ) { showPager(binding.wall.highlightedIndex) }
    }

    private fun isCurrentImageAtMinimumScale(): Boolean {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return false
        val holder = recyclerView.findViewHolderForAdapterPosition(binding.viewPager.currentItem)
            as? ImageViewerAdapter.ViewHolder ?: return false
        val itemBinding = holder.binding
        return when {
            itemBinding.image.isVisible ->
                itemBinding.image.scale <= itemBinding.image.minimumScale + 0.01f
            itemBinding.largeImage.isVisible ->
                !itemBinding.largeImage.isReady ||
                    itemBinding.largeImage.scale <= itemBinding.largeImage.minScale * 1.01f
            // Still loading or failed: nothing to zoom, so a pinch can only mean the grid.
            else -> true
        }
    }

    private fun showGrid() {
        if (isGridMode) {
            return
        }
        val position = binding.viewPager.currentItem
        isGridMode = true
        gridBackCallback.isEnabled = true
        systemUiHelper.show()
        binding.wall.highlightedIndex = position
        // Start from the screen's shape so the pinch can carry on at once; the real image shape
        // follows as soon as its bounds are read.
        val screenAspect = binding.root.height.toFloat() / binding.root.width
        binding.wall.setPaths(paths, screenAspect.takeIf { it > 0 } ?: 1f)
        updateWallInsets()
        binding.wall.showImage(position, ENTER_GRID_FRACTION)
        binding.wall.beginExternalScale()
        binding.pagerContainer.isInvisible = true
        binding.wall.isVisible = true
        updateTitle()
        val path = paths[position]
        viewLifecycleOwner.lifecycleScope.launch {
            val aspect = withContext(Dispatchers.IO) { path.readImageAspect() } ?: return@launch
            if (!isGridMode || binding.wall.highlightedIndex != position) {
                return@launch
            }
            binding.wall.setPaths(paths, aspect)
            binding.wall.showImage(position, ENTER_GRID_FRACTION)
        }
    }

    private fun showPager(position: Int) {
        if (!isGridMode) {
            return
        }
        isGridMode = false
        gridBackCallback.isEnabled = false
        binding.wall.isInvisible = true
        binding.pagerContainer.isVisible = true
        if (position in paths.indices) {
            binding.viewPager.setCurrentItem(position, false)
        }
        updateTitle()
        // Same DepthPageTransformer blank-page workaround as in delete().
        binding.viewPager.doOnPreDraw { binding.viewPager.requestTransform() }
    }

    private fun updateWallInsets() {
        binding.wall.setContentInsets(
            gridInsets.left, binding.appBarLayout.height, gridInsets.right, gridInsets.bottom
        )
    }

    private fun Path.readImageAspect(): Float? =
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            newInputStream().use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outHeight.toFloat() / options.outWidth
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    companion object {
        // Entering from the single-image view, start just below one-image size so the
        // neighbours already peek in at the edges.
        private const val ENTER_GRID_FRACTION = 0.75f
    }

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    @Parcelize
    private class State(
        val paths: @WriteWith<ParcelableListParceler> List<Path>,
        val isGridMode: Boolean
    ) : ParcelableState
}
