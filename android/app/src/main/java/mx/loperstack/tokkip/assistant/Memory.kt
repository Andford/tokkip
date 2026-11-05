package mx.loperstack.tokkip.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One line of the conversation. */
data class Line(val fromYou: Boolean, val text: String, val at: Long = System.currentTimeMillis())

/**
 * What you and Tokkip have talked about, kept only on this phone and
 * encrypted with a key that lives in the Android Keystore (it never leaves
 * the phone's secure hardware, and goes away with the app). Tokkip remembers
 * the last [KEEP] lines; the model sees the most recent few each time.
 */
object Memory {
    private const val FILE = "platicas.bin"
    private const val KEY = "tokkip_platicas"
    const val KEEP = 400

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun load(context: Context): List<Line> = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        val bytes = f.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONArray(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Line(o.optBoolean("tu"), o.optString("texto"), o.optLong("en"))
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, lines: List<Line>) {
        runCatching {
            val json = JSONArray()
            for (l in lines.takeLast(KEEP)) json.put(JSONObject().put("tu", l.fromYou).put("texto", l.text).put("en", l.at))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeBytes(sealed)
            tmp.renameTo(File(context.filesDir, FILE))
        }
    }

    fun forget(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
