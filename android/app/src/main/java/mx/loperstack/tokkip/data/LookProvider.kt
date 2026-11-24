// Tokkip's look for his other apps (Tokkip Fit): one row with the Look as
// JSON (character/Look.kt). Only apps signed with Tokkip's key can read it
// (READ_LOOK is a signature permission), and it's read only: nothing else of
// yours is shared.

package mx.loperstack.tokkip.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import mx.loperstack.tokkip.character.Look

class LookProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val p = context!!.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)
        val look = Look(
            color = p.getLong("tokkipColor", 0xFFF5F6F8),
            species = p.getString("species", "CONEJO") ?: "CONEJO",
            skin = p.getString("skin", "LISA") ?: "LISA",
            accent = p.getLong("accent", 0xFF3F9E9A),
            ink = p.getLong("faceInk", 0),
            cheek = p.getLong("cheek", 0),
            outfit = p.getString("outfit", "auto") ?: "auto",
        )
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(look.json())) }
    }

    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
