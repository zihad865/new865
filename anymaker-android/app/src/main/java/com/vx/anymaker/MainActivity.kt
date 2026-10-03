package com.vx.anymaker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.vx.anymaker.feature.resize.ResizeViewModel
import com.vx.anymaker.ui.theme.AnymakerTheme

class MainActivity : ComponentActivity() {

    private val resizeViewModel: ResizeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the view model already holds the shared photo.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AnymakerTheme {
                AnymakerApp(resizeViewModel = resizeViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        resizeViewModel.onImageShared(uri)
    }
}
