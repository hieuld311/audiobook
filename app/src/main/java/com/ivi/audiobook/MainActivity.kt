package com.ivi.audiobook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ivi.audiobook.presentation.library.LibraryScreen
import com.ivi.audiobook.presentation.navigation.AppRoute
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
                    var route by remember { mutableStateOf<AppRoute>(AppRoute.Library) }

                    // Same split as the sibling widget app's WidgetHostScreen: this app's window
                    // spans the full 3840x208 strip, but the app itself only ever renders into the
                    // right half — the left half stays blank for whatever else occupies that space.
                    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Crossfade(targetState = route, label = "AppRoute") { current ->
                                when (current) {
                                    is AppRoute.Library -> LibraryScreen(
                                        onOpenBook = { bookId -> route = AppRoute.Player(bookId) },
                                    )
                                    is AppRoute.Player -> PlayerScreen(
                                        bookId = current.bookId,
                                        onBack = { route = AppRoute.Library },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
