// Tokkip Fit on the watch face and in the tiles: today's three rings (steps,
// minutes, calories) as the phone last read them from Health Connect, with
// Tokkip in the middle and a tap to train; and a complication with your steps
// as a ring. The phone sends the numbers (/tokkipfit/today) whenever it reads them.

package mx.loperstack.tokkipfit.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import mx.loperstack.tokkip.character.StatIcon
import org.json.JSONObject

/** Hoy's numbers, as the phone last sent them. */
data class Rings(val steps: Long = 0, val goalSteps: Long = 8000, val minutes: Long = 0, val goalMinutes: Long = 30, val kcal: Long = 0, val goalKcal: Long = 400, val streak: Int = 0, val at: Long = 0) {
    fun ring(v: Long, g: Long) = if (g <= 0) 0f else (v.toFloat() / g).coerceIn(0f, 1f)

    companion object {
        fun parse(raw: String?): Rings? = runCatching {
            val o = JSONObject(raw ?: return null)
            Rings(o.optLong("steps"), o.optLong("goalSteps", 8000), o.optLong("minutes"), o.optLong("goalMinutes", 30), o.optLong("kcal"), o.optLong("goalKcal", 400), o.optInt("streak"), o.optLong("at"))
        }.getOrNull()

        fun load(context: Context): Rings = parse(context.getSharedPreferences("fitwear", Context.MODE_PRIVATE).getString("today", null)) ?: Rings()

        /** Keeps what the phone sent, and asks the tile and the complication to redraw. */
        fun save(context: Context, raw: String) {
            context.getSharedPreferences("fitwear", Context.MODE_PRIVATE).edit().putString("today", raw).apply()
            runCatching { TileService.getUpdater(context).requestUpdate(FitTileService::class.java) }
            runCatching { ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, StepsComplicationService::class.java)).requestUpdateAll() }
        }
    }
}

private const val TEAL = 0xFF00E5FF.toInt()
private const val LIME = 0xFFD7FF3A.toInt()
private const val CORAL = 0xFFFF3D8B.toInt()

private fun fmt(n: Long): String = String.format(java.util.Locale.US, "%,d", n)

class FitTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val r = Rings.load(this)
        WatchLook.load(this)
        fun arc(size: Float, color: Int, v: Float) = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees(359.9f)).setThickness(dp(9f)).setColor(argb((color and 0x00FFFFFF) or 0x38000000)).build())
            .build().let { track ->
                LayoutElementBuilders.Box.Builder().setWidth(dp(size)).setHeight(dp(size))
                    .addContent(track)
                    .addContent(
                        LayoutElementBuilders.Arc.Builder()
                            .setAnchorAngle(degrees(0f))
                            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                            .addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees((360f * v).coerceAtLeast(0.5f))).setThickness(dp(9f)).setColor(argb(color)).build())
                            .build(),
                    )
                    .build()
            }
        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder().setResourceId("tokkip").setWidth(dp(26f)).setHeight(dp(26f))
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(TEAL)).build()).build(),
            )
            .addContent(text(if (r.at == 0L) "Abre Fit" else fmt(r.steps), 16f, 0xFFFFFFFF.toInt(), true))
            .addContent(text(if (r.at == 0L) "en el teléfono" else "pasos", 11f, 0xFFA5A9B1.toInt()))
            .addContent(text(if (r.streak > 0) "Racha ${r.streak}" else "${r.minutes} min · ${r.kcal} kcal", 11f, LIME))
            .build()
        val open = ModifiersBuilders.Clickable.Builder().setId("train").setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(FitWearActivity::class.java.name).build(),
            ).build(),
        ).build()
        val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(arc(186f, TEAL, r.ring(r.steps, r.goalSteps)))
            .addContent(arc(162f, LIME, r.ring(r.minutes, r.goalMinutes)))
            .addContent(arc(138f, CORAL, r.ring(r.kcal, r.goalKcal)))
            .addContent(center)
            .build()
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder().setResourcesVersion("1-" + mx.loperstack.tokkip.character.TokkipStyle.species.name)
                .setFreshnessIntervalMillis(30 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root)).build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion("1-" + mx.loperstack.tokkip.character.TokkipStyle.species.name)
                .addIdToImageMapping(
                    "tokkip",
                    ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(StatIcon.current()).build(),
                    ).build(),
                ).build(),
        )

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder().setText(s).setMaxLines(1).setFontStyle(
            LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build(),
        ).build()
}

/** Your steps today, as a ring on the watch face (or "6,2k"). */
class StepsComplicationService : SuspendingComplicationDataSourceService() {
    private fun plain(s: String) = PlainComplicationText.Builder(s).build()

    private fun data(type: ComplicationType, r: Rings, tap: Boolean): ComplicationData? {
        val short = if (r.steps >= 1000) String.format(java.util.Locale.US, "%.1fk", r.steps / 1000.0) else "${r.steps}"
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, StatIcon.current())).build()
        val desc = plain("${r.steps} de ${r.goalSteps} pasos")
        val open = PendingIntent.getActivity(this, 0, Intent(this, FitWearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(r.steps.coerceAtMost(r.goalSteps).toFloat(), 0f, r.goalSteps.toFloat().coerceAtLeast(1f), desc)
                .setText(plain(short)).setMonochromaticImage(icon).apply { if (tap) setTapAction(open) }.build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(short), desc)
                .setTitle(plain("Pasos")).setMonochromaticImage(icon).apply { if (tap) setTapAction(open) }.build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = data(request.complicationType, Rings.load(this), true)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, Rings(steps = 6_240, goalSteps = 8_000), false)
}
