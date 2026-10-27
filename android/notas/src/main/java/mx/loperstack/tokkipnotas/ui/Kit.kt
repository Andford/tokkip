// Small pieces every screen uses: panels, labels, a page's icon in its
// Expressive shape, the icon set, navigation and the app's settings.

package mx.loperstack.tokkipnotas.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.TokkipStyle
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Species
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.Store

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(Squircle(28.dp)).background(Ink.Card).padding(padding), content = content)
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.SemiBold, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, color = color, fontSize = size, fontWeight = weight, modifier = modifier, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Ink.VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))

/** Text and icons on a page's colour. */
val INK_ON = Color.Black.copy(alpha = 0.72f)

fun shapeOf(i: Int) = MShapes.ALL[Math.floorMod(i, MShapes.ALL.size)]

/** Every icon a page can have, by a stable key (Tokkip's Cuaderno keys included). */
val ICONS: List<Pair<String, ImageVector>> = listOf(
    "doc" to Icons.Rounded.Description, "board" to Icons.Rounded.ViewKanban, "table" to Icons.Rounded.TableChart, "inbox" to Icons.Rounded.Inbox,
    "idea" to Icons.Rounded.Lightbulb, "rocket" to Icons.Rounded.RocketLaunch, "art" to Icons.Rounded.Brush, "code" to Icons.Rounded.Code,
    "book" to Icons.Rounded.MenuBook, "study" to Icons.Rounded.School, "work" to Icons.Rounded.Work, "home" to Icons.Rounded.Home,
    "travel" to Icons.Rounded.Flight, "gym" to Icons.Rounded.FitnessCenter, "music" to Icons.Rounded.MusicNote, "photo" to Icons.Rounded.CameraAlt,
    "food" to Icons.Rounded.Restaurant, "shop" to Icons.Rounded.ShoppingCart, "money" to Icons.Rounded.Savings, "pet" to Icons.Rounded.Pets,
    "heart" to Icons.Rounded.Favorite, "spark" to Icons.Rounded.AutoAwesome, "people" to Icons.Rounded.Groups, "star" to Icons.Rounded.Star,
    "flag" to Icons.Rounded.Flag, "calendar" to Icons.Rounded.CalendarMonth, "check" to Icons.Rounded.TaskAlt, "leaf" to Icons.Rounded.Park,
    "game" to Icons.Rounded.SportsEsports, "movie" to Icons.Rounded.Movie, "car" to Icons.Rounded.DirectionsCar, "tools" to Icons.Rounded.Build,
)

fun iconOf(key: String): ImageVector = ICONS.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Description

/** A page's icon, in its colour and shape. */
@Composable
fun PageIcon(p: Page, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clip(PolygonShape(shapeOf(p.shape))).background(Color(p.color)))
        Icon(iconOf(p.icon), null, tint = INK_ON, modifier = Modifier.size(size * 0.52f))
    }
}

/** The time, refreshed every `periodMs`. */
@Composable
fun rememberNow(periodMs: Long = 60_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

fun greeting(now: Long): String = when (java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

// ── Where you are ─────────────────────────────────────────────────────────────

/**
 * The screens you went through, newest last: "p:<id>" for a page, "search",
 * "trash", "settings". Empty = Inicio. Kept for the whole session.
 */
object Nav {
    val stack = mutableStateListOf<String>()
    /** The folded/unfolded pages of the tree. */
    val open = mutableStateMapOf<String, Boolean>()
    val current get() = stack.lastOrNull() ?: "home"
    fun page(id: String) {
        Store.opened(id)
        Sounds.play("page", 0.35f)
        if (current != "p:$id") stack.add("p:$id")
    }
    fun go(route: String) { if (current != route) stack.add(route) }
    fun back(): Boolean = if (stack.isEmpty()) false else { stack.removeAt(stack.lastIndex); true }
    fun home() = stack.clear()
    /** A page was thrown away: take it off the way back. */
    fun forget(ids: Set<String>) { stack.removeAll { it.startsWith("p:") && it.removePrefix("p:") in ids } }
}

// ── Settings ──────────────────────────────────────────────────────────────────

object Prefs {
    var theme by mutableStateOf("pixel")
    var color by mutableLongStateOf(0xFFF5F6F8)
    var sounds by mutableStateOf(true)
    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
        val p = context.getSharedPreferences("notas", Context.MODE_PRIVATE)
        theme = mx.loperstack.tokkip.wellness.Legacy.theme(p.getString("theme", "pixel")) ?: "pixel"
        color = p.getLong("color", 0xFFF5F6F8)
        sounds = p.getBoolean("sounds", true)
        TokkipStyle.species = Species.of(p.getString("species", null))
        Sounds.enabled = sounds
    }

    private fun save() {
        ctx?.getSharedPreferences("notas", Context.MODE_PRIVATE)?.edit()
            ?.putString("theme", theme)?.putLong("color", color)?.putBoolean("sounds", sounds)?.putString("species", TokkipStyle.species.name)?.apply()
        Sounds.enabled = sounds
    }

    fun set(theme: String = this.theme, color: Long = this.color, sounds: Boolean = this.sounds, species: Species = TokkipStyle.species) {
        this.theme = theme; this.color = color; this.sounds = sounds; TokkipStyle.species = species
        save()
    }
}
