// Talking to Tokkip from the watch: the watch hears you and sends the text; the
// phone thinks (Gemma) and sends back her answer as it grows, and her voice in
// small pieces of 16-bit PCM when it should sound on the wrist.

package mx.loperstack.tokkip.wellness

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Her answer so far. [where] is "watch" or "buds": where her voice sounds. */
data class TalkReply(
    val text: String,
    val done: Boolean,
    val error: Boolean = false,
    val crisis: Boolean = false,
    val where: String = "",
)

/** A piece of her voice. */
class TalkAudio(val rate: Int, val pitch: Float, val pcm: ShortArray)

object TalkWire {
    /** Samples per message: 64 KB, well under the Data Layer's message limit. */
    const val PIECE = 32_000
    private const val HEAD = 8

    fun text(r: TalkReply): String = JSONObject()
        .put("text", r.text).put("done", r.done).put("error", r.error).put("crisis", r.crisis).put("where", r.where)
        .toString()

    fun replyOf(json: String): TalkReply? = runCatching {
        val o = JSONObject(json)
        TalkReply(o.optString("text"), o.optBoolean("done"), o.optBoolean("error"), o.optBoolean("crisis"), o.optString("where"))
    }.getOrNull()

    /** Float samples into messages of at most [PIECE] samples each. */
    fun audio(samples: FloatArray, rate: Int, pitch: Float): List<ByteArray> =
        (samples.indices step PIECE).map { from ->
            val to = minOf(from + PIECE, samples.size)
            val b = ByteBuffer.allocate(HEAD + (to - from) * 2).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(rate).putFloat(pitch)
            for (i in from until to) b.putShort((samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            b.array()
        }

    fun audioOf(bytes: ByteArray): TalkAudio? {
        if (bytes.size < HEAD || (bytes.size - HEAD) % 2 != 0) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rate = b.int
        val pitch = b.float
        if (rate !in 8_000..48_000 || pitch !in 0.5f..2f) return null
        val pcm = ShortArray((bytes.size - HEAD) / 2) { b.short }
        return TalkAudio(rate, pitch, pcm)
    }
}
