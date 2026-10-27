// Tokkip's launcher icon follows the species you pick: four activity-aliases,
// one enabled at a time. The launcher may take a moment to show the new one.

package mx.loperstack.tokkip.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIcon {
    private val ALIASES = mapOf("CONEJO" to "LauncherConejo", "GATO" to "LauncherGato", "PERRO" to "LauncherPerro", "ROBOT" to "LauncherRobot", "MAPACHE" to "LauncherMapache")

    fun apply(context: Context, species: String) {
        val pm = context.packageManager
        val chosen = ALIASES[species] ?: "LauncherConejo"
        runCatching {
            // Enable the new one first, so there is never no icon at all.
            set(pm, context, chosen, true)
            for (name in ALIASES.values) if (name != chosen) set(pm, context, name, false)
        }
    }

    private fun set(pm: PackageManager, context: Context, alias: String, on: Boolean) {
        val c = ComponentName(context.packageName, "mx.loperstack.tokkip.$alias")
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(c) != state) pm.setComponentEnabledSetting(c, state, PackageManager.DONT_KILL_APP)
    }
}
