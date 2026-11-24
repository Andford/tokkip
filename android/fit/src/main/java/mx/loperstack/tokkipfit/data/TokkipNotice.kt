// Tells the Tokkip app about a workout you finished in Fit (phone or watch),
// so he celebrates it there too and it counts as cariño for his growth. Only
// the workout's summary goes (what, when, how long), through the Tokkip app's
// FitProvider, which only apps signed with Tokkip's key may call. If the
// Tokkip app isn't installed (or is an older one), nothing happens.

package mx.loperstack.tokkipfit.data

import android.content.Context
import android.net.Uri
import mx.loperstack.tokkip.fit.Done

object TokkipNotice {
    private val URI: Uri = Uri.parse("content://mx.loperstack.tokkip.fit")

    fun tell(context: Context, d: Done) {
        runCatching { context.applicationContext.contentResolver.call(URI, "workout", d.toJson().toString(), null) }
    }
}
