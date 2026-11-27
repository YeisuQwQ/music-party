package dev.yiqidi.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val sharedInvite = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receiveShare(intent)
        val store = (application as YiqidiApplication).music
        store.connectPlayback(applicationContext)
        store.boot()
        setContent {
            val ui by store.state.collectAsStateWithLifecycle()
            val player by store.audioState.collectAsStateWithLifecycle()
            val tree by store.uiTree.collectAsStateWithLifecycle()
            YiqidiTheme(ui.theme) { dark ->
                SideEffect {
                    val insets = WindowCompat.getInsetsController(window, window.decorView)
                    insets.isAppearanceLightStatusBars = !dark
                    insets.isAppearanceLightNavigationBars = !dark
                }
                MusicApp(store, ui, player, tree, dark, sharedInvite.value, { sharedInvite.value = "" })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedInvite.value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.take(4096)?.toString().orEmpty()
        }
    }
}