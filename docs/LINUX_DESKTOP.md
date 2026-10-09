# Echo Music for Linux (Desktop)

Echo Music is a YouTube Music & local media player for the Linux desktop, built with Kotlin
Multiplatform and Compose for Desktop. This guide covers installing, running, and developing the
desktop client.

## Features

- **3-panel desktop shell** — left sidebar (Home / Explore / Library / Local / Settings), center
  content area, right queue & lyrics panel, with a top menu bar and a bottom player bar.
- **Immersive canvas** — fullscreen, shader-driven mesh-gradient visualizer with word-by-word
  synced lyrics (press `F11`).
- **MPRIS2 media control** — the app registers `org.mpris.MediaPlayer2.EchoMusic` on the session
  D-Bus, so media keys, Waybar/Polybar widgets, GNOME lock-screen controls, and `playerctl` all
  work.
- **System tray** — tray icon with quick controls.
- **Pluggable playback engines** — MPV (best), GStreamer, and a built-in Fallback engine; switch
  any time from the **Audio** menu.
- **Companion browser extension** — 1-click cookie sync with music.youtube.com for signed-in
  library access, over a loopback-only local server.
- **Local music library** — scans local folders, reads tags (jaudiotagger), album-art theming.

## Install

Prebuilt artifacts are produced by the packaging tasks (see *Building from source*):

| Format | Task | Output |
| --- | --- | --- |
| Debian/Ubuntu | `:desktop:packageDeb` | `desktop/build/compose/binaries/main/deb/echo-music_1.0.0_amd64.deb` |
| Fedora/openSUSE | `:desktop:packageRpm` | `desktop/build/compose/binaries/main/rpm/echo-music-1.0.0-1.x86_64.rpm` |
| tar.gz | `:desktop:packageTarGz` | `desktop/build/compose/binaries/main/tar.gz/echo-music-1.0.0.tar.gz` |
| App image (unpacked) | `:desktop:packageAppImage` | `desktop/build/compose/binaries/main/app/echo-music/` |
| Uber jar | `:desktop:packageUberJarForCurrentOS` | `desktop/build/compose/jars/echo-music-linux-x64-1.0.0.jar` |

Install the native package:

```sh
sudo apt install ./echo-music_1.0.0_amd64.deb     # Debian/Ubuntu (installs to /opt/echo-music)
sudo rpm -i echo-music-1.0.0-1.x86_64.rpm         # Fedora/openSUSE
```

Run from the tarball (no install needed):

```sh
tar -xzf echo-music-1.0.0.tar.gz
./echo-music/bin/echo-music
```

Note: the deb/rpm bundle their own Java runtime (jlink'ed, no system JDK needed). The bundled
`.desktop` file lives inside `/opt/echo-music/lib/` (jpackage layout); if your menu does not show
an entry, create one manually:

```ini
# ~/.local/share/applications/echo-music.desktop
[Desktop Entry]
Name=Echo Music
Comment=YouTube Music & local media player for Linux
Exec=/opt/echo-music/bin/echo-music
Icon=/opt/echo-music/lib/echo-music.png
Terminal=false
Type=Application
Categories=Audio;
```

### System requirements

- x86_64 (amd64) — the packaged bundles target x86_64. Other architectures (e.g. arm64) require
  rebuilding from source; Compose for Desktop provides Skiko runtimes for linux-arm64.
- A glibc-based distribution (Debian, Ubuntu, Fedora, openSUSE, Arch, …). musl-only systems
  (e.g. vanilla Alpine) are not supported by the prebuilt bundles.
- Works on both Wayland and X11 (Compose/AWT picks the backend; on Wayland it runs through XWayland
  or the native Wayland AWT backend depending on your JDK). No display server is required for the
  packaging tasks, but a session is obviously required to actually use the app.

## Playback engines & runtime dependencies

The engines are tried in order of capability; you can force one from the **Audio** menu in the top
menu bar. Switching engines does not restart the app.

| Engine | Quality | What it needs |
| --- | --- | --- |
| **MPV** | Best (gapless, seeking, network streams) | `libmpv` — the JNA loader probes `mpv`, `libmpv.so.2`, `libmpv.so.1` |
| **GStreamer** | Good | GStreamer 1.x with base/good plugins plus a libav/ugly decoder and an audio sink |
| **Fallback** | Basic (always available) | `javax.sound` (built into the JRE) for WAV/AIFF/FLAC-PCM; optionally an `ffmpeg` binary on `PATH` to decode anything else |

### MPV engine — libmpv

Install the shared library (package names differ by distro):

- Debian/Ubuntu: `sudo apt install libmpv2` (older releases: `libmpv1`)
- Fedora: `sudo dnf install mpv-libs`
- Arch: `sudo pacman -S mpv`

Verify the loader can find it:

```sh
ldconfig -p | grep mpv     # should list libmpv.so.2 (or .so.1)
```

If libmpv is missing, the app logs `libmpv is unavailable` and falls back to the next engine — it
will not crash.

### GStreamer engine

```sh
# Debian/Ubuntu
sudo apt install gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
                 gstreamer1.0-libav gstreamer1.0-pulseaudio
# for PipeWire sessions additionally:
sudo apt install gstreamer1.0-pipewire
# Fedora
sudo dnf install gstreamer1{,-plugins-base,-plugins-good,-libav} gstreamer1-plugin-pipewire
```

The engine uses `playbin`, which auto-selects the best audio sink (`pipewritesink`,
`pulsesink`, `alsasink`…). Verify your install with:

```sh
gst-inspect-1.0 avdec_aac && gst-inspect-1.0 autoaudiosink
```

### Fallback engine

Always available — it decodes via `javax.sound.sampled` for formats the JRE supports, and shells
out to `ffmpeg` (if installed) for MP3/AAC/etc. Install it for best results:

```sh
sudo apt install ffmpeg    # or: sudo dnf install ffmpeg
```

## Audio troubleshooting (PipeWire / PulseAudio / ALSA)

- Modern distros route everything through **PipeWire** (often with the PulseAudio compatibility
  layer). Check what is actually playing:
  ```sh
  pw-top                    # live PipeWire node/activity view
  pactl info | grep Server  # confirms which PulseAudio-compatible server answers
  pactl list short sinks    # your available sinks
  ```
- If you hear nothing in the GStreamer/MPV engines, confirm the default sink is not muted and is
  the one you expect (`pavucontrol` or `pw-top` while playing).
- **"No decoder available"** (GStreamer) means `gstreamer1.0-libav` (or an equivalent decoder
  plugin for that codec) is missing — install the libav plugin set shown above. It is *not* an
  Echo Music error.
- If MPV and GStreamer both fail (missing libmpv/GStreamer), the app stays usable: the Fallback
  engine always plays what `javax.sound`/`ffmpeg` can decode. Switch engines any time:
  **Audio menu → GStreamer / Fallback** (or Settings).
- Pure-ALSA systems: `playbin` and `javax.sound` both talk to ALSA directly; make sure your user
  is in the `audio` group and nothing else holds the device (`fuser -v /dev/snd/*`).

## Companion extension (signed-in library access)

YouTube Music library browsing requires a signed-in session. Echo Music never sees your Google
credentials: it syncs the `music.youtube.com` cookies from your own browser via a companion
extension.

1. Open `companion-extension/` in your browser's extension page ("Load unpacked"):
   - Chromium/Brave: `chrome://extensions` → enable *Developer mode* → *Load unpacked* → select
     the `companion-extension/` directory of this repository.
   - Firefox: `about:debugging#/runtime/this-firefox` → *Load Temporary Add-on…* → pick
     `companion-extension/manifest.json`.
2. Grant the extension permission for `https://music.youtube.com/*` (it only reads cookies for
   that site).
3. In Echo Music open **Account & Sync → Extension Sync**. With the extension installed, syncing
   is one click: the extension posts the cookies to the local server and the app shows
   *Extension Sync: Synced* (a status dot also appears in the menu bar).
4. **Manual fallback** — if you prefer not to install the extension, use
   **Account & Sync → Manual Cookie Input…** and paste the `VISITOR_INFO1_LIVE` / `SID` /
   `HSID` / `SSID` cookie values from your browser.

Security notes:

- The sync server binds to `127.0.0.1:45454` only (loopback; never reachable from the network).
- The handshake uses a single-use token; cookies are handed over once and never stored in transit
  outside your machine.
- `GET http://127.0.0.1:45454/auth/status` returns the current sync state
  (e.g. `{"synced":false}`).

## MPRIS2 media control

Echo Music registers `org.mpris.MediaPlayer2.EchoMusic` on the session bus with the object path
`/org/mpris/MediaPlayer2`. This integrates with:

- media-key daemons and bars (Waybar, Polybar, eww), GNOME/KDE lock-screen players, and
- the `playerctl` CLI:

```sh
playerctl -p EchoMusic play-pause
playerctl -p EchoMusic next
playerctl metadata          # title/artist/album + mpris:artUrl
```

Debugging D-Bus:

```sh
dbus-monitor --session "destination=org.mpris.MediaPlayer2.EchoMusic"
busctl --user list | grep mpris
```

Graceful degradation: if there is no session bus (e.g. running from a TTY or a container),
MPRIS registration is skipped and the app continues to run normally.

## Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| `Space` | Play / pause |
| `Ctrl+Right` | Next track |
| `Ctrl+Left` | Previous track |
| `Ctrl+S` | Toggle shuffle |
| `Ctrl+R` | Cycle repeat mode |
| `Ctrl+1` | Toggle left sidebar |
| `Ctrl+2` | Toggle right (queue/lyrics) panel |
| `Ctrl+M` | Toggle mini player |
| `Ctrl+K` | Open search overlay |
| `F11` | Toggle immersive canvas (fullscreen visualizer + lyrics) |
| `Ctrl+,` | Open settings |
| `Ctrl+Q` | Quit |

## Building from source

Requirements: **JDK 21** (the build uses the Kotlin JVM toolchain; `jpackage` from JDK 21 is used
for native packaging) and the platform tools listed per engine above for *running* the app.

```sh
./gradlew :desktop:run                     # run from source
./gradlew :desktop:jvmTest                 # run the desktop test suite
./gradlew :desktop:packageDeb              # .deb   (needs fakeroot/dpkg-deb)
./gradlew :desktop:packageRpm              # .rpm   (needs rpmbuild)
./gradlew :desktop:packageTarGz            # tar.gz of the app image (needs no extra tools)
./gradlew :desktop:packageAppImage         # unpacked app image with bundled JRE
./gradlew :desktop:packageUberJarForCurrentOS   # single runnable jar (needs a system JRE 21+)
```

Notes:

- `packageTarGz` is a thin wrapper over `packageAppImage` (jpackage has no native tar.gz target);
  it archives the app image into `echo-music-1.0.0.tar.gz`.
- The uber jar needs Skiko's native bits and a system Java 21+:
  `java -jar desktop/build/compose/jars/echo-music-linux-x64-1.0.0.jar`
- Headless environments can still build all package formats; launching the GUI needs a display
  (a real session, or `xvfb-run -a` for smoke tests).

## Known limitations

- **Signed-out browsing** — home/explore/search work without an account, but your personal
  library requires the companion-extension sync (or manual cookies); library sections show an
  empty state until synced.
- **Lyrics providers** — the desktop build queries BetterLyrics, LRCLib, SimpMusic, Unison, KuGou
  and YouLyPlus; the paxsenix provider is not included on desktop.
- **Downloads and EQ** are placeholders in this release.
- **Ciphered InnerTube clients are unsupported** — playback uses YouTube's non-ciphered clients
  (WEB_REMIX, TVHTML5, IOS, ANDROID) and falls back to NewPipe-based stream extraction when needed;
  no JavaScript cipher engine is bundled.
- Menu integration of the deb/rpm relies on the manually-installable `.desktop` file described
  above.
