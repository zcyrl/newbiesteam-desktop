# NewbiesTeam Desktop — repo invariants (ALWAYS FOLLOW)

1. **Two-app parity:** `:app-public` (NewbiesTeam Desktop) and `:app-dev`
   (NewbiesDev Desktop) must mirror their Android twins from the GT 20 Pro
   webapp repo (`android-app/`, `dev-app/android`) — same versions, same
   bridge surface, same router policy. Keep `gradle.properties`,
   `app-public/Main.kt` + `app-dev/Main.kt` version constants and the
   `UpdateService` user-agents in lockstep on every bump.
2. **Version bump rule:** whenever changes ship to app/shell/shared code, bump
   by change volume: `< 500 lines` → `+0.0.1`, `500–1000` → `+0.1`,
   `> 1000` → `+1.0`. Update at once: `gradle.properties` (`newbies.versionName`,
   `newbies.versionCode`), both app `Main.kt` `APP_VERSION_*` constants,
   `scripts/arch/*/PKGBUILD` `pkgver`, and this README's version table.
3. **No secrets in bundles:** never place `keys.txt`/`.relay-keys.json` into any
   packaged artifact (same rule as the APK apps). The dev portal verifies keys
   through `auth-manifest.json` (SHA-256 hashes only) fetched from the CDN /
   admin daemon — mirror that, don't bundle keys offline.
4. **No fake statuses:** never fake update-check timestamps; polling state is
   only written after a real network-backed `checkOnce()` in `UpdateService`
   (fed by `UpdatesRepository.fetchLatestUpdate()`).
5. **Dev app UI source of truth:** the dev portal files under
   `app-dev/src/main/resources/newbies-dev-ui/` are a **synced copy** of the
   webapp's `dev-app/` (`index.html, app.js, styles.css, data.js, theme.js,
   assets/, fonts/, icons/`). Re-copy them (never edit them here) and skip
   `sw.js`, `manifest.json`, APK binaries and READMEs whenever the two apps
   change upstream.
6. **Synced files caveat:** this repo clones the APK apps but does NOT
   participate in the webapp's mandatory 3-way sync (`./sync-all.sh`) —
   pipeline refactors live upstream. Only exception: rule 5's dev UI copy.
7. **Release packaging must self-contain the engine:** shipped packages always
   bundle the pinned JetBrains Runtime with JCEF
   (`jbrsdk_jcef-17.0.10-b1087.23` family). Keep the smoke test
   (`NBT_DESKTOP_SMOKE=1` exit codes 0/3/4) wired into release CI green before
   publishing; never publish installers without a passing engine boot proof.
8. **Sources stay honest:** `:shared` and `:desktop-shell` are the ported core
   of the Android apps. Comment a file with its upstream twin
   (`android-app/.../X.kt`) when it exists, and keep behavioural changes in
   both repos documented in the desktop changelog (GitHub release notes).
