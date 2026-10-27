// Handwriting: the drawing format shared with Windows (notas-pc/src/ink/ink.ts).
//   {"v":1,"bg":"lines","h":1414,"s":[{"k":"pen","c":0,"w":3,"p":[x,y,pr, x,y,pr…]}]}
// In page units: the page is 1000 wide and `h` tall; points are stored ×2 as
// whole numbers with the pen's pressure 0…100. Kinds: "pen" (width follows
// pressure) and "hl" (marcatextos, see-through). Colour 0 is "tinta": the
// theme's ink (dark on light paper, light on dark).

package mx.loperstack.tokkipnotas.data

import org.json.JSONArray
import org.json.JSONObject

const val INK_W = 1000f
const val A4_H = 1414f

data class Stroke(val kind: String, val color: Long, val width: Float, val points: IntArray) {
    val count get() = points.size / 3
    fun x(i: Int) = points[i * 3] / 2f
    fun y(i: Int) = points[i * 3 + 1] / 2f
    fun pressure(i: Int) = points[i * 3 + 2]
    val highlighter get() = kind == "hl"
    override fun equals(other: Any?) = other is Stroke && kind == other.kind && color == other.color && width == other.width && points.contentEquals(other.points)
    override fun hashCode() = points.contentHashCode() * 31 + kind.hashCode()
}

data class Drawing(val bg: String = "lines", val h: Float = A4_H, val strokes: List<Stroke> = emptyList())

private val BGS = setOf("plain", "lines", "grid", "dots")

fun drawingOf(raw: String, fallback: Drawing = Drawing()): Drawing = runCatching {
    val o = JSONObject(raw)
    if (o.optInt("v") != 1) return fallback
    val arr = o.optJSONArray("s") ?: JSONArray()
    val strokes = (0 until arr.length()).mapNotNull { i ->
        val s = arr.optJSONObject(i) ?: return@mapNotNull null
        val p = s.optJSONArray("p") ?: return@mapNotNull null
        if (p.length() < 3) return@mapNotNull null
        Stroke(
            kind = if (s.optString("k") == "hl") "hl" else "pen",
            color = s.optLong("c", 0L),
            width = s.optDouble("w", 3.0).toFloat().takeIf { it > 0 } ?: 3f,
            points = IntArray(p.length() / 3 * 3) { j -> Math.round(p.optDouble(j, 0.0)).toInt() },
        )
    }
    val h = o.optDouble("h", A4_H.toDouble()).toFloat()
    Drawing(o.optString("bg").takeIf { it in BGS } ?: "plain", if (h > 50) h else A4_H, strokes)
}.getOrDefault(fallback)

fun drawingJson(d: Drawing): String = JSONObject()
    .put("v", 1).put("bg", d.bg).put("h", Math.round(d.h))
    .put("s", JSONArray().apply {
        for (s in d.strokes) put(JSONObject().put("k", s.kind).put("c", s.color).put("w", s.width.toDouble()).put("p", JSONArray().apply { for (v in s.points) put(v) }))
    }).toString()

/** The pen's colours: tinta (follows the theme), then blue, red, green, violet, orange. */
val PEN_COLORS = listOf(0L, 0xFF2563EB, 0xFFDC2626, 0xFF16A34A, 0xFF7C3AED, 0xFFEA580C)
/** Marcatextos: yellow, green, pink, blue. */
val HL_COLORS = listOf(0xFFFDE047, 0xFF86EFAC, 0xFFF9A8D4, 0xFF93C5FD)
val PEN_SIZES = listOf(2f, 3.5f, 6f)
const val HL_SIZE = 18f

/** A pen's width at a pressure (0…100). */
fun widthAt(base: Float, pressure: Int) = base * (0.45f + 1.1f * (pressure / 100f))

/** The strokes an eraser at (x, y) touches (page units, radius r). */
fun strokesHit(d: Drawing, x: Float, y: Float, r: Float): Set<Int> {
    val out = HashSet<Int>()
    val x2 = x * 2
    val y2 = y * 2
    d.strokes.forEachIndexed { i, s ->
        val reach = (r + s.width) * 2
        val p = s.points
        var j = 0
        while (j < p.size) {
            val dx = p[j] - x2
            val dy = p[j + 1] - y2
            if (dx * dx + dy * dy <= reach * reach) { out += i; break }
            if (j >= 3) {
                val ax = p[j - 3].toFloat(); val ay = p[j - 2].toFloat()
                val bx = p[j].toFloat(); val by = p[j + 1].toFloat()
                val len = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
                if (len > 0) {
                    val t = (((x2 - ax) * (bx - ax) + (y2 - ay) * (by - ay)) / len).coerceIn(0f, 1f)
                    val cx = ax + t * (bx - ax) - x2
                    val cy = ay + t * (by - ay) - y2
                    if (cx * cx + cy * cy <= reach * reach) { out += i; break }
                }
            }
            j += 3
        }
    }
    return out
}

/** The lowest point drawn, to grow the page as you write. */
fun lowest(d: Drawing): Float = d.strokes.maxOfOrNull { s -> (0 until s.count).maxOfOrNull { s.y(it) } ?: 0f } ?: 0f
