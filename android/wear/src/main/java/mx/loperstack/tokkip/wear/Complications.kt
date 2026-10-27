// Watch face complications: how many tasks are left, and the phone's battery.
// Both read the last state the phone sent (WatchStore) and open Tokkip on tap.

package mx.loperstack.tokkip.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import mx.loperstack.tokkip.wellness.pendingCount

private fun plain(s: String) = PlainComplicationText.Builder(s).build()

private fun tokkipIcon(context: Context) =
    MonochromaticImage.Builder(Icon.createWithResource(context, mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)).build()

private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context, 0,
        Intent(context, WearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** "3" with Tokkip's icon: tasks still to do. */
class PendingComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(count: Int, tap: Boolean): ComplicationData =
        ShortTextComplicationData.Builder(plain(count.toString()), plain("$count pendientes"))
            .setTitle(plain("Tareas"))
            .setMonochromaticImage(tokkipIcon(this))
            .apply { if (tap) setTapAction(openApp(this@PendingComplicationService)) }
            .build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        WatchStore.init(this)
        return data(pendingCount(WatchStore.state.value.tasks), tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) data(3, tap = false) else null
}

/** The phone's battery, as a ring or a short "82%". */
class PhoneBatteryComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(type: ComplicationType, level: Int, charging: Boolean, tap: Boolean): ComplicationData? {
        val known = level >= 0
        val label = if (known) "$level%" else "–"
        val desc = plain(if (known) "Batería del teléfono $level%" else "Batería del teléfono desconocida")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(if (known) level.toFloat() else 0f, 0f, 100f, desc)
                    .setText(plain(if (charging) "⚡$label" else label))
                    .setMonochromaticImage(tokkipIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@PhoneBatteryComplicationService)) }
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(label), desc)
                    .setTitle(plain(if (charging) "⚡ Tel" else "Tel"))
                    .setMonochromaticImage(tokkipIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@PhoneBatteryComplicationService)) }
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WatchStore.init(this)
        val s = WatchStore.state.value
        return data(request.complicationType, s.phoneBattery, s.phoneCharging, tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, 82, false, tap = false)
}

/** Tokkip's doomscrolling indicator: today's scroll against your goal, as a ring or "45m". */
class DoomComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(type: ComplicationType, scroll: Int, goal: Int, level: Int, tap: Boolean): ComplicationData? {
        val name = mx.loperstack.tokkip.wellness.DOOM_LEVELS[level.coerceIn(0, 3)].name
        val label = if (scroll < 60) "${scroll}m" else "${scroll / 60}h${if (scroll % 60 > 0) "${scroll % 60}" else ""}"
        val desc = plain("Scroll de hoy: $scroll de $goal minutos. Tokkip $name")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(scroll.coerceAtMost(goal).toFloat(), 0f, goal.coerceAtLeast(1).toFloat(), desc)
                    .setText(plain(label))
                    .setTitle(plain(name))
                    .setMonochromaticImage(tokkipIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@DoomComplicationService)) }
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(label), desc)
                    .setTitle(plain(name))
                    .setMonochromaticImage(tokkipIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@DoomComplicationService)) }
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WatchStore.init(this)
        val s = WatchStore.state.value
        val fresh = System.currentTimeMillis() - s.doomAt < 60 * 60_000L
        return data(request.complicationType, if (fresh) s.scrollMin else 0, s.scrollGoal, if (fresh) s.doom else 0, tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, 45, 60, 1, tap = false)
}

