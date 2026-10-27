// Your mood from the wrist: a tile with Tokkip's five faces (one tap logs it in
// the phone's diary) and a complication with today's face on your watch face.

package mx.loperstack.tokkip.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
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
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkip.wellness.MOODS
import mx.loperstack.tokkip.wellness.moodOf
import java.io.ByteArrayOutputStream

private const val MOOD_RES = "mood-v1"

private fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

/** Opens the app on its mood screen. */
private fun openMood(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 11,
    Intent(context, WearActivity::class.java).putExtra(WearActivity.EXTRA_SCREEN, "mood")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

class MoodTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        WatchStore.init(this)
        // A face was tapped: log it, then draw the tile with it chosen.
        val clicked = requestParams.currentState.lastClickableId
        if (clicked.startsWith("mood_")) clicked.removePrefix("mood_").toIntOrNull()?.takeIf { it in 1..5 }?.let { WatchStore.mood(it, sound = false) }
        val today = WatchStore.todayMood()
        val name = WatchStore.state.value.name

        fun face(value: Int): LayoutElementBuilders.LayoutElement {
            val m = moodOf(value)!!
            val on = today == value
            val mods = ModifiersBuilders.Modifiers.Builder()
                .setClickable(
                    ModifiersBuilders.Clickable.Builder().setId("mood_$value")
                        .setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
                )
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(if (on) Color(m.color).copy(alpha = 0.45f).toArgbInt() else 0x22FFFFFF))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(22f)).build())
                        .build(),
                )
                .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(4f)).build())
                .build()
            return LayoutElementBuilders.Box.Builder()
                .setWidth(dp(44f)).setHeight(dp(44f))
                .setModifiers(mods)
                .addContent(LayoutElementBuilders.Image.Builder().setResourceId("face$value").setWidth(dp(36f)).setHeight(dp(30f)).build())
                .build()
        }

        fun row(values: List<Int>) = LayoutElementBuilders.Row.Builder().apply {
            values.forEachIndexed { i, v ->
                if (i > 0) addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                addContent(face(v))
            }
        }.build()

        fun text(s: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build(),
            ).build()

        val chosen = moodOf(today)
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(if (name.isBlank()) "¿Cómo te sientes?" else "¿Cómo estás, $name?", 13f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(row(listOf(5, 4, 3)))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(row(listOf(2, 1)))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(
                text(
                    chosen?.let { "Hoy: ${it.label}" } ?: "Toca una cara",
                    12f, chosen?.let { Color(it.color).toArgbInt() } ?: 0xFFA5A9B1.toInt(),
                ),
            )
            .build()
        val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand()).addContent(column).build()
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(MOOD_RES)
                .setFreshnessIntervalMillis(60 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val res = ResourceBuilders.Resources.Builder().setVersion(MOOD_RES)
        for (m in MOODS) {
            val bmp = tokkipBitmap(96, 80, Color(m.color), face = m.face)
            res.addIdToImageMapping(
                "face${m.value}",
                ResourceBuilders.ImageResource.Builder().setInlineResource(
                    ResourceBuilders.InlineImageResource.Builder()
                        .setData(png(bmp)).setWidthPx(96).setHeightPx(80)
                        .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED).build(),
                ).build(),
            )
        }
        return Futures.immediateFuture(res.build())
    }
}

private fun Color.toArgbInt(): Int = toArgb()

/** Today's face on the watch face (a small image), or "Bien" with Tokkip's icon. */
class MoodComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(type: ComplicationType, value: Int, tap: Boolean): ComplicationData? {
        val m = moodOf(value)
        val desc = PlainComplicationText.Builder(m?.let { "Ánimo de hoy: ${it.label}" } ?: "Anota tu ánimo").build()
        return when (type) {
            ComplicationType.SMALL_IMAGE -> {
                val bmp = tokkipBitmap(120, 100, m?.let { Color(it.color) } ?: Color(0xFFF5F6F8), face = m?.face)
                SmallImageComplicationData.Builder(SmallImage.Builder(Icon.createWithBitmap(bmp), SmallImageType.ICON).build(), desc)
                    .apply { if (tap) setTapAction(openMood(this@MoodComplicationService)) }
                    .build()
            }
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(m?.label ?: "¿Ánimo?").build(), desc)
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(Icon.createWithResource(this, mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)).build(),
                    )
                    .apply { if (tap) setTapAction(openMood(this@MoodComplicationService)) }
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WatchStore.init(this)
        return data(request.complicationType, WatchStore.todayMood(), tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, 4, tap = false)
}
