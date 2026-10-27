// Tokkip's old Cuaderno, brought over: each project becomes a page (with its
// colour, shape and icon, and its "why" as an aviso at the top) holding its
// notes; notes from the Bandeja go to the Bandeja. The text format is the
// same, so steps and bullets arrive as they were. Ids keep a "tk-" prefix:
// bringing it over twice replaces instead of duplicating.

package mx.loperstack.tokkipnotas.data

import org.json.JSONObject

fun fromTokkip(root: JSONObject): List<Pair<Page, String>> {
    val out = ArrayList<Pair<Page, String>>()
    val projects = root.optJSONArray("projects")
    if (projects != null) for (i in 0 until projects.length()) {
        val p = projects.optJSONObject(i) ?: continue
        val id = p.optString("id"); if (id.isEmpty()) continue
        val why = p.optString("why")
        val done = p.optLong("done_at") > 0
        val body = listOfNotNull(
            why.takeIf { it.isNotBlank() }?.let { "! $it" },
            if (done) "> Terminado en Tokkip ✓" else null,
        ).joinToString("\n")
        out += Page(
            id = "tk-$id", parent = null, title = p.optString("name"), icon = p.optString("icon", "idea"),
            color = p.optLong("color", 0xFFA78BFA), shape = p.optInt("shape"), ord = p.optDouble("ord", i.toDouble()),
            created = p.optLong("created"), updated = p.optLong("updated"),
        ) to body
    }
    val notes = root.optJSONArray("notes")
    if (notes != null) for (i in 0 until notes.length()) {
        val n = notes.optJSONObject(i) ?: continue
        val id = n.optString("id"); if (id.isEmpty()) continue
        val project = n.optString("project").takeIf { it.isNotEmpty() && n.has("project") }
        val body = n.optString("body")
        val f = factsOf(body)
        out += Page(
            id = "tk-$id", parent = project?.let { "tk-$it" } ?: INBOX_ID, title = n.optString("title"), icon = "doc",
            color = 0xFFA78BFA, ord = -n.optLong("updated").toDouble(), created = n.optLong("created"), updated = n.optLong("updated"),
            opened = n.optLong("opened"), favorite = n.optLong("pinned") != 0L, preview = f.preview, steps = f.steps, done = f.done,
        ) to body
    }
    return out
}
