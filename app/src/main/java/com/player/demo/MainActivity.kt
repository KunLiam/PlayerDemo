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
 * 主界面：内置三类测试音源（assets），循环播放。
 * 第三方可通过 Intent Action 控制播放；通过 [PlayerIntentContract.EXTRA_TRACK] 选择音轨。
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
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var tabWakeup: TextView
    private lateinit var tabAirtight: TextView
    private lateinit var tabVibration: TextView
    /** 用户正在拖动进度条时不根据播放进度更新 seekBar */
    private var isUserSeeking = false
    /** 拖动进度条前是否在播放，用于松手后恢复播放 */
    private var wasPlayingBeforeSeek = false

    private var currentTrack: TestAudioTrack = TestAudioTrack.WAKEUP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReplay = findViewById(R.id.btnReplay)
        seekBar = findViewById(R.id.seekBar)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        tabWakeup = findViewById(R.id.tabWakeup)
        tabAirtight = findViewById(R.id.tabAirtight)
        tabVibration = findViewById(R.id.tabVibration)

        progressRunnable = object : Runnable {
            override fun run() {
                updateProgress()
                handler.postDelayed(this, 200)
            }
        }

        currentTrack = resolveTrackFromIntent(intent, isColdStart = true)
        updateTrackUi()

        initPlayer(currentTrack)
        setupPlayPauseButton()
        setupReplayButton()
        setupSeekBar()
        setupTrackTabs()
        setupFocusOrder()
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
        intent?.let { handleNewIntent(it) }
    }

    /**
     * 从 Intent 解析目标音轨。冷启动时非法 extra 会 Toast 并回退默认唤醒音轨。
     */
    private fun resolveTrackFromIntent(intent: Intent?, isColdStart: Boolean): TestAudioTrack {
        if (intent?.hasExtra(PlayerIntentContract.EXTRA_TRACK) != true) {
            return TestAudioTrack.WAKEUP
        }
        val raw = intent.getStringExtra(PlayerIntentContract.EXTRA_TRACK)
        val parsed = TestAudioTrack.fromIntentValue(raw)
        if (parsed == null && isColdStart && !raw.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.unknown_track_extra, raw), Toast.LENGTH_SHORT).show()
        }
        return parsed ?: TestAudioTrack.WAKEUP
    }

    /** 仅读取 extra；无 extra 返回 null（表示不切换音轨） */
    private fun readTrackExtra(intent: Intent?): TestAudioTrack? {
        if (intent?.hasExtra(PlayerIntentContract.EXTRA_TRACK) != true) return null
        val raw = intent.getStringExtra(PlayerIntentContract.EXTRA_TRACK)
        val parsed = TestAudioTrack.fromIntentValue(raw)
        if (parsed == null && !raw.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.unknown_track_extra, raw), Toast.LENGTH_SHORT).show()
        }
        return parsed
    }

    private fun updateTrackUi() {
        trackTitle.setText(currentTrack.titleRes)
        trackArtist.setText(currentTrack.subtitleRes)
        updateTrackTabSelection()
    }

    private fun updateTrackTabSelection() {
        tabWakeup.isSelected = currentTrack == TestAudioTrack.WAKEUP
        tabAirtight.isSelected = currentTrack == TestAudioTrack.AIRTIGHT
        tabVibration.isSelected = currentTrack == TestAudioTrack.VIBRATION
    }

    private fun setupTrackTabs() {
        tabWakeup.setOnClickListener { switchTrackFromUser(TestAudioTrack.WAKEUP) }
        tabAirtight.setOnClickListener { switchTrackFromUser(TestAudioTrack.AIRTIGHT) }
        tabVibration.setOnClickListener { switchTrackFromUser(TestAudioTrack.VIBRATION) }
    }

    /** 用户在界面切换音轨：若正在播则切轨后继续播，否则仅加载并保持暂停 */
    private fun switchTrackFromUser(track: TestAudioTrack) {
        if (track == currentTrack) return
        val wasPlaying = mediaPlayer?.isPlaying == true
        startWhenReady = wasPlaying
        replayWhenReady = false
        currentTrack = track
        updateTrackUi()
        timeCurrent.text = "0:00"
        seekBar.progress = 0
        releasePlayer()
        initPlayer(currentTrack)
    }

    private fun handleNewIntent(intent: Intent) {
        val extraTrack = readTrackExtra(intent)
        if (extraTrack != null && extraTrack != currentTrack) {
            currentTrack = extraTrack
            updateTrackUi()
            when (intent.action) {
                "com.player.demo.PLAY" -> {
                    startWhenReady = true
                    replayWhenReady = false
                }
                "com.player.demo.REPLAY" -> {
                    replayWhenReady = true
                    startWhenReady = false
                }
                else -> {
                    startWhenReady = false
                    replayWhenReady = false
                }
            }
            timeCurrent.text = "0:00"
            seekBar.progress = 0
            releasePlayer()
            initPlayer(currentTrack)
            return
        }
        handleLaunchIntent(intent)
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
                    applySeekBarPositionToPlayer()
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
                    applySeekBarPositionToPlayer()
                    mp.start()
                    handler.post(progressRunnable ?: return)
                    updatePlayPauseIcon(true)
                }
            }
        }
    }

    private fun initPlayer(track: TestAudioTrack) {
        releasePlayer()
        mediaPlayer = try {
            val afd = assets.openFd(track.assetFileName)
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
            android.util.Log.e(TAG, "initPlayer failed ${track.assetFileName}", e)
            Toast.makeText(this, "无法加载音频：${track.assetFileName}", Toast.LENGTH_LONG).show()
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
        tabWakeup.setNextFocusRightId(R.id.tabAirtight)
        tabWakeup.setNextFocusDownId(R.id.seekBar)
        tabAirtight.setNextFocusLeftId(R.id.tabWakeup)
        tabAirtight.setNextFocusRightId(R.id.tabVibration)
        tabAirtight.setNextFocusDownId(R.id.seekBar)
        tabVibration.setNextFocusLeftId(R.id.tabAirtight)
        tabVibration.setNextFocusDownId(R.id.seekBar)
        seekBar.setNextFocusDownId(R.id.btnReplay)
        seekBar.setNextFocusUpId(R.id.tabAirtight)
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

    /**
     * 将当前 SeekBar 进度同步到 MediaPlayer（解决：遥控/部分机型上松手未触发 seek，
     * 或 seek 尚未完成就 start，导致播放位置与进度条不一致）。
     */
    private fun applySeekBarPositionToPlayer() {
        val mp = mediaPlayer ?: return
        if (!isPrepared) return
        val duration = mp.duration
        if (duration <= 0) return
        val position = (seekBar.progress * duration / 100).coerceIn(0, duration)
        mp.seekTo(position)
    }

    private fun startPlayback() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            applySeekBarPositionToPlayer()
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
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlayerDemo"
    }
}
