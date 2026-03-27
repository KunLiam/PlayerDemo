package com.player.demo

import androidx.annotation.StringRes

/**
 * 内置三类测试音源：与 assets 内文件名一一对应。
 * 外部工具通过 Intent 附加 [PlayerIntentContract.EXTRA_TRACK] 指定 [intentValue]。
 */
enum class TestAudioTrack(
    /** adb / Intent 里使用的稳定标识（大小写不敏感） */
    val intentValue: String,
    val assetFileName: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    WAKEUP(
        intentValue = "wakeup",
        assetFileName = "shape_of_you.mp3",
        titleRes = R.string.test_track_wakeup_title,
        subtitleRes = R.string.test_track_wakeup_subtitle,
    ),
    AIRTIGHT(
        intentValue = "airtight",
        assetFileName = "sweep_speech_48k.wav",
        titleRes = R.string.test_track_airtight_title,
        subtitleRes = R.string.test_track_airtight_subtitle,
    ),
    VIBRATION(
        intentValue = "vibration",
        assetFileName = "80-1KHz-20S(-3dB).wav",
        titleRes = R.string.test_track_vibration_title,
        subtitleRes = R.string.test_track_vibration_subtitle,
    ),
    ;

    companion object {
        fun fromIntentValue(raw: String?): TestAudioTrack? {
            if (raw.isNullOrBlank()) return null
            val key = raw.trim().lowercase()
            return entries.firstOrNull { it.intentValue == key }
        }
    }
}

object PlayerIntentContract {
    /** String 类型：wakeup | airtight | vibration */
    const val EXTRA_TRACK = "com.player.demo.EXTRA_TRACK"
}
