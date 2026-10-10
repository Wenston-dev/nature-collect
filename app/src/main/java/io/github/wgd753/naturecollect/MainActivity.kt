package io.github.wgd753.naturecollect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.wgd753.naturecollect.ui.NatureCollectApp
import io.github.wgd753.naturecollect.ui.theme.NatureCollectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NatureCollectTheme {
                NatureCollectApp()
            }
        }
    }
}
