// Tokkip Notas on the home screen (Glance):
//   Nota rápida — Tokkip and three buttons: a note, a page to write by hand, search.
//   Mis páginas — your favourites, then the pages you opened last; one tap opens it.
//   Una página — the page you choose, as it is, with its steps to tick right there.
// They redraw whenever the notebook changes (Store.index), at most every half second.

package mx.loperstack.tokkipnotas.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkipnotas.MainActivity
import mx.loperstack.tokkipnotas.R
import mx.loperstack.tokkipnotas.data.BlockKind
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.Sync
import mx.loperstack.tokkipnotas.data.blocksOf
import mx.loperstack.tokkipnotas.data.isInkBody
import mx.loperstack.tokkipnotas.data.toggleStep
import mx.loperstack.tokkipnotas.ui.Prefs
import mx.loperstack.tokkipnotas.ui.iconOf

/** Keeps the widgets in step with the notebook. Safe to call from anywhere, as often as you like. */
object NotasWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var watching = false

    /** Opens the notebook (once) for a widget or its button. */
    fun ready(context: Context) {
        Store.init(context)
        Sync.init(context)
        Prefs.init(context)
        watch(context)
    }

    @OptIn(FlowPreview::class)
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        scope.launch {
            Store.index.drop(1).debounce(500).distinctUntilChanged().collect { refresh(app) }
        }
    }

    suspend fun refresh(app: Context) {
        update(app, QuickNoteWidget(), QuickNoteWidgetReceiver::class.java)
        update(app, MyPagesWidget(), MyPagesWidgetReceiver::class.java)
        update(app, PinnedPageWidget(), PinnedPageWidgetReceiver::class.java)
    }

    private suspend fun update(app: Context, widget: GlanceAppWidget, receiver: Class<*>) {
        runCatching {
            val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, receiver))
            if (ids.isEmpty()) return
            val glance = GlanceAppWidgetManager(app)
            for (id in ids) runCatching { widget.update(app, glance.getGlanceIdBy(id)) }
        }
    }

    // ── Which page each "Una página" shows ──
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun pinned(context: Context, widgetId: Int): String? = prefs(context).getString("pin_$widgetId", null)
    fun pin(context: Context, widgetId: Int, pageId: String) = prefs(context).edit().putString("pin_$widgetId", pageId).apply()
    fun unpin(context: Context, widgetId: Int) = prefs(context).edit().remove("pin_$widgetId").apply()
}

// ── What a tap opens (a distinct data URI each, so their PendingIntents stay apart) ──

private fun open(context: Context, what: String) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("tokkipnotas://$what"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun openPage(context: Context, id: String) = open(context, "page/$id")

private fun choosePage(context: Context, widgetId: Int) =
    actionStartActivity(
        Intent(context, PinPageActivity::class.java)
            .setData(Uri.parse("tokkipnotas://pin/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

// ── Pieces ──

private fun Context.px(dp: Dp) = (dp.value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

private fun tokkipColor() = Color(Prefs.color)

@Composable
private fun NotasCard(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    GlanceTheme {
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp).then(modifier).padding(14.dp),
        ) { content() }
    }
}

/** Tokkip with a soft bob and a blink: a ViewFlipper the launcher animates on its own. */
private fun tokkipMoving(context: Context, w: Int, h: Int): RemoteViews {
    val flip = RemoteViews(context.packageName, R.layout.widget_flip_bob)
    val main = tokkipBitmap(w, h, tokkipColor(), Mood.IDLE, null, wear = true, headroom = 0.32f)
    val blink = tokkipBitmap(w, h, tokkipColor(), Mood.IDLE, TokkipEngine.Eye.CLOSED, wear = true, headroom = 0.32f)
    for (b in listOf(main, main, main, blink)) {
        val frame = RemoteViews(context.packageName, R.layout.widget_frame)
        frame.setImageViewBitmap(R.id.frame, b)
        flip.addView(R.id.flip, frame)
    }
    return flip
}

private fun tokkipStill(w: Int, h: Int, mood: Mood = Mood.HAPPY) =
    ImageProvider(tokkipBitmap(w, h, tokkipColor(), mood, null, wear = true, headroom = 0.3f))

/** A Material icon drawn white, to be tinted with a theme colour. */
private val iconCache = HashMap<String, Bitmap>()
private fun icon(v: ImageVector, px: Int = 72): ImageProvider = ImageProvider(
    iconCache.getOrPut(v.name + px) {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        canvas.scale(px / v.viewportWidth, px / v.viewportHeight)
        fun walk(group: androidx.compose.ui.graphics.vector.VectorGroup) {
            for (node in group) when (node) {
                is androidx.compose.ui.graphics.vector.VectorGroup -> walk(node)
                is androidx.compose.ui.graphics.vector.VectorPath -> {
                    @Suppress("DEPRECATION")
                    val ap = androidx.compose.ui.graphics.vector.PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    ap.fillType = if (node.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd)
                        android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                    canvas.drawPath(ap, paint)
                }
            }
        }
        walk(v.root)
        bmp
    },
)

private fun Page.label() = title.ifBlank { if (kind == PageKind.INK) "Hoja a mano" else "Sin título" }

// ── Nota rápida ──────────────────────────────────────────────────────────────

class QuickNoteWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        NotasWidgets.ready(context)
        provideContent { QuickNoteContent(context) }
    }
}

class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}

@Composable
private fun QuickNoteContent(context: Context) {
    val size = LocalSize.current
    val wide = size.width >= 250.dp
    NotasCard {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                val w = (size.height - 28.dp).coerceIn(56.dp, 100.dp)
                Box(GlanceModifier.size(w, w).clickable(open(context, "home")), contentAlignment = Alignment.Center) {
                    AndroidRemoteViews(tokkipMoving(context, context.px(w).coerceAtMost(240), context.px(w).coerceAtMost(240)), GlanceModifier.size(w, w))
                }
                Spacer(GlanceModifier.width(10.dp))
            }
            QuickButton(context, Icons.Rounded.EditNote, "Nota", "new/doc", primary = true)
            Spacer(GlanceModifier.width(8.dp))
            QuickButton(context, Icons.Rounded.Draw, "Pluma", "new/ink")
            Spacer(GlanceModifier.width(8.dp))
            QuickButton(context, Icons.Rounded.Search, "Buscar", "search")
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.QuickButton(context: Context, v: ImageVector, label: String, what: String, primary: Boolean = false) {
    val bg = if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val size = LocalSize.current
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(22.dp).background(bg).clickable(open(context, what)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon(v), contentDescription = null, colorFilter = ColorFilter.tint(fg), modifier = GlanceModifier.size(26.dp))
        if (size.height >= 90.dp) {
            Spacer(GlanceModifier.height(4.dp))
            Text(label, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

// ── Mis páginas ──────────────────────────────────────────────────────────────

class MyPagesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        NotasWidgets.ready(context)
        provideContent {
            val ix by Store.index.collectAsState()
            val favorites = ix.favorites.take(12)
            val recent = ix.recent.filter { !it.favorite }.take(12)
            MyPagesContent(context, favorites, recent)
        }
    }
}

class MyPagesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyPagesWidget()
}

private sealed interface Row2 {
    data class Head(val text: String) : Row2
    data class Item(val page: Page) : Row2
}

@Composable
private fun MyPagesContent(context: Context, favorites: List<Page>, recent: List<Page>) {
    val rows = buildList {
        if (favorites.isNotEmpty()) { add(Row2.Head("Favoritas")); favorites.forEach { add(Row2.Item(it)) } }
        if (recent.isNotEmpty()) { add(Row2.Head("Recientes")); recent.forEach { add(Row2.Item(it)) } }
    }
    NotasCard {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(tokkipStill(84, 72), contentDescription = null, modifier = GlanceModifier.size(32.dp, 28.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "Mis páginas",
                    modifier = GlanceModifier.defaultWeight().clickable(open(context, "home")),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Box(
                    GlanceModifier.size(36.dp).cornerRadius(18.dp).background(GlanceTheme.colors.primary).clickable(open(context, "new/doc")),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(icon(Icons.Rounded.EditNote), contentDescription = "Nota nueva", colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary), modifier = GlanceModifier.size(20.dp))
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(
                    "Marca páginas con la estrella o abre algunas y van a salir aquí.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 6.dp).clickable(open(context, "home")),
                )
            } else {
                LazyColumn {
                    items(rows, itemId = { r -> when (r) { is Row2.Head -> r.text.hashCode().toLong(); is Row2.Item -> r.page.id.hashCode().toLong() * 31 } }) { r ->
                        when (r) {
                            is Row2.Head -> Text(
                                r.text,
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            is Row2.Item -> PageRow(context, r.page)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageRow(context: Context, p: Page) {
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).cornerRadius(14.dp).background(GlanceTheme.colors.surfaceVariant)
            .clickable(openPage(context, p.id)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The page's own icon in its own colour, as in the app's tree.
        Image(icon(iconOf(p.icon)), contentDescription = null, colorFilter = ColorFilter.tint(ColorProvider(Color(p.color))), modifier = GlanceModifier.size(20.dp))
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(p.label(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val sub = when {
                p.steps > 0 -> "${p.done} de ${p.steps} pasos"
                p.preview.isNotBlank() -> p.preview
                else -> null
            }
            if (sub != null) Text(sub, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

// ── Una página ───────────────────────────────────────────────────────────────

private val PageIdKey = ActionParameters.Key<String>("page")
private val LineKey = ActionParameters.Key<Int>("line")

class PinnedPageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        NotasWidgets.ready(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val ix by Store.index.collectAsState()
            val pageId = NotasWidgets.pinned(context, widgetId)
            val page = ix.page(pageId)?.takeIf { it.trashed == 0L }
            val body = remember(page?.id, page?.updated) { page?.let { Store.body(it.id) } ?: "" }
            PinnedContent(context, widgetId, page, body)
        }
    }
}

class PinnedPageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedPageWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) NotasWidgets.unpin(context, id)
    }
}

/** Ticking a step on the widget: saved like in the app, and it travels with sync. */
class ToggleStep : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val pageId = parameters[PageIdKey] ?: return
        val line = parameters[LineKey] ?: return
        NotasWidgets.ready(context)
        val page = Store.page(pageId) ?: return
        val body = Store.body(pageId)
        val next = toggleStep(body, line)
        if (next != body) Store.saveBody(pageId, page.title, next)
        PinnedPageWidget().update(context, glanceId)
    }
}

@Composable
private fun PinnedContent(context: Context, widgetId: Int, page: Page?, body: String) {
    NotasCard {
        if (page == null) {
            Column(
                GlanceModifier.fillMaxSize().clickable(choosePage(context, widgetId)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(tokkipStill(150, 128, Mood.IDLE), contentDescription = null, modifier = GlanceModifier.size(64.dp, 54.dp))
                Spacer(GlanceModifier.height(6.dp))
                Text("Toca para elegir una página", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
            return@NotasCard
        }
        val blocks = if (isInkBody(body)) emptyList() else blocksOf(body).withIndex().filter { (_, b) ->
            b.kind != BlockKind.PAGE && b.kind != BlockKind.DRAW && b.kind != BlockKind.DIVIDER && b.text.isNotBlank()
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(icon(iconOf(page.icon)), contentDescription = null, colorFilter = ColorFilter.tint(ColorProvider(Color(page.color))), modifier = GlanceModifier.size(20.dp))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    page.label(),
                    modifier = GlanceModifier.defaultWeight().clickable(openPage(context, page.id)),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (page.steps > 0) {
                    Text(
                        "${page.done}/${page.steps}",
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(start = 6.dp).clickable(choosePage(context, widgetId)),
                    )
                } else {
                    Text(
                        "Cambiar",
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(start = 6.dp).clickable(choosePage(context, widgetId)),
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            when {
                page.kind == PageKind.INK || isInkBody(body) -> Text(
                    "Una hoja escrita a mano. Tócala para abrirla.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(openPage(context, page.id)),
                )
                page.kind == PageKind.COLLECTION -> Text(
                    "Un tablero. Tócalo para abrirlo.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(openPage(context, page.id)),
                )
                blocks.isEmpty() -> Text(
                    "Todavía está vacía. Tócala para escribir.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(openPage(context, page.id)),
                )
                else -> LazyColumn {
                    items(blocks.take(60), itemId = { it.index.toLong() }) { (line, b) ->
                        BlockRow(context, page.id, line, b.kind, b.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(context: Context, pageId: String, line: Int, kind: BlockKind, text: String) {
    val on = GlanceTheme.colors.onSurface
    val dim = GlanceTheme.colors.onSurfaceVariant
    val tap = GlanceModifier.fillMaxWidth().clickable(openPage(context, pageId))
    when (kind) {
        BlockKind.TODO, BlockKind.DONE -> CheckBox(
            checked = kind == BlockKind.DONE,
            onCheckedChange = actionRunCallback<ToggleStep>(actionParametersOf(PageIdKey to pageId, LineKey to line)),
            text = text,
            style = TextStyle(
                color = if (kind == BlockKind.DONE) dim else on,
                fontSize = 14.sp,
                textDecoration = if (kind == BlockKind.DONE) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 2,
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        )
        BlockKind.H1 -> Text(text, style = TextStyle(color = on, fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = tap.padding(top = 4.dp), maxLines = 2)
        BlockKind.H2 -> Text(text, style = TextStyle(color = on, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = tap.padding(top = 3.dp), maxLines = 2)
        BlockKind.BULLET, BlockKind.NUMBER -> Text("•  $text", style = TextStyle(color = on, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = tap.padding(vertical = 1.dp), maxLines = 3)
        BlockKind.QUOTE, BlockKind.CALLOUT -> Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = tap.padding(vertical = 2.dp).cornerRadius(12.dp).background(GlanceTheme.colors.secondaryContainer).padding(8.dp),
            maxLines = 4,
        )
        else -> Text(text, style = TextStyle(color = on, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = tap.padding(vertical = 1.dp), maxLines = 4)
    }
}
