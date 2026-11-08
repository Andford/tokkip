package mx.loperstack.tokkip.character

import mx.loperstack.tokkip.shared.R

/**
 * The little icon in the status bar, on the lock screen and on the watch's
 * tiles: the pet you chose (tools/make_icons.py draws them).
 */
object StatIcon {
    fun of(s: Species): Int = when (s) {
        Species.CONEJO -> R.drawable.ic_stat_conejo
        Species.GATO -> R.drawable.ic_stat_gato
        Species.PERRO -> R.drawable.ic_stat_perro
        Species.ROBOT -> R.drawable.ic_stat_robot
        Species.MAPACHE -> R.drawable.ic_stat_mapache
    }

    /** The current pet's (Store / WatchStore set [TokkipStyle.species] when they load). */
    fun current(): Int = of(TokkipStyle.species)
}
