# AudioBook — File Structure

**Package:** `com.ivi.audiobook`
**What it is:** a single-screen Compose app that plays one MP3 bundled in `assets/`, on a 3840×208 automotive strip display. No library, no database, no navigation — everything below exists to support exactly one screen.

13 Kotlin files total. This doc explains what each one does and how the similarly-named ones differ from each other.

## Root

| File | Purpose |
|---|---|
| `AudioBookApp.kt` | `@HiltAndroidApp` — just enables the Hilt DI graph. No logic. |
| `MainActivity.kt` | The only Activity. Renders the right-half strip split (`Row` with a blank `Spacer` for the left half, matching the sibling widget app's layout) and hosts `PlayerScreen()` directly — no `Crossfade`, no route state, since there's only one screen. |

## `data/playback/` — the four files that actually make sound come out

These four are easy to confuse by name. Here's the split:

| File | Layer | Talks to Android? | Purpose |
|---|---|---|---|
| `PlaybackService.kt` | Media3 service | Yes — `MediaSessionService` | The foreground service that owns the real `ExoPlayer`. Exists so playback survives the Activity closing/backgrounding. Implements `onPlaybackResumption` (resolves the built-in file again) because Media3's default throws if the system asks it to resume with nothing loaded. |
| `PlaybackController.kt` | App-side session client | Yes — connects a `MediaController` to the service | The **single source of truth** for playback UI state (`PlaybackUiState`: title, author, cover, position, duration, lyrics, isPlaying). `PlayerViewModel` reads its `StateFlow` directly — this is the one place in the app that deliberately skips the ViewModel→use-case pattern, because playback is stateful/session-bound in a way a use-case doesn't fit. `start()` is idempotent (re-entering the screen doesn't restart from 0). |
| `BuiltInAudioAsset.kt` | Metadata resolver | Yes — `Context.assets`, `MediaMetadataRetriever` | Finds the one `.mp3` in `assets/` (never a hardcoded filename) and reads its title/artist/duration/cover art via `MediaMetadataRetriever`. Cover art is written to `cacheDir/built_in_cover.jpg` since the player UI expects a file path, not raw bytes. Also kicks off lyrics resolution (sidecar `.lrc` first, then `Id3LyricsReader`) and bundles everything into one `BuiltInAudioInfo`. Called once by `PlaybackController.start()` and once by `PlaybackService`'s resumption callback. |
| `Id3LyricsReader.kt` | Byte-level parser | **No** — pure Kotlin/JVM, no Android imports | A hand-rolled ID3v2 tag walker. Exists because MP3 has no lyrics API on `MediaMetadataRetriever` and no equivalent to the MP4 `©lyr` atom this app used to parse for M4B files. It looks specifically for a `TXXX` (user-defined text) frame whose descriptor is `USLT` or `LYRICS` — that's the *only* pattern this app's actual files use, confirmed against real FLAC→MP3 conversions (ffmpeg itself maps a Vorbis `LYRICS` comment into exactly that shape when muxing to MP3, rather than a native `SYLT`/`USLT` frame). Being Android-free, it's directly unit-testable on the plain JVM. |

**Why four files and not one:** `PlaybackService` is the only one allowed to own the actual `ExoPlayer`/`MediaSession` (Android requires this to live in a `Service`). `PlaybackController` is the only one the UI talks to. `BuiltInAudioAsset` and `Id3LyricsReader` exist only because "read the file's real metadata" is a big enough job (Android framework calls + hand-rolled binary parsing) to not belong inline in the controller.

## `domain/model/`

| File | Purpose |
|---|---|
| `LyricLine.kt` | `data class LyricLine(startMs: Long, text: String)` — one parsed lyric line with its timestamp. The only domain model left after the Room/library removal; everything else (`Book`, `LibraryQuery`, `BookRepository`) was deleted along with the database. |

## `presentation/player/` — the screen itself

| File | Purpose |
|---|---|
| `PlayerScreen.kt` | Thin Composable entry point. Collects `PlayerViewModel.uiState`, fires `viewModel.start()` once via `LaunchedEffect(Unit)`, and renders `CompactPlayerBar`. No parameters (`bookId`, `onBack`, etc. were removed — there's nothing to navigate to or from). |
| `PlayerViewModel.kt` | `@HiltViewModel` wrapper around `PlaybackController` — exposes its `StateFlow` as-is and forwards `start()`/`togglePlayPause()`/`seekPreview()`/`seekFinished()`. No logic of its own; exists purely so `PlayerScreen` doesn't depend on `PlaybackController` directly (standard Compose/Hilt convention). |
| `CompactPlayerBar.kt` | The actual UI. Fixed layout: left block (567×208dp) = cover (bounded in `img_button_play_background_n`, the same glow asset as CoWatch's play button) + title/author + drag-seek progress bar; right block (1279×208dp) = teleprompter-style lyric preview with animated line transitions. No play/pause button by design — opening the screen and dragging the seek bar are the only playback controls, both of which always resume playback on release/load. |

**Why three files for one screen:** `PlayerScreen` (Compose glue) / `PlayerViewModel` (Hilt boundary) / `CompactPlayerBar` (pure UI, no ViewModel dependency) is the standard split so `CompactPlayerBar` stays previewable and testable without a real `PlaybackController`.

## `presentation/theme/`

| File | Purpose |
|---|---|
| `Color.kt` | Raw color constants (`BackgroundDark`, `AccentGreen`, etc.) — leftover from the original phone-era Material theme. `CompactPlayerBar` doesn't use these; it defines its own black/cyan palette locally to match the sibling widget app. |
| `Theme.kt` | `AudioBookTheme` — wraps `MaterialTheme` with a `darkColorScheme` built from `Color.kt`. Still applied at the `MainActivity` root even though the strip UI mostly ignores Material theming in favor of its own hardcoded colors. |
| `Type.kt` | `AudioBookTypography` — Material `Typography` definitions. Same story as `Color.kt`: inherited from the phone-era design, not actively used by the strip UI's own `fontSize`-per-`Text` styling. |

## What's *not* here anymore

Room database, `LibraryScanner`, `UsbVolumeObserver`, `BookRepository`/`LocalBookRepository`, all `domain/usecase/*`, the Library screen/ViewModel/carousel, `AppRoute` navigation, `StoragePermissions`, and the old phone-era transport components (`PlaybackControlBar`, `VideoSeekBar`, `ScriptView`, etc.) were all deleted when the app was refactored down to "one screen, one built-in file, no database." If you're looking for how any of that used to work, it's gone — not hidden elsewhere.
