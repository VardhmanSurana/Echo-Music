# Subagent Prompts for Echo Music Linux Desktop Implementation

This document contains self-contained, copy-ready prompts for subagents assigned to implement each task of the [Implementation Plan](implementation_plan.md).

---

### Task 1: Desktop Module Setup & Multiplatform Gradle Configuration

```markdown
You are assigned to implement **Task 1: Desktop Module Setup & Multiplatform Gradle Configuration** for Echo Music.

**Context & Requirements:**
- Read the architecture and spec at `docs/superpowers/specs/2026-10-09-linux-desktop-design.md` or artifact `design_spec.md`.
- Set up a new `:desktop` Gradle subproject using Compose Multiplatform for Kotlin Desktop (JVM target 21).
- Ensure existing Android build variants (`foss` and `gms`) are unaffected in `app/build.gradle.kts`.
- Include dependencies linking `:core`, `:innertube`, `:domain`, and `:lyrics`.
- Create the initial `Main.kt` entrypoint creating an `application { Window(...) { ... } }` and verify with a smoke test.

**Verification Command:**
Run `./gradlew :desktop:compileKotlinJvm` and ensure it succeeds without errors.
```

---

### Task 2: Playback Abstraction & Pluggable Audio Backends (`:playback`)

```markdown
You are assigned to implement **Task 2: Playback Abstraction & Pluggable Audio Backends** for Echo Music.

**Context & Requirements:**
- Create an abstract `PlaybackEngine` interface and `PlaybackState` in `playback/src/commonMain/kotlin/echo/music/playback/`.
- Implement `MpvPlaybackEngine` utilizing `libmpv` C-JNI bindings for Linux desktop.
- Implement `GStreamerPlaybackEngine` and a bundled `FallbackAudioEngine` for minimal environments.
- Implement `PlaybackManager` singleton to coordinate queue navigation, track transitions, volume, seeking, and runtime engine switching.
- Write unit tests verifying state transitions (Idle -> Loading -> Playing -> Paused -> Stopped) and stream URL parsing.

**Verification Command:**
Run `./gradlew :playback:jvmTest` and verify all tests pass.
```

---

### Task 3: Linux Desktop Integration (MPRIS2, Tray & Performance Governor)

```markdown
You are assigned to implement **Task 3: Linux Desktop Integration (MPRIS2, Tray & Performance Governor)**.

**Context & Requirements:**
- Implement `MprisService` over Linux D-Bus (`org.mpris.MediaPlayer2` and `org.mpris.MediaPlayer2.Player`).
- Wire system media keys (Play/Pause, Next, Previous, Stop, Seek, Volume) and publish current track metadata and album art URI to D-Bus.
- Implement `TrayManager` for the Linux system tray / AppIndicator with quick playback actions.
- Implement `PerformanceGovernor` to monitor window minimized/iconified state and freeze Skia frame tickers to reduce background CPU usage to < 1%.

**Verification Command:**
Run `./gradlew :desktop:test --tests "echo.music.desktop.system.MprisServiceTest"` and verify D-Bus command mapping.
```

---

### Task 4: Local Auth Server & Companion WebExtension (1-Click Cookie Sync)

```markdown
You are assigned to implement **Task 4: Local Auth Server & Companion WebExtension (1-Click Cookie Sync)**.

**Context & Requirements:**
- Implement `LocalAuthServer` in `:desktop` running on `http://127.0.0.1:45454/auth/sync` with strict localhost origin validation.
- Build a Manifest V3 WebExtension in `companion-extension/` with a 1-click popup that grabs `music.youtube.com` session cookies (`SAPISID`, `SSID`, `HSID`, `LOGIN_INFO`) and posts them to the local server.
- Update `InnerTube` session credentials automatically on successful sync.

**Verification Command:**
Run `./gradlew :desktop:test --tests "echo.music.desktop.auth.LocalAuthServerTest"` to verify HTTP payload parsing and cookie ingestion.
```

---

### Task 5: Top Panel Menu Bar & 3-Panel Core UI Shell

```markdown
You are assigned to implement **Task 5: Top Panel Menu Bar & 3-Panel Core UI Shell**.

**Context & Requirements:**
- Build `TopMenuBar` with dropdown menus: `File`, `Playback`, `Audio` (engine selector), `View`, `Account & Sync`, plus global search shortcut (`Ctrl+K`) and window controls.
- Build `Sidebar` navigation for Online (Home, Explore, Liked, Playlists) and Local Media.
- Build `BottomPlayerBar` with track details, scrubber progress bar, volume control, and mode switches.
- Build collapsible `QueueLyricsPanel` on the right side.
- Assemble `DesktopShell` connecting keyboard shortcuts (`Ctrl+Q`, `Ctrl+,`, `F11`, `Space`, `Ctrl+Right/Left`, `Ctrl+B`).

**Verification Command:**
Run `./gradlew :desktop:compileKotlinJvm` and verify UI component rendering in test previews.
```

---

### Task 6: Desktop Screens & Local Media Indexer

```markdown
You are assigned to implement **Task 6: Desktop Screens & Local Media Indexer**.

**Context & Requirements:**
- Implement `LocalMediaScanner` to index folders for MP3, FLAC, OGG, and M4A files with ID3 metadata.
- Implement `HomeScreen` (Quick Picks, recommendations, recent songs) using `:innertube`.
- Implement `ExploreScreen` (Charts, Moods, Genres) and `LibraryScreen` (Liked Songs, User Playlists).
- Implement `LocalMusicScreen` with directory browser and tag filters.
- Implement `SettingsScreen` with Audio Engine selector, Theme picker, and cookie sync status.

**Verification Command:**
Run `./gradlew :desktop:test --tests "echo.music.desktop.local.LocalMediaScannerTest"`.
```

---

### Task 7: Full-Screen Immersive Canvas & Synced Lyrics Engine

```markdown
You are assigned to implement **Task 7: Full-Screen Immersive Canvas & Synced Lyrics Engine**.

**Context & Requirements:**
- Implement Skia AGSL runtime shaders (`Shaders.kt`) for animated fluid color-mesh background gradients extracted from album art.
- Build dynamic palette extraction using dominant/complementary colors from album art bitmaps.
- Implement `SyncedLyricsView` with word-by-word active tracking, spring scroll physics, and blurred inactive lines.
- Assemble `ImmersiveCanvasScreen` (triggered by F11 or Fullscreen button) with auto-hiding HUD controls (3-second mouse idle timer).

**Verification Command:**
Run `./gradlew :desktop:compileKotlinJvm` and verify shader compilation without Skia runtime errors.
```

---

### Task 8: Desktop Packaging & End-to-End Verification

```markdown
You are assigned to implement **Task 8: Desktop Packaging & End-to-End Verification**.

**Context & Requirements:**
- Configure Compose Multiplatform desktop packaging in `desktop/build.gradle.kts` for native Linux distribution (`.deb`, `.rpm`, `.tar.gz`).
- Verify end-to-end audio playback for both YouTube streaming and local music files.
- Benchmark idle, active, and minimized background CPU and RAM usage.
- Create user and developer documentation in `docs/LINUX_DESKTOP.md`.

**Verification Command:**
Run `./gradlew :desktop:packageDistributionForCurrentOS` and verify output binary builds successfully.
```
