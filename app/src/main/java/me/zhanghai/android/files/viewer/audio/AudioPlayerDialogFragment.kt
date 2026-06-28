/*
 * 白い熊 fork: the built-in audio player UI, shown as a small floating mini-player
 * docked at the bottom over the file list (non-modal — the list stays visible and
 * usable behind it). Title + close, a seek bar, and current time · play/pause ·
 * duration. Playback lives in AudioPlayerViewModel so it survives rotation.
 */

package me.zhanghai.android.files.viewer.audio

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.SkAudioPlayerDialogBinding
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.applySkSlot
import me.zhanghai.android.files.skui.skColor
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.viewModels
import java.util.Locale

class AudioPlayerDialogFragment : DialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: SkAudioPlayerDialogBinding

    private val viewModel by viewModels { { AudioPlayerViewModel(args.path) } }

    private var isSeekBarTracking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        // Inflate against the activity so the 白い熊 theme overlay (yellow controls) applies.
        SkAudioPlayerDialogBinding.inflate(requireActivity().layoutInflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.titleText.text = args.path.fileName.toString()
        applySkUi()
        binding.closeButton.setOnClickListener { dismiss() }
        binding.playPauseButton.setOnClickListener { viewModel.togglePlayPause() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTimeText.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeekBarTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeekBarTracking = false
                viewModel.seekTo(seekBar.progress)
            }
        })

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            launch { viewModel.playbackState.collect { onPlaybackStateChanged(it) } }
            launch { viewModel.isPlaying.collect { onIsPlayingChanged(it) } }
            launch { viewModel.durationMs.collect { onDurationChanged(it) } }
            launch {
                while (true) {
                    if (!isSeekBarTracking) {
                        onPositionChanged(viewModel.currentPositionMs)
                    }
                    delay(POSITION_POLL_INTERVAL_MS)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // A small bottom-docked, non-modal box: no dim, and touches outside it pass
        // through to the file list (FLAG_NOT_TOUCH_MODAL) so browsing continues.
        val window = dialog?.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        dialog?.setCanceledOnTouchOutside(false)
    }

    // 白い熊 fork: apply the audio-player skui slots (colors + fonts) to the live views,
    // so the 白い熊 UI page can customize the mini-player like every other surface.
    private fun applySkUi() {
        val controls = skColor(SkThemeSlot.AUDIO_PLAYER_CONTROLS)
        val root = binding.root
        // Rebuild the rounded box with the configured fill + border, preserving padding
        // (setting a borderless drawable can otherwise reset it).
        val paddingStart = root.paddingStart
        val paddingTop = root.paddingTop
        val paddingEnd = root.paddingEnd
        val paddingBottom = root.paddingBottom
        root.background = GradientDrawable().apply {
            cornerRadius = 12 * resources.displayMetrics.density
            setColor(skColor(SkThemeSlot.AUDIO_PLAYER_BACKGROUND))
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), controls)
        }
        root.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)

        binding.titleText.applySkSlot(SkThemeSlot.AUDIO_PLAYER_TITLE)
        binding.currentTimeText.applySkSlot(SkThemeSlot.AUDIO_PLAYER_TIME)
        binding.durationText.applySkSlot(SkThemeSlot.AUDIO_PLAYER_TIME)

        val controlsTint = ColorStateList.valueOf(controls)
        binding.closeButton.imageTintList = controlsTint
        binding.playPauseButton.imageTintList = controlsTint
        binding.seekBar.progressTintList = controlsTint
        binding.seekBar.thumbTintList = controlsTint
    }

    private fun onPlaybackStateChanged(state: AudioPlayerViewModel.PlaybackState) {
        if (state == AudioPlayerViewModel.PlaybackState.ERROR) {
            showToast(R.string.sk_audio_player_error)
            dismiss()
        }
    }

    private fun onIsPlayingChanged(isPlaying: Boolean) {
        binding.playPauseButton.setImageResource(
            if (isPlaying) R.drawable.sk_pause_icon_24dp else R.drawable.sk_play_icon_24dp
        )
        binding.playPauseButton.contentDescription = getString(
            if (isPlaying) R.string.sk_audio_player_pause else R.string.sk_audio_player_play
        )
    }

    private fun onDurationChanged(durationMs: Int) {
        binding.seekBar.max = durationMs
        binding.durationText.text = formatTime(durationMs)
    }

    private fun onPositionChanged(positionMs: Int) {
        binding.seekBar.progress = positionMs
        binding.currentTimeText.text = formatTime(positionMs)
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs

    companion object {
        private const val TAG = "AudioPlayerDialogFragment"
        private const val POSITION_POLL_INTERVAL_MS = 200L

        // Replaces any mini-player already showing, so only one track plays at a time.
        // Hosted on the activity so it floats over the list regardless of the active tab.
        fun show(path: Path, fragment: Fragment) {
            val fragmentManager = fragment.requireActivity().supportFragmentManager
            (fragmentManager.findFragmentByTag(TAG) as? AudioPlayerDialogFragment)
                ?.dismissAllowingStateLoss()
            AudioPlayerDialogFragment().putArgs(Args(path)).show(fragmentManager, TAG)
        }
    }
}
