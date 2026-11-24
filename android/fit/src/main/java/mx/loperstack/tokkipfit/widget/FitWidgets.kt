// Tokkip Fit on the home screen (Glance):
//   Tus anillos — Tokkip inside today's three rings; wider, the numbers, the
//     streak and the week's challenge. Tapping opens Hoy.
//   Entrena — Tokkip and one tap to each kind of workout (En casa, Gym, Cardio,
//     Respira), or straight into the last routine you did.
// They draw Hoy as Fit last read it, and try Health Connect themselves when it
// lets them read in the background.

package mx.loperstack.tokkipfit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkip.fit.routine
import mx.loperstack.tokkip.fit.weekChallenge
import mx.loperstack.tokkipfit.MainActivity
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs
import mx.loperstack.tokkipfit.data.Snapshot
import mx.loperstack.tokkipfit.data.fmt

object FitWidgets {
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        for ((w, r) in listOf(RingsWidget() to RingsWidgetReceiver::class.java, TrainWidget() to TrainWidgetReceiver::class.java)) {
            runCatching {
                val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, r))
                val glance = GlanceAppWidgetManager(app)
                for (id in ids) w.update(app, glance.getGlanceIdBy(id))
            }
        }
    }
}

private fun open(context: Context, what: String) = actionStartActivity(
    Intent(context, MainActivity::class.java).setData(Uri.parse("tokkipfit://$what")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

private fun Context.px(dp: Dp) = (dp.value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

/** The three rings with Tokkip inside, as one picture. */
private fun ringsBitmap(context: Context, s: Snapshot, side: Int): Bitmap {
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val steps = context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
    val minutes = if (night) 0xFFB9F35A.toInt() else 0xFF4F8A10.toInt()
    val kcal = if (night) 0xFFFF7A6B.toInt() else 0xFFD93A2B.toInt()
    val track = if (night) 0x26FFFFFF else 0x1A000000
    val w = side * 0.085f
    val gap = side * 0.025f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND }
    listOf(steps to s.stepsRing, minutes to s.minutesRing, kcal to s.kcalRing).forEachIndexed { i, (color, v) ->
        val inset = w / 2 + i * (w + gap)
        val r = RectF(inset, inset, side - inset, side - inset)
        p.color = track; c.drawArc(r, 0f, 360f, false, p)
        p.color = color; c.drawArc(r, -90f, 360f * v.coerceIn(0f, 1f), false, p)
    }
    val inner = (side - 2 * (3 * w + 2 * gap) - side * 0.04f).toInt().coerceAtLeast(1)
    val closed = listOf(s.stepsRing, s.minutesRing, s.kcalRing).count { it >= 1f }
    val him = tokkipBitmap(inner, (inner * 0.9f).toInt(), Color(Prefs.color), if (closed == 3) Mood.HAPPY else Mood.IDLE, if (closed == 3) TokkipEngine.Eye.STAR else null, wear = closed != 3, headroom = 0.3f)
    c.drawBitmap(him, (side - inner) / 2f, (side - inner * 0.9f) / 2f, null)
    return bmp
}

/** Hoy: the one Fit saved, or a fresh one from Health Connect when it lets us read in the background. */
private suspend fun snapshot(context: Context): Snapshot? {
    Prefs.init(context)
    val fresh = runCatching { HealthData.today(context, Prefs.goals) }.getOrNull()
    if (fresh != null) {
        val s = Snapshot.of(fresh)
        Prefs.saveToday(s.json())
        return s
    }
    return Snapshot.parse(Prefs.savedToday())
}

@Composable
private fun Card(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    GlanceTheme {
        Box(GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).then(modifier).padding(12.dp)) { content() }
    }
}

// ── Tus anillos ──────────────────────────────────────────────────────────────

class RingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshot(context)
        val challenge = runCatching { weekChallenge(mx.loperstack.tokkip.fit.Effort(Prefs.workouts())) }.getOrNull()
        provideContent { RingsContent(context, s, challenge) }
    }
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingsWidget()
}

@Composable
private fun RingsContent(context: Context, s: Snapshot?, challenge: mx.loperstack.tokkip.fit.Challenge?) {
    val size = LocalSize.current
    val on = GlanceTheme.colors.onSurface
    val dim = GlanceTheme.colors.onSurfaceVariant
    Card(GlanceModifier.clickable(open(context, "today"))) {
        if (s == null) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(tokkipBitmap(200, 180, Color(Prefs.color), Mood.HAPPY)), null, GlanceModifier.size(70.dp, 62.dp))
                Text("Abre Tokkip Fit para ver tus anillos", style = TextStyle(color = on, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        } else {
        val wide = size.width >= 230.dp
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
            val side = (if (wide) size.height - 24.dp else minOf(size.width, size.height) - 24.dp).coerceIn(80.dp, 200.dp)
            Image(ImageProvider(ringsBitmap(context, s, context.px(side))), "Tus anillos", GlanceModifier.size(side))
            if (wide) {
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Line("Pasos", "${fmt(s.steps)} / ${fmt(s.goalSteps)}", on, dim)
                    Line("Minutos", "${s.minutes} / ${s.goalMinutes}", on, dim)
                    Line("Calorías", "${fmt(s.kcal)} / ${fmt(s.goalKcal)}", on, dim)
                    if (size.height >= 150.dp) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(if (s.streak > 0) "Racha: ${s.streak} ${if (s.streak == 1) "día" else "días"}" else "Sin racha todavía", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        if (challenge != null) Text("Semana: ${challenge.title}", style = TextStyle(color = dim, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun Line(label: String, value: String, on: androidx.glance.unit.ColorProvider, dim: androidx.glance.unit.ColorProvider) {
    Column(GlanceModifier.padding(bottom = 3.dp)) {
        Text(label, style = TextStyle(color = dim, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        Text(value, style = TextStyle(color = on, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

// ── Entrena ──────────────────────────────────────────────────────────────────

class TrainWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Prefs.init(context)
        val last = Prefs.workouts().firstOrNull { routine(it.routine) != null }?.let { routine(it.routine) }
        provideContent { TrainContent(context, last) }
    }
}

class TrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrainWidget()
}

@Composable
private fun TrainContent(context: Context, last: mx.loperstack.tokkip.fit.Routine?) {
    val size = LocalSize.current
    Card {
        Column(GlanceModifier.fillMaxSize()) {
            if (size.height >= 150.dp) {
                Row(GlanceModifier.fillMaxWidth().clickable(open(context, if (last != null) "routine/${last.id}" else "home")), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(tokkipBitmap(180, 160, Color(Prefs.color), Mood.HAPPY)), null, GlanceModifier.size(52.dp, 46.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("¿Entrenamos?", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        Text(if (last != null) "Otra vez: ${last.name} · ${last.minutes} min" else "Elige y Tokkip va contigo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, "En casa", "home", GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer)
                Spacer(GlanceModifier.width(6.dp))
                Tile(context, "Gym", "gym", GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer)
                Spacer(GlanceModifier.width(6.dp))
                Tile(context, "Cardio", "cardio", GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer)
                Spacer(GlanceModifier.width(6.dp))
                Tile(context, "Respira", "breathe", GlanceTheme.colors.surfaceVariant, GlanceTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.Tile(context: Context, label: String, what: String, bg: androidx.glance.unit.ColorProvider, fg: androidx.glance.unit.ColorProvider) {
    Box(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(20.dp).background(bg).clickable(open(context, what)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}
