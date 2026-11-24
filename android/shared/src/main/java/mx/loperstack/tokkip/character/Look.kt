// How Tokkip looks, in one piece: body colour, species, skin, accent, eyes and
// mouth, cheeks, and what he wears ("auto" = the season's). Tokkip shares it
// with his other apps (Tokkip Fit) through LOOK_URI, a provider only apps
// signed with the same key can read; each app can also keep its own.

package mx.loperstack.tokkip.character

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class Look(
    val color: Long = 0xFFF5F6F8,
    val species: String = "CONEJO",
    val skin: String = "LISA",
    val accent: Long = 0xFF3F9E9A,
    /** 0 = automatic. */
    val ink: Long = 0,
    val cheek: Long = 0,
    /** "auto" or an Outfit's name. */
    val outfit: String = "auto",
) {
    /** Puts it on Tokkip, everywhere he's drawn in this app. */
    fun apply() {
        TokkipStyle.species = Species.of(species)
        TokkipStyle.skin = Skin.of(skin)
        TokkipStyle.accent = Color(accent)
        TokkipStyle.ink = if (ink == 0L) Color.Unspecified else Color(ink)
        TokkipStyle.cheek = if (cheek == 0L) Color.Unspecified else Color(cheek)
        TokkipStyle.outfit = Outfit.resolve(outfit)
    }

    fun json(): String = JSONObject()
        .put("v", 1).put("color", color).put("species", species).put("skin", skin)
        .put("accent", accent).put("ink", ink).put("cheek", cheek).put("outfit", outfit)
        .toString()

    companion object {
        /** Tokkip's own look, for his other apps (needs LOOK_PERMISSION, a signature permission). */
        val LOOK_URI: Uri by lazy { Uri.parse("content://mx.loperstack.tokkip.look/look") }
        const val LOOK_PERMISSION = "mx.loperstack.tokkip.permission.READ_LOOK"

        /** The Data Layer path Fit's phone app sends its look to its watch app on. */
        const val FIT_LOOK_PATH = "/tokkipfit/look"

        fun parse(raw: String?): Look? = runCatching {
            val o = JSONObject(raw ?: return null)
            val d = Look()
            Look(
                color = o.optLong("color", d.color),
                species = o.optString("species", d.species),
                skin = o.optString("skin", d.skin),
                accent = o.optLong("accent", d.accent),
                ink = o.optLong("ink", 0),
                cheek = o.optLong("cheek", 0),
                outfit = o.optString("outfit", d.outfit),
            )
        }.getOrNull()

        /** Tokkip's look, if Tokkip is installed and lets this app read it. */
        fun fromTokkip(context: Context): Look? = runCatching {
            context.contentResolver.query(LOOK_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) parse(c.getString(0)) else null
            }
        }.getOrNull()
    }
}
