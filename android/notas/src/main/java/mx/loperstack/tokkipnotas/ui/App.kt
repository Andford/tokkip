// Tokkip Notas' frame and Inicio. On the phone, one screen at a time; on a
// tablet held sideways (the Legion Tab), the tree of your pages stays on the
// left and the page opens on the right.
//
// Inicio is made so you never have to think where to start: one field to get
// a thought out of your head (it waits in the Bandeja), what you opened last,
// what's due today on your boards, your favourites and all your pages.

package mx.loperstack.tokkipnotas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.INBOX_ID
import mx.loperstack.tokkipnotas.data.Index
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.PageKind
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.dueSoon
import mx.loperstack.tokkipnotas.data.shortDate

/** Tokkip's hops, shared by every screen (a step ticked, a card done…). */
object Hop {
    var count by mutableIntStateOf(0)
    fun now() { count++ }
}

@Composable
fun NotasApp() {
    val ix by Store.index.collectAsState()
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 840
    var templates by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val newPage: (String?) -> Unit = { parent -> templates = parent; picking = true }

    BackHandler(enabled = Nav.stack.isNotEmpty()) { Nav.back() }
    // Every few minutes while the app is open (it also syncs when it comes back and after changes).
    LaunchedEffect(Unit) {
        while (true) { delay(3 * 60_000L); mx.loperstack.tokkipnotas.data.Sync.now() }
    }
    // A page that's gone (to the Papelera) leaves the way back.
    LaunchedEffect(ix) {
        val gone = Nav.stack.filter { it.startsWith("p:") }.map { it.removePrefix("p:") }.filter { (ix.page(it)?.trashed ?: 1L) != 0L }.toSet()
        if (gone.isNotEmpty()) Nav.forget(gone)
    }

    TokkipTheme(Prefs.theme) {
        Box(Modifier.fillMaxSize().background(Ink.Ground)) {
            if (wide) {
                Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Sidebar(ix, newPage, Modifier.width(300.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 860.dp).fillMaxSize()) { Route(Nav.current, ix, wide, newPage) }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    AnimatedContent(Nav.current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
                        Route(r, ix, wide, newPage)
                    }
                }
            }
        }
        if (picking) TemplateSheet(templates, onClose = { picking = false })
    }
}

@Composable
private fun Route(route: String, ix: Index, wide: Boolean, newPage: (String?) -> Unit) {
    when {
        route.startsWith("p:") -> ix.page(route.removePrefix("p:"))?.let { PageScreen(it, ix, newPage) }
        route == "search" -> SearchScreen(ix)
        route == "trash" -> TrashScreen(ix)
        route == "settings" -> SettingsScreen()
        else -> HomeScreen(ix, wide, newPage)
    }
}

// ── Inicio ────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(ix: Index, wide: Boolean, newPage: (String?) -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val recent = remember(ix) { ix.recent.filter { it.parent != INBOX_ID }.take(8) }
    val due = remember(ix, now / 3_600_000) { ix.dueSoon(now) }
    val inbox = ix.inbox
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tokkip(Modifier.size(62.dp, 54.dp), color = Color(Prefs.color), jump = Hop.count, greet = true)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        ScreenTitle(greeting(now), size = 28.sp)
                        Label(
                            when {
                                due.isNotEmpty() -> if (due.size == 1) "Tienes 1 cosa para hoy" else "Tienes ${due.size} cosas para hoy"
                                inbox.size >= 6 -> "${inbox.size} ideas esperando en la Bandeja"
                                ix.live.isEmpty() -> "Empecemos con una página"
                                else -> "¿En qué andamos?"
                            },
                            Ink.Dim2, 13.sp,
                        )
                    }
                    if (!wide) {
                        CircleButton(Icons.Rounded.Search, "Buscar") { Nav.go("search") }
                        Spacer(Modifier.width(8.dp))
                        CircleButton(Icons.Rounded.Settings, "Ajustes") { Nav.go("settings") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CaptureCard("¿Qué traes en la cabeza?") { text ->
                    if (Store.capture(text) != null) { Sounds.play("pop"); Haptics.success(context); Hop.now() }
                }
                // The Bandeja, right below: what you caught and haven't sorted.
                if (inbox.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(Squircle(20.dp)).background(Ink.Card).clickable { Nav.page(INBOX_ID) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapeIcon(Icons.Rounded.Inbox, shape = MShapes.Circle, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bandeja", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Label(inbox.take(3).joinToString(" · ") { it.shownTitle }, Ink.Dim2, 12.sp, maxLines = 1)
                        }
                        Label("${inbox.size}", Ink.VioletSoft, 15.sp, FontWeight.ExtraBold)
                        Icon(Icons.Rounded.ChevronRight, null, tint = Ink.Dim)
                    }
                }
            }
            if (due.isNotEmpty()) {
                item { SectionLabel("PARA HOY") }
                items(due, key = { "due" + it.card.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(Ink.Card).clickable { Nav.page(d.card.id) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PageIcon(d.collection, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.card.shownTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Label(d.collection.shownTitle, Ink.Dim2, 12.sp, maxLines = 1)
                        }
                        val late = d.day < mx.loperstack.tokkipnotas.data.dayKey(now)
                        Label(shortDate(d.day, now), if (late) Ink.Red else Ink.Amber, 12.sp, FontWeight.Bold)
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item {
                    SectionLabel("SEGUIR DONDE TE QUEDASTE")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recent, key = { "r" + it.id }) { p -> RecentCard(p, ix) }
                    }
                }
            }
            val favs = ix.favorites
            if (favs.isNotEmpty()) {
                item { SectionLabel("FAVORITOS") }
                items(favs, key = { "f" + it.id }) { p -> PageRow(p, ix) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("TUS PÁGINAS", Modifier.weight(1f))
                    if (!wide) Text(
                        "Papelera", color = Ink.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp).clip(Squircle(10.dp)).clickable { Nav.go("trash") }.padding(8.dp),
                    )
                }
                if (ix.roots.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(Squircle(22.dp)).background(Ink.Card).clickable { newPage(null) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapeIcon(Icons.Rounded.Add, shape = MShapes.Sunny, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Una página puede ser una nota, un proyecto con su tablero o una lista. Empieza con una plantilla y ya.",
                            color = Ink.Dim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { Tree(ix, null, 0) }
            item { Spacer(Modifier.height(120.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { newPage(null) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Nueva página", fontWeight = FontWeight.Bold) },
            containerColor = Ink.VioletDeep, contentColor = Ink.Text, shape = Squircle(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun RecentCard(p: Page, ix: Index) {
    val where = remember(ix, p.id) { ix.path(p.id).lastOrNull()?.shownTitle }
    Column(
        Modifier.width(170.dp).height(126.dp).clip(Squircle(24.dp)).background(Ink.Card).clickable { Nav.page(p.id) }.padding(14.dp),
    ) {
        PageIcon(p, 34.dp)
        Spacer(Modifier.weight(1f))
        Text(p.shownTitle, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        if (where != null) Label(where, Ink.Dim2, 11.sp, maxLines = 1)
    }
}

/** A page in a list: icon, title, and what it holds. */
@Composable
fun PageRow(p: Page, ix: Index, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit = { Nav.page(p.id) }) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(Ink.Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageIcon(p, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.shownTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                p.kind == PageKind.COLLECTION -> ix.children(p.id).size.let { if (it == 1) "1 tarjeta" else "$it tarjetas" }
                p.steps > 0 -> "${p.done} de ${p.steps} pasos"
                else -> p.preview
            }
            if (sub.isNotBlank()) Label(sub, Ink.Dim2, 12.sp, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** The tree of pages: a tap opens, the arrow unfolds (a board's cards stay inside it). */
@Composable
fun Tree(ix: Index, parent: String?, depth: Int, compact: Boolean = false) {
    val kids = if (parent == null) ix.roots else ix.children(parent)
    Column {
        for (p in kids) {
            val hasKids = p.kind == PageKind.DOC && ix.children(p.id).isNotEmpty()
            val unfolded = Nav.open[p.id] == true
            val here = Nav.current == "p:${p.id}"
            Row(
                Modifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 2.dp).clip(Squircle(14.dp))
                    .background(if (here) Ink.VioletDeep else Color.Transparent).clickable { Nav.page(p.id) }
                    .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(Squircle(10.dp)).clickable(enabled = hasKids) { Nav.open[p.id] = !unfolded },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasKids) Icon(if (unfolded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, if (unfolded) "Plegar" else "Desplegar", tint = Ink.Dim, modifier = Modifier.size(20.dp))
                }
                PageIcon(p, if (compact) 24.dp else 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(p.shownTitle, fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (p.kind == PageKind.COLLECTION) Label("${ix.children(p.id).size}", Ink.Dim2, 12.sp)
            }
            if (hasKids && unfolded) Tree(ix, p.id, depth + 1, compact)
        }
    }
}

// ── The tablet's side ─────────────────────────────────────────────────────────

@Composable
private fun Sidebar(ix: Index, newPage: (String?) -> Unit, modifier: Modifier) {
    Column(modifier.padding(10.dp).clip(Squircle(28.dp)).background(Ink.Card).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            Tokkip(Modifier.size(44.dp, 38.dp), color = Color(Prefs.color), jump = Hop.count)
            Spacer(Modifier.width(8.dp))
            Text("Tokkip Notas", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        SideItem(Icons.Rounded.Home, "Inicio", Nav.current == "home") { Nav.home() }
        SideItem(Icons.Rounded.Search, "Buscar", Nav.current == "search") { Nav.go("search") }
        SideItem(Icons.Rounded.Inbox, "Bandeja · ${ix.inbox.size}", Nav.current == "p:$INBOX_ID") { Nav.page(INBOX_ID) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val favs = ix.favorites
            if (favs.isNotEmpty()) {
                SectionLabel("FAVORITOS", Modifier.padding(top = 0.dp))
                for (p in favs) SideItem(null, p.shownTitle, Nav.current == "p:${p.id}", page = p) { Nav.page(p.id) }
            }
            SectionLabel("PÁGINAS")
            Tree(ix, null, 0, compact = true)
        }
        SideItem(Icons.Rounded.Add, "Nueva página", false) { newPage(null) }
        SideItem(Icons.Rounded.DeleteOutline, "Papelera", Nav.current == "trash") { Nav.go("trash") }
        SideItem(Icons.Rounded.Settings, "Ajustes", Nav.current == "settings") { Nav.go("settings") }
    }
}

@Composable
private fun SideItem(icon: ImageVector?, label: String, on: Boolean, page: Page? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(Squircle(14.dp)).background(if (on) Ink.VioletDeep else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != null) PageIcon(page, 24.dp) else if (icon != null) Icon(icon, null, tint = if (on) Ink.OnVioletDeep else Ink.Dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Quick capture ─────────────────────────────────────────────────────────────

@Composable
fun CaptureCard(hint: String, compact: Boolean = false, save: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableLongStateOf(0L) }
    LaunchedEffect(saved) { if (saved != 0L) { delay(2200); saved = 0L } }
    Row(
        Modifier.fillMaxWidth().clip(Squircle(24.dp)).background(Ink.Card).padding(if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(4000) },
                placeholder = { Text(hint, color = Ink.Dim2) }, maxLines = 5, shape = Squircle(18.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                    focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
                ),
            )
            if (saved != 0L) Label("Guardado ✓", Ink.GreenSoft, 12.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleButton(Icons.Rounded.Send, "Guardar", container = if (text.isBlank()) Ink.CardHigh else Ink.VioletSoft, tint = if (text.isBlank()) Ink.Dim else Ink.OnViolet) {
            if (text.isNotBlank()) { save(text); text = ""; saved = System.currentTimeMillis() }
        }
    }
}

/** A star for favourites, used in rows and page headers. */
@Composable
fun StarIcon(on: Boolean) = Icon(Icons.Rounded.Star, null, tint = if (on) Color(0xFFFBBF24) else Ink.Dim2, modifier = Modifier.size(18.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose, containerColor = Ink.CardHigh,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        content()
    }
}
