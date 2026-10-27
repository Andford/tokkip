// A page: its top (back, where it lives, favourite, more), its icon and title,
// and its text, written in lines (blocks). Type "/" on an empty line (or tap
// +) to pick what the line is: a heading, a list, steps, a quote, an aviso, a
// separator, or a new page, board or table inside this one. A card from a
// board shows its values (estado, fecha, etiquetas…) above its text.
//
// The editor fills the screen; the text is saved a moment after you stop
// typing, and when you leave. A page you opened and left empty doesn't stay.

package mx.loperstack.tokkipnotas.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.Block
import mx.loperstack.tokkipnotas.data.BlockKind
import mx.loperstack.tokkipnotas.data.INBOX_ID
import mx.loperstack.tokkipnotas.data.Index
import mx.loperstack.tokkipnotas.data.PALETTE
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.Sync
import mx.loperstack.tokkipnotas.data.Drawing
import mx.loperstack.tokkipnotas.data.INK_EMPTY
import mx.loperstack.tokkipnotas.data.drawingJson
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Gesture
import mx.loperstack.tokkipnotas.data.blockOf
import mx.loperstack.tokkipnotas.data.blocksOf
import mx.loperstack.tokkipnotas.data.bodyOf
import mx.loperstack.tokkipnotas.data.numberAt

@Composable
fun PageScreen(p: Page, ix: Index, newPage: (String?) -> Unit) {
    when {
        p.id == INBOX_ID -> InboxScreen(ix)
        p.kind == PageKind.COLLECTION -> CollectionScreen(p, ix)
        p.kind == PageKind.INK -> InkScreen(p, ix)
        else -> DocScreen(p, ix, newPage)
    }
}

// ── The top of a page ─────────────────────────────────────────────────────────

@Composable
fun PageTop(p: Page, ix: Index, extra: List<Pair<String, () -> Unit>> = emptyList(), share: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val path = remember(ix, p.id) { ix.path(p.id) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.Rounded.ArrowBack, "Volver", size = 44.dp) { Nav.back() }
        Spacer(Modifier.width(8.dp))
        // Where it lives: tap a step to go there.
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            for ((i, q) in path.withIndex()) {
                if (i > 0) Icon(Icons.Rounded.ChevronRight, null, tint = Ink.Dim2, modifier = Modifier.size(16.dp))
                Text(
                    q.shownTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.Dim, maxLines = 1,
                    modifier = Modifier.clip(Squircle(10.dp)).clickable { Nav.page(q.id) }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        CircleButton(
            if (p.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (p.favorite) "Quitar de favoritos" else "Favorito", size = 44.dp,
            tint = if (p.favorite) Color(0xFFFBBF24) else Ink.Dim,
        ) { Store.update(p.copy(favorite = !p.favorite), touch = false); Haptics.tick(context) }
        Spacer(Modifier.width(6.dp))
        Box {
            CircleButton(Icons.Rounded.MoreVert, "Más", size = 44.dp) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Ink.CardHigh) {
                DropdownMenuItem(text = { Text("Ícono, color y forma") }, leadingIcon = { Icon(Icons.Rounded.Palette, null) }, onClick = { menu = false; styling = true })
                DropdownMenuItem(text = { Text("Mover a…") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) }, onClick = { menu = false; moving = true })
                if (share != null) DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; share() })
                for ((label, act) in extra) DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; act() })
                DropdownMenuItem(
                    text = { Text("A la papelera") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    onClick = {
                        menu = false
                        Store.trash(p.id)
                        Nav.forget(ix.subtree(p.id).map { it.id }.toSet())
                        android.widget.Toast.makeText(context, "En la Papelera (30 días)", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
    if (moving) MoveSheet(listOf(p), ix) { moving = false }
    if (styling) StyleSheet(p) { styling = false }
}

/** The page's icon (tap to change it) and its title. */
@Composable
fun PageHeader(p: Page, title: TextFieldValue, onTitle: (TextFieldValue) -> Unit, titleFocus: FocusRequester? = null, onNext: () -> Unit = {}) {
    var styling by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.clip(Squircle(18.dp)).clickable { styling = true }) { PageIcon(p, 60.dp) }
        Spacer(Modifier.height(12.dp))
        Box {
            if (title.text.isEmpty()) Text("Sin título", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Dim2, fontFamily = DisplayFont)
            BasicTextField(
                value = title, onValueChange = { onTitle(TextFieldValue(it.text.replace("\n", "").take(140), it.selection, it.composition)) },
                textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, fontFamily = DisplayFont, lineHeight = 36.sp),
                cursorBrush = SolidColor(Color(p.color)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { onNext() }),
                modifier = Modifier.fillMaxWidth().then(if (titleFocus != null) Modifier.focusRequester(titleFocus) else Modifier),
            )
        }
    }
    if (styling) StyleSheet(p) { styling = false }
}

/** Icon, colour and shape. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleSheet(p: Page, close: () -> Unit) {
    var icon by remember { mutableStateOf(p.icon) }
    var color by remember { mutableLongStateOf(p.color) }
    var shape by remember { mutableIntStateOf(p.shape) }
    fun save() { Store.page(p.id)?.let { Store.update(it.copy(icon = icon, color = color, shape = shape), touch = false) } }
    Sheet({ save(); close() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageIcon(p.copy(icon = icon, color = color, shape = shape), 64.dp)
                Spacer(Modifier.width(14.dp))
                ScreenTitle("Su look", size = 26.sp)
            }
            SectionLabel("COLOR")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in PALETTE) Box(Modifier.size(40.dp).clip(PolygonShape(if (c == color) MShapes.Cookie9 else MShapes.Circle)).background(Color(c)).clickable { color = c })
            }
            SectionLabel("FORMA")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MShapes.ALL.forEachIndexed { i, s -> Box(Modifier.size(40.dp).clip(PolygonShape(s)).background(if (i == shape) Color(color) else Ink.Track).clickable { shape = i }) }
            }
            SectionLabel("ÍCONO")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, img) in ICONS) {
                    val on = k == icon
                    Box(Modifier.size(44.dp).clip(Squircle(14.dp)).background(if (on) Color(color) else Ink.Card).clickable { icon = k }, contentAlignment = Alignment.Center) {
                        Icon(img, null, tint = if (on) INK_ON else Ink.Dim, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            ExpressiveButton("Listo", Modifier.fillMaxWidth(), big = true) { save(); close() }
        }
    }
}

/** Mover a…: under another page, to the top, or to the Bandeja. */
@Composable
fun MoveSheet(pages: List<Page>, ix: Index, close: () -> Unit) {
    val context = LocalContext.current
    val ids = pages.map { it.id }.toSet()
    val targets = remember(ix) {
        // Every page you could put it in, as a flat tree; not inside itself.
        val out = ArrayList<Pair<Page, Int>>()
        fun walk(parent: String?, depth: Int) {
            for (q in if (parent == null) ix.roots else ix.children(parent)) {
                if (q.id in ids) continue
                out += q to depth
                if (q.kind == PageKind.DOC) walk(q.id, depth + 1)
            }
        }
        walk(null, 0)
        out
    }
    fun go(parent: String?) {
        var ok = 0
        for (p in pages) if (Store.move(p.id, parent)) ok++
        if (ok > 0) { Sounds.play("pop"); Haptics.tick(context) }
        close()
    }
    Sheet(close) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(horizontal = 24.dp)) {
            item {
                ScreenTitle("¿A dónde va?", size = 26.sp)
                Label(if (pages.size == 1) pages[0].shownTitle else "${pages.size} páginas", Ink.Dim2, 13.sp)
                Spacer(Modifier.height(10.dp))
                MoveRow(null, "Arriba de todo", 0, Icons.Rounded.Description) { go(null) }
                MoveRow(null, "Bandeja", 0, Icons.Rounded.Inbox) { go(INBOX_ID) }
            }
            items(targets, key = { it.first.id }) { (q, depth) -> MoveRow(q, q.shownTitle, depth, null) { go(q.id) } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MoveRow(p: Page?, label: String, depth: Int, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Squircle(16.dp)).background(Ink.Card).clickable(onClick = onClick)
            .padding(start = (12 + depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p != null) PageIcon(p, 28.dp) else if (icon != null) ShapeIcon(icon, shape = MShapes.Circle, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p?.kind == PageKind.COLLECTION) Label("  como tarjeta", Ink.Dim2, 12.sp)
    }
}

// ── Bandeja ───────────────────────────────────────────────────────────────────

@Composable
private fun InboxScreen(ix: Index) {
    val context = LocalContext.current
    var moving by remember { mutableStateOf<Page?>(null) }
    val list = ix.inbox
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.ArrowBack, "Volver", size = 44.dp) { Nav.back() }
            }
            Spacer(Modifier.height(10.dp))
            ScreenTitle("Bandeja", size = 32.sp)
            Label("Lo que anotas de prisa llega aquí. Cuando tengas un rato, acomódalo.", Ink.Dim2, 13.sp)
            Spacer(Modifier.height(12.dp))
            CaptureCard("Otra idea…", compact = true) { if (Store.capture(it) != null) { Sounds.play("pop"); Haptics.tick(context); Hop.now() } }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) Label("Vacía. Tu cabeza, ligera.", Ink.Dim2, 14.sp, modifier = Modifier.padding(8.dp))
        }
        items(list, key = { it.id }) { p ->
            PageRow(p, ix, trailing = { CircleButton(Icons.Rounded.DriveFileMove, "Acomodar", size = 40.dp) { moving = p } })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
    moving?.let { MoveSheet(listOf(it), ix) { moving = null } }
}

// ── The editor ────────────────────────────────────────────────────────────────

private const val ZW = '​'
private var blockKeys = 0L

private class EditBlock(kind: BlockKind, text: String) {
    val key = blockKeys++
    var kind by mutableStateOf(kind)
    var value by mutableStateOf(TextFieldValue(ZW + text, TextRange(1 + text.length)))
    val focus = FocusRequester()
    val text: String get() = value.text.removePrefix(ZW.toString())
    fun set(text: String, cursor: Int = text.length) { value = TextFieldValue(ZW + text, TextRange(1 + cursor.coerceIn(0, text.length))) }
    val isStep get() = kind == BlockKind.TODO || kind == BlockKind.DONE
    /** Lines without a text field. */
    val fixed get() = kind == BlockKind.DIVIDER || kind == BlockKind.PAGE || kind == BlockKind.DRAW
}

private fun nextKind(k: BlockKind) = when (k) {
    BlockKind.TODO, BlockKind.DONE -> BlockKind.TODO
    BlockKind.BULLET, BlockKind.NUMBER -> k
    else -> BlockKind.TEXT
}

@Composable
private fun DocScreen(p: Page, ix: Index, newPage: (String?) -> Unit) {
    val context = LocalContext.current
    val id = p.id
    val accent = Color(p.color)
    val collection = remember(ix, id) { ix.collectionOf(p) }
    var title by remember(id) { mutableStateOf(TextFieldValue(p.title)) }
    var blocks by remember(id) { mutableStateOf<SnapshotStateList<EditBlock>?>(null) }
    var version by remember(id) { mutableIntStateOf(0) }
    var focused by remember(id) { mutableLongStateOf(-1L) }
    var focusTo by remember(id) { mutableLongStateOf(-1L) }
    var slashFor by remember(id) { mutableLongStateOf(-1L) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val titleFocus = remember { FocusRequester() }

    // What's stored (or was last saved): leaving or pausing only writes when something changed,
    // so a page you just looked at doesn't travel to your other devices.
    var saved by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(id) {
        val body = withContext(Dispatchers.IO) { Store.body(id) }
        blocks = mutableStateListOf<EditBlock>().apply { addAll(blocksOf(body).map { EditBlock(it.kind, it.text) }) }
        saved = p.title.trim() to bodyOf(blocksOf(body).map { Block(it.kind, if (it.kind == BlockKind.PAGE) it.text.trim() else it.text) })
        if (p.title.isEmpty() && body.isEmpty() && collection == null) runCatching { delay(150); titleFocus.requestFocus() }
    }
    fun current(): Pair<String, String>? {
        val bs = blocks ?: return null
        return title.text.trim() to bodyOf(bs.map { Block(it.kind, if (it.kind == BlockKind.PAGE) it.text.trim() else it.text) })
    }
    fun save() {
        val c = current() ?: return
        if (c == saved) return
        Store.saveBody(id, c.first, c.second)
        saved = c
    }
    // The page changed on another device: show it, unless you're in the middle of writing here.
    LaunchedEffect(id) {
        Sync.remote.collect { ids ->
            if (id !in ids || current() != saved) return@collect
            val body = withContext(Dispatchers.IO) { Store.body(id) }
            val t = Store.page(id)?.title ?: return@collect
            if (current() != saved) return@collect
            blocks = mutableStateListOf<EditBlock>().apply { addAll(blocksOf(body).map { EditBlock(it.kind, it.text) }) }
            title = TextFieldValue(t)
            saved = t.trim() to bodyOf(blocksOf(body).map { Block(it.kind, if (it.kind == BlockKind.PAGE) it.text.trim() else it.text) })
        }
    }
    LaunchedEffect(version) { if (version > 0) { delay(600); save() } }
    DisposableEffect(id) {
        onDispose {
            val c = current()
            val q = Store.page(id)
            // A page left empty doesn't stay as clutter (unless something lives in it).
            if (c != null && q != null && q.trashed == 0L && c.first.isEmpty() && c.second.isBlank() && Store.index.value.children(id).isEmpty() && collection == null) Store.purge(id)
            else save()
        }
    }
    LaunchedEffect(focusTo) {
        if (focusTo >= 0) {
            delay(30)
            blocks?.firstOrNull { it.key == focusTo }?.let { runCatching { it.focus.requestFocus() } }
            focusTo = -1
        }
    }
    fun changed() { version++ }

    fun onChange(i: Int, new: TextFieldValue) {
        val bs = blocks ?: return
        val b = bs.getOrNull(i) ?: return
        val old = b.value
        var t = new.text
        if (!t.startsWith(ZW)) {
            if (ZW + t == old.text && old.selection.start <= 1) {
                // Backspace at the start of a line.
                val prev = bs.getOrNull(i - 1)
                when {
                    b.kind != BlockKind.TEXT -> b.kind = BlockKind.TEXT
                    prev == null -> {}
                    prev.kind == BlockKind.DIVIDER -> bs.removeAt(i - 1)
                    prev.kind == BlockKind.PAGE || prev.kind == BlockKind.DRAW -> if (t.isEmpty()) { bs.removeAt(i); focusTo = -1 }
                    else -> {
                        val join = prev.text.length
                        prev.set(prev.text + t, join)
                        bs.removeAt(i)
                        focusTo = prev.key
                    }
                }
                if (bs.contains(b)) b.set(b.text, 0)
                changed()
                return
            }
            t = ZW + t.replace(ZW.toString(), "")
        }
        val content = t.substring(1)
        if (content.contains('\n')) {
            val parts = content.split('\n')
            if (parts.size == 2 && parts[0].isEmpty() && parts[1].isEmpty() && b.kind != BlockKind.TEXT) {
                // Enter on an empty list line: back to plain text.
                b.kind = BlockKind.TEXT
                b.set("")
                changed()
                return
            }
            b.set(parts[0])
            val kind = nextKind(b.kind)
            val added = parts.drop(1).map { part ->
                val parsed = blockOf(part)
                if (parsed.kind == BlockKind.PAGE) EditBlock(BlockKind.TEXT, part)
                else EditBlock(if (parsed.kind != BlockKind.TEXT) parsed.kind else kind, parsed.text)
            }
            if (parts.size == 2) added.first().set(added.first().text, 0)
            bs.addAll(i + 1, added)
            focusTo = added.last().key
            changed()
            return
        }
        if (content == "/") {
            // The menu of what a line can be.
            b.set("")
            slashFor = b.key
            return
        }
        if (b.kind == BlockKind.TEXT) {
            val shortcut = when {
                content.startsWith("[] ") -> BlockKind.TODO to 3
                content.startsWith("[ ] ") -> BlockKind.TODO to 4
                content.startsWith("- ") || content.startsWith("* ") -> BlockKind.BULLET to 2
                content.startsWith("1. ") -> BlockKind.NUMBER to 3
                content.startsWith("## ") -> BlockKind.H2 to 3
                content.startsWith("# ") -> BlockKind.H1 to 2
                content.startsWith("> ") -> BlockKind.QUOTE to 2
                content.startsWith("! ") -> BlockKind.CALLOUT to 2
                content == "---" -> BlockKind.DIVIDER to 3
                else -> null
            }
            if (shortcut != null) {
                b.kind = shortcut.first
                b.set(content.substring(shortcut.second), 0)
                if (b.kind == BlockKind.DIVIDER) {
                    val n = EditBlock(BlockKind.TEXT, "")
                    bs.add(i + 1, n); focusTo = n.key
                }
                changed()
                return
            }
        }
        val sel = TextRange(new.selection.start.coerceAtLeast(1), new.selection.end.coerceAtLeast(1))
        b.value = if (t == new.text) new.copy(selection = sel) else TextFieldValue(t, TextRange(sel.start.coerceAtMost(t.length), sel.end.coerceAtMost(t.length)))
        if (t != old.text) changed()
    }

    /** What the slash menu picked, for the line `key`. */
    fun pick(key: Long, kind: BlockKind, sub: PageKind? = null, view: String = "board") {
        val bs = blocks ?: return
        val i = bs.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: bs.lastIndex
        val b = bs.getOrNull(i) ?: return
        when {
            kind == BlockKind.DRAW -> {
                b.kind = BlockKind.DRAW; b.set(drawingJson(Drawing("plain", 450f)))
                if (i == bs.lastIndex || bs[i + 1].kind != BlockKind.TEXT) { val n = EditBlock(BlockKind.TEXT, ""); bs.add(i + 1, n) }
                changed()
            }
            sub != null -> {
                save()
                val child = Store.newPage(id, sub, body = if (sub == PageKind.INK) INK_EMPTY else "")
                if (sub == PageKind.COLLECTION) Store.update(child.copy(title = if (view == "table") "Tabla" else "Tablero", icon = if (view == "table") "table" else "board", schema = child.schema?.copy(view = view)), touch = false)
                b.kind = BlockKind.PAGE; b.set(child.id)
                if (i == bs.lastIndex) bs.add(EditBlock(BlockKind.TEXT, ""))
                changed()
                save()
                Nav.page(child.id)
            }
            kind == BlockKind.DIVIDER -> {
                b.kind = BlockKind.DIVIDER; b.set("")
                val n = EditBlock(BlockKind.TEXT, "")
                bs.add(i + 1, n); focusTo = n.key
                changed()
            }
            else -> { b.kind = kind; focusTo = b.key; changed() }
        }
        Haptics.tick(context)
    }

    fun setKind(k: BlockKind) {
        val bs = blocks ?: return
        val b = bs.firstOrNull { it.key == focused } ?: bs.lastOrNull() ?: return
        if (b.fixed) return
        b.kind = if (b.kind == k || (k == BlockKind.TODO && b.kind == BlockKind.DONE)) BlockKind.TEXT else k
        Haptics.tick(context)
        changed()
    }

    val share = {
        val c = current()
        if (c != null) {
            val text = listOf(c.first, c.second.lines().filter { !it.startsWith("@@") && !it.startsWith("%%") }.joinToString("\n").replace("[ ] ", "☐ ").replace("[x] ", "☑ "))
                .filter { it.isNotBlank() }.joinToString("\n\n")
            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir página")) }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageTop(p, ix, share = share, extra = listOf("Nueva subpágina" to { save(); newPage(id) }))
        val bs = blocks
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            PageHeader(p, title, onTitle = { title = it; changed() }, titleFocus = titleFocus, onNext = { bs?.firstOrNull { !it.fixed }?.let { focusTo = it.key } })
            if (collection != null) PropsPanel(p, collection)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 22.dp)) {
                if (bs != null) {
                    val kinds = bs.map { it.kind }
                    bs.forEachIndexed { i, b ->
                        key(b.key) {
                            BlockRow(
                                b, accent, number = if (b.kind == BlockKind.NUMBER) numberAt(kinds, i) else 0, ix = ix,
                                onToggle = {
                                    val checking = b.kind == BlockKind.TODO
                                    b.kind = if (checking) BlockKind.DONE else BlockKind.TODO
                                    if (checking) { Sounds.play("task_done", 0.6f); Haptics.success(context); Hop.now() } else Haptics.tick(context)
                                    changed()
                                },
                                onFocus = { focused = b.key },
                                onRemove = { bs.remove(b); changed() },
                                onDrawn = { changed() },
                            ) { onChange(i, it) }
                        }
                    }
                }
                // Tapping below the text: a new line at the end.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).clickable(MutableInteractionSource(), null) {
                        val list = blocks ?: return@clickable
                        val last = list.lastOrNull()
                        if (last == null || last.fixed || last.text.isNotEmpty() || last.kind != BlockKind.TEXT) {
                            val n = EditBlock(BlockKind.TEXT, "")
                            list.add(n); focusTo = n.key
                        } else focusTo = last.key
                    },
                )
                // Pages inside this one that its text doesn't link (made from the tree, moved here…).
                val linked = bs?.filter { it.kind == BlockKind.PAGE }?.map { it.text }?.toSet() ?: emptySet()
                val loose = ix.children(id).filter { it.id !in linked }
                if (loose.isNotEmpty()) {
                    SectionLabel("DENTRO DE ESTA PÁGINA")
                    for (c in loose) PageRow(c, ix)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        // The line's kind, the "/" menu and the keyboard.
        val fb = bs?.firstOrNull { it.key == focused }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).clip(CircleShape).background(Ink.CardHigh)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Add, "Insertar", false) {
                val list = blocks ?: return@ToolButton
                val target = fb ?: list.lastOrNull()
                if (target == null || target.fixed || target.text.isNotEmpty()) {
                    val n = EditBlock(BlockKind.TEXT, "")
                    val at = (target?.let { list.indexOf(it) } ?: list.lastIndex) + 1
                    list.add(at.coerceIn(0, list.size), n)
                    slashFor = n.key
                } else slashFor = target.key
            }
            ToolButton(Icons.Rounded.Title, "Título", fb?.kind == BlockKind.H1 || fb?.kind == BlockKind.H2) { setKind(BlockKind.H2) }
            ToolButton(Icons.Rounded.FormatListBulleted, "Viñetas", fb?.kind == BlockKind.BULLET) { setKind(BlockKind.BULLET) }
            ToolButton(Icons.Rounded.CheckBox, "Paso", fb?.isStep == true) { setKind(BlockKind.TODO) }
            ToolButton(Icons.Rounded.AutoAwesome, "Aviso", fb?.kind == BlockKind.CALLOUT) { setKind(BlockKind.CALLOUT) }
            ToolButton(Icons.Rounded.KeyboardHide, "Listo", false) { keyboard?.hide(); focusManager.clearFocus(); save() }
        }
    }
    if (slashFor >= 0) {
        val k = slashFor
        SlashSheet(onClose = { slashFor = -1; focusTo = k }) { kind, sub, view -> slashFor = -1; pick(k, kind, sub, view) }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (on) Ink.VioletSoft else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) Ink.OnViolet else Ink.Text, modifier = Modifier.size(22.dp)) }
}

/** A step's check: an empty circle that turns into a cookie when done. */
@Composable
fun StepDot(done: Boolean, accent: Color, size: Int = 24, onClick: () -> Unit) {
    val shape = PolygonShape(if (done) MShapes.Cookie9 else MShapes.Circle)
    Box(
        Modifier.size(size.dp).clip(shape).background(if (done) accent else Color.Transparent)
            .border(2.dp, if (done) accent else Ink.Dim2, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Rounded.TaskAlt, null, tint = INK_ON, modifier = Modifier.size((size * 0.6f).dp)) }
}

@Composable
private fun BlockRow(
    b: EditBlock, accent: Color, number: Int, ix: Index, onToggle: () -> Unit, onFocus: () -> Unit, onRemove: () -> Unit, onDrawn: () -> Unit, onChange: (TextFieldValue) -> Unit,
) {
    when (b.kind) {
        BlockKind.DIVIDER -> {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(2.dp).clip(CircleShape).background(Ink.Hairline))
            return
        }
        BlockKind.DRAW -> {
            DrawBlockView(b.text, onChange = { b.set(it); onDrawn() }, onRemove = onRemove)
            return
        }
        BlockKind.PAGE -> {
            val child = ix.page(b.text.trim())
            if (child == null || child.trashed != 0L) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Página en la Papelera", Ink.Dim2, 13.sp, modifier = Modifier.weight(1f))
                    Text("Quitar", color = Ink.VioletSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(Squircle(10.dp)).clickable(onClick = onRemove).padding(8.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(16.dp)).background(Ink.Card).clickable { Nav.page(child.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageIcon(child, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(child.shownTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (child.kind == PageKind.COLLECTION) Label("${ix.children(child.id).size}", Ink.Dim2, 12.sp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = Ink.Dim)
                }
            }
            return
        }
        else -> {}
    }
    val style = when (b.kind) {
        BlockKind.H1 -> TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, lineHeight = 31.sp)
        BlockKind.H2 -> TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, lineHeight = 27.sp)
        BlockKind.DONE -> TextStyle(fontSize = 17.sp, color = Ink.Dim2, textDecoration = TextDecoration.LineThrough, lineHeight = 25.sp)
        BlockKind.QUOTE -> TextStyle(fontSize = 17.sp, color = Ink.Dim, fontStyle = FontStyle.Italic, lineHeight = 25.sp)
        else -> TextStyle(fontSize = 17.sp, color = Ink.Text, lineHeight = 25.sp)
    }
    val row = Modifier.fillMaxWidth().padding(vertical = if (b.kind == BlockKind.H1 || b.kind == BlockKind.H2) 6.dp else 2.dp)
    val callout = b.kind == BlockKind.CALLOUT
    Row(
        if (callout) row.padding(vertical = 4.dp).clip(Squircle(18.dp)).background(accent.copy(alpha = 0.14f)).padding(12.dp) else row,
        verticalAlignment = Alignment.Top,
    ) {
        when (b.kind) {
            BlockKind.TODO, BlockKind.DONE -> Box(Modifier.padding(top = 1.dp, end = 10.dp)) { StepDot(b.kind == BlockKind.DONE, accent, 24, onToggle) }
            BlockKind.BULLET -> Box(Modifier.padding(top = 9.dp, start = 6.dp, end = 12.dp).size(8.dp).clip(PolygonShape(MShapes.Clover4)).background(accent))
            BlockKind.NUMBER -> Text("$number.", color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 2.dp, end = 10.dp).width(24.dp))
            BlockKind.QUOTE -> Box(Modifier.padding(end = 12.dp).width(4.dp).height(25.dp).clip(CircleShape).background(accent))
            BlockKind.CALLOUT -> Box(Modifier.padding(end = 10.dp)) { Tokkip(Modifier.size(30.dp, 26.dp), color = Color(Prefs.color)) }
            else -> {}
        }
        BasicTextField(
            value = b.value, onValueChange = onChange, textStyle = style, cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).focusRequester(b.focus).onFocusChanged { if (it.isFocused) onFocus() },
        )
    }
}

/** What a line can be (and the pages you can put here). */
@Composable
private fun SlashSheet(onClose: () -> Unit, pick: (BlockKind, PageKind?, String) -> Unit) {
    val items = listOf(
        Triple(Icons.Rounded.Notes, BlockKind.TEXT, "Texto normal"),
        Triple(Icons.Rounded.Title, BlockKind.H1, "Título grande"),
        Triple(Icons.Rounded.Title, BlockKind.H2, "Subtítulo"),
        Triple(Icons.Rounded.FormatListBulleted, BlockKind.BULLET, "Lista con viñetas"),
        Triple(Icons.Rounded.FormatListNumbered, BlockKind.NUMBER, "Lista numerada"),
        Triple(Icons.Rounded.CheckBox, BlockKind.TODO, "Paso para marcar"),
        Triple(Icons.Rounded.FormatQuote, BlockKind.QUOTE, "Cita"),
        Triple(Icons.Rounded.AutoAwesome, BlockKind.CALLOUT, "Aviso con Tokkip"),
        Triple(Icons.Rounded.HorizontalRule, BlockKind.DIVIDER, "Separador"),
    )
    Sheet(onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            ScreenTitle("¿Qué va aquí?", size = 24.sp)
            Spacer(Modifier.height(8.dp))
            SectionLabel("PÁGINAS DENTRO DE ESTA")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlashTile(Icons.Rounded.Description, "Subpágina", Modifier.weight(1f)) { pick(BlockKind.PAGE, PageKind.DOC, "") }
                SlashTile(Icons.Rounded.ViewKanban, "Tablero", Modifier.weight(1f)) { pick(BlockKind.PAGE, PageKind.COLLECTION, "board") }
                SlashTile(Icons.Rounded.TableChart, "Tabla", Modifier.weight(1f)) { pick(BlockKind.PAGE, PageKind.COLLECTION, "table") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlashTile(Icons.Rounded.Draw, "Página a mano", Modifier.weight(1f)) { pick(BlockKind.PAGE, PageKind.INK, "") }
                SlashTile(Icons.Rounded.Gesture, "Dibujo aquí", Modifier.weight(1f)) { pick(BlockKind.DRAW, null, "") }
                Spacer(Modifier.weight(1f))
            }
            SectionLabel("ESTA LÍNEA ES…")
            for ((icon, kind, label) in items) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Squircle(16.dp)).clickable { pick(kind, null, "") }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeIcon(icon, shape = MShapes.Cookie9, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Label(kind.prefix.trim().ifEmpty { "" }, Ink.Dim2, 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SlashTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(Squircle(20.dp)).background(Ink.Card).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeIcon(icon, shape = MShapes.Sunny, size = 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
