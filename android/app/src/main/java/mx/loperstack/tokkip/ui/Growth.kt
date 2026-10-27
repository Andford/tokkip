// Inicio → "Tokkip y tú": his growth stage with the cariño left for the next
// one, and what he noticed in your routine today. Tapping it opens the whole
// path: the six stages and the cycle after them, this week's cariño by kind,
// how to give him cariño, and everything he noticed. When he reaches a new
// stage, a little celebration.

package mx.loperstack.tokkip.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.data.Care
import mx.loperstack.tokkip.data.Growth
import mx.loperstack.tokkip.data.STAGES
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.TokkipNote
import mx.loperstack.tokkip.data.growth
import mx.loperstack.tokkip.data.noteOfTheDay
import mx.loperstack.tokkip.data.routine
import mx.loperstack.tokkip.data.tokkipNotes
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle

/** The icon for a note's key (data/Insights.kt). */
fun noteIcon(key: String): ImageVector = when (key) {
    "write" -> Icons.Rounded.EditNote
    "night" -> Icons.Rounded.Bedtime
    "sun" -> Icons.Rounded.WbSunny
    "flame" -> Icons.Rounded.LocalFireDepartment
    "heart" -> Icons.Rounded.Favorite
    "air" -> Icons.Rounded.Air
    "thanks" -> Icons.Rounded.VolunteerActivism
    "work" -> Icons.Rounded.Work
    "family" -> Icons.Rounded.FamilyRestroom
    "friends" -> Icons.Rounded.Groups
    "sport" -> Icons.Rounded.DirectionsRun
    "study" -> Icons.Rounded.School
    "money" -> Icons.Rounded.Savings
    "leaf" -> Icons.Rounded.Park
    "music" -> Icons.Rounded.MusicNote
    "star" -> Icons.Rounded.Star
    "calendar" -> Icons.Rounded.CalendarMonth
    "wallet" -> Icons.Rounded.AccountBalanceWallet
    "piggy" -> Icons.Rounded.Savings
    "trend" -> Icons.Rounded.TrendingUp
    "party" -> Icons.Rounded.Celebration
    "coffee" -> Icons.Rounded.LocalCafe
    "card" -> Icons.Rounded.CreditCard
    "phone" -> Icons.Rounded.PhoneAndroid
    else -> Icons.Rounded.AutoAwesome
}

private fun careIcon(c: Care): ImageVector = when (c) {
    Care.RACHAS -> Icons.Rounded.LocalFireDepartment
    Care.DIARIO -> Icons.Rounded.EditNote
    Care.ANIMO -> Icons.Rounded.Favorite
    Care.RESPIRAR -> Icons.Rounded.Air
    Care.SUENO -> Icons.Rounded.Bedtime
    Care.FINANZAS -> Icons.Rounded.Savings
    Care.PANTALLA -> Icons.Rounded.PhoneAndroid
}

private fun careHow(c: Care): String = when (c) {
    Care.RACHAS -> "+3 por cada racha que marcas (hasta 9 al día)"
    Care.DIARIO -> "+4 si escribes, +2 si pasas de 40 palabras, +1 con foto"
    Care.ANIMO -> "+2 cuando registras cómo te sientes"
    Care.RESPIRAR -> "+3 por sesión (hasta 6 al día)"
    Care.SUENO -> "+1 al registrar tu noche, +2 si cumples tu meta"
    Care.FINANZAS -> "+2 el día que anotas tus gastos o ingresos"
    Care.PANTALLA -> "+3 cada día que te quedas dentro de tu meta de scroll"
}

/** The growth and the note of the day, recomputed when your history changes (and each hour). */
@Composable
fun rememberGrowth(now: Long): Pair<Growth, TokkipNote> {
    val d by Store.state.collectAsState()
    val hour = now / 3_600_000
    return remember(d.logs, d.habits, d.sessions, d.sleep, d.sleepGoal, d.money, d.budget, d.debts, d.screenDays, d.screen, hour) {
        val r = d.routine()
        growth(r, now) to noteOfTheDay(r, now)
    }
}

/** A ring that fills toward the next stage, with the level in the middle. */
@Composable
private fun StageRing(g: Growth, size: Int = 54) {
    val accent = Ink.VioletSoft
    val track = Ink.Track
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val w = this.size.minDimension * 0.11f
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(accent, -90f, 360f * g.progress.coerceAtLeast(0.02f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${g.stage.level}", fontSize = (size * 0.36f).sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text)
    }
}

/** Inicio's card: stage, cariño to go, and what Tokkip noticed. */
@Composable
fun GrowthCard(now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (g, note) = rememberGrowth(now)
    var open by remember { mutableStateOf(false) }
    Panel(modifier.fillMaxWidth().clickable { Haptics.click(context); open = true }, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StageRing(g)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label("TOKKIP Y TÚ", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                Text("Tokkip ${g.title}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Label(
                    if (g.today > 0) "Hoy: +${g.today} de cariño · faltan ${g.toNext} para ${g.nextName}"
                    else "Faltan ${g.toNext} de cariño para ${g.nextName}",
                    Ink.Dim2, 12.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Ver su camino", tint = Ink.Dim)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
        Spacer(Modifier.height(12.dp))
        NoteRow(note, big = true)
    }
    if (open) GrowthSheet(g, now) { open = false }
}

@Composable
private fun NoteRow(n: TokkipNote, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(if (big) 36.dp else 32.dp).clip(Squircle(12.dp)).background(Ink.VioletDeep),
            contentAlignment = Alignment.Center,
        ) { Icon(noteIcon(n.icon), contentDescription = null, tint = Ink.OnVioletDeep, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (big) Label("Tokkip notó", Ink.Dim2, 11.sp, FontWeight.Bold)
            Text(n.text, fontSize = if (big) 15.sp else 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(n.tip, fontSize = 13.sp, color = Ink.Dim, lineHeight = 18.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthSheet(g: Growth, now: Long, close: () -> Unit) {
    val d by Store.state.collectAsState()
    val notes = remember(g.points, now / 3_600_000) { tokkipNotes(d.routine(), now).take(6) }
    ModalBottomSheet(
        onDismissRequest = close, containerColor = Ink.CardHigh,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            ScreenTitle("Tu camino con Tokkip", size = 30.sp)
            Spacer(Modifier.height(4.dp))
            Label("Crece con el cariño que te das: tus rachas, tu diario, tu ánimo, respirar, dormir y cuidar tu dinero. Todo se calcula aquí, en tu teléfono.", Ink.Dim2, 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Tokkip(
                    Modifier.width(150.dp).height(124.dp).graphicsLayer { scaleX = g.scale; scaleY = g.scale },
                    color = Color(d.tokkipColor), interactive = true, greet = true,
                )
            }
            Text("Tokkip ${g.title}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(g.stage.blurb, fontSize = 13.sp, color = Ink.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("${g.points} de cariño en total · ${g.week} esta semana", fontSize = 13.sp, color = Ink.Dim2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            SectionTitle("Etapas")
            for (s in STAGES) {
                val reached = g.stage.level >= s.level
                val current = g.stage.level == s.level
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(if (reached) Ink.VioletSoft else Ink.Track),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            current -> Text("${s.level}", fontWeight = FontWeight.ExtraBold, color = Ink.OnViolet, fontSize = 14.sp)
                            reached -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Ink.OnViolet, modifier = Modifier.size(18.dp))
                            else -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = Ink.Dim2, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.name + if (current && g.loops > 0) " ★${g.loops}" else "", fontSize = 15.sp,
                            fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (reached) Ink.Text else Ink.Dim2,
                        )
                        if (current) {
                            Spacer(Modifier.height(4.dp))
                            Bar(g.progress)
                            Spacer(Modifier.height(2.dp))
                            Label("${g.into} de ${g.span} · faltan ${g.toNext} para ${g.nextName}", Ink.Dim2, 11.sp)
                        }
                    }
                    Text(if (s.from == 0) "" else "${s.from}", fontSize = 12.sp, color = Ink.Dim2)
                }
            }
            Label("Después de Leyenda el ciclo sigue: cada 600 de cariño, una vuelta más (★).", Ink.Dim2, 12.sp)
            var party by remember { mutableStateOf(false) }
            TextButton(onClick = { party = true }) { Text("Celebrar otra vez") }
            if (party) LevelUpCelebration(g, Color(d.tokkipColor), first = false) { party = false }

            SectionTitle("Cómo darle cariño")
            val top = (g.byCare.values.maxOrNull() ?: 0).coerceAtLeast(1)
            for (c in Care.entries) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(careIcon(c), contentDescription = null, tint = Ink.VioletSoft, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${g.byCare[c] ?: 0}", fontSize = 13.sp, color = Ink.Dim, fontWeight = FontWeight.Bold)
                        }
                        Label(careHow(c), Ink.Dim2, 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Bar((g.byCare[c] ?: 0).toFloat() / top)
                    }
                }
            }

            SectionTitle("Lo que Tokkip notó")
            if (notes.isEmpty()) Label("Aún no hay suficiente para notar algo. Escribe en tu diario y marca tus rachas unos días.", Ink.Dim2, 13.sp)
            for (n in notes) {
                NoteRow(n)
                Spacer(Modifier.height(14.dp))
            }
            Label("Tokkip lo saca de tu diario, ánimo, rachas, sueño, respiraciones y gastos, aquí mismo. Nada sale de tu teléfono.", Ink.Dim2, 11.sp)
        }
    }
}

@Composable
private fun Bar(frac: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Ink.Track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(Ink.VioletSoft))
    }
}

/** When Tokkip reaches a new stage (or a new turn of the cycle): a little party, once. */
@Composable
fun GrowthCelebration(g: Growth) {
    val context = LocalContext.current
    val key = g.stage.level * 100 + g.loops
    var show by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(key) {
        val seen = Store.growthSeen()
        if (key > seen) show = seen
    }
    val seen = show ?: return
    LevelUpCelebration(g, Color(Store.state.value.tokkipColor), first = seen < 0) { Store.setGrowthSeen(key); show = null }
}
