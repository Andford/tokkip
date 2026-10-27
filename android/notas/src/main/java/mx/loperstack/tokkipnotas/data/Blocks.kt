// A page's text: one block per line, with a short mark in front.
//   "# "  título           "## " subtítulo
//   "• "  viñeta           "1. " lista numerada
//   "[ ] " paso por hacer  "[x] " paso hecho
//   "> "  cita             "! "  aviso (Tokkip te lo recuerda)
//   "---" separador        "@@<id>" una subpágina
//   "%%<json>" un dibujo hecho con la pluma (Ink.kt)
// A page written by hand (PageKind.INK) keeps its whole drawing as its body
// instead (it starts with {"v":).
// Anything else is a paragraph. Readable as is, outside the app too.

package mx.loperstack.tokkipnotas.data

enum class BlockKind(val prefix: String, val label: String) {
    TEXT("", "Texto"), H1("# ", "Título"), H2("## ", "Subtítulo"), BULLET("• ", "Viñetas"), NUMBER("1. ", "Lista numerada"),
    TODO("[ ] ", "Paso"), DONE("[x] ", "Paso hecho"), QUOTE("> ", "Cita"), CALLOUT("! ", "Aviso"), DIVIDER("---", "Separador"), PAGE("@@", "Subpágina"),
    DRAW("%%", "Dibujo"),
}

data class Block(val kind: BlockKind, val text: String) {
    val isStep get() = kind == BlockKind.TODO || kind == BlockKind.DONE
    fun line() = when (kind) {
        BlockKind.DIVIDER -> "---"
        else -> kind.prefix + text
    }
}

private val NUMBERED = Regex("""^\d{1,3}\. """)

fun blockOf(l: String): Block = when {
    l == "---" -> Block(BlockKind.DIVIDER, "")
    l.startsWith("@@") -> Block(BlockKind.PAGE, l.substring(2).trim())
    l.startsWith("%%") -> Block(BlockKind.DRAW, l.substring(2))
    l.startsWith("[x] ") || l.startsWith("[X] ") -> Block(BlockKind.DONE, l.substring(4))
    l.startsWith("[ ] ") -> Block(BlockKind.TODO, l.substring(4))
    l.startsWith("## ") -> Block(BlockKind.H2, l.substring(3))
    l.startsWith("# ") -> Block(BlockKind.H1, l.substring(2))
    l.startsWith("• ") || l.startsWith("- ") || l.startsWith("* ") -> Block(BlockKind.BULLET, l.substring(2))
    l.startsWith("> ") -> Block(BlockKind.QUOTE, l.substring(2))
    l.startsWith("! ") -> Block(BlockKind.CALLOUT, l.substring(2))
    NUMBERED.containsMatchIn(l) -> Block(BlockKind.NUMBER, l.replaceFirst(NUMBERED, ""))
    else -> Block(BlockKind.TEXT, l)
}

fun blocksOf(body: String): List<Block> = if (body.isEmpty()) listOf(Block(BlockKind.TEXT, "")) else body.split('\n').map(::blockOf)

fun bodyOf(blocks: List<Block>): String = blocks.joinToString("\n") { it.line() }.trimEnd('\n')

/** The number a numbered line shows: its place in its run of numbered lines. */
fun numberAt(blocks: List<BlockKind>, i: Int): Int {
    var n = 1
    var j = i - 1
    while (j >= 0 && blocks[j] == BlockKind.NUMBER) { n++; j-- }
    return n
}

data class Facts(val preview: String, val steps: Int, val done: Int, val links: List<String>)

/** A handwritten page's body (a drawing, not lines of text). */
fun isInkBody(body: String) = body.startsWith("{\"v\":")

fun factsOf(body: String): Facts {
    if (isInkBody(body)) return Facts("", 0, 0, emptyList())
    val blocks = blocksOf(body)
    val preview = blocks.filter { it.kind != BlockKind.PAGE && it.kind != BlockKind.DIVIDER && it.kind != BlockKind.DRAW && it.text.isNotBlank() }
        .take(3).joinToString(" · ") { it.text.trim() }.take(140)
    return Facts(preview, blocks.count { it.isStep }, blocks.count { it.kind == BlockKind.DONE }, blocks.filter { it.kind == BlockKind.PAGE }.map { it.text })
}

/** The text without marks, for search. */
fun plainOf(body: String): String =
    if (isInkBody(body)) "" else blocksOf(body).filter { it.kind != BlockKind.PAGE && it.kind != BlockKind.DRAW }.joinToString("\n") { it.text }

/** A capture: the first line is the title (when it's short), the rest the body. */
fun captureOf(text: String): Pair<String, String> {
    val lines = text.trim().lines()
    val first = lines.firstOrNull().orEmpty().trim()
    return if (first.length <= 80) first to lines.drop(1).joinToString("\n").trim()
    else "" to text.trim()
}
