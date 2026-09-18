package io.github.ruiquanqiao.alarmsets.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import java.io.File

/**
 * Plays a [RingtoneRef].
 *
 * Uses `USAGE_ALARM`, which routes to the alarm stream. That is what makes an
 * alarm audible when the phone is on silent - an alarm the user cannot hear is
 * not an alarm - while still respecting the alarm volume they chose.
 */
class AlarmTonePlayer(
    private val context: Context,
    private val store: ImportedRingtoneStore = ImportedRingtoneStore(context),
) {

    private var player: MediaPlayer? = null
    private var rampHandler: Handler? = null
    private var rampRunnable: Runnable? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    /**
     * @param loop true while an alarm is actually firing, false for previews.
     * @param fadeInMillis ramps the volume up from silence. A tone that starts
     *        at full volume in a dark room is unpleasant; 0 disables it.
     */
    fun play(
        ref: RingtoneRef,
        volumePercent: Int,
        loop: Boolean,
        fadeInMillis: Long = 0L,
    ): Result<Unit> {
        stop()
        if (ref is RingtoneRef.Silent) return Result.success(Unit)

        return runCatching {
            val target = (volumePercent.coerceIn(0, 100)) / 100f
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = loop
                applySource(this, ref)
                prepare()
            }

            player = mediaPlayer
            if (fadeInMillis > 0L) {
                mediaPlayer.setVolume(0f, 0f)
                startRamp(mediaPlayer, target, fadeInMillis)
            } else {
                mediaPlayer.setVolume(target, target)
            }
            mediaPlayer.start()
        }.onFailure { stop() }
    }

    fun stop() {
        rampRunnable?.let { rampHandler?.removeCallbacks(it) }
        rampRunnable = null
        rampHandler = null

        player?.let { mediaPlayer ->
            runCatching { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
            runCatching { mediaPlayer.reset() }
            runCatching { mediaPlayer.release() }
        }
        player = null
    }

    private fun applySource(mediaPlayer: MediaPlayer, ref: RingtoneRef) {
        when (ref) {
            is RingtoneRef.Bundled -> {
                val descriptor = context.assets.openFd("$ASSET_DIR/${ref.key}.ogg")
                descriptor.use {
                    mediaPlayer.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }

            is RingtoneRef.Imported -> {
                val entry = store.findBlocking(ref.uri)
                    ?: error("imported ringtone ${ref.uri} is gone")
                val file = File(context.filesDir, "imported_ringtones/${entry.fileName}")
                check(file.exists()) { "imported ringtone file missing: ${file.name}" }
                mediaPlayer.setDataSource(file.absolutePath)
            }

            RingtoneRef.SystemDefault -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: error("no system alarm tone available")
                mediaPlayer.setDataSource(context, uri)
            }

            RingtoneRef.Silent -> error("silent should not reach the player")
        }
    }

    private fun startRamp(mediaPlayer: MediaPlayer, target: Float, durationMillis: Long) {
        val handler = Handler(Looper.getMainLooper())
        rampHandler = handler
        val startedAt = System.currentTimeMillis()

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startedAt
                val progress = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                // Perceived loudness is roughly logarithmic, so a linear ramp
                // sounds like it jumps at the start. Squaring it sounds even.
                val volume = target * progress * progress
                runCatching { mediaPlayer.setVolume(volume, volume) }
                if (progress < 1f) handler.postDelayed(this, RAMP_STEP_MILLIS)
            }
        }
        rampRunnable = runnable
        handler.post(runnable)
    }

    /** True when the alarm stream is muted, so the UI can warn about it. */
    fun isAlarmStreamMuted(): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return manager.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    private companion object {
        const val ASSET_DIR = "ringtones"
        const val RAMP_STEP_MILLIS = 80L
    }
}
