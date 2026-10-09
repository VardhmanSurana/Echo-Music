# Echo Music Linux Desktop Client — Architectural Design Specification

## 1. Overview & Objectives

Echo Music Desktop is a dedicated Linux desktop client for Echo Music that provides:
1. **Ad-free YouTube Music & Local Media playback** on Linux desktops and laptops.
2. **High code reuse** from the existing Android app's Kotlin backend modules (`:innertube`, `:lyrics`, `:betterlyrics`, `:lrclib`, `:domain`, `:core`).
3. **Pluggable Audio Engine**: User-selectable audio backends (`libmpv`, `GStreamer`, or a bundled fallback).
4. **Desktop-First UI**:
   - Top panel menu bar for rapid navigation and productivity shortcuts while working.
   - Three-panel desktop layout (Navigation Sidebar, Dynamic Content View, Collapsible Queue/Lyrics).
   - Immersive full-screen canvas mode featuring animated GPU color-mesh gradients, ambient album art glow, and synchronized word-by-word lyrics.
5. **Background Efficiency**: Pauses UI tickers and GPU render loops when minimized or running in the background, keeping CPU usage near 0-1%.
6. **Linux Desktop Ecosystem Integration**: MPRIS2 D-Bus service for desktop media keys, lockscreen controls, and system tray/top-panel indicator menus.
7. **Companion Browser Extension**: 1-click cookie sync from `music.youtube.com` into the desktop app via a secure local loopback service.

---

## 2. Architecture & Module Structure

```
Echo-Music/
├── core/                # Shared utilities, data models, network client configuration
├── domain/              # Shared business logic: playlists, favorites, history, local library scanning
├── innertube/           # Shared YouTube & YouTube Music API extraction
├── lyrics/              # Shared lyrics engine (LRCLib, BetterLyrics, Kugou, Paxsenix)
├── playback/            # Pluggable audio playback abstraction layer
│   ├── src/commonMain/kotlin/
│   │   ├── PlaybackEngine.kt          # Interface for audio backends
│   │   ├── PlaybackState.kt           # State data classes (Playing, Paused, Buffering, Position, Duration)
│   │   ├── QueueManager.kt            # Queue & playback history management
│   │   └── AudioOutputDevice.kt       # Audio sink definitions (PipeWire / PulseAudio devices)
│   ├── src/desktopMain/kotlin/
│   │   ├── MpvPlaybackEngine.kt       # libmpv JNI binding implementation
│   │   ├── GStreamerPlaybackEngine.kt # GStreamer JNI / GObject binding implementation
│   │   └── FallbackAudioEngine.kt     # Bundled JavaSound/FFmpeg fallback
├── app/                 # Existing Android mobile application
├── desktop/             # NEW: Compose Multiplatform Desktop Application
│   ├── src/jvmMain/kotlin/
│   │   ├── main.kt
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   ├── TopMenuBar.kt      # File/Playback/Audio/View/Sync top panel menu
│   │   │   │   ├── Sidebar.kt         # Navigation panel
│   │   │   │   ├── BottomPlayerBar.kt # Global playback controls & scrubber
│   │   │   │   ├── QueueLyricsPanel.kt# Right collapsible sidebar
│   │   │   │   └── MiniPlayer.kt      # Floating compact always-on-top window
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt
│   │   │   │   ├── ExploreScreen.kt
│   │   │   │   ├── LibraryScreen.kt
│   │   │   │   ├── LocalMusicScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── ImmersiveCanvasScreen.kt # Full-screen Apple-Music style canvas & lyrics
│   │   │   └── theme/
│   │   │       ├── Theme.kt           # Dynamic Material 3 theme adapted for desktop
│   │   │       └── Shaders.kt         # Skia AGSL / GLSL animated color mesh shaders
│   │   ├── system/
│   │   │   ├── MprisService.kt        # Linux D-Bus org.mpris.MediaPlayer2 integration
│   │   │   ├── TrayManager.kt         # Linux System Tray / AppIndicator top panel menu
│   │   │   ├── LocalAuthServer.kt     # Local loopback HTTP server (127.0.0.1) for extension sync
│   │   │   └── PerformanceGovernor.kt # Resource throttler (freezes UI loop when minimized)
└── companion-extension/ # NEW: WebExtension (Chrome / Firefox / Brave)
    ├── manifest.json
    ├── background.js    # Listens for YouTube Music cookie events & triggers 1-click sync
    └── popup/           # Popup UI with "Sync to Echo Music Desktop" button
```

---

## 3. Core Functional Components

### 3.1 Pluggable Audio Subsystem (`:playback`)
The audio subsystem is abstracted behind `PlaybackEngine`:
```kotlin
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val volume: StateFlow<Float>

    suspend fun loadStream(url: String, headers: Map<String, String>, startPositionMs: Long = 0)
    suspend fun loadFile(path: String, startPositionMs: Long = 0)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setOutputDevice(device: AudioOutputDevice)
    fun release()
}
```

- **`MpvPlaybackEngine` (Default)**: Leverages `libmpv` via lightweight JNI bindings. Supports native HLS/DASH/Opus decoding, gapless playback, buffer management, and hardware acceleration on Linux.
- **`GStreamerPlaybackEngine`**: Uses `gst-play-1.0` / GStreamer pipeline for native Linux PipeWire integration.
- **`FallbackAudioEngine`**: Built-in pure JVM/FFmpeg decoder for out-of-the-box operation on minimal Linux environments without external C libraries.
- **User Preference**: A toggle in **Settings > Audio Engine** lets users switch at runtime without restarting the app.

---

### 3.2 UI Design & Layouts

#### A. In-App Top Panel Menu Bar (`TopMenuBar.kt`)
Always accessible at the top of the application window:
- **Menu Items**:
  - `File`: Open File / Folder, Add to Library, Rescan Local Folders, Export Library, Preferences (`Ctrl+,`), Quit (`Ctrl+Q`).
  - `Playback`: Play/Pause (`Space`), Next (`Ctrl+Right`), Previous (`Ctrl+Left`), Shuffle (`Ctrl+S`), Repeat (`Ctrl+R`), Seek ±5s (`Left`/`Right`).
  - `Audio`: Audio Engine Selector (`Auto (libmpv)` / `GStreamer` / `Fallback`), Output Sink Selector, Audio Equalizer preset.
  - `View`: Toggle Left Sidebar (`Ctrl+1`), Toggle Right Panel (`Ctrl+2`), Mini-Player (`Ctrl+M`), Fullscreen Immersive Mode (`F11`).
  - `Account & Sync`: Browser Extension Sync status, Manual Cookie Input, Fast Sync Spotify Playlists.
- **Right Header Controls**: Global quick search bar (`Ctrl+K`), Audio Engine indicator chip, Extension sync status dot, and Window controls (Minimize, Maximize, Close).

#### B. Three-Panel Primary Layout
- **Left Panel (Navigation)**:
  - Online sections: *Home*, *Explore & Charts*, *Liked Songs*, *YT Playlists*, *Artists*.
  - Local sections: *Local Library*, *Folders*, *Downloaded Offline*.
  - System: *Settings*, *EQ & Audio Tweaks*.
- **Center Panel (Main Content Area)**:
  - Adaptive responsive grid for albums, artists, search recommendations, and full tracklists.
  - Context menus on track rows (Play Next, Add to Queue, Add to Playlist, Download, Share via Odesli/Song.link).
- **Right Panel (Collapsible Drawer)**:
  - Up Next Queue with drag-and-drop reordering.
  - Compact synchronized lyrics view.
- **Bottom Bar (Persistent Player)**:
  - Track thumbnail, title, artist, like button.
  - Playback controls with progress bar and timestamp.
  - Volume slider, mute toggle, output selector, lyrics switch, queue switch, and "Enter Immersive Mode" button.

#### C. Full-Screen Immersive Canvas Mode (`ImmersiveCanvasScreen.kt`)
- **Shader Visuals**: Skia GPU AGSL runtime shaders extracting dominant and complementary color palettes from album art to render moving fluid color blobs and mesh gradients.
- **Synced Lyrics**: Word-by-word active tracking with spring physics scrolling, smooth font scaling, and blurred inactive lines.
- **Auto-Hide Controls**: Floating minimalist controls that gracefully fade out after 3 seconds of cursor inactivity.

---

### 3.3 Resource Optimization & Background Playback

To ensure minimal battery and CPU consumption on laptops:
1. **Window State Governor (`PerformanceGovernor.kt`)**:
   - Listens to window minimized, hidden, or iconified state.
   - Disables Skia render ticker (`pausedState = true`), halts shader calculations, and suspends lyric layout updates.
   - CPU usage during background playback drops to **< 0.5% - 1%**.
2. **Memory Footprint Management**:
   - Lazy-loading for album art bitmaps using Coil with desktop cache limits.
   - Discards high-resolution canvas buffers when exiting Immersive Mode.

---

### 3.4 Linux Desktop Integration

- **MPRIS2 Service (`MprisService.kt`)**:
  - Implements `org.mpris.MediaPlayer2` and `org.mpris.MediaPlayer2.Player` over D-Bus.
  - Exposes playback status, current track metadata, album cover URL (`file://` or `https://`), playback position, volume, and playback rate.
  - Connects system media keys, lockscreen players, and status bars (Waybar, Polybar, GNOME Shell) to app actions.
- **System Tray & AppIndicator Menu (`TrayManager.kt`)**:
  - Top-panel tray icon with quick actions: *Now Playing: Song - Artist*, *Play/Pause*, *Next*, *Previous*, *Open Window*, *Quit*.

---

### 3.5 Companion Browser Extension (`companion-extension`)

- **Manifest V3 WebExtension** (compatible with Chrome, Edge, Brave, Firefox).
- **Functionality**:
  - Detects active session on `music.youtube.com`.
  - Extracts `SAPISID`, `__Secure-*`, `LOGIN_INFO`, and `HSID/SSID` cookies.
  - Posts payload to local loopback daemon `http://127.0.0.1:45454/auth/sync` with a unique one-time local handshake token.
  - Informs user with a clean popup: *"Echo Music Desktop Synced Successfully!"*.

---

## 4. Verification & Testing Strategy

1. **Unit & Multiplatform Tests**:
   - Test `PlaybackEngine` state machine transitions (idle -> loading -> playing -> paused -> stopped).
   - Test YouTube extraction (`:innertube`) on JVM with mock and live InnerTube endpoints.
   - Test local music folder scanner and ID3 tag extractor.
2. **Desktop Integration Tests**:
   - Verify MPRIS2 D-Bus calls via `playerctl` (`playerctl play-pause`, `playerctl next`, `playerctl metadata`).
   - Verify browser extension local sync endpoint over localhost.
   - Benchmark idle vs. active vs. background CPU/RAM consumption via `htop` / `top`.
