// Boards and tables: the same cards, seen two ways. On the board, each column
// is a status (Por hacer · Haciendo · Hecho, or yours); hold a card and drag
// it to another column or place. Add a card right at the bottom of a column,
// no forms. The table shows every column (estado, fecha, etiquetas, texto,
// número, casilla); tap a cell to change it, tap a column's name to sort.
// A card opens as a page, with its values on top and its own notes below.

package mx.loperstack.tokkipnotas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.Index
import mx.loperstack.tokkipnotas.data.Lane
import mx.loperstack.tokkipnotas.data.Opt
import mx.loperstack.tokkipnotas.data.PALETTE
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.Prop
import mx.loperstack.tokkipnotas.data.PropType
import mx.loperstack.tokkipnotas.data.Schema
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.board
import mx.loperstack.tokkipnotas.data.dayKey
import mx.loperstack.tokkipnotas.data.display
import mx.loperstack.tokkipnotas.data.isDone
import mx.loperstack.tokkipnotas.data.moveCard
import mx.loperstack.tokkipnotas.data.multiOf
import mx.loperstack.tokkipnotas.data.newId
import mx.loperstack.tokkipnotas.data.rows
import mx.loperstack.tokkipnotas.data.shortDate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun Store.setSchema(c: Page, s: Schema) { Store.page(c.id)?.let { update(it.copy(schema = s), touch = false) } }

/** A card's new value; a card moving to the done column gets a cheer. */
private fun putValue(card: Page, collection: Page, prop: Prop, value: String?, cheer: () -> Unit = {}) {
    val now = Store.page(card.id) ?: return
    val props = if (value.isNullOrEmpty()) now.props - prop.id else now.props + (prop.id to value)
    Store.update(now.copy(props = props))
    val s = collection.schema
    if (s != null && prop.id == s.group?.id && s.isDone(value) && !s.isDone(now.props[prop.id])) {
        Sounds.play("task_done"); Hop.now(); cheer()
    }
}

@Composable
fun CollectionScreen(c: Page, ix: Index) {
    val context = LocalContext.current
    val s = c.schema ?: Schema()
    var title by remember(c.id) { mutableStateOf(TextFieldValue(c.title)) }
    var adding by remember { mutableStateOf(false) }
    var cheer by remember { mutableStateOf(0L) }
    LaunchedEffect(title.text) {
        if (title.text != c.title) { delay(500); Store.page(c.id)?.let { Store.update(it.copy(title = title.text.trim())) } }
    }
    LaunchedEffect(cheer) { if (cheer != 0L) { delay(1800); cheer = 0L } }
    val view = s.view
    Column(Modifier.fillMaxSize().imePadding()) {
        PageTop(c, ix)
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { PageHeader(c, title, onTitle = { title = it }) }
            Box(Modifier.padding(end = 18.dp)) {
                Tokkip(Modifier.size(56.dp, 48.dp), color = Color(Prefs.color), jump = Hop.count, face = if (cheer != 0L) TokkipEngine.Eye.STAR else null, dancing = cheer != 0L)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillRow(Modifier.weight(1f)) {
                Pill("Tablero", view == "board", Icons.Rounded.ViewKanban) { Store.setSchema(c, s.copy(view = "board")) }
                Pill("Tabla", view == "table", Icons.Rounded.TableChart) { Store.setSchema(c, s.copy(view = "table")) }
                Pill("Columna", false, Icons.Rounded.Add) { adding = true }
            }
        }
        if (cheer != 0L) Label("¡Uno menos! 🎉", Ink.GreenSoft, 14.sp, FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (view == "table") Table(c, s, ix) { cheer = System.currentTimeMillis() }
            else Board(c, s, ix) { cheer = System.currentTimeMillis() }
        }
    }
    if (adding) AddColumnSheet(c, s) { adding = false }
}

// ── Board ─────────────────────────────────────────────────────────────────────

@Composable
private fun Board(c: Page, s: Schema, ix: Index, cheer: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lanes = remember(ix, c.id) { ix.board(c) }
    val group = s.group
    val laneBounds = remember { mutableStateMapOf<String, Rect>() }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<Page?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var boxWidth by remember { mutableStateOf(0f) }
    var addingLane by remember { mutableStateOf(false) }
    var laneMenu by remember { mutableStateOf<Opt?>(null) }
    val scroll = rememberScrollState()

    // Near an edge while dragging, the board scrolls by itself.
    LaunchedEffect(dragging) {
        val edge = with(density) { 56.dp.toPx() }
        while (dragging != null) {
            val x = pointer.x - origin.x
            if (x < edge) scroll.scrollBy(-22f) else if (x > boxWidth - edge) scroll.scrollBy(22f)
            delay(16)
        }
    }
    fun drop() {
        val card = Store.page((dragging ?: return).id)
        dragging = null
        if (card == null) return
        // The board as it is now (this runs from a gesture started earlier).
        val cNow = Store.page(c.id) ?: return
        val sNow = cNow.schema ?: return
        val group = sNow.group ?: return
        val lanes = Store.index.value.board(cNow)
        val p = pointer
        val laneKey = laneBounds.entries.firstOrNull { p.x >= it.value.left && p.x <= it.value.right }?.key
            ?: laneBounds.entries.minByOrNull { kotlin.math.abs(it.value.center.x - p.x) }?.key ?: return
        val lane = lanes.firstOrNull { (it.opt?.id ?: "") == laneKey } ?: return
        val at = lane.cards.filter { it.id != card.id }.count { (cardBounds[it.id]?.center?.y ?: Float.MAX_VALUE) < p.y }
        val before = card.props[group.id]
        val moved = moveCard(card, group, lane, at)
        Store.update(moved)
        Haptics.tick(context)
        if (sNow.isDone(moved.props[group.id]) && !sNow.isDone(before)) { Sounds.play("task_done"); Haptics.success(context); Hop.now(); cheer() }
    }

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); boxWidth = it.size.width.toFloat() },
    ) {
        Row(Modifier.fillMaxSize().horizontalScroll(scroll).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (lane in lanes) {
                val key = lane.opt?.id ?: ""
                val color = lane.opt?.let { Color(it.color) } ?: Ink.Dim2
                val hot = dragging != null && laneBounds[key]?.let { pointer.x >= it.left && pointer.x <= it.right } == true
                Column(
                    Modifier.width(276.dp).fillMaxHeight().padding(bottom = 12.dp).clip(Squircle(24.dp))
                        .background(if (hot) color.copy(alpha = 0.16f) else Ink.Card)
                        .onGloballyPositioned { laneBounds[key] = it.boundsInRoot() },
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(PolygonShape(MShapes.Cookie9)).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(lane.opt?.name ?: "Sin estado", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Label("${lane.cards.size}", Ink.Dim2, 13.sp)
                        if (lane.opt != null) Box(Modifier.size(36.dp).clip(CircleShape).clickable { laneMenu = lane.opt }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MoreHoriz, "Columna", tint = Ink.Dim, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        for (card in lane.cards) {
                            val ghost = dragging?.id == card.id
                            CardView(
                                card, c, s,
                                Modifier.padding(vertical = 4.dp).alpha(if (ghost) 0.25f else 1f)
                                    .onGloballyPositioned { cardBounds[card.id] = it.boundsInRoot() }
                                    .pointerInput(card.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { off ->
                                                val top = cardBounds[card.id]?.topLeft ?: Offset.Zero
                                                grab = off; pointer = top + off; dragging = card
                                                Haptics.click(context)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val top = cardBounds[card.id]?.topLeft ?: Offset.Zero
                                                pointer = top + change.position
                                            },
                                            onDragEnd = { drop() },
                                            onDragCancel = { dragging = null },
                                        )
                                    },
                            ) { Nav.page(card.id) }
                        }
                    }
                    if (group != null || lane.opt == null) QuickAdd(if (lane.opt == null && group == null) "Nueva tarjeta…" else "Agregar…") { text ->
                        val props = if (group != null && lane.opt != null) mapOf(group.id to lane.opt.id) else emptyMap()
                        Store.newPage(c.id, title = text, props = props)
                        Sounds.play("pop"); Haptics.tick(context)
                    }
                }
            }
            if (group != null) {
                Box(
                    Modifier.width(150.dp).height(56.dp).clip(Squircle(20.dp)).border(2.dp, Ink.Hairline, Squircle(20.dp)).clickable { addingLane = true },
                    contentAlignment = Alignment.Center,
                ) { Label("+ Columna", Ink.Dim, 14.sp, FontWeight.Bold) }
            }
            Spacer(Modifier.width(4.dp))
        }
        // The card you're dragging, under your finger.
        dragging?.let { card ->
            val pos = pointer - origin - grab
            Box(
                Modifier.offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }.width(260.dp)
                    .graphicsLayer { rotationZ = 3f; scaleX = 1.04f; scaleY = 1.04f }.shadow(12.dp, Squircle(18.dp)),
            ) { CardView(card, c, s, Modifier) {} }
        }
    }
    if (addingLane && group != null) NameDialog("Nueva columna", "") { name ->
        addingLane = false
        if (name != null && name.isNotBlank()) {
            val opt = Opt(newId(), name.trim(), PALETTE[group.options.size % PALETTE.size])
            Store.setSchema(c, s.copy(props = s.props.map { if (it.id == group.id) it.copy(options = it.options + opt) else it }))
        }
    }
    laneMenu?.let { opt -> if (group != null) OptionSheet(c, s, group, opt) { laneMenu = null } }
}

/** A card on the board: title, its date and tags, its steps. */
@Composable
private fun CardView(card: Page, c: Page, s: Schema, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.CardHigh).clickable(onClick = onClick).padding(12.dp)) {
        Text(card.shownTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        val chips = s.props.filter { it.type != PropType.SELECT || it.id != s.group?.id }.mapNotNull { p ->
            val raw = card.props[p.id] ?: return@mapNotNull null
            when (p.type) {
                PropType.MULTI -> multiOf(raw).mapNotNull { id -> p.options.firstOrNull { it.id == id } }.map { it.name to Color(it.color) }
                PropType.SELECT -> p.options.firstOrNull { it.id == raw }?.let { listOf(it.name to Color(it.color)) }
                PropType.DATE -> listOf("📅 " + shortDate(raw) to (if (raw < dayKey(System.currentTimeMillis())) Ink.Red else Ink.Dim))
                PropType.CHECK -> if (raw == "1") listOf("✓ ${p.name}" to Ink.GreenSoft) else null
                else -> raw.takeIf { it.isNotBlank() }?.let { listOf(it.take(24) to Ink.Dim) }
            }
        }.flatten()
        if (chips.isNotEmpty() || card.steps > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card.steps > 0) Chip("${card.done}/${card.steps}", if (card.done == card.steps) Ink.GreenSoft else Ink.Dim)
                for ((t, col) in chips) Chip(t, col)
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A field at the bottom of a column (or the table): type and Enter, and the card is there. */
@Composable
private fun QuickAdd(hint: String, add: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(8.dp).clip(Squircle(16.dp)).background(Ink.Track).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Ink.Dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(hint, color = Ink.Dim2, fontSize = 14.sp)
            BasicTextField(
                text, { text = it.replace("\n", "").take(200) }, singleLine = true,
                textStyle = TextStyle(color = Ink.Text, fontSize = 14.sp), cursorBrush = SolidColor(Ink.Violet),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { add(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotBlank()) Text("Agregar", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { add(text.trim()); text = "" })
    }
}

// ── Table ─────────────────────────────────────────────────────────────────────

@Composable
private fun Table(c: Page, s: Schema, ix: Index, cheer: () -> Unit) {
    val context = LocalContext.current
    val rows = remember(ix, c.id) { ix.rows(c) }
    var editing by remember { mutableStateOf<Pair<Page, Prop>?>(null) }
    var propMenu by remember { mutableStateOf<Prop?>(null) }
    val h = rememberScrollState()
    val titleW = 190.dp
    val cellW = 150.dp
    fun sortBy(id: String) {
        val next = when {
            s.sortBy != id -> s.copy(sortBy = id, sortDesc = false)
            !s.sortDesc -> s.copy(sortDesc = true)
            else -> s.copy(sortBy = "", sortDesc = false)
        }
        Store.setSchema(c, next)
        Haptics.tick(context)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Column(Modifier.clip(Squircle(20.dp)).background(Ink.Card).horizontalScroll(h)) {
            // Column names: tap to sort, ⋯ to rename or remove.
            Row(Modifier.background(Ink.CardHigh), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("Nombre", titleW, s.sortBy == "title", s.sortDesc, null) { sortBy("title") }
                for (p in s.props) HeaderCell(p.name, cellW, s.sortBy == p.id, s.sortDesc, { propMenu = p }) { sortBy(p.id) }
            }
            for (r in rows) {
                Row(Modifier.height(IntrinsicHeight), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.width(titleW).clickable { Nav.page(r.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = Ink.Dim2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(r.shownTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    for (p in s.props) {
                        Box(
                            Modifier.width(cellW).clickable {
                                if (p.type == PropType.CHECK) { putValue(r, c, p, if (r.props[p.id] == "1") null else "1"); Haptics.tick(context) }
                                else editing = r to p
                            }.padding(horizontal = 10.dp, vertical = 12.dp),
                        ) { CellValue(p, r.props[p.id]) }
                    }
                }
                Box(Modifier.width(titleW + cellW * s.props.size).height(1.dp).background(Ink.Hairline))
            }
        }
        QuickAdd("Nueva fila…") { text ->
            Store.newPage(c.id, title = text)
            Sounds.play("pop"); Haptics.tick(context)
        }
        Spacer(Modifier.height(80.dp))
    }
    editing?.let { (r, p) -> ValueSheet(r, c, p, cheer) { editing = null } }
    propMenu?.let { p -> PropSheet(c, s, p) { propMenu = null } }
}

private val IntrinsicHeight = 48.dp

@Composable
private fun HeaderCell(name: String, width: androidx.compose.ui.unit.Dp, sorted: Boolean, desc: Boolean, menu: (() -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.width(width).clickable(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (sorted) Ink.VioletSoft else Ink.Dim, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (sorted) Icon(if (desc) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null, tint = Ink.VioletSoft, modifier = Modifier.padding(start = 4.dp).size(14.dp))
        Spacer(Modifier.weight(1f))
        if (menu != null) Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = menu), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreHoriz, "Columna", tint = Ink.Dim2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CellValue(p: Prop, raw: String?) {
    when (p.type) {
        PropType.SELECT -> p.options.firstOrNull { it.id == raw }?.let { Chip(it.name, Color(it.color)) }
        PropType.MULTI -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (id in multiOf(raw)) p.options.firstOrNull { it.id == id }?.let { Chip(it.name, Color(it.color)) }
        }
        PropType.CHECK -> StepDot(raw == "1", Ink.Green, 20) {}
        PropType.DATE -> if (!raw.isNullOrBlank()) Text(shortDate(raw), fontSize = 14.sp, color = if (raw < dayKey(System.currentTimeMillis())) Ink.Red else Ink.Text)
        else -> Text(display(p, raw), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── A card's values, on its page ──────────────────────────────────────────────

@Composable
fun PropsPanel(card: Page, collection: Page) {
    val s = collection.schema ?: return
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Prop?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(Squircle(20.dp)).background(Ink.Card).padding(vertical = 4.dp)) {
        for (p in s.props) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (p.type == PropType.CHECK) { putValue(card, collection, p, if (card.props[p.id] == "1") null else "1"); Haptics.tick(context) }
                    else editing = p
                }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(p.name, Ink.Dim, 14.sp, modifier = Modifier.width(110.dp), maxLines = 1)
                Box(Modifier.weight(1f)) {
                    if (card.props[p.id].isNullOrBlank() && p.type != PropType.CHECK) Label("Vacío", Ink.Dim2, 14.sp, FontWeight.Normal)
                    else CellValue(p, card.props[p.id])
                }
            }
        }
    }
    editing?.let { p -> ValueSheet(card, collection, p) { editing = null } }
}

/** Change one value: pick a status or tags (or make a new one), a date, text or a number. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ValueSheet(card: Page, collection: Page, prop: Prop, cheer: () -> Unit = {}, close: () -> Unit) {
    val context = LocalContext.current
    val live = Store.page(card.id) ?: card
    val raw = live.props[prop.id]
    // The column as it is now (a new option shows right away).
    val current = Store.index.value.page(collection.id)?.schema?.prop(prop.id) ?: prop
    Sheet(close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Label(live.shownTitle, Ink.Dim2, 13.sp)
            ScreenTitle(prop.name, size = 26.sp)
            Spacer(Modifier.height(14.dp))
            when (prop.type) {
                PropType.SELECT, PropType.MULTI -> {
                    val chosen = if (prop.type == PropType.MULTI) multiOf(raw).toSet() else setOfNotNull(raw)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in current.options) {
                            val on = o.id in chosen
                            Text(
                                o.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) INK_ON else Color(o.color),
                                modifier = Modifier.clip(CircleShape).background(if (on) Color(o.color) else Color(o.color).copy(alpha = 0.14f))
                                    .clickable {
                                        Haptics.tick(context)
                                        if (prop.type == PropType.SELECT) { putValue(live, collection, prop, if (on) null else o.id) { cheer() }; close() }
                                        else putValue(live, collection, prop, (if (on) chosen - o.id else chosen + o.id).joinToString(","))
                                    }.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true, shape = Squircle(16.dp),
                        placeholder = { Text(if (prop.type == PropType.SELECT) "Nueva opción" else "Nueva etiqueta", color = Ink.Dim2) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (name.isBlank()) return@KeyboardActions
                            val opt = Opt(newId(), name.trim(), PALETTE[current.options.size % PALETTE.size])
                            val s = Store.page(collection.id)?.schema ?: return@KeyboardActions
                            Store.setSchema(collection, s.copy(props = s.props.map { if (it.id == prop.id) it.copy(options = it.options + opt) else it }))
                            if (prop.type == PropType.SELECT) { putValue(live, collection, prop, opt.id); close() }
                            else putValue(live, collection, prop, (chosen + opt.id).joinToString(","))
                            name = ""
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PropType.DATE -> {
                    val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
                    val state = rememberDatePickerState(initialSelectedDateMillis = raw?.let { runCatching { utc.parse(it)?.time }.getOrNull() })
                    PillRow {
                        Pill("Hoy", false, Icons.Rounded.CalendarMonth) { putValue(live, collection, prop, dayKey(System.currentTimeMillis())); close() }
                        Pill("Mañana", false) { putValue(live, collection, prop, dayKey(System.currentTimeMillis() + 86_400_000L)); close() }
                        if (raw != null) Pill("Quitar", false, Icons.Rounded.Close) { putValue(live, collection, prop, null); close() }
                    }
                    DatePicker(
                        state, title = null, headline = null, showModeToggle = false,
                        colors = DatePickerDefaults.colors(containerColor = Ink.CardHigh),
                    )
                    ExpressiveButton("Listo", Modifier.fillMaxWidth()) {
                        state.selectedDateMillis?.let { putValue(live, collection, prop, utc.format(java.util.Date(it))) }
                        close()
                    }
                }
                else -> {
                    var text by remember { mutableStateOf(raw ?: "") }
                    OutlinedTextField(
                        value = text, onValueChange = { text = if (prop.type == PropType.NUMBER) it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(16) else it.take(400) },
                        shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(), maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (prop.type == PropType.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    ExpressiveButton("Guardar", Modifier.fillMaxWidth()) { putValue(live, collection, prop, text.trim()); close() }
                }
            }
        }
    }
}

/** A board column's option: rename, colour, remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionSheet(c: Page, s: Schema, group: Prop, opt: Opt, close: () -> Unit) {
    var name by remember { mutableStateOf(opt.name) }
    var color by remember { mutableStateOf(opt.color) }
    fun save(remove: Boolean = false) {
        val latest = Store.page(c.id)?.schema ?: s
        Store.setSchema(c, latest.copy(props = latest.props.map { p ->
            if (p.id != group.id) p
            else p.copy(options = if (remove) p.options.filter { it.id != opt.id } else p.options.map { if (it.id == opt.id) it.copy(name = name.trim().ifEmpty { opt.name }, color = color) else it })
        }))
        close()
    }
    Sheet({ save() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            ScreenTitle("Columna", size = 26.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth())
            SectionLabel("COLOR")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (cc in PALETTE) Box(Modifier.size(38.dp).clip(PolygonShape(if (cc == color) MShapes.Cookie9 else MShapes.Circle)).background(Color(cc)).clickable { color = cc })
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveButton("Guardar", Modifier.weight(1f)) { save() }
                ExpressiveButton("Quitar", container = Ink.CardHigh, content = Ink.Red) { save(remove = true) }
            }
            Label("Las tarjetas de una columna quitada pasan a «Sin estado».", Ink.Dim2, 12.sp, FontWeight.Normal, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** A table column: rename it, make it the board's, or remove it. */
@Composable
private fun PropSheet(c: Page, s: Schema, prop: Prop, close: () -> Unit) {
    var name by remember { mutableStateOf(prop.name) }
    var removing by remember { mutableStateOf(false) }
    fun save() {
        val latest = Store.page(c.id)?.schema ?: s
        Store.setSchema(c, latest.copy(props = latest.props.map { if (it.id == prop.id) it.copy(name = name.trim().ifEmpty { prop.name }) else it }))
        close()
    }
    Sheet({ save() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Label(prop.type.label, Ink.Dim2, 13.sp)
            ScreenTitle("Columna", size = 26.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth())
            if (prop.type == PropType.SELECT && s.group?.id != prop.id) {
                Spacer(Modifier.height(10.dp))
                Pill("Agrupar el tablero con esta", false, Icons.Rounded.ViewKanban) {
                    Store.setSchema(c, (Store.page(c.id)?.schema ?: s).copy(groupBy = prop.id)); close()
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveButton("Guardar", Modifier.weight(1f)) { save() }
                ExpressiveButton("Quitar", container = Ink.CardHigh, content = Ink.Red) { removing = true }
            }
        }
    }
    if (removing) AlertDialog(
        onDismissRequest = { removing = false }, containerColor = Ink.CardHigh,
        title = { Text("¿Quitar «${prop.name}»?") }, text = { Text("Sus valores se borran de todas las tarjetas.") },
        confirmButton = {
            TextButton(onClick = {
                removing = false
                val latest = Store.page(c.id)?.schema ?: s
                Store.setSchema(c, latest.copy(props = latest.props.filter { it.id != prop.id }, sortBy = if (latest.sortBy == prop.id) "" else latest.sortBy))
                for (r in Store.index.value.children(c.id)) if (prop.id in r.props) Store.update(r.copy(props = r.props - prop.id), touch = false)
                close()
            }) { Text("Quitar", color = Ink.Red) }
        },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancelar", color = Ink.Dim) } },
    )
}

/** A new column: its name and what it holds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddColumnSheet(c: Page, s: Schema, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PropType.TEXT) }
    Sheet(close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            ScreenTitle("Nueva columna", size = 26.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                name, { name = it.take(40) }, singleLine = true, shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ej. Prioridad, Cliente, Precio", color = Ink.Dim2) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            SectionLabel("GUARDA…")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in PropType.entries) Pill(t.label, t == type) { type = t }
            }
            Spacer(Modifier.height(18.dp))
            ExpressiveButton("Agregar", Modifier.fillMaxWidth(), big = true) {
                if (name.isBlank()) return@ExpressiveButton
                val prop = Prop(newId(), name.trim(), type)
                val latest = Store.page(c.id)?.schema ?: s
                val next = latest.copy(props = latest.props + prop)
                Store.setSchema(c, if (type == PropType.SELECT && latest.group == null) next.copy(groupBy = prop.id) else next)
                close()
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, done: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { done(null) }, containerColor = Ink.CardHigh, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, shape = Squircle(16.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) },
        confirmButton = { TextButton(onClick = { done(name) }) { Text("Listo", color = Ink.VioletSoft) } },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("Cancelar", color = Ink.Dim) } },
    )
}
