// Tokkip Fit: exercise with Tokkip by your side (docs/FIT-PLAN.md).

package mx.loperstack.tokkipfit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkipfit.data.Prefs
import mx.loperstack.tokkipfit.ui.FitApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        Sounds.init(this)
        // Health Connect opens Fit to show why it asks (the privacy screen).
        val privacy = intent?.action?.let { it == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || it == Intent.ACTION_VIEW_PERMISSION_USAGE } == true
        val link = intent?.data?.takeIf { it.scheme == "tokkipfit" }?.let { (listOfNotNull(it.host) + it.pathSegments).joinToString("/") }
        setContent { FitApp(startOnPrivacy = privacy, link = link) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A widget tapped while Fit is open: start over on what it points to.
        if (intent.data?.scheme == "tokkipfit") recreate()
    }
}
