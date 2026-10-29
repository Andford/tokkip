// The alarm's own screen, over the lock screen: a sunrise, the time, Tokkip
// stretching awake, and two ways out — put it off, or turn it off. With
// "Apagar respirando" on, turning it off takes three breaths with Tokkip
// (the tune goes quiet meanwhile). After that: "¡Buenos días!", and it closes.

package mx.loperstack.tokkip.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.wellness.BreathPhase
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.WakeAlarm
import mx.loperstack.tokkip.wellness.breathFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Dawn1 = Color(0xFF1E2A5A)
private val Dawn2 = Color(0xFF8A5A9E)
private val Dawn3 = Color(0xFFF2A07B)
private val Sun = Color(0xFFFFD58A)
private val OnDawn = Color(0xFFFFF7EC)
private val OnDawnDim = Color(0xFFF3DCCB)

/** Three breaths of 4 s in and 4 s out. */
private const val BREATHS = 3
private val WAKE_BREATH = Breathing(startedAt = null, minutes = 1, inhale = 4.0, exhale = 4.0)

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        Store.init(this)
        Store.applyLook()
        setContent { TokkipTheme(Store.state.value.theme) { AlarmFace(onClose = { finish() }) } }
    }
}

@Composable
private fun AlarmFace(onClose: () -> Unit) {
    val context = LocalContext.current
    val ringing by WakeAlarm.ringing.collectAsState()
    // Once turned off: a good morning, then the screen goes away.
    var morning by remember { mutableStateOf(false) }
    var breathingSince by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var jump by remember { mutableIntStateOf(0) }
    BackHandler(enabled = ringing != null && !morning) {}

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (breathingSince > 0) 50 else 1000)
        }
    }
    // He stretches now and then while it rings.
    LaunchedEffect(ringing?.since) {
        while (ringing != null && !morning) { delay(2600); jump++ }
    }
    // Rang out, or put off from the notification: nothing left to show.
    LaunchedEffect(ringing, morning) { if (ringing == null && !morning) onClose() }
    LaunchedEffect(morning) { if (morning) { delay(2200); onClose() } }

    val r = ringing
    val breath = if (breathingSince > 0) WAKE_BREATH.copy(startedAt = breathingSince) else null
    val frame = breath?.let { breathFrame(it, now) }
    val breathsDone = breath?.let { ((now - breathingSince) / 1000.0 / it.cycle).toInt() } ?: 0
    LaunchedEffect(breathsDone) {
        if (breathingSince > 0 && breathsDone >= BREATHS && !morning) {
            Haptics.success(context)
            WakeAlarm.dismiss(context)
            morning = true
        }
    }

    val name = Store.state.value.name
    val time = SimpleDateFormat("H:mm", Locale("es", "MX")).format(Date(now))
    val day = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX")).format(Date(now)).replaceFirstChar { it.uppercase() }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Dawn1, Dawn2, Dawn3))),
    ) {
        // The sun, coming up behind Tokkip.
        Box(
            Modifier.align(Alignment.Center).padding(top = 40.dp).size(240.dp)
                .background(Brush.radialGradient(listOf(Sun.copy(alpha = 0.55f), Sun.copy(alpha = 0f)))),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(time, fontSize = 84.sp, fontWeight = FontWeight.ExtraBold, color = OnDawn)
            Text(day, fontSize = 17.sp, color = OnDawnDim)
            if (r != null && r.label.isNotBlank() && !morning) {
                Text(r.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnDawn, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            Tokkip(
                Modifier.size(220.dp),
                mood = when {
                    morning -> Mood.HAPPY
                    breath != null -> Mood.BREATHING
                    else -> Mood.SLEEPY
                },
                breathing = breath,
                jump = jump,
                greet = false,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    morning -> if (name.isBlank()) "¡Buenos días!" else "¡Buenos días, $name!"
                    frame != null -> when (frame.phase) {
                        BreathPhase.IN, BreathPhase.HOLD_IN -> "Inhala…"
                        else -> "Exhala…"
                    } + "   ${(breathsDone + 1).coerceAtMost(BREATHS)} de $BREATHS"
                    r?.breathe == true -> "Respira conmigo tres veces para apagarla"
                    else -> "Es hora de despertar"
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnDawn, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            if (r != null && !morning && breath == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExpressiveButton(
                        "Posponer ${r.snoozeMin} min", Modifier.weight(1f), container = Color(0x33FFFFFF), content = OnDawn, big = true,
                    ) {
                        Haptics.click(context)
                        WakeAlarm.snooze(context)
                    }
                    ExpressiveButton(
                        if (r.breathe) "Respirar" else "Apagar", Modifier.weight(1f), container = OnDawn, content = Dawn1, big = true,
                    ) {
                        Haptics.click(context)
                        if (r.breathe) {
                            WakeAlarm.quiet(context)
                            breathingSince = System.currentTimeMillis()
                        } else {
                            WakeAlarm.dismiss(context)
                            morning = true
                        }
                    }
                }
            }
        }
    }
}
