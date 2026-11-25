// The app: five sections behind a floating pill bar (the chosen one grows and
// shows its name), each screen sliding and scaling in, the goals and Tokkip in
// a sheet, and Health Connect asked for only when you tap "Conectar".

package mx.loperstack.tokkipfit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Species
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipStyle
import mx.loperstack.tokkip.character.TOKKIP_PALETTE
import mx.loperstack.tokkipfit.data.Goals
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs
import mx.loperstack.tokkipfit.data.Today
import mx.loperstack.tokkipfit.data.fmt
import mx.loperstack.tokkipfit.data.sampleWeek
import java.time.LocalDate

enum class Section(val label: String, val icon: ImageVector) {
    TODAY("Hoy", Icons.Rounded.CalendarToday),
    GYM("Gym", Icons.Rounded.FitnessCenter),
    CARDIO("Cardio", Icons.AutoMirrored.Rounded.DirectionsRun),
    HOME("En casa", Icons.Rounded.SportsGymnastics),
    BREATHE("Respira", Icons.Rounded.SelfImprovement),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitApp(startOnPrivacy: Boolean = false, link: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(Section.TODAY) }
    var today by remember { mutableStateOf<Today>(sampleWeek(LocalDate.now(), Prefs.goals)) }
    var settings by remember { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(startOnPrivacy) }
    var wardrobe by rememberSaveable { mutableStateOf(false) }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var gym by rememberSaveable { mutableStateOf<String?>(null) }
    var cardio by rememberSaveable { mutableStateOf<String?>(null) }
    var breathe by rememberSaveable { mutableStateOf<String?>(null) }
    var breatheMin by rememberSaveable { mutableStateOf(3) }
    var badgesOpen by rememberSaveable { mutableStateOf(false) }
    // Editing one of yours: its id, or "new".
    var editRoutine by rememberSaveable { mutableStateOf<String?>(null) }
    var editPlan by rememberSaveable { mutableStateOf<String?>(null) }
    // From a widget or the cardio notification: tokkipfit://home, gym, cardio, breathe, routine/<id>.
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        when {
            l.startsWith("routine/") -> playing = l.removePrefix("routine/")
            l == "home" -> section = Section.HOME
            l == "gym" -> section = Section.GYM
            l == "cardio" -> section = Section.CARDIO
            l == "breathe" -> section = Section.BREATHE
            l == "today" -> section = Section.TODAY
        }
    }

    fun refresh() {
        scope.launch {
            val real = runCatching { HealthData.today(context, Prefs.goals) }.getOrNull()
            today = real ?: sampleWeek(LocalDate.now(), Prefs.goals)
            // For the widgets and the watch (they can't always ask Health Connect themselves).
            val snap = mx.loperstack.tokkipfit.data.Snapshot.of(today).json()
            if (real != null) {
                Prefs.saveToday(snap)
                mx.loperstack.tokkipfit.data.Wrist.pushToday(context, snap)
            }
            runCatching { mx.loperstack.tokkipfit.widget.FitWidgets.refresh(context) }
        }
    }
    val ask = rememberLauncherForActivityResult(HealthData.permissionContract()) { refresh() }
    val connect = {
        when (HealthData.status(context)) {
            HealthData.Status.AVAILABLE -> ask.launch(HealthData.permissions(context))
            // Android 13 and before: Health Connect is an app from the Play Store.
            else -> runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        Unit
    }
    LifecycleResumeEffect(Unit) {
        Prefs.refreshLook()
        refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Prefs.goals) { today = today.copy(goals = Prefs.goals) }

    FitTheme {
        val cs = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(cs.surface)) {
            val routine = playing?.let { Prefs.routine(it) }
            if (privacy) {
                BackHandler { privacy = false }
                PrivacyScreen { privacy = false }
            } else if (routine != null) {
                PlayerScreen(routine) { playing = null; refresh() }
            } else if (gym != null) {
                val plan = Prefs.plan(gym!!) ?: FREE_PLAN
                GymSessionScreen(plan) { gym = null; refresh() }
            } else if (cardio != null || mx.loperstack.tokkipfit.data.CardioLive.running) {
                BackHandler { }
                CardioLiveScreen(mx.loperstack.tokkip.fit.Sport.of(cardio ?: mx.loperstack.tokkipfit.data.CardioLive.sport.id)) { cardio = null; refresh() }
            } else if (breathe != null) {
                val p = mx.loperstack.tokkip.wellness.BREATH_PATTERNS.firstOrNull { it.id == breathe } ?: mx.loperstack.tokkip.wellness.BREATH_PATTERNS.first()
                BreatheSessionScreen(p, breatheMin) { breathe = null; refresh() }
            } else if (editRoutine != null) {
                MyRoutineEditor(editRoutine.takeIf { it != "new" }) { editRoutine = null }
            } else if (editPlan != null) {
                MyPlanEditor(editPlan.takeIf { it != "new" }) { editPlan = null }
            } else if (badgesOpen) {
                BadgesScreen(today) { badgesOpen = false }
            } else if (wardrobe) {
                BackHandler { wardrobe = false }
                WardrobeScreen { wardrobe = false }
            } else {
            BackHandler(enabled = section != Section.TODAY) { section = Section.TODAY }
            val bottom = PaddingValues(bottom = 104.dp)
            AnimatedContent(
                section,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.03f))
                },
                label = "section",
            ) { s ->
                when (s) {
                    Section.TODAY -> TodayScreen(today, bottom, onConnect = connect, onSettings = { settings = true }, onRefresh = { refresh(); Sounds.play("blip") }, onBadges = { badgesOpen = true; Sounds.play("pop") })
                    Section.HOME -> HomeScreen(bottom, onStart = { playing = it.id; Sounds.play("pop") }, onEdit = { editRoutine = it ?: "new"; Sounds.play("pop") })
                    Section.GYM -> GymScreen(bottom, onStart = { gym = it.id; Sounds.play("pop") }, onEdit = { editPlan = it ?: "new"; Sounds.play("pop") })
                    Section.CARDIO -> CardioScreen(bottom) { cardio = it.id; Sounds.play("pop") }
                    Section.BREATHE -> BreatheScreen(bottom) { p, m -> breathe = p.id; breatheMin = m; Sounds.play("pop") }
                }
            }
            NavPill(section, Modifier.align(Alignment.BottomCenter)) {
                if (it != section) {
                    section = it
                    Sounds.play("blip")
                    Haptics.tick(context)
                }
            }
            }
        }
        if (settings) {
            ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = cs.surfaceContainerLow) {
                SettingsSheet(onPrivacy = { settings = false; privacy = true }, onWardrobe = { settings = false; wardrobe = true }, connected = !today.sample, onConnect = connect)
            }
        }
    }
}

/** The floating pill bar: the chosen section grows into a pill with its name. */
@Composable
private fun NavPill(current: Section, modifier: Modifier, onPick: (Section) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(14.dp, CircleShape)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (s in Section.entries) {
            val on = s == current
            val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "bg")
            val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, label = "fg")
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(s.icon, s.label, tint = fg, modifier = Modifier.size(22.dp))
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)), exit = fadeOut() + shrinkHorizontally()) {
                    Text(s.label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** What's coming to each section, with Tokkip already warming up. */
@Composable
private fun SoonScreen(s: Section, bottom: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val (phase, about, points) = when (s) {
        Section.GYM -> Triple("0.3", "Rutinas, series, repeticiones y descansos. Tokkip cuenta contigo y celebra tus récords.", listOf("Tus rutinas y ejercicios", "Series con peso, repeticiones y descanso", "Historial y récords por ejercicio"))
        Section.CARDIO -> Triple("0.4", "Correr, caminar y bici con GPS, desde el teléfono o el reloj.", listOf("Tu ruta en el mapa", "Ritmo, pulso y aviso por kilómetro", "Resumen con la ruta dibujada"))
        Section.HOME -> Triple("0.2", "Rutinas guiadas con temporizador, y Tokkip haciendo cada ejercicio contigo.", listOf("HIIT, fuerza y estiramiento", "Tokkip hace sentadillas, saltos y plancha", "Desde el teléfono o desde tu reloj"))
        else -> Triple("0.5", "Respiración y meditación guiada: Tokkip respira contigo.", listOf("Respiración 4-4-6 y caja", "Meditaciones cortas", "Se guarda como mindfulness en Health Connect"))
    }
    val mood = when (s) {
        Section.GYM -> Mood.FOCUS
        Section.BREATHE -> Mood.BREATHING
        else -> Mood.HAPPY
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = bottom.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Appear(0) { Title(s.label, Modifier.padding(top = 18.dp), size = 44.sp) }
        Appear(1) {
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(cs.primaryContainer, Modifier.size(230.dp))
                Tokkip(Modifier.size(170.dp, 150.dp), color = Color(Prefs.color), mood = mood, interactive = true, greet = true, dancing = s == Section.HOME)
            }
        }
        Appear(2) {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeIcon(s.icon, cs.primary, cs.onPrimary, 46.dp, MShapes.Clover8)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Llega en la versión $phase", style = MaterialTheme.typography.titleMedium)
                        Text(about, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        points.forEachIndexed { i, p ->
            Appear(3 + i) {
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(cs.tertiary))
                    Spacer(Modifier.width(12.dp))
                    Text(p, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Goals, Tokkip and sounds. */
@Composable
private fun SettingsSheet(onPrivacy: () -> Unit, onWardrobe: () -> Unit, connected: Boolean, onConnect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title("Ajustes", size = 30.sp)
        Text("Tus metas", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        val g = Prefs.goals
        Stepper("Pasos al día", fmt(g.steps), { Prefs.set(goals = g.copy(steps = g.steps - 500)) }, { Prefs.set(goals = g.copy(steps = g.steps + 500)) })
        Stepper("Minutos de ejercicio", "${g.minutes} min", { Prefs.set(goals = g.copy(minutes = g.minutes - 5)) }, { Prefs.set(goals = g.copy(minutes = g.minutes + 5)) })
        Stepper("Calorías activas", "${fmt(g.kcal)} kcal", { Prefs.set(goals = g.copy(kcal = g.kcal - 50)) }, { Prefs.set(goals = g.copy(kcal = g.kcal + 50)) })
        TextButton(onClick = { Prefs.set(goals = Goals()) }) { Text("Volver a las metas de siempre") }

        Text("Tokkip", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        FitCard(color = cs.primaryContainer, onClick = onWardrobe) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Tokkip(Modifier.size(64.dp, 56.dp), color = Color(Prefs.color), mood = Mood.HAPPY)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tu Tokkip", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                    Text(
                        if (Prefs.follow && Prefs.tokkipAround()) "Igual que en la app de Tokkip" else "Especie, colores, piel y accesorios",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sonidos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(Prefs.sounds, { Prefs.set(sounds = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Contarle a Tokkip", style = MaterialTheme.typography.bodyLarge)
                Text("Festeja tus entrenamientos en su app y le dan cariño", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(Prefs.tellTokkip, { Prefs.changeTellTokkip(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mapa bajo tu ruta", style = MaterialTheme.typography.bodyLarge)
                Text("Baja el mapa de OpenStreetMap solo de la zona de tu ruta; se guarda en el teléfono", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(Prefs.map, { Prefs.changeMap(it) })
        }

        Text("Health Connect", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text(
            if (connected) "Conectado: Tokkip lee tus pasos, ejercicio, sueño y pulso. Lo cambias cuando quieras en Health Connect."
            else "Sin conectar: Hoy muestra datos de ejemplo.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = onConnect) { Text(if (connected) "Revisar permisos" else "Conectar") }
            TextButton(onClick = onPrivacy) { Text("Privacidad") }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = { minus(); Sounds.play("tick") }) { Icon(Icons.Rounded.Remove, "Menos") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont))
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { plus(); Sounds.play("tick") }) { Icon(Icons.Rounded.Add, "Más") }
    }
}

/** Why Fit asks Health Connect for each thing (Health Connect opens this screen too). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
        FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
        Spacer(Modifier.height(8.dp))
        Title("Privacidad", size = 36.sp)
        Spacer(Modifier.height(12.dp))
        for ((t, b) in PRIVACY) {
            Text(t, style = MaterialTheme.typography.titleMedium, color = cs.primary)
            Text(b, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.padding(bottom = 14.dp))
        }
    }
}

private val PRIVACY = listOf(
    "Tus datos no salen del teléfono" to "Tokkip Fit no tiene servidor ni nube, y no manda telemetría. Lo que lee de Health Connect se queda en tu teléfono y solo sirve para mostrártelo.",
    "Qué lee de Health Connect" to "Pasos, distancia, calorías activas y sesiones de ejercicio (para tus anillos y tu semana), sueño (cuánto dormiste) y pulso (tu pulso en reposo). Cada permiso es opcional: sin uno, solo falta esa cifra.",
    "Qué escribe" to "Solo los entrenamientos que haces con Tokkip (desde el teléfono o el reloj): la sesión, sus calorías activas y, en cardio, la distancia y la ruta, para que también los veas en Samsung Health o Fitbit. Respira se guarda como sesión de mindfulness donde Health Connect las tiene. Nada más.",
    "Tu reloj" to "El reloj manda cada entrenamiento terminado a tu teléfono, directo y sin internet, y el teléfono le manda el look de Tokkip.",
    "El Tokkip de la app de Tokkip" to "Si quieres el mismo Tokkip, Fit lee su look (especie, colores, accesorios) de la app de Tokkip. Solo eso, y solo porque las dos están firmadas por ti.",
    "El mapa (si lo prendes)" to "Con \"Mapa bajo tu ruta\" prendido, Fit baja de OpenStreetMap los pedacitos de mapa de la zona de tu ruta para dibujarla encima. A OpenStreetMap solo le llega qué pedacitos pides (como cualquier mapa en internet), no tu cuenta ni nada más. Se guardan en el teléfono y se borran al apagarlo. Viene apagado.",
    "La app de Tokkip" to "Si la tienes, Fit le cuenta a Tokkip cada entrenamiento que terminas (qué, cuándo y cuánto duró) para que lo festeje y le dé cariño. Solo eso, de una app tuya a otra, sin internet. Lo apagas en Ajustes.",
    "Tu ubicación (Cardio)" to "Solo mientras corres, caminas o pedaleas, para dibujar tu ruta y medir la distancia. Se queda en tu teléfono y, si lo permites, en Health Connect con el entrenamiento.",
    "Cómo quitarle el acceso" to "En Health Connect → Permisos de apps → Tokkip Fit. Puedes quitar cualquier permiso o borrar lo que Fit haya escrito.",
)
