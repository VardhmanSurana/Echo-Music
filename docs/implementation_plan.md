# Echo Music Linux Desktop Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a high-performance, resource-efficient Linux desktop client for Echo Music using Compose Multiplatform, pluggable audio backends (`libmpv`/`GStreamer`), a 3-panel desktop interface with a top panel menu bar, an Apple Music-style full-screen immersive canvas with synchronized lyrics, MPRIS2 media integration, and a companion browser extension for 1-click YouTube Music account sync.

**Architecture:** A Kotlin Multiplatform `:desktop` module sharing the existing core business logic, InnerTube YouTube extraction, and lyrics engines, connected to a decoupled `PlaybackEngine` interface with runtime-switchable audio decoders and Linux D-Bus services.

**Tech Stack:** Kotlin 2.1+, Compose Multiplatform (Desktop / Skia), `libmpv` / GStreamer JNI, Ktor Client, Room/SQLite, D-Bus (dbus-java / MPRIS2), MaterialKolor (M3 theming), WebExtension Manifest V3.

**Spec:** [`design_spec.md`](file:///home/chotaxdon/.gemini/antigravity/brain/ae0f8ce7-4ff6-4c73-ab87-99dd3cf9a428/design_spec.md)

---

## Global Constraints

- **Kotlin & JVM Version**: Java 21 / JVM Toolchain 21.
- **Target OS**: Linux (x86_64 and arm64), supporting both Wayland and X11.
- **Background Efficiency**: CPU usage must drop to < 1% when minimized or playing in background.
- **UI Responsiveness**: 60/120 FPS rendering for canvas shaders and synchronized lyrics animations.
- **Shared Code Integrity**: Must not break existing Android build flavors (`foss` and `gms`).

## Review Focus

1. **Audio Server Compatibility**: Works seamlessly across PipeWire, PulseAudio, and ALSA without stream clipping or buffer underrun.
2. **Window Minimization State**: UI frame clock, animations, and lyric timers must freeze completely when window is minimized.
3. **Cookie Sync Security**: Local loopback auth server only accepts requests from `127.0.0.1` and validates origin/token.
4. **Offline Resilience**: Clean error handling and fallback when streaming links expire or network drops mid-playback.
5. **D-Bus / MPRIS Lifecycle**: Gracefully release MPRIS2 D-Bus registration on app exit without dangling system processes.

---

## Task Decomposition

### Task 1: Desktop Module Setup & Multiplatform Gradle Configuration

**Files:**
- Create: `desktop/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/Main.kt`
- Test: `desktop/src/jvmTest/kotlin/echo/music/desktop/SmokeTest.kt`

**Interfaces:**
- Produces: Runnable Desktop target via `./gradlew :desktop:run` and shared dependencies linking `:core`, `:innertube`, `:domain`, `:lyrics`.

- [ ] **Step 1: Configure multiplatform plugins in `settings.gradle.kts` and root `build.gradle.kts`**
- [ ] **Step 2: Create `desktop/build.gradle.kts` with Compose Multiplatform Desktop plugin & dependencies**
- [ ] **Step 3: Create minimal entrypoint `Main.kt` initializing Compose Desktop window**
- [ ] **Step 4: Verify build and launch via `./gradlew :desktop:run`**
- [ ] **Step 5: Commit changes**

---

### Task 2: Playback Abstraction & Pluggable Audio Backends (`:playback`)

**Files:**
- Create: `playback/src/commonMain/kotlin/echo/music/playback/PlaybackEngine.kt`
- Create: `playback/src/commonMain/kotlin/echo/music/playback/PlaybackState.kt`
- Create: `playback/src/jvmMain/kotlin/echo/music/playback/MpvPlaybackEngine.kt`
- Create: `playback/src/jvmMain/kotlin/echo/music/playback/GStreamerPlaybackEngine.kt`
- Create: `playback/src/jvmMain/kotlin/echo/music/playback/FallbackAudioEngine.kt`
- Create: `playback/src/commonMain/kotlin/echo/music/playback/PlaybackManager.kt`
- Test: `playback/src/jvmTest/kotlin/echo/music/playback/PlaybackEngineTest.kt`

**Interfaces:**
- Produces: `PlaybackManager` singleton with `StateFlow<PlaybackState>`, `play()`, `pause()`, `seekTo(ms)`, `setEngine(type)`.

- [ ] **Step 1: Define `PlaybackEngine` and `PlaybackState` contracts in commonMain**
- [ ] **Step 2: Implement `MpvPlaybackEngine` using `libmpv` C-JNI bindings**
- [ ] **Step 3: Implement `GStreamerPlaybackEngine` and `FallbackAudioEngine`**
- [ ] **Step 4: Implement `PlaybackManager` with queue handling, gapless transition, and engine switching**
- [ ] **Step 5: Write unit tests verifying state transitions and mock stream playback**
- [ ] **Step 6: Run tests and commit**

---

### Task 3: Linux Desktop Integration (MPRIS2, Tray & Performance Governor)

**Files:**
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/system/MprisService.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/system/TrayManager.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/system/PerformanceGovernor.kt`
- Test: `desktop/src/jvmTest/kotlin/echo/music/desktop/system/MprisServiceTest.kt`

**Interfaces:**
- Consumes: `PlaybackManager`
- Produces: Active MPRIS2 D-Bus interface (`org.mpris.MediaPlayer2.EchoMusic`), top-panel tray indicator menu, and window state listener for UI throttling.

- [ ] **Step 1: Implement `MprisService` responding to Play/Pause/Next/Seek and broadcasting metadata over D-Bus**
- [ ] **Step 2: Implement `TrayManager` with quick controls (Play/Pause, Next, Prev, Open, Quit)**
- [ ] **Step 3: Implement `PerformanceGovernor` to halt rendering and frame clocks when minimized**
- [ ] **Step 4: Test MPRIS controls via `playerctl` command line**
- [ ] **Step 5: Commit changes**

---

### Task 4: Local Auth Server & Companion WebExtension (1-Click Cookie Sync)

**Files:**
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/auth/LocalAuthServer.kt`
- Create: `companion-extension/manifest.json`
- Create: `companion-extension/background.js`
- Create: `companion-extension/popup/popup.html`
- Create: `companion-extension/popup/popup.js`
- Test: `desktop/src/jvmTest/kotlin/echo/music/desktop/auth/LocalAuthServerTest.kt`

**Interfaces:**
- Produces: Local HTTP server on `http://127.0.0.1:45454/auth/sync` and packed WebExtension for Chrome/Firefox.

- [ ] **Step 1: Implement `LocalAuthServer` with CORS security and cookie validation logic**
- [ ] **Step 2: Create Manifest V3 browser extension with 1-click sync popup**
- [ ] **Step 3: Update `InnerTube` session credentials upon receiving sync payload**
- [ ] **Step 4: Test cookie sync flow from extension to desktop app**
- [ ] **Step 5: Commit changes**

---

### Task 5: Top Panel Menu Bar & 3-Panel Core UI Shell

**Files:**
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/components/TopMenuBar.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/components/Sidebar.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/components/BottomPlayerBar.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/components/QueueLyricsPanel.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/shell/DesktopShell.kt`

**Interfaces:**
- Produces: Complete responsive desktop shell with keyboard shortcuts (`Ctrl+Q`, `Ctrl+,`, `F11`, `Space`, `Ctrl+Right/Left`, `Ctrl+K`).

- [ ] **Step 1: Implement `TopMenuBar` with File, Playback, Audio, View, and Sync dropdowns**
- [ ] **Step 2: Implement `Sidebar` with Online (Home, Explore, Library) and Local Media navigation**
- [ ] **Step 3: Implement `BottomPlayerBar` with track details, scrubber, volume slider, and mode switches**
- [ ] **Step 4: Implement collapsible `QueueLyricsPanel` on the right**
- [ ] **Step 5: Wire keyboard shortcuts and verify layout responsiveness**
- [ ] **Step 6: Commit changes**

---

### Task 6: Desktop Screens & Local Media Indexer

**Files:**
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/HomeScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/ExploreScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/LibraryScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/LocalMusicScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/SettingsScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/local/LocalMediaScanner.kt`
- Test: `desktop/src/jvmTest/kotlin/echo/music/desktop/local/LocalMediaScannerTest.kt`

**Interfaces:**
- Consumes: `:innertube`, `:domain`, `LocalMediaScanner`
- Produces: Browsable and playable views for online YT Music catalogue, user playlists, and local files.

- [ ] **Step 1: Implement `LocalMediaScanner` to parse directories for MP3/FLAC/OGG/M4A with ID3 tags**
- [ ] **Step 2: Build `HomeScreen` (Quick Picks, recommendations, recent songs)**
- [ ] **Step 3: Build `ExploreScreen` and `LibraryScreen`**
- [ ] **Step 4: Build `LocalMusicScreen` with directory tree and tag filtering**
- [ ] **Step 5: Build `SettingsScreen` (Audio Engine switch, Theme picker, Cookie sync status)**
- [ ] **Step 6: Commit changes**

---

### Task 7: Full-Screen Immersive Canvas & Synced Lyrics Engine

**Files:**
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/screens/ImmersiveCanvasScreen.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/theme/Shaders.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/components/SyncedLyricsView.kt`
- Create: `desktop/src/jvmMain/kotlin/echo/music/desktop/ui/theme/PaletteGenerator.kt`

**Interfaces:**
- Consumes: `:lyrics` module and `PlaybackManager.currentPosition`
- Produces: 60/120 FPS Skia GPU color-mesh backdrop with word-by-word active lyric highlighting and auto-hiding controls.

- [ ] **Step 1: Implement Skia AGSL runtime shader in `Shaders.kt` for fluid mesh gradient morphing**
- [ ] **Step 2: Implement dynamic palette extraction from album art bitmaps**
- [ ] **Step 3: Build `SyncedLyricsView` with word-by-word timing, spring scroll, and blur transitions**
- [ ] **Step 4: Assemble `ImmersiveCanvasScreen` with auto-hiding HUD controls (3s timer)**
- [ ] **Step 5: Test performance and frame rate**
- [ ] **Step 6: Commit changes**

---

### Task 8: Desktop Packaging & End-to-End Verification

**Files:**
- Modify: `desktop/build.gradle.kts` (configure native distributions: deb, rpm, AppImage/tar.gz)
- Create: `docs/LINUX_DESKTOP.md` (installation, dependencies, audio engine troubleshooting)

- [ ] **Step 1: Configure Compose Multiplatform desktop packaging tasks (`packageDeb`, `packageDistributionForCurrentOS`)**
- [ ] **Step 2: Verify end-to-end playback (streaming & local media) on Linux**
- [ ] **Step 3: Benchmark idle vs. background CPU/RAM performance**
- [ ] **Step 4: Write installation and user guide in `docs/LINUX_DESKTOP.md`**
- [ ] **Step 5: Commit changes**
