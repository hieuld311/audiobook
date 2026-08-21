package com.ivi.audiobook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
