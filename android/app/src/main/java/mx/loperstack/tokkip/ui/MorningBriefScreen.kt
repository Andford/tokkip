// The morning summary, on the alarm screen once it's off: the weather like
// the Pixel shows it (a sky-coloured card, the temperature big, the hours
// ahead), today's events and your pending tasks. Tokkip reads it aloud if
// that's on (data/MorningBrief.kt).

package mx.loperstack.tokkip.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.assistant.Voice
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.MorningBrief
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.Weather
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val OnSky = Color(0xFFFFFFFF)
private val OnSkyDim = Color(0xD9FFFFFF)

/** Today's events as the summary shows them: (title, "9:30" or null for all day). */
private fun todayEvents(context: Context): List<MorningBrief.Item> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val utc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(start.time)
    val hm = SimpleDateFormat("H:mm", Locale.US)
    return runCatching { Agenda.load(context, start.timeInMillis, end.timeInMillis) }.getOrDefault(emptyList())
        .filter { e ->
            // All-day events are stored at midnight UTC: compare their date, not the hour.
            if (e.allDay) utc.format(Date(e.begin)) <= today && utc.format(Date(e.end - 1)) >= today
            else e.begin < end.timeInMillis && e.end > start.timeInMillis
        }
        .sortedWith(compareBy({ !it.allDay }, { it.begin }))
        .map { MorningBrief.Item(it.title.ifBlank { "Evento" }, if (it.allDay) null else hm.format(Date(it.begin))) }
}

@Composable
fun MorningBriefScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val name = Store.state.value.name
    val tasks = remember { Store.state.value.tasks.filter { !it.done }.map { it.text } }
    var events by remember { mutableStateOf<List<MorningBrief.Item>?>(null) }
    var weather by remember { mutableStateOf(Weather.now) }

    LaunchedEffect(Unit) {
        events = withContext(Dispatchers.IO) { todayEvents(context) }
        // The weather was asked for when the alarm rang; give it a few seconds if it's still on its way.
        Weather.refresh(context)
        for (i in 0 until 8) {
            val w = Weather.now
            if (w != null && System.currentTimeMillis() - w.at < 60 * 60_000L) break
            delay(500)
        }
        weather = Weather.now?.takeIf { System.currentTimeMillis() - it.at < 3 * 3600_000L }
        if (MorningBrief.aloud(context)) Voice.say(context, MorningBrief.speech(name, weather, events.orEmpty(), tasks))
    }
    // It goes away by itself after a while, so the alarm screen never stays on.
    LaunchedEffect(Unit) { delay(3 * 60_000L); Voice.stop(); onDone() }

    val day = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX")).format(Date()).replaceFirstChar { it.uppercase() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(if (name.isBlank()) "¡Buenos días!" else "¡Buenos días, $name!", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = OnSky)
        Text(day, fontSize = 16.sp, color = OnSkyDim)
        Spacer(Modifier.height(18.dp))
        WeatherCard(weather)
        Spacer(Modifier.height(14.dp))
        BriefList(
            Icons.Rounded.Event, "Hoy en tu agenda",
            when {
                events == null -> listOf("…")
                events!!.isEmpty() -> listOf("Nada en la agenda: día libre")
                else -> events!!.map { if (it.time == null) "${it.title} · todo el día" else "${it.time}  ${it.title}" }
            },
        )
        Spacer(Modifier.height(12.dp))
        BriefList(
            Icons.Rounded.TaskAlt, "Pendientes",
            if (tasks.isEmpty()) listOf("Ningún pendiente") else tasks.take(6) + listOfNotNull(if (tasks.size > 6) "y ${tasks.size - 6} más" else null),
        )
        Spacer(Modifier.height(20.dp))
        ExpressiveButton("Empezar el día", Modifier.fillMaxWidth(), container = OnSky, content = Color(0xFF1E2A5A), big = true) {
            Voice.stop()
            onDone()
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The sky's colours for the card, like the Pixel's weather. */
private fun skyOf(code: Int, day: Boolean): List<Color> = when {
    !day -> listOf(Color(0xFF1B2550), Color(0xFF3A3F7A))
    code in 95..99 -> listOf(Color(0xFF3B3F55), Color(0xFF5F5C7E))
    code in 51..67 || code in 80..82 -> listOf(Color(0xFF41607E), Color(0xFF6C8BA6))
    code in 71..77 || code in 85..86 -> listOf(Color(0xFF6F8EB0), Color(0xFFB7CBE0))
    code >= 3 || code == 45 || code == 48 -> listOf(Color(0xFF5E7894), Color(0xFF93A9BF))
    else -> listOf(Color(0xFF2F7BD8), Color(0xFF7DB8F2))
}

private fun iconOf(code: Int, day: Boolean): ImageVector = when {
    code in 95..99 -> Icons.Rounded.Thunderstorm
    code in 71..77 || code in 85..86 -> Icons.Rounded.AcUnit
    code in 61..67 || code in 80..82 -> Icons.Rounded.Umbrella
    code in 51..57 -> Icons.Rounded.WaterDrop
    code == 45 || code == 48 -> Icons.Rounded.Dehaze
    code == 3 -> Icons.Rounded.Cloud
    code == 2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.Cloud
    else -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
}

private fun tintOf(code: Int, day: Boolean): Color = if (day && code <= 1) Color(0xFFFFD54F) else OnSky

@Composable
private fun WeatherCard(w: Weather.Now?) {
    Column(
        Modifier.fillMaxWidth().clip(Squircle(28.dp))
            .background(Brush.verticalGradient(skyOf(w?.code ?: 2, w?.day ?: true)))
            .padding(20.dp),
    ) {
        if (w == null) {
            Text("El clima no llegó", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSky)
            Text("Sin internet o sin permiso de ubicación (Sueño → Resumen matinal).", fontSize = 14.sp, color = OnSkyDim)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${w.temp}°", fontSize = 76.sp, fontWeight = FontWeight.Light, color = OnSky)
                Text(Weather.label(w.code), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OnSky)
                Text("Máx ${w.max}° · Mín ${w.min}°" + if (w.rain > 0) " · Lluvia ${w.rain}%" else "", fontSize = 14.sp, color = OnSkyDim)
            }
            Icon(iconOf(w.code, w.day), null, tint = tintOf(w.code, w.day), modifier = Modifier.size(84.dp))
        }
        if (w.hours.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                w.hours.take(12).forEachIndexed { i, h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Ahora" else "${h.hour}:00", fontSize = 13.sp, color = OnSkyDim)
                        Spacer(Modifier.height(6.dp))
                        Icon(iconOf(h.code, h.day), null, tint = tintOf(h.code, h.day), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("${h.temp}°", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OnSky)
                        if (h.rain >= 20) Text("${h.rain}%", fontSize = 11.sp, color = Color(0xFFB3E5FC))
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefList(icon: ImageVector, title: String, lines: List<String>) {
    Column(Modifier.fillMaxWidth().clip(Squircle(24.dp)).background(Color(0x26FFFFFF)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = OnSky, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnSky)
        }
        Spacer(Modifier.height(8.dp))
        for (l in lines) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(OnSkyDim))
                Spacer(Modifier.width(10.dp))
                Text(l, fontSize = 15.sp, color = OnSky, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
