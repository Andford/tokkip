// Inicio, your way: which sections show and in what order (Personalizar
// Inicio, at the bottom of Inicio). Two more sections live here, for Inicio
// only: today's streaks to tick, and Pantalla's indicator.

package mx.loperstack.tokkip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.doneOn
import mx.loperstack.tokkip.data.habitIconKey
import mx.loperstack.tokkip.data.scheduledOn
import mx.loperstack.tokkip.data.streak
import mx.loperstack.tokkip.data.toggle
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.wellness.DOOM_LEVELS
import java.util.Calendar

/** Inicio's sections: id, name, icon and a word on what it shows. In their default order. */
data class HomeSection(val id: String, val label: String, val icon: ImageVector, val about: String)

val HOME_SECTIONS = listOf(
    HomeSection("tokkip", "Tokkip y lo siguiente", Icons.Rounded.Favorite, "Tokkip y tu próximo evento"),
    HomeSection("music", "Lo que suena", Icons.Rounded.MusicNote, "El mini reproductor, cuando hay música"),
    HomeSection("growth", "Tokkip y tú", Icons.Rounded.AutoAwesome, "Su nivel y lo que notó"),
    HomeSection("streaks", "Rachas de hoy", Icons.Rounded.LocalFireDepartment, "Marca tus hábitos desde aquí"),
    HomeSection("money", "Dinero", Icons.Rounded.AccountBalanceWallet, "Lo que te queda de la semana"),
    HomeSection("screen", "Pantalla", Icons.Rounded.PhoneAndroid, "Tu scroll de hoy y la cara de Tokkip"),
    HomeSection("agenda", "Agenda", Icons.Rounded.CalendarMonth, "Hoy y mañana"),
    HomeSection("shortcuts", "Enfoque y Respirar", Icons.Rounded.Timer, "Dos atajos"),
    HomeSection("tasks", "Tareas", Icons.Rounded.TaskAlt, "Tus pendientes y uno nuevo"),
)

/** The sections to show, in your order (new ones join at their default place). */
fun homeSections(layout: List<String>, hidden: Set<String>): List<String> {
    val known = HOME_SECTIONS.map { it.id }
    val ordered = layout.filter { it in known }.toMutableList()
    for ((i, id) in known.withIndex()) if (id !in ordered) ordered.add(minOf(i, ordered.size), id)
    return ordered.filter { it !in hidden }
}

/** Personalizar Inicio: move sections up or down, show or hide them. */
@Composable
fun HomeCustomizer() {
    val context = LocalContext.current
    val d by Store.state.collectAsState()
    val known = HOME_SECTIONS.map { it.id }
    val order = remember(d.homeLayout) {
        val o = d.homeLayout.filter { it in known }.toMutableList()
        for ((i, id) in known.withIndex()) if (id !in o) o.add(minOf(i, o.size), id)
        o.toList()
    }
    fun save(o: List<String>, h: Set<String>) { Store.setHome(o, h); Haptics.tick(context) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        ScreenTitle("Tu Inicio", size = 30.sp)
        Spacer(Modifier.height(4.dp))
        Label("Ordénalo y deja solo lo que te sirve. Lo que ocultes sigue en su sección.", Ink.Dim2, 12.sp)
        Spacer(Modifier.height(12.dp))
        for ((i, id) in order.withIndex()) {
            val sec = HOME_SECTIONS.first { it.id == id }
            val on = id !in d.homeHidden
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Squircle(18.dp)).background(Ink.Card).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeIcon(sec.icon, shape = MShapes.ALL[i % MShapes.ALL.size], size = 38.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(sec.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ink.Text else Ink.Dim2)
                    Label(sec.about, Ink.Dim2, 12.sp)
                }
                Column {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp, "Subir", tint = if (i > 0) Ink.Text else Ink.Track,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable(enabled = i > 0) {
                            save(order.toMutableList().apply { add(i - 1, removeAt(i)) }, d.homeHidden)
                        },
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown, "Bajar", tint = if (i < order.lastIndex) Ink.Text else Ink.Track,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable(enabled = i < order.lastIndex) {
                            save(order.toMutableList().apply { add(i + 1, removeAt(i)) }, d.homeHidden)
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = on, onCheckedChange = { save(order, if (it) d.homeHidden - id else d.homeHidden + id) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Volver al orden de siempre", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.clip(Squircle(14.dp)).clickable { save(emptyList(), emptySet()) }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Today's streaks: tap one to mark it, like in Rachas. Hidden with no streaks due today. */
@Composable
fun StreaksHomeCard(now: Long, open: () -> Unit) {
    val context = LocalContext.current
    val d by Store.state.collectAsState()
    val cal = Calendar.getInstance()
    val today = d.habits.filter { it.scheduledOn(cal) }
    if (today.isEmpty()) return
    val done = today.count { it.doneOn(now) }
    val best = d.habits.maxOfOrNull { it.streak(now) } ?: 0
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = open), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("RACHAS DE HOY", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                Text(if (done == today.size) "¡Todo listo hoy!" else "$done de ${today.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (best > 0) IconLabel(Icons.Rounded.LocalFireDepartment, if (best == 1) "1 día" else "$best días", Color(0xFFF97316))
            Icon(Icons.Rounded.ChevronRight, "Ver Rachas", tint = Ink.Dim)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in today.take(6)) {
                val on = h.doneOn(now)
                Box(
                    Modifier.size(44.dp).clip(PolygonShape(if (on) MShapes.Cookie9 else MShapes.Circle))
                        .background(if (on) Color(h.color) else Ink.CardHigh)
                        .clickable {
                            Store.setHabits(Store.state.value.habits.map { if (it.id == h.id) it.toggle(System.currentTimeMillis()) else it })
                            if (!on) { Sounds.play("task_done"); Haptics.success(context) } else Haptics.tick(context)
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(MIcon.habitIcon(habitIconKey(h.emoji)), h.name, tint = if (on) Color.Black.copy(alpha = 0.75f) else Color(h.color), modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

/** Pantalla on Inicio: Tokkip's face for today's scroll. Hidden without a reading today. */
@Composable
fun ScreenHomeCard(now: Long, open: () -> Unit) {
    val d by Store.state.collectAsState()
    val today = d.screenDays.firstOrNull { it.day == dayKey(now) } ?: return
    val level = if (now - d.doomAt < 60 * 60_000L) d.doom else 0
    val lvl = DOOM_LEVELS[level]
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth().clickable(onClick = open), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(DOOM_COLORS[level].copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                mx.loperstack.tokkip.character.Tokkip(Modifier.size(52.dp, 44.dp), color = Color(d.tokkipColor), face = doomEye(level))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label("PANTALLA · TOKKIP ${lvl.name.uppercase()}", DOOM_COLORS[level], 11.sp, FontWeight.Bold)
                Text("${today.scrollMin} min de scroll de ${d.screen.goal}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Label("${today.totalMin} min de pantalla hoy", Ink.Dim2, 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, "Ver Pantalla", tint = Ink.Dim)
        }
    }
}
