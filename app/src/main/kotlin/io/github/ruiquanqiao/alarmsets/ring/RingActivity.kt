package io.github.ruiquanqiao.alarmsets.ring

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ruiquanqiao.alarmsets.AlarmSetsApplication
import io.github.ruiquanqiao.alarmsets.R
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmIntents
import io.github.ruiquanqiao.alarmsets.core.alarm.AlarmRingService
import io.github.ruiquanqiao.alarmsets.core.designsystem.AlarmSetsTheme
import io.github.ruiquanqiao.alarmsets.core.model.Alarm
import io.github.ruiquanqiao.alarmsets.ui.formatted

/**
 * The full-screen UI shown while an alarm is going off.
 *
 * This activity does not own the alarm. Sound, vibration and the auto-silence
 * timeout all live in [AlarmRingService]; this is only a remote control for it.
 * That separation is what lets the alarm keep ringing if this window is killed.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()

        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val container = (application as AlarmSetsApplication).container

        setContent {
            AlarmSetsTheme {
                var alarm by remember { mutableStateOf<Alarm?>(null) }
                var setName by remember { mutableStateOf("") }

                LaunchedEffect(alarmId) {
                    alarm = container.repository.getAlarm(alarmId)
                    setName = alarm?.let { container.repository.getSet(it.setId)?.name }.orEmpty()
                }

                RingScreen(
                    alarm = alarm,
                    setName = setName,
                    onDismiss = {
                        sendToService(AlarmIntents.ACTION_DISMISS, alarmId)
                        finish()
                    },
                    onSnooze = {
                        sendToService(AlarmIntents.ACTION_SNOOZE, alarmId)
                        finish()
                    },
                )
            }
        }
    }

    private fun sendToService(action: String, alarmId: Long) {
        val intent = Intent(this, AlarmRingService::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        runCatching { ContextCompat.startForegroundService(this, intent) }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    /**
     * The back gesture must not silence an alarm. Dismissing is an explicit
     * choice, made with the button.
     */
    @Deprecated("Intentional: back is ignored while an alarm is ringing")
    override fun onBackPressed() {
        // no-op
    }

    companion object {
        fun intent(context: Context, alarmId: Long, setId: Long): Intent =
            Intent(context, RingActivity::class.java).apply {
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmIntents.EXTRA_SET_ID, setId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
    }
}

@Composable
private fun RingScreen(
    alarm: Alarm?,
    setName: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                alarm?.time?.formatted(context).orEmpty(),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                alarm?.label?.ifBlank { setName } ?: setName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (setName.isNotBlank() && alarm?.label?.isNotBlank() == true) {
                Text(
                    setName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(64.dp))

            if (alarm?.snooze?.enabled == true) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Text(
                        stringResource(R.string.ring_snooze),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text(
                    stringResource(R.string.ring_dismiss),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
