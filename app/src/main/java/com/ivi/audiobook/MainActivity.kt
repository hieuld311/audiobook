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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.audiobook.data.debug.AudioBookStateProvider
import com.ivi.audiobook.data.debug.TopPaddingOverrideProvider
import com.ivi.audiobook.data.playback.PlaybackController
import com.ivi.audiobook.presentation.player.PlayerScreen
import com.ivi.audiobook.presentation.theme.AudioBookTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var topPaddingOverrideProvider: TopPaddingOverrideProvider

    @Inject
    lateinit var audioBookStateProvider: AudioBookStateProvider

    @Inject
    lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioBookTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val density = LocalDensity.current
                    val topPaddingPx by topPaddingOverrideProvider.topPaddingPx.collectAsStateWithLifecycle()
                    val topPadding = with(density) { topPaddingPx.toDp() }

                    // AUDIO_BOOK_STATE == 0 means the platform wants this app turned off.
                    val audioBookState by audioBookStateProvider.state.collectAsStateWithLifecycle()
                    LaunchedEffect(audioBookState) {
                        if (audioBookState == 0) finish()
                    }

                    // Auto-close once the built-in file has finished playing — there's nothing
                    // else for this single-file player to do.
                    val playbackUiState by playbackController.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(playbackUiState.isPlaybackEnded) {
                        if (playbackUiState.isPlaybackEnded) finish()
                    }

                    // Same split as the sibling widget app's WidgetHostScreen: this app's window
                    // spans the full 3840x208 strip, but only ever renders into the right half —
                    // the left half stays blank for whatever else occupies that space. Top padding
                    // mirrors WidgetHostScreen's own runtime-adjustable padding (same broadcast
                    // pattern, same SharedPreferences persistence).
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(top = topPadding),
                    ) {
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
