// Tokkip Fit tells Tokkip about each workout you finish (phone or watch): he
// celebrates it with a notification and it gives him cariño (data/Growth.kt).
// Only apps signed with Tokkip's own key may call it (checked on every call),
// and all it takes is the workout's summary: what, when and how long.

package mx.loperstack.tokkip.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import mx.loperstack.tokkip.fit.Done
import org.json.JSONObject

class FitProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        val caller = callingPackage ?: return null
        if (c.packageManager.checkSignatures(caller, c.packageName) != PackageManager.SIGNATURE_MATCH) return null
        if (method != "workout") return null
        val d = runCatching { Done.of(JSONObject(arg ?: return null)) }.getOrNull() ?: return null
        Store.init(c)
        if (Store.addFitWorkout(d)) mx.loperstack.tokkip.wellness.TokkipNotifier.cheerWorkout(c, d)
        return Bundle().apply { putBoolean("ok", true) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
