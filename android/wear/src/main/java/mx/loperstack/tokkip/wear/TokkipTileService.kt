// The Tokkip tile: the next event and how many tasks are left. Tapping it opens
// the app. Refreshed whenever the phone sends a new state (WatchStore).

package mx.loperstack.tokkip.wear

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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import mx.loperstack.tokkip.wellness.minutesUntil
import mx.loperstack.tokkip.wellness.nextEvents
import mx.loperstack.tokkip.wellness.pendingCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RES_VERSION = "1"

class TokkipTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        WatchStore.init(this)
        val snap = WatchStore.state.value
        val now = System.currentTimeMillis()
        val next = nextEvents(snap.events, now).firstOrNull { !it.allDay }
        val pending = pendingCount(snap.tasks)
        val violet = snap.tokkipColor.toInt()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId("tokkip")
                    .setWidth(dp(28f)).setHeight(dp(28f))
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(violet)).build())
                    .build(),
            )
            .addContent(spacer(6f))

        if (next != null) {
            val min = minutesUntil(next.begin, now)
            val whenText = when {
                min <= 0 -> "Ahora"
                min < 60 -> "En $min min"
                else -> SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(next.begin))
            }
            column.addContent(text(whenText, 12f, 0xFFC4B5FD.toInt(), bold = true))
            column.addContent(text(next.title, 15f, 0xFFFFFFFF.toInt(), bold = true, lines = 2))
        } else {
            column.addContent(text(if (snap.at == 0L) "Abre Tokkip en tu teléfono" else "Nada más por hoy", 13f, 0xFFA5A9B1.toInt()))
        }
        column.addContent(spacer(8f))
        column.addContent(
            text(
                if (pending == 0) "Sin pendientes ✓" else if (pending == 1) "1 pendiente" else "$pending pendientes",
                13f, if (pending == 0) 0xFF4ADE80.toInt() else 0xFFFFFFFF.toInt(), bold = pending > 0,
            ),
        )
        if (snap.phoneBattery >= 0) {
            column.addContent(spacer(4f))
            column.addContent(text("Teléfono ${snap.phoneBattery}%${if (snap.phoneCharging) " ⚡" else ""}", 11f, 0xFFA5A9B1.toInt()))
        }

        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(column.build())
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RES_VERSION)
                .addIdToImageMapping(
                    "tokkip",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

    private fun spacer(h: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 1) =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(lines)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()
}
