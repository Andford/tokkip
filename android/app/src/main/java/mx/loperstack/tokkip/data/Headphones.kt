// Your headphones' battery (Pixel Buds and most Bluetooth headsets report it
// to the phone), for the watch's Baterías and Ajustes. Android keeps the level
// each connected device reports; it isn't a public call, so this reads it
// gently and shows nothing if the phone or the headphones don't say.
// Needs "Dispositivos cercanos" (BLUETOOTH_CONNECT), asked in Ajustes.

package mx.loperstack.tokkip.data

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Headphones {
    fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Connected devices with a known battery: (name, 0…100). */
    @android.annotation.SuppressLint("MissingPermission")
    fun levels(context: Context): List<Pair<String, Int>> {
        if (!granted(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return emptyList()
        return bonded.mapNotNull { d ->
            val connected = runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
            if (!connected) return@mapNotNull null
            val level = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int }.getOrDefault(-1)
            if (level !in 0..100) return@mapNotNull null
            val name = runCatching { d.alias ?: d.name }.getOrNull() ?: "Audífonos"
            name to level
        }
    }
}
