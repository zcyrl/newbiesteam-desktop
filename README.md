# NewbiesTeam Desktop

Desktop clones of the NewbiesTeam Android apps, built with **Kotlin 2.2.x +
Compose Multiplatform + KCEF (Chromium)** for **Windows 11, macOS and Linux
(Arch-first)**.

The two apps reuse the same web UIs as the Android APKs — every shell loads the
*exact same hub/dev portal* (the phone's WebView stack, ported) via the
embedded JCEF browser bridge:

| Module         | App                    | Port of                                  | Version |
| -------------- | ---------------------- | ---------------------------------------- | ------- |
| `:app-public`  | **NewbiesTeam Desktop**| `android-app/` (NewbiesTeam.apk)         | 3.1.1 (code 38) |
| `:app-dev`     | **NewbiesDev Desktop** | `dev-app/android` (NewbiesDev.apk)      | 3.1.1 (code 38) |
| `:desktop-shell` | shared shell         | KCEF bootstrap, WebView, splash, tray, notifier, update polling, JS shims, dev UI server | — |
| `:shared`      | shared core            | URL router, feed parser, updates repository (KMP) | — |

The dev portal UI is **bundled inside NewbiesDev Desktop** — it is served over a
loopback HTTP server (`http://127.0.0.1:4721/index.html`) so relative paths,
`fetch()` and localStorage behave exactly like the phone, and the portal's
built-in **host mode** (`location.hostname === 127.0.0.1`) activates just like
the design intent in `app.js`. The existing admin daemon on `127.0.0.1:4720`
(`dev-tools/newbies-admin-daemon.py`) keeps working via CORS for local
publishing flows.

## Parity contract with the Android apps

* **Version parity** — both desktop apps march alongside their APK twins
  (`3.1.1`, code `38` today). Bump `gradle.properties`,
  `:desktop-shell`/`UpdateService` default UA, both app `Main.kt` constants and
  the `PKGBUILD` `pkgver=` in one change.
* **Bridge parity** — same `window.AndroidBridge` surface the Android side
  exposes (`getAppVersion`, `getAppVersionCode`, `getDeviceId`, dev-state save/
  load/clear, clipboard, toasts, APK download, file picker…), implemented by
  injected JS shims in `JsShims.kt`.
* **Router parity** — external pages/downloads open in the OS browser, hub
  pages stay in the shell. The policy is the shared `UrlRouter`
  (both the Kotlin one for the shell and a JS mirror injected per page load).
* **Update polling parity** — 15-min `updates.json` polling + desktop toasts,
  mirroring the Android `NewbiesApp`/WorkManager pair.

## Building

```bash
./gradlew build                                        # tests + compile
./gradlew :app-public:run                              # run NewbiesTeam Desktop (system JDK)
./gradlew :app-dev:run                                 # run NewbiesDev Desktop (system JDK)
```

A plain system JDK run downloads the CEF engine once into
`~/.local/share/NewbiesTeamDesktop/kcef-bundle` (progress is shown on the
splash). Production packages are built on CI with the pinned **JetBrains
Runtime with JCEF** (`jbrsdk_jcef-17.0.10-b1087.23`), which removes that
download entirely — even offline installs work.

### CI / Release

* `ci.yml` — build + shared core tests on every push/PR.
* `release.yml` — tag `v*` → 3-OS matrix (Windows MSI, macOS unsigned DMG,
  Linux DEB/RPM/AppImage), portable zips, smoke-tested (`NBT_DESKTOP_SMOKE=1`
  boots the packaged engine headlessly), plus the Arch `.pkg.tar.zst` packages
  (via `scripts/build-arch.sh`) and a GitHub release upload.

### Arch packages

```bash
# With release bundles downloaded into ./bundles:
./scripts/build-arch.sh 3.1.1 bundles
pacman -U out/newbiesteam-desktop-3.1.1-1-x86_64.pkg.tar.zst
```

## Dev runs (troubleshooting)

* `NBT_OPEN_TARGET=updates` starts the public app on the Updates section.
* `NBT_DEV_FALLBACK_URL=...` overrides the dev portal fallback (CDN URL) if the
  loopback server fails to bind.
* `NBT_DESKTOP_SMOKE=1` runs the headless engine boot (CI proof, exits 0/3/4).
* `NBT_DESKTOP_CEF_ARGS="--fake-user-data-dir=... ..."` feeds extra Chromium
  flags, e.g. on nightly GPU stacks.
