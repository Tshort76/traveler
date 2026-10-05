package dev.tlong.traveler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dev.tlong.traveler.ui.TravelerNav
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.theme.TravelerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                TravelerTheme { TravelerNav() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A file opened with, or shared to, Traveler becomes a pending import; the nav shows its preview. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri -> lifecycleScope.launch { container.openUri(uri) } }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                lifecycleScope.launch {
                    when {
                        stream != null -> container.openUri(stream)
                        text != null -> container.openText(text, "shared text")
                    }
                }
            }
        }
    }
}
