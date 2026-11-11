// Tokkip shared with Tokkip PC, through Tokkip Phone: the same species,
// colours and outfit on both, and how you feel today (the diary's mood and
// last night's rest) for the island. This app hands its Tokkip to Tokkip
// Phone (an explicit broadcast), which carries it to the PC; Tokkip PC's comes
// back the same way (PcTokkipReceiver). The newer look wins (`changedAt`).
// The JSON is described in Andford/tokkipphone, docs/protocol/tokkip.md.

package mx.loperstack.tokkip.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** A look as both apps write it (Tokkip PC's ids). */
data class SharedLook(
    val species: String,
    val skin: String,
    val body: Long,
    val accent: Long,
    /** 0: automatic. */
    val ink: Long,
    val cheek: Long,
    /** Tokkip PC's outfit id ("auto", "none", "partyHat"…). */
    val outfit: String,
)

data class Shared(val changedAt: Long, val look: SharedLook)

/** The JSON both apps write: pure, so it's tested without a phone. */
object TokkipJson {
    private const val MAX_LEN = 4096

    /** This app's outfit names ↔ Tokkip PC's ids (wardrobe.ts). */
    private val OUTFITS = mapOf(
        "auto" to "auto", "NONE" to "none", "PARTY" to "partyHat", "CROWN" to "crown", "BEANIE" to "beanie",
        "HEARTS" to "hearts", "SPRING" to "flowerCrown", "SUNGLASSES" to "sunglasses", "STRAW" to "strawHat",
        "TRICOLOR" to "tricolor", "WITCH" to "witchHat", "FLOWERS" to "marigolds", "SCARF" to "scarf",
        "ANTLERS" to "antlers", "SANTA" to "santaHat", "GLASSES" to "roundGlasses", "BOW" to "bow",
        "HEADPHONES" to "headphones",
    )
    private val SPECIES = setOf("conejo", "gato", "perro", "robot", "mapache")

    fun outfitToPc(name: String): String = OUTFITS[name] ?: "auto"
    fun outfitFromPc(id: String): String = OUTFITS.entries.firstOrNull { it.value == id }?.key ?: "auto"

    private fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)
    private fun argbOf(hex: String?): Long? =
        hex?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.substring(1)?.toLong(16)?.or(0xFF000000)

    fun lookOf(d: AppData) = SharedLook(
        species = d.species.lowercase(), skin = d.skin.lowercase(), body = d.tokkipColor, accent = d.accent,
        ink = d.faceInk, cheek = d.cheek, outfit = outfitToPc(d.outfit),
    )

    /** What tells one look from another. */
    fun keyOf(l: SharedLook) = listOf(l.species, l.skin, l.body and 0xFFFFFF, l.accent and 0xFFFFFF, l.ink, l.cheek, l.outfit).joinToString("|")

    /** Today's mood (1…5, 0 none) and last night's rest, as Tokkip PC reads them. */
    fun feelingJson(d: AppData, now: Long): JSONObject? {
        val today = dayKey(now)
        val mood = d.logs.firstOrNull { it.day == today }?.averageMood ?: 0
        val rest = when (restLevel(d.sleep, now)) {
            mx.loperstack.tokkip.character.Rest.TIRED -> "tired"
            mx.loperstack.tokkip.character.Rest.OK -> "ok"
            mx.loperstack.tokkip.character.Rest.GREAT -> "great"
            else -> ""
        }
        if (mood == 0 && rest.isEmpty()) return null
        return JSONObject().put("day", today).put("mood", mood).put("rest", rest)
    }

    fun json(look: SharedLook, changedAt: Long, feeling: JSONObject?): String = JSONObject()
        .put("v", 1).put("from", "phone").put("changedAt", changedAt)
        .put(
            "look", JSONObject()
                .put("species", look.species).put("skin", look.skin)
                .put("body", hex(look.body)).put("accent", hex(look.accent))
                .put("ink", if (look.ink == 0L) "" else hex(look.ink))
                .put("cheek", if (look.cheek == 0L) "" else hex(look.cheek))
                .put("outfit", look.outfit),
        )
        .apply { if (feeling != null) put("feeling", feeling) }
        .toString()

    /** The PC's Tokkip, or null when it isn't one. */
    fun parse(raw: String?): Shared? {
        if (raw == null || raw.length > MAX_LEN) return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (o.optInt("v") != 1) return null
        val changedAt = o.optLong("changedAt", -1).takeIf { it >= 0 } ?: return null
        val l = o.optJSONObject("look") ?: return null
        val species = l.optString("species").takeIf { it in SPECIES } ?: "conejo"
        val skin = if (l.optString("skin") == "ensueno") "ensueno" else "lisa"
        return Shared(
            changedAt,
            SharedLook(
                species = species, skin = skin,
                body = argbOf(l.optString("body")) ?: 0xFFF7EAD3, accent = argbOf(l.optString("accent")) ?: 0xFF3F9E9A,
                ink = argbOf(l.optString("ink")) ?: 0L, cheek = argbOf(l.optString("cheek")) ?: 0L,
                outfit = l.optString("outfit").takeIf { id -> OUTFITS.containsValue(id) } ?: "auto",
            ),
        )
    }
}

object PcTokkip {
    const val TOKKIP_PHONE = "mx.loperstack.tokkiphone"
    /** To Tokkip Phone: here is this phone's Tokkip. */
    const val ACTION_SEND = "mx.loperstack.tokkiphone.action.TOKKIP"
    /** From Tokkip Phone: here is the PC's. */
    const val ACTION_FROM_PC = "mx.loperstack.tokkip.action.PC_TOKKIP"
    const val EXTRA_JSON = "json"
    private const val PREFS = "pc_tokkip"

    // ── On the phone ──────────────────────────────────────────────────────────

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pushApp: Context? = null
    private val pushLater = Runnable { pushApp?.let { pushNow(it) } }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When the last PC's Tokkip came in (0: never), for Ajustes → Tus dispositivos. */
    fun lastFromPc(context: Context): Long = prefs(context).getLong("fromPc", 0)

    /** Something changed (the look, the diary, the night): tell Tokkip Phone, soon. */
    fun push(context: Context) {
        pushApp = context.applicationContext
        main.removeCallbacks(pushLater)
        main.postDelayed(pushLater, 1500)
    }

    private fun pushNow(context: Context) {
        val app = context.applicationContext
        val d = Store.state.value
        val look = TokkipJson.lookOf(d)
        val key = TokkipJson.keyOf(look)
        val p = prefs(app)
        var changedAt = p.getLong("changedAt", 0)
        if (p.getString("key", null) != key) {
            // The look changed here (the PC's own changes come with their time: applyFromPc).
            if (p.contains("key")) changedAt = System.currentTimeMillis()
            p.edit().putString("key", key).putLong("changedAt", changedAt).apply()
        }
        val json = TokkipJson.json(look, changedAt, TokkipJson.feelingJson(d, System.currentTimeMillis()))
        if (json == p.getString("sent", null)) return
        val sent = runCatching {
            app.sendBroadcast(
                Intent(ACTION_SEND).setPackage(TOKKIP_PHONE).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra(EXTRA_JSON, json),
            )
        }.isSuccess
        if (sent) p.edit().putString("sent", json).apply()
    }

    /** The PC's Tokkip: his look becomes this one if he changed later. */
    fun applyFromPc(context: Context, raw: String?) {
        val shared = TokkipJson.parse(raw) ?: return
        Store.init(context)
        val p = prefs(context)
        p.edit().putLong("fromPc", System.currentTimeMillis()).apply()
        if (shared.changedAt <= p.getLong("changedAt", 0)) return
        val l = shared.look
        // Remembered first, so the changes below aren't taken for new ones made here.
        p.edit().putString("key", TokkipJson.keyOf(l)).putLong("changedAt", shared.changedAt).apply()
        Store.setTokkip(species = l.species.uppercase(), skin = l.skin.uppercase(), accent = l.accent)
        Store.setColor(l.body)
        Store.setFaceColors(faceInk = l.ink, cheek = l.cheek)
        Store.setOutfit(TokkipJson.outfitFromPc(l.outfit))
    }
}

/** Tokkip Phone brings the PC's Tokkip. */
class PcTokkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PcTokkip.ACTION_FROM_PC) return
        PcTokkip.applyFromPc(context, intent.getStringExtra(PcTokkip.EXTRA_JSON))
    }
}
