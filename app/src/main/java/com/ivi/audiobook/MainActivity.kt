package com.ivi.audiobook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ivi.audiobook.presentation.player.PlayerScreen
import com.ivi.audiobook.presentation.theme.AudioBookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioBookTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Same split as the sibling widget app's WidgetHostScreen: this app's window
                    // spans the full 3840x208 strip, but only ever renders into the right half —
                    // the left half stays blank for whatever else occupies that space.
                    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            PlayerScreen()
                        }
                    }
                }
            }
        }
    }
}
