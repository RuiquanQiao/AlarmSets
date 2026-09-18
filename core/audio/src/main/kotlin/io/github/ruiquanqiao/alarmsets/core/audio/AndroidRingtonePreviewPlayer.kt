package io.github.ruiquanqiao.alarmsets.core.audio

import android.content.Context
import io.github.ruiquanqiao.alarmsets.core.domain.RingtonePreviewPlayer
import io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays a single tone once while the user is browsing the picker.
 *
 * Deliberately a separate instance from the one the ringing service uses, so
 * auditioning a tone can never interfere with an alarm that is going off.
 */
class AndroidRingtonePreviewPlayer(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RingtonePreviewPlayer {

    private val player = AlarmTonePlayer(context.applicationContext)

    override suspend fun play(ref: RingtoneRef, volumePercent: Int) {
        withContext(io) {
            player.play(ref, volumePercent, loop = false, fadeInMillis = 0L)
        }
    }

    override fun stop() = player.stop()
}
