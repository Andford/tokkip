// Templates, so you never start from a blank page: each one builds a little
// tree of pages (a project with its board, a class's notes, a reading list…).

package mx.loperstack.tokkipnotas.data

/** A page to create, with its body and its own pages. */
data class Draft(val page: Page, val body: String = "", val children: List<Draft> = emptyList())

data class Template(val id: String, val name: String, val about: String, val icon: String, val color: Long, val shape: Int)

val TEMPLATES = listOf(
    Template("blank", "Página en blanco", "Solo tú y la hoja", "doc", 0xFFA78BFA, 0),
    Template("ink", "Página a mano", "Para escribir con la pluma: renglones, cuadrícula o lisa", "art", 0xFF22D3EE, 4),
    Template("project", "Proyecto", "El porqué, lo siguiente y un tablero de tareas", "rocket", 0xFF60A5FA, 1),
    Template("brain", "Segundo cerebro", "Proyectos, áreas, recursos y archivo (método PARA)", "spark", 0xFFA78BFA, 11),
    Template("board", "Tablero", "Por hacer · Haciendo · Hecho", "board", 0xFFFBBF24, 3),
    Template("class", "Apuntes de clase", "Tema, ideas clave, dudas y resumen", "study", 0xFF34D399, 4),
    Template("ideas", "Lluvia de ideas", "Todo afuera sin filtro, luego escoges", "idea", 0xFFF472B6, 2),
    Template("meeting", "Reunión", "Quiénes, temas y acuerdos", "people", 0xFF22D3EE, 5),
    Template("reading", "Lista de lecturas y pelis", "Una tabla con estado, autor y calificación", "book", 0xFFF97316, 8),
    Template("trip", "Viaje", "Maleta, itinerario y pendientes", "travel", 0xFF34D399, 7),
)

private fun page(parent: String?, title: String, t: Template, now: Long, kind: PageKind = PageKind.DOC, schema: Schema? = null, props: Map<String, String> = emptyMap(), ord: Double = 0.0) =
    Page(newId(), parent, title, t.icon, t.color, t.shape, kind, ord, now, now, 0, props = props, schema = schema)

/** The pages a template makes, under `parent`. */
fun build(t: Template, parent: String?, now: Long, ord: Double = 0.0): Draft {
    val root = page(parent, if (t.id == "blank" || t.id == "ink") "" else t.name, t, now, ord = ord)
    return when (t.id) {
        "project" -> {
            val board = page(root.id, "Tareas", t.copy(icon = "board"), now, PageKind.COLLECTION, defaultSchema("board"))
            val first = Draft(page(board.id, "Escribir el primer paso", t.copy(icon = "doc"), now, props = mapOf("status" to "todo")))
            Draft(
                root,
                "! ¿Por qué te importa? Escríbelo aquí: cuando se ponga pesado, te lo va a recordar.\n" +
                    "## Lo siguiente\n[ ] Lo más pequeño que puedas hacer hoy\n" +
                    "## Tareas\n@@${board.id}\n## Notas\n",
                listOf(Draft(board, children = listOf(first))),
            )
        }
        "ink" -> Draft(root.copy(kind = PageKind.INK), INK_EMPTY)
        "brain" -> brain(root, t, now)
        "board" -> Draft(root.copy(kind = PageKind.COLLECTION, schema = defaultSchema("board")))
        "class" -> Draft(root, "## Tema\n\n## Ideas clave\n• \n## Dudas\n[ ] \n## En mis palabras\n! Resúmelo como se lo contarías a alguien.")
        "ideas" -> Draft(root, "! Sin filtro: todo vale. Ya después escoges.\n• \n## Las que me laten\n[ ] ")
        "meeting" -> Draft(root, "## Quiénes\n• \n## Temas\n1. \n## Acuerdos\n[ ] ")
        "reading" -> Draft(
            root.copy(
                kind = PageKind.COLLECTION,
                schema = Schema(
                    props = listOf(
                        Prop("status", "Estado", PropType.SELECT, listOf(Opt("todo", "Pendiente", GREY), Opt("doing", "Leyendo / viendo", 0xFFFBBF24), Opt("done", "Terminado", 0xFF34D399))),
                        Prop("author", "Autor", PropType.TEXT),
                        Prop("stars", "Calificación", PropType.NUMBER),
                        Prop("tags", "Etiquetas", PropType.MULTI, listOf(Opt("book", "Libro", 0xFFA78BFA), Opt("movie", "Peli", 0xFFF472B6), Opt("series", "Serie", 0xFF60A5FA))),
                    ),
                    view = "table", groupBy = "status",
                ),
            ),
        )
        "trip" -> Draft(root, "## Maleta\n[ ] Cargadores\n[ ] Identificación\n## Itinerario\n1. \n## Pendientes antes de salir\n[ ] ")
        else -> Draft(root)
    }
}

/** An empty handwritten page with lines (Ink.kt's format). */
const val INK_EMPTY = "{\"v\":1,\"bg\":\"lines\",\"h\":1414,\"s\":[]}"

private val AREAS = listOf(Opt("work", "Trabajo", 0xFF60A5FA), Opt("health", "Salud", 0xFF34D399), Opt("home", "Casa", 0xFFFBBF24), Opt("money", "Dinero", 0xFFF472B6))

/**
 * A second brain, the PARA way: Proyectos (with a goal and an end), Áreas (what
 * you look after always), Recursos (what interests or helps you) and Archivo
 * (what's no longer active), plus a weekly review. Things land first in the
 * Bandeja and get sorted here.
 */
private fun brain(root: Page, t: Template, now: Long): Draft {
    val projects = page(root.id, "Proyectos", t.copy(icon = "rocket", color = 0xFF60A5FA, shape = 1), now, PageKind.COLLECTION,
        Schema(
            props = listOf(
                Prop("status", "Estado", PropType.SELECT, listOf(Opt("todo", "Por hacer", GREY), Opt("doing", "Haciendo", 0xFFFBBF24), Opt("done", "Hecho", 0xFF34D399))),
                Prop("area", "Área", PropType.SELECT, AREAS),
                Prop("date", "Fecha límite", PropType.DATE),
                Prop("tags", "Etiquetas", PropType.MULTI),
            ),
            view = "board", groupBy = "status",
        ), ord = 0.0)
    val areas = page(root.id, "Áreas", t.copy(icon = "home", color = 0xFF34D399, shape = 3), now, PageKind.COLLECTION,
        Schema(
            props = listOf(
                Prop("state", "Estado", PropType.SELECT, listOf(Opt("active", "Activa", 0xFF34D399), Opt("paused", "En pausa", GREY))),
                Prop("review", "Próxima revisión", PropType.DATE),
            ),
            view = "table", groupBy = "state",
        ), ord = 1.0)
    val resources = page(root.id, "Recursos", t.copy(icon = "book", color = 0xFFF97316, shape = 8), now, PageKind.COLLECTION,
        Schema(
            props = listOf(
                Prop("kind", "Tipo", PropType.SELECT, listOf(Opt("article", "Artículo", 0xFF60A5FA), Opt("video", "Video", 0xFFF472B6), Opt("book", "Libro", 0xFFA78BFA), Opt("course", "Curso", 0xFF22D3EE), Opt("idea", "Idea", 0xFFFBBF24))),
                Prop("area", "Área", PropType.SELECT, AREAS),
                Prop("topic", "Tema", PropType.MULTI),
                Prop("link", "Enlace", PropType.TEXT),
            ),
            view = "table", groupBy = "kind",
        ), ord = 2.0)
    val archive = page(root.id, "Archivo", t.copy(icon = "inbox", color = GREY, shape = 10), now, ord = 3.0)
    val firstProject = page(projects.id, "Mi primer proyecto", t.copy(icon = "doc"), now, props = mapOf("status" to "todo", "area" to "work"))
    val areaCards = AREAS.mapIndexed { i, o -> Draft(page(areas.id, o.name, t.copy(icon = "doc"), now, props = mapOf("state" to "active"), ord = i.toDouble())) }
    return Draft(
        root,
        "! Todo lo que entra a tu cabeza tiene un lugar: primero la Bandeja, y cuando tengas un rato, aquí.\n" +
            "## Proyectos\n@@${projects.id}\n> Algo con una meta y una fecha: se termina.\n" +
            "## Áreas\n@@${areas.id}\n> Lo que cuidas siempre: trabajo, salud, casa, dinero.\n" +
            "## Recursos\n@@${resources.id}\n> Lo que te interesa o te sirve: artículos, cursos, ideas, referencias.\n" +
            "## Archivo\n@@${archive.id}\n> Lo que ya no está activo, guardado por si acaso.\n" +
            "## Cada semana\n[ ] Vaciar la Bandeja\n[ ] Revisar cada proyecto: ¿qué sigue?\n[ ] Pasar lo terminado al Archivo",
        listOf(
            Draft(projects, children = listOf(Draft(firstProject))),
            Draft(areas, children = areaCards),
            Draft(resources),
            Draft(archive, "Mueve aquí (⋮ → Mover a…) los proyectos terminados y lo que ya no usas. Nada se borra."),
        ),
    )
}

/** Every page of a draft, top-down, with its body. */
fun Draft.flatten(): List<Pair<Page, String>> = listOf(page to body) + children.flatMap { it.flatten() }
