package com.player.demo

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * 主界面：内置 Shape of You 音频，循环播放。
 * 支持通过 Intent action "com.player.demo.PLAY" 被第三方/ADB 调起并自动开始播放。
 */
class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    /** 由第三方/ADB 通过 PLAY 调起时，准备完成后自动开始播放 */
    private var startWhenReady = false
    /** REPLAY 调起时若尚未准备好，准备完成后从头播放 */
    private var replayWhenReady = false
    private var isPrepared = false

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnReplay: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    /** 用户正在拖动进度条时不根据播放进度更新 seekBar */
    private var isUserSeeking = false
    /** 拖动进度条前是否在播放，用于松手后恢复播放 */
    private var wasPlayingBeforeSeek = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReplay = findViewById(R.id.btnReplay)
        seekBar = findViewById(R.id.seekBar)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)

        initPlayer()
        setupPlayPauseButton()
        setupReplayButton()
        setupSeekBar()
        setupFocusOrder()
        handleLaunchIntent(intent)

        progressRunnable = object : Runnable {
            override fun run() {
                updateProgress()
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleLaunchIntent(it) }
    }

    /** 处理第三方/ADB 发来的 Intent：PLAY=播放，PAUSE=暂停，RESUME=继续，REPLAY=从头播 */
    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            "com.player.demo.PLAY" -> {
                startWhenReady = true
                val mp = mediaPlayer ?: return
                if (mp.isPlaying) {
                    startWhenReady = false
                    return
                }
                if (isPrepared) {
                    mp.start()
                    handler.post(progressRunnable ?: return)
                    updatePlayPauseIcon(true)
                    startWhenReady = false
                }
            }
            "com.player.demo.REPLAY" -> {
                timeCurrent.text = "0:00"
                seekBar.progress = 0
                val mp = mediaPlayer ?: return
                if (!isPrepared) {
                    replayWhenReady = true
                    return
                }
                mp.seekTo(0)
                if (!mp.isPlaying) {
                    mp.start()
                    handler.post(progressRunnable ?: return)
                    updatePlayPauseIcon(true)
                }
            }
            "com.player.demo.PAUSE" -> {
                val mp = mediaPlayer ?: return
                if (mp.isPlaying) {
                    mp.pause()
                    progressRunnable?.let { handler.removeCallbacks(it) }
                    updatePlayPauseIcon(false)
                }
            }
            "com.player.demo.RESUME" -> {
                val mp = mediaPlayer ?: return
                if (isPrepared && !mp.isPlaying) {
                    mp.start()
                    handler.post(progressRunnable ?: return)
                    updatePlayPauseIcon(true)
                }
            }
        }
    }

    private fun initPlayer() {
        releasePlayer()
        mediaPlayer = try {
            val afd = assets.openFd("shape_of_you.mp3")
            val player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setOnPreparedListener {
                    isPrepared = true
                    val durationMs = it.duration
                    seekBar.max = 100
                    timeTotal.text = formatTime(durationMs)
                    when {
                        startWhenReady -> {
                            it.start()
                            handler.post(progressRunnable!!)
                            updatePlayPauseIcon(true)
                            startWhenReady = false
                        }
                        replayWhenReady -> {
                            it.seekTo(0)
                            it.start()
                            handler.post(progressRunnable!!)
                            updatePlayPauseIcon(true)
                            replayWhenReady = false
                        }
                        else -> updatePlayPauseIcon(false)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    Toast.makeText(this@MainActivity, "播放出错", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            player
        } catch (e: Exception) {
            android.util.Log.e(TAG, "initPlayer failed", e)
            Toast.makeText(this, "无法加载音频文件", Toast.LENGTH_LONG).show()
            timeTotal.text = "0:00"
            null
        }
    }

    private fun setupPlayPauseButton() {
        btnPlayPause.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                handler.removeCallbacks(progressRunnable!!)
                updatePlayPauseIcon(false)
            } else {
                startPlayback()
            }
        }
    }

    private fun setupReplayButton() {
        btnReplay.setOnClickListener {
            mediaPlayer?.seekTo(0)
            timeCurrent.text = "0:00"
            seekBar.progress = 0
            if (!(mediaPlayer?.isPlaying == true)) {
                startPlayback()
            }
        }
    }

    /** 设置焦点顺序，便于 D-pad/遥控上下移动时焦点与光标正确切换 */
    private fun setupFocusOrder() {
        seekBar.setNextFocusDownId(R.id.btnReplay)
        seekBar.setNextFocusUpId(R.id.btnPlayPause)
        btnReplay.setNextFocusUpId(R.id.seekBar)
        btnReplay.setNextFocusDownId(R.id.btnPlayPause)
        btnPlayPause.setNextFocusUpId(R.id.btnReplay)
        btnPlayPause.setNextFocusDownId(R.id.seekBar)
        btnPlayPause.requestFocus()
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                wasPlayingBeforeSeek = mediaPlayer?.isPlaying == true
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mp = mediaPlayer ?: return
                    val duration = mp.duration
                    if (duration > 0) {
                        val position = (progress * duration / 100).coerceIn(0, duration)
                        timeCurrent.text = formatTime(position)
                    }
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val mp = mediaPlayer ?: return
                val duration = mp.duration
                if (duration > 0) {
                    val position = (seekBar?.progress ?: 0) * duration / 100
                    mp.seekTo(position.coerceIn(0, duration))
                }
                // 拖动前若在播放，松手后继续播放
                if (wasPlayingBeforeSeek) {
                    mp.start()
                    handler.post(progressRunnable ?: return)
                    updatePlayPauseIcon(true)
                }
            }
        })
    }

    private fun startPlayback() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            mp.start()
            handler.post(progressRunnable ?: return)
            updatePlayPauseIcon(true)
        }
    }

    private fun updateProgress() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) return
        if (isUserSeeking) return
        val current = mp.currentPosition
        val duration = mp.duration
        timeCurrent.text = formatTime(current)
        if (duration > 0) {
            seekBar.progress = (current * 100 / duration).coerceIn(0, 100)
        }
    }

    private fun updatePlayPauseIcon(playing: Boolean) {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000) % 60
        val min = (ms / 1000) / 60
        return String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }

    private fun releasePlayer() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "releasePlayer", e)
        }
        mediaPlayer = null
        isPrepared = false
        replayWhenReady = false
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlayerDemo"
    }
}
