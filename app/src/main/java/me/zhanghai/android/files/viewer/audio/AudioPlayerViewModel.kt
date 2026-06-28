/*
 * 白い熊 fork: a minimal built-in audio player for voice recordings and other audio
 * files, shown as a floating mini-player (see AudioPlayerDialogFragment). The
 * MediaPlayer lives in the ViewModel so playback survives configuration changes.
 */

package me.zhanghai.android.files.viewer.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.fileProviderUri

class AudioPlayerViewModel(file: Path) : ViewModel() {
    enum class PlaybackState { LOADING, READY, ERROR }

    private val _playbackState = MutableStateFlow(PlaybackState.LOADING)
    val playbackState = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    // Duration in milliseconds; 0 until the player is prepared.
    private val _durationMs = MutableStateFlow(0)
    val durationMs = _durationMs.asStateFlow()

    private var isPrepared = false
    private var isReleased = false

    private val player = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        setOnPreparedListener {
            isPrepared = true
            _durationMs.value = it.duration.coerceAtLeast(0)
            _playbackState.value = PlaybackState.READY
            // Auto-play once prepared — tapping a recording should just start playing.
            it.start()
            _isPlaying.value = true
        }
        setOnCompletionListener {
            // Rewind to the start so the play button replays from the beginning.
            it.seekTo(0)
            _isPlaying.value = false
        }
        setOnErrorListener { _, _, _ ->
            _playbackState.value = PlaybackState.ERROR
            _isPlaying.value = false
            true
        }
    }

    init {
        try {
            player.setDataSource(application, file.fileProviderUri)
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            _playbackState.value = PlaybackState.ERROR
        }
    }

    // Current playback position in milliseconds, safe to poll from the UI.
    val currentPositionMs: Int
        get() = if (isPrepared && !isReleased) player.currentPosition else 0

    fun togglePlayPause() {
        if (!isPrepared || isReleased) {
            return
        }
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(positionMs: Int) {
        if (!isPrepared || isReleased) {
            return
        }
        player.seekTo(positionMs.coerceIn(0, _durationMs.value))
    }

    override fun onCleared() {
        super.onCleared()
        isReleased = true
        player.release()
    }
}
