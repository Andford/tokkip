// Tokkip in the car, on Android Auto. Only navigation, point-of-interest and
// weather apps may draw on the screen's map surface. Tokkip is a POI app using
// the map-with-content template (Car App Library 1.7): Android Auto does not
// list sideloaded navigation apps, and a POI app also never takes Google Maps'
// place. The surface shows the ambient glow with Tokkip and the time; the list
// card beside it holds the panel: what's playing, what's next, your tasks.
// Hosts too old for that template get the list alone.
// Personal use: installed from the APK with Android Auto's "unknown sources".

package mx.loperstack.tokkip.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import mx.loperstack.tokkip.R
import mx.loperstack.tokkip.data.NowPlaying
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.Weather
import mx.loperstack.tokkip.character.Sounds

class TokkipCarService : CarAppService() {
    // Any Android Auto host may connect: fine for a sideloaded personal build.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = TokkipSession()
}

class TokkipSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = TokkipScreen(carContext)
}

class TokkipScreen(context: CarContext) : Screen(context), DefaultLifecycleObserver {
    private val painter = CarPainter(context) { invalidate() }
    private val appManager get() = carContext.getCarService(AppManager::class.java)

    init { lifecycle.addObserver(this) }

    override fun onCreate(owner: LifecycleOwner) {
        Store.init(carContext)
        Store.applyLook()
        Sounds.init(carContext)
        Sounds.enabled = Store.state.value.soundOn
        appManager.setSurfaceCallback(painter)
        painter.onBreak = {
            CarToast.makeText(carContext, "Llevas 2 horas manejando. ¿Una pausa?", CarToast.LENGTH_LONG).show()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        NowPlaying.start(carContext)
        painter.start()
        askLocationOnce()
    }

    /** The weather needs your approximate location: asked once, on the phone. */
    private fun askLocationOnce() {
        if (Weather.granted(carContext)) return
        val prefs = carContext.getSharedPreferences("tokkip_car", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedLocation", false)) return
        prefs.edit().putBoolean("askedLocation", true).apply()
        runCatching {
            carContext.requestPermissions(listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)) { granted, _ ->
                if (granted.isNotEmpty()) Weather.refresh(carContext, force = true)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        painter.stop()
        NowPlaying.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching { appManager.setSurfaceCallback(null) }
    }

    /** The panel as rows: what's playing, what's next today, and the tasks left. */
    private fun panelRows(): ItemList {
        val items = ItemList.Builder()
        val track = NowPlaying.track.value
        val es = java.util.Locale("es", "MX")
        val clock = java.text.SimpleDateFormat("h:mm a", es)
        if (track != null && track.title.isNotBlank()) {
            items.addItem(
                Row.Builder()
                    .setTitle((if (track.playing) "Sonando · " else "En pausa · ") + track.title)
                    .addText(listOf(track.artist, track.app).filter { it.isNotBlank() }.joinToString(" · "))
                    .build(),
            )
        } else if (!NowPlaying.granted(carContext)) {
            items.addItem(Row.Builder().setTitle("Música").addText("Permite el acceso en Tokkip → Enfoque").build())
        }
        val now = System.currentTimeMillis()
        mx.loperstack.tokkip.data.nextEvent(painter.upcoming(), now)?.let { e ->
            items.addItem(Row.Builder().setTitle(e.title).addText("Próximo · ${clock.format(java.util.Date(e.begin))}").build())
        }
        Weather.now?.let { w ->
            items.addItem(Row.Builder().setTitle("${w.temp}° · ${Weather.label(w.code)}").addText("Hoy ${w.max}° / ${w.min}°").build())
        }
        val tasks = mx.loperstack.tokkip.wellness.orderedTasks(Store.state.value.tasks).filter { !it.done }
        if (tasks.isEmpty()) {
            items.addItem(Row.Builder().setTitle("Todo al día").addText("Disfruta el camino").build())
        } else {
            for (t in tasks.take(3)) items.addItem(Row.Builder().setTitle(t.text).addText("Pendiente").build())
        }
        return items.build()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    override fun onGetTemplate(): Template {
        val playing = NowPlaying.track.value?.playing == true
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (painter.ambient) R.drawable.ic_car_dashboard else R.drawable.ic_car_ambient))
                    .setOnClickListener { painter.toggle(); invalidate() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (playing) R.drawable.ic_car_pause else R.drawable.ic_car_play))
                    .setOnClickListener { painter.touch(); NowPlaying.playPause() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_car_next))
                    .setOnClickListener { painter.touch(); NowPlaying.next() }
                    .build(),
            )
            .build()
        val list = ListTemplate.Builder()
            .setTitle("Tokkip")
            .setSingleList(panelRows())
            .setActionStrip(strip)
            .build()
        if (carContext.carAppApiLevel < 7) return list
        return MapWithContentTemplate.Builder()
            .setContentTemplate(ListTemplate.Builder().setTitle("Tokkip").setSingleList(panelRows()).build())
            .setActionStrip(strip)
            .build()
    }
}
