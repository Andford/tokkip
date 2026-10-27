// Sync's rules, without Android (tested in src/test): the crypto, how a page
// travels, and how a page from the server is settled against this device.
// The same rules as Tokkip Notas for Windows (notas-pc/src-tauri/src/sync.rs):
//   key  = PBKDF2-HMAC-SHA256(your phrase in NFC, the server's salt, 150 000, 32 bytes)
//   data = base64(iv[12] ‖ AES-256-GCM(JSON of the page))
// The page JSON is the copy format without "opened" (that stays on each device).

package mx.loperstack.tokkipnotas.data

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SyncCrypto {
    const val ROUNDS = 150_000
    const val CHECK = "tokkip-notas:check"
    private val random = SecureRandom()

    fun derive(phrase: String, saltB64: String): ByteArray {
        val salt = Base64.getDecoder().decode(saltB64.trim())
        val nfc = Normalizer.normalize(phrase, Normalizer.Form.NFC)
        val spec = PBEKeySpec(nfc.toCharArray(), salt, ROUNDS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun seal(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }

    fun open(key: ByteArray, data: String): ByteArray? = runCatching {
        val raw = Base64.getDecoder().decode(data.trim())
        if (raw.size < 28) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, 12))
        c.doFinal(raw, 12, raw.size - 12)
    }.getOrNull()

    /** A fingerprint of a page's words (title and text). */
    fun fingerprint(title: String, body: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(title.toByteArray())
        md.update(0)
        md.update(body.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** A page as it travels: the copy format, without "opened". */
fun travelJson(p: Page, body: String): JSONObject = JSONObject()
    .put("id", p.id).put("parent", p.parent ?: JSONObject.NULL).put("title", p.title).put("icon", p.icon).put("color", p.color)
    .put("shape", p.shape).put("kind", p.kind.name).put("ord", p.ord).put("created", p.created).put("updated", p.updated)
    .put("favorite", p.favorite).put("trashed", p.trashed).put("props", propsJson(p.props)).put("schema", schemaJson(p.schema)).put("body", body)

fun travelOf(o: JSONObject): Pair<Page, String> = Page(
    id = o.optString("id"), parent = if (o.isNull("parent")) null else o.optString("parent"), title = o.optString("title"),
    icon = o.optString("icon").ifEmpty { "doc" }, color = o.optLong("color"), shape = o.optInt("shape"),
    kind = runCatching { PageKind.valueOf(o.optString("kind")) }.getOrDefault(PageKind.DOC), ord = o.optDouble("ord", 0.0),
    created = o.optLong("created"), updated = o.optLong("updated"), favorite = o.optBoolean("favorite"), trashed = o.optLong("trashed"),
    props = propsOf(o.optString("props")), schema = schemaOf(o.optString("schema")),
) to o.optString("body")

/** What sync knows of a page here. */
data class SyncState(val ver: Long, val dirty: Boolean, val base: String?)

/** What to do with a page from the server. */
sealed class Settle {
    /** Already here, or ours is newer and has nothing to learn. */
    object Skip : Settle()
    /** Take the server's version as it is. */
    object Take : Settle()
    /** Keep ours (maybe with their words or their rest merged in), waiting to go up at `ver`. */
    data class Keep(val page: Page?, val body: String?, val ver: Long, val base: String?, val deleted: Boolean) : Settle()
}

/**
 * Settles a page from the server against this device — the same rules as Windows:
 * when this device's copy waits to go up too, the words and the rest are weighed
 * apart against what both last agreed on (`base`): only one side changed the
 * words → those words with the newer side's rest; both changed them → the newer
 * wins and the other is kept as a copy "(conflicto)" (`copy`); a delete loses
 * against new words and wins against anything else.
 */
fun settle(
    id: String,
    ver: Long,
    remote: Pair<Page, String>?,
    state: SyncState?,
    local: Pair<Page, String>?,
    copy: (Page, String) -> Unit,
): Settle {
    if (state != null && !state.dirty && ver <= state.ver) return Settle.Skip
    if (state == null || !state.dirty) return Settle.Take
    if (id == INBOX_ID) return if (ver >= state.ver) Settle.Take else Settle.Skip
    val fp = { p: Pair<Page, String>? -> p?.let { SyncCrypto.fingerprint(it.first.title, it.second) } }
    val mine = fp(local)
    val theirs = fp(remote)
    val remoteNewer = ver >= state.ver
    val editedHere = mine != state.base
    val changedThere = theirs != state.base
    if (theirs == mine) return if (remoteNewer) Settle.Take else Settle.Keep(null, null, state.ver, theirs, local == null)
    if (!editedHere) {
        return if (local != null && remote != null && !remoteNewer) {
            Settle.Keep(local.first.copy(title = remote.first.title), remote.second, state.ver, theirs, false)
        } else Settle.Take
    }
    if (!changedThere) {
        if (!remoteNewer) return Settle.Skip
        return if (local != null && remote != null) {
            Settle.Keep(remote.first.copy(title = local.first.title), local.second, ver + 1, state.base, false)
        } else Settle.Keep(null, null, ver + 1, state.base, local == null)
    }
    return when {
        local == null && remote != null -> Settle.Take
        local != null && remote == null -> Settle.Keep(null, null, maxOf(state.ver, ver + 1), null, false)
        local != null && remote != null -> if (remoteNewer) {
            if (local.first.kind != PageKind.COLLECTION) copy(local.first, local.second)
            Settle.Take
        } else {
            if (remote.first.kind != PageKind.COLLECTION) copy(remote.first, remote.second)
            Settle.Keep(null, null, state.ver, theirs, false)
        }
        else -> Settle.Skip
    }
}
