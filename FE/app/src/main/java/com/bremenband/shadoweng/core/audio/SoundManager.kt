package com.bremenband.shadoweng.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.bremenband.shadoweng.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bgmPlayer: MediaPlayer? = null
    fun isBgmPlaying(): Boolean = bgmPlayer?.isPlaying == true

    // ── BGM ─────────────────────────────────────
    fun playBgmHome() = startBgm(R.raw.bgm_game_home)

    fun stopBgm() {
        bgmPlayer?.stop()
        bgmPlayer?.release()
        bgmPlayer = null
    }

    private fun startBgm(resId: Int) {
        stopBgm()
        bgmPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(0.4f, 0.4f)  // BGM은 작게
            start()
        }
    }

    // ── 효과음 ───────────────────────────────────
    fun playButtonClick() = playSfx(R.raw.sfx_button_click)
    fun playError()       = playSfx(R.raw.sfx_error)
    fun playGameLevel()   = playSfx(R.raw.sfx_game_level)
    fun playHeartFail()   = playSfx(R.raw.sfx_heart_fail)
    fun playGameFail() = playSfx(R.raw.sfx_game_fail)
    fun playHeartGain()   = playSfx(R.raw.sfx_heart_success)
    fun playScoreShow()   = playSfx(R.raw.sfx_score_show)

    private fun playSfx(resId: Int) {
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                context.resources.openRawResourceFd(resId).use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                isLooping = false
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        }
    }
}