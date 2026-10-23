# NewbiesTeam Desktop

Desktop companions for the **NewbiesTeam GT 20 Pro Web Hub**, built with
**Kotlin 2.4 + Compose Multiplatform + KCEF (Chromium)** for
**Windows, macOS and Linux (Arch-first)**:

| App | What it is |
|---|---|
| **NewbiesTeam** | The public Hub — ROMs, guides, firmware, updates and the community feed, in a native window. |
| **NewbiesDev** | The team authoring tool — publish/edit hub content, dev relay presence and live-feed sync. |

Every release package ships its own **JetBrains Runtime with JCEF** — a full
Chromium engine that needs **zero first-run downloads** and works offline.
You do **not** need Java installed.

[![Desktop Release](https://github.com/zcyrl/newbiesteam-desktop/actions/workflows/release.yml/badge.svg?event=tag)](https://github.com/zcyrl/newbiesteam-desktop/actions/workflows/release.yml)

---

## Downloads

Grab everything from the [**latest release page**](https://github.com/zcyrl/newbiesteam-desktop/releases/latest):

| OS | Installer (recommended) | Portable (no install) |
|---|---|---|
| Windows | `NewbiesTeam-<ver>.msi` / `NewbiesDev-<ver>.msi` | `newbiesteam-<ver>-win-portable.zip` / `newbiesdev-<ver>-win-portable.zip` |
| macOS | `NewbiesTeam-<ver>.dmg` / `NewbiesDev-<ver>.dmg` | `newbiesteam-<ver>-mac-portable.zip` / `newbiesdev-<ver>-mac-portable.zip` |
| Debian / Ubuntu | `newbiesteam_<ver>-1_amd64.deb` / `newbiesdev_<ver>-1_amd64.deb` | `newbiesteam-<ver>-linux-portable.zip` / `newbiesdev-<ver>-linux-portable.zip` |
| Fedora / openSUSE | `newbiesteam-<ver>-1.x86_64.rpm` / `newbiesdev-<ver>-1.x86_64.rpm` | same portable zips |
| Arch Linux | `newbiesteam-desktop-<ver>-1-x86_64.pkg.tar.zst` / `newbiesdev-desktop-…` | same portable zips |

## Install guide by OS

### Windows 10 / 11 (x64)

**MSI installer**
1. Download `NewbiesTeam-<version>.msi` (and/or the NewbiesDev MSI).
2. Double-click and follow the wizard — the app installs under
   `Program Files` with Start-menu shortcuts.
3. **SmartScreen may show "Windows protected your PC"** — the builds are
   currently unsigned. Click *More info → Run anyway*. Verify checksums
   against the release page if in doubt.

**Portable**
1. Download the `-win-portable.zip` for the app you want.
2. Right-click → *Extract All…* to any folder (e.g. `C:\Tools\NewbiesTeam`).
3. Run `NewbiesTeam\NewbiesTeam.exe` inside.

### macOS 12+ (Apple Silicon)

**DMG**
1. Download `NewbiesTeam-<version>.dmg`, open it, drag `NewbiesTeam.app`
   into *Applications*.
2. **Gatekeeper** warns that the app is from an unknown developer (builds
   are currently unsigned). Allow it with either method:
   - Terminal (easiest):
     ```
     xattr -cr /Applications/NewbiesTeam.app
     ```
     then open normally, or
   - *System Settings → Privacy & Security → Security → Open Anyway*.

**Portable**
```bash
unzip newbiesteam-<version>-mac-portable.zip -d ~/Applications
xattr -cr ~/Applications/NewbiesTeam.app
open ~/Applications/NewbiesTeam.app
```

### Debian / Ubuntu / Mint (x64)

```bash
sudo apt update
sudo apt install ./newbiesteam_<version>-1_amd64.deb
# or double-click the .deb in your package manager
```

### Fedora / RHEL / openSUSE (x64)

```bash
# Fedora / RHEL
sudo dnf install ./newbiesteam-<version>-1.x86_64.rpm

# openSUSE
sudo zypper install ./newbiesteam-<version>-1.x86_64.rpm
```

### Arch Linux (x64)

The `.pkg.tar.zst` files are built with makepkg and install directly —
no AUR package needed:

```bash
sudo pacman -U ./newbiesteam-desktop-<version>-1-x86_64.pkg.tar.zst
```

### Universal Linux portable (any distro)

No root required, nothing installed system-wide:

```bash
unzip newbiesteam-<version>-linux-portable.zip
./NewbiesTeam/bin/NewbiesTeam
```

## Requirements

| | Minimum |
|---|---|
| Windows | Windows 10 (x64) |
| macOS | macOS 12 (Apple Silicon) |
| Linux | glibc 2.31+ (x64: Debian 10+, Ubuntu 20.04+, Arch current, Fedora 36+) |
| RAM | ~1 GB for the Hub (each app ships its own browser engine) |
| Disk | ~700 MB installed per app |

## Security model (why your keys/data stay safe)

- Authentication for the Dev app uses **remote SHA-256 key-hash
  verification**: developer keys exist only with their owners; the
  repository and the packages never contain keys, tokens or passwords.
- Dev submissions use a relay channel injected at build time from a
  repository secret (never committed) — see
  [`config-overrides.example.js`](app-dev/src/main/resources/newbies-dev-ui/config-overrides.example.js).
- Credential/signing files (`keys.txt`, `.relay-keys.json`, `*.jks`,
  `*.pem`, `*.key`, `.env`) are gitignored and must never be committed.

---

## Project layout

| Module | App | Port of | Version |
| -------------- | ---------------------- | ---------------------------------------- | ------- |
| `:app-public`  | **NewbiesTeam Desktop**| `android-app/` (NewbiesTeam.apk)         | 3.1.1 (code 38) |
| `:app-dev`     | **NewbiesDev Desktop** | `dev-app/android` (NewbiesDev.apk)      | 3.1.1 (code 38) |
| `:desktop-shell` | shared shell         | KCEF bootstrap, WebView, splash, tray, notifier, update polling, JS shims, dev UI server | — |
| `:shared`      | shared core            | URL router, feed parser, updates repository (KMP) | — |

The dev portal UI is **bundled inside NewbiesDev Desktop** — it is served over
a loopback HTTP server (`http://127.0.0.1:4721/index.html`) so relative paths,
`fetch()` and localStorage behave exactly like the phone, and the portal's
built-in **host mode** (`location.hostname === 127.0.0.1`) activates just like
on the APK.

## Parity contract with the Android apps

* **Version parity** — both desktop apps march alongside their APK twins.
  Bump `gradle.properties`, the UpdateService default UA, both app `Main.kt`
  constants and the `PKGBUILD` `pkgver=` in one change.
* **Bridge parity** — same `window.AndroidBridge` surface the Android side
  exposes (`getAppVersion`, `getAppVersionCode`, `getDeviceId`, dev-state
  save/load/clear, clipboard, toasts, APK download, file picker…),
  implemented by injected JS shims in `JsShims.kt`.
* **Router parity** — external pages/downloads open in the OS browser, hub
  pages stay in the shell (shared `UrlRouter`).
* **Update polling parity** — 15-min `updates.json` polling + desktop
  toasts, mirroring the Android `NewbiesApp`/WorkManager pair.

## Building from source

```bash
git clone https://github.com/zcyrl/newbiesteam-desktop.git
cd newbiesteam-desktop
./gradlew build                                        # tests + compile
./gradlew :app-public:run                              # run NewbiesTeam (system JDK)
./gradlew :app-dev:run                                 # run NewbiesDev (system JDK)
./gradlew :app-public:packageDistributionForCurrentOS \
           :app-dev:packageDistributionForCurrentOS   # packages
```

Outputs land in
`app-<flavor>/build/compose/binaries/main/{app,msi,deb,rpm,dmg,pkg}/`.
A plain system-JDK run downloads the CEF engine once into
`~/.local/share/NewbiesTeamDesktop/kcef-bundle`; production packages use the
pinned **JBR-with-JCEF** runtime and never download.

### CI / Release

* `ci.yml` — build + shared core tests on every push/PR.
* `release.yml` — tag `v*` → 3-OS matrix (Windows MSI, macOS DMG/PKG,
  Linux DEB/RPM), portable zips, smoke-tested
  (`NBT_DESKTOP_SMOKE=1` boots the packaged engine headlessly), plus the
  Arch `.pkg.tar.zst` packages (`scripts/build-arch.sh`) and a GitHub
  release publish.
* The dev relay config is injected only from the
  `NBT_DEV_RELAY_CONFIG` repository secret — keep real channels out of
  the source tree.

### Arch packages (local)

```bash
# With release bundles downloaded into ./bundles:
./scripts/build-arch.sh 3.1.1 bundles
pacman -U out/newbiesteam-desktop-3.1.1-1-x86_64.pkg.tar.zst
```

## Dev runs (troubleshooting)

* `NBT_OPEN_TARGET=updates` starts the public app on the Updates section.
* `NBT_DEV_FALLBACK_URL=...` overrides the dev portal fallback (CDN URL) if
  the loopback server fails to bind.
* `NBT_DESKTOP_SMOKE=1` runs the headless engine boot (CI proof, exits 0/3/4).
* `NBT_DESKTOP_CEF_ARGS="--fake-user-data-dir=..."` feeds extra Chromium
  flags, e.g. on nightly GPU stacks.

---

## License

Released under the [**GPL-3.0**](LICENSE) license. You are free to use,
study, modify and redistribute — **but** any fork/derivative must also be
GPL-licensed and carry the same freedoms. This keeps private commercial
clones out while staying fully open source.

© NewbiesTeam · GT 20 Pro Web Hub
