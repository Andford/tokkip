// Bienestar → Pantalla: Tokkip's doomscrolling indicator (his face goes from
// happy to dizzy), today's time on the phone and in scroll apps, unlocks and
// the longest session, by kind of app, your top apps, by hour (late night in
// red), the last week against your goal, what Tokkip noticed, and your
// settings: the daily scroll goal, the nudge after a long session, and which
// kinds count as scrolling. All read on the phone with "Acceso al uso".

package mx.loperstack.tokkip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.AppKind
import mx.loperstack.tokkip.data.ScreenNow
import mx.loperstack.tokkip.data.ScreenUsage
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.averageMood
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.hoursText
import mx.loperstack.tokkip.data.Night
import mx.loperstack.tokkip.data.minutes
import mx.loperstack.tokkip.data.screenNotes
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.wellness.DOOM_LEVELS
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")

/** The indicator's colours, calm to dizzy. */
val DOOM_COLORS = listOf(Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFFF4505E))

fun doomEye(level: Int): TokkipEngine.Eye? = runCatching { TokkipEngine.Eye.valueOf(DOOM_LEVELS[level.coerceIn(0, 3)].eye) }.getOrNull()

fun kindIcon(k: AppKind): ImageVector = when (k) {
    AppKind.SOCIAL -> Icons.Rounded.Groups
    AppKind.VIDEO -> Icons.Rounded.PlayCircle
    AppKind.GAMES -> Icons.Rounded.SportsEsports
    AppKind.NEWS -> Icons.Rounded.Newspaper
    AppKind.MESSAGES -> Icons.Rounded.Chat
    AppKind.MUSIC -> Icons.Rounded.MusicNote
    AppKind.PRODUCTIVITY -> Icons.Rounded.Work
    AppKind.MAPS -> Icons.Rounded.Map
    AppKind.OTHER -> Icons.Rounded.Apps
}

private fun kindColor(k: AppKind) = when (k) {
    AppKind.SOCIAL -> Color(0xFFF472B6)
    AppKind.VIDEO -> Color(0xFFF87171)
    AppKind.GAMES -> Color(0xFFA78BFA)
    AppKind.NEWS -> Color(0xFFFBBF24)
    AppKind.MESSAGES -> Color(0xFF34D399)
    AppKind.MUSIC -> Color(0xFF22D3EE)
    AppKind.PRODUCTIVITY -> Color(0xFF60A5FA)
    AppKind.MAPS -> Color(0xFF4ADE80)
    AppKind.OTHER -> Color(0xFFA5A9B1)
}

private fun mins(m: Int) = if (m < 60) "$m min" else hoursText(m)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KindsSettings(data: AppData) {
    val s = data.screen
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in listOf(AppKind.SOCIAL, AppKind.VIDEO, AppKind.GAMES, AppKind.NEWS, AppKind.MESSAGES)) {
            val on = k in s.scrollKinds
            Pill(k.label, on, kindIcon(k)) {
                val next = if (on) s.scrollKinds - k else s.scrollKinds + k
                if (next.isNotEmpty()) Store.setScreen(s.copy(scrollKinds = next))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeScreen(data: AppData, color: Color, actions: Actions) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ScreenUsage.granted(context)) }
    // Coming back from Ajustes: check again.
    LaunchedEffect(granted) { while (!granted) { delay(1500); granted = ScreenUsage.granted(context) } }
    // An 8-bit "on" when you open it.
    LaunchedEffect(Unit) { mx.loperstack.tokkip.character.Sounds.play("gb_on", 0.6f) }
    val reading by produceState<ScreenNow?>(ScreenUsage.last, granted, data.screen) {
        while (granted) {
            value = withContext(Dispatchers.IO) { ScreenUsage.refresh(context) }
            delay(60_000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Pantalla", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (!granted) {
            Panel(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Tokkip(Modifier.width(130.dp).height(108.dp), color = color, face = TokkipEngine.Eye.WIDE)
                }
                Text("¿Cuánto te atrapa el teléfono?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tokkip puede ver cuánto usas cada app y avisarte cuando llevas mucho scroll. Activa \"Acceso al uso\" para Tokkip: todo se queda en tu teléfono.",
                    fontSize = 14.sp, color = Ink.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ExpressiveButton("Activar acceso al uso", icon = Icons.Rounded.PhoneAndroid) { ScreenUsage.openSettings(context) }
                }
            }
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val r = reading
        // When the level changes while you look: a little jingle, up or down.
        var heard by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(r?.level) {
            val l = r?.level ?: return@LaunchedEffect
            val before = heard
            if (before != null && l != before) mx.loperstack.tokkip.character.Sounds.play(if (l > before) "gb_warn" else "gb_level")
            heard = l
        }
        val today = r?.today
        val level = r?.level ?: 0
        val lvl = DOOM_LEVELS[level]

        // Tokkip and the indicator.
        Panel(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).clip(CircleShape).background(DOOM_COLORS[level].copy(alpha = 0.16f)))
                Tokkip(
                    Modifier.width(150.dp).height(124.dp), color = color, face = doomEye(level),
                    mood = if (level >= 3) mx.loperstack.tokkip.character.Mood.SLEEPY else mx.loperstack.tokkip.character.Mood.IDLE,
                    jump = actions.jump, interactive = true,
                )
            }
            Text("Tokkip ${lvl.name.lowercase(MX)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(lvl.line, fontSize = 14.sp, color = Ink.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in DOOM_LEVELS) {
                    val on = l.level == level
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(if (on) 12.dp else 8.dp).clip(CircleShape).background(if (l.level <= level) DOOM_COLORS[l.level] else Ink.Track))
                        Spacer(Modifier.height(4.dp))
                        Text(l.name, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Ink.Text else Ink.Dim2)
                    }
                }
            }
            if (today != null && today.sessionMin >= 5 && today.sessionApp != null) {
                Spacer(Modifier.height(10.dp))
                IconLabel(Icons.Rounded.Timelapse, "Ahora: ${today.sessionMin} min seguidos en ${ScreenUsage.label(context, today.sessionApp)}", DOOM_COLORS[level], Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (today == null) {
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Pantalla hoy", mins(today.totalMin), Ink.Text, Modifier.weight(1f))
            Stat("Scroll", "${mins(today.scrollMin)} / ${mins(data.screen.goal)}", if (today.scrollMin > data.screen.goal) DOOM_COLORS[3] else DOOM_COLORS[0], Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Desbloqueos", "${today.unlocks}", Ink.Text, Modifier.weight(1f), Icons.Rounded.LockOpen)
            Stat("Sesión más larga", mins(today.longestMin), Ink.Text, Modifier.weight(1f), Icons.Rounded.Timelapse)
        }

        // Tokkip noticed.
        val notes = remember(data.screenDays, data.sleep, data.logs, data.screen.goal) {
            val now = System.currentTimeMillis()
            screenNotes(
                data.screenDays, data.screenDays.firstOrNull { it.day == dayKey(now) }, data.screen.goal,
                data.sleep.map { Night(it.night, it.minutes, it.quality) }, data.logs.filter { it.averageMood > 0 }.associate { it.day to it.averageMood }, now,
            ).sortedByDescending { it.weight }.take(2)
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Label("TOKKIP NOTÓ", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                for (n in notes) {
                    Spacer(Modifier.height(10.dp))
                    Text(n.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                    Text(n.tip, fontSize = 13.sp, color = Ink.Dim, lineHeight = 18.sp)
                }
            }
        }

        // By kind.
        if (today.byKind.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Por tipo de app", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                val top = today.byKind.maxOf { it.second }.coerceAtLeast(1)
                for ((k, m) in today.byKind) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(Squircle(11.dp)).background(kindColor(k).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(kindIcon(k), null, tint = kindColor(k), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(k.label + if (k in data.screen.scrollKinds) " · scroll" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(mins(m), fontSize = 13.sp, color = Ink.Dim, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Ink.Track)) {
                                Box(Modifier.fillMaxWidth((m.toFloat() / top).coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(kindColor(k)))
                            }
                        }
                    }
                }
            }
        }

        // Top apps.
        if (today.apps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Tus apps de hoy", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                for (a in today.apps.take(8)) {
                    val icon = remember(a.pkg) { ScreenUsage.icon(context, a.pkg) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(34.dp).clip(Squircle(10.dp)))
                        else Box(Modifier.size(34.dp).clip(Squircle(10.dp)).background(Ink.Card))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Label(a.kind.label, kindColor(a.kind), 12.sp)
                        }
                        Text(mins(a.minutes), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // By hour.
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("A qué horas", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            val bar = Ink.VioletSoft
            val late = DOOM_COLORS[3]
            val track = Ink.Track
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                val step = size.width / 24
                for (h in 0 until 24) {
                    val v = today.hourly[h]
                    val hh = if (v == 0) 3f else (size.height - 2f) * v / 60f + 3f
                    drawRoundRect(if (v == 0) track else if (h >= 23 || h < 5) late else bar, Offset(h * step + step * 0.15f, size.height - hh), Size(step * 0.7f, hh), CornerRadius(step * 0.3f))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                for (l in listOf("0", "6", "12", "18", "23")) Text(l, fontSize = 10.sp, color = Ink.Dim2, modifier = Modifier.weight(1f))
            }
            if (today.lateMin > 0) Label("${mins(today.lateMin)} de scroll de madrugada (de 11 pm a 5 am)", late, 12.sp)
        }

        // The week.
        val now = System.currentTimeMillis()
        val week = (6 downTo 0).map { back ->
            val k = dayKey(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis)
            k to data.screenDays.firstOrNull { it.day == k }
        }
        if (week.count { it.second != null } >= 2) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("Tu semana", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                val total = Ink.Track
                val goodC = DOOM_COLORS[0]
                val badC = DOOM_COLORS[3]
                val line = Ink.Dim
                val goal = data.screen.goal
                Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                    val top = maxOf(week.maxOf { it.second?.totalMin ?: 0 }, goal).coerceAtLeast(1) * 1.1f
                    val step = size.width / 7
                    for ((i, wd) in week.withIndex()) {
                        val d = wd.second ?: continue
                        val ht = size.height * d.totalMin / top
                        val hs = size.height * d.scrollMin / top
                        drawRoundRect(total, Offset(i * step + step * 0.2f, size.height - ht), Size(step * 0.6f, ht), CornerRadius(10f))
                        drawRoundRect(if (d.scrollMin > goal) badC else goodC, Offset(i * step + step * 0.2f, size.height - hs), Size(step * 0.6f, hs), CornerRadius(10f))
                    }
                    val y = size.height - size.height * goal / top
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                Row(Modifier.fillMaxWidth()) {
                    for ((k, _) in week) Text(SimpleDateFormat("EEEEE", MX).format(Date(mx.loperstack.tokkip.data.dayStart(k))).uppercase(MX), fontSize = 11.sp, color = Ink.Dim2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
                Label("Color: scroll · gris: todo lo demás · línea: tu meta de scroll", Ink.Dim2, 11.sp)
            }
        }

        // Settings.
        SectionTitle("Tu meta de scroll al día")
        PillRow {
            for (m in listOf(30, 45, 60, 90, 120, 180)) Pill(mins(m), data.screen.goal == m) { Store.setScreen(data.screen.copy(goal = m)); Haptics.tick(context) }
        }
        SectionTitle("Avísame tras un rato seguido")
        PillRow {
            for (m in listOf(0, 10, 15, 20, 30, 45)) Pill(if (m == 0) "Nunca" else "$m min", data.screen.nudgeAfter == m) { Store.setScreen(data.screen.copy(nudgeAfter = m)); Haptics.tick(context) }
        }
        SectionTitle("Cuenta como scroll")
        KindsSettings(data)
        Spacer(Modifier.height(8.dp))
        Label("Tokkip revisa cada 10 minutos mientras usas el teléfono. Si quieres ver los detalles de Android, están en Bienestar digital.", Ink.Dim2, 12.sp)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Stat(title: String, value: String, color: Color, modifier: Modifier, icon: ImageVector? = null) {
    Column(modifier.clip(Squircle(18.dp)).background(Ink.Card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Ink.Dim2, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Label(title, Ink.Dim2, 12.sp, FontWeight.Bold)
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
    }
}
