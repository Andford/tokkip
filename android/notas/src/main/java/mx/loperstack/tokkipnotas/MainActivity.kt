// Tokkip Notas: notes, pages inside pages, boards and tables, with Tokkip.
// Text shared from any app lands in the Bandeja; Tokkip can hand over its
// old Cuaderno (Ajustes → Pasar a Tokkip Notas).

package mx.loperstack.tokkipnotas

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkipnotas.data.INBOX_ID
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.Sync
import mx.loperstack.tokkipnotas.ui.Hop
import mx.loperstack.tokkipnotas.ui.Nav
import mx.loperstack.tokkipnotas.ui.NotasApp
import mx.loperstack.tokkipnotas.ui.Prefs
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Sync.init(this)
        Prefs.init(this)
        Sounds.init(this)
        if (savedInstanceState == null) handle(intent)
        setContent { NotasApp() }
    }

    override fun onResume() {
        super.onResume()
        Sync.now()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                    .filter { it.isNotBlank() }.joinToString("\n")
                if (Store.capture(text) != null) {
                    Toast.makeText(this, "Guardado en la Bandeja", Toast.LENGTH_SHORT).show()
                    Hop.now()
                    Nav.home(); Nav.page(INBOX_ID)
                }
            }
            IMPORT_TOKKIP -> {
                val uri = intent.data ?: return
                lifecycleScope.launch {
                    val n = withContext(Dispatchers.IO) {
                        runCatching {
                            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching -1
                            Store.importTokkip(JSONObject(text))
                        }.getOrDefault(-1)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (n < 0) "No se pudo leer el Cuaderno de Tokkip" else "Llegaron $n notas y proyectos de Tokkip",
                        Toast.LENGTH_LONG,
                    ).show()
                    if (n > 0) { Sounds.play("level_up", 0.7f); Hop.now(); Nav.home() }
                }
            }
        }
    }

    companion object {
        const val IMPORT_TOKKIP = "mx.loperstack.tokkipnotas.IMPORT_TOKKIP"
    }
}
