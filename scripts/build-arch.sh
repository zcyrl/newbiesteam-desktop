#!/usr/bin/env bash
# Builds the Arch Linux packages (.pkg.tar.zst) for NewbiesTeam + NewbiesDev.
#
# Usage:
#   ./scripts/build-arch.sh <version> [bundles-dir]
#
# <bundles-dir> must contain:
#   newbiesteam-$version-linux-portable.zip
#   newbiesdev-$version-linux-portable.zip
# (as produced by the desktop-release workflow — they are copied next to the
#  PKGBUILDs so makepkg reuses them instead of fetching the release URL).
#
# Works standalone (host makepkg, run as a normal user) or inside a Docker/CI
# container running as root: a throwaway `builder` user is created then, since
# makepkg refuses to run as root.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${1:?usage: build-arch.sh <version> [bundles-dir]}"
BUNDLES="${2:-$ROOT/bundles}"
OUT="$ROOT/out"
WORK_BASE="$ROOT/.archbuild"

if [ "$(id -u)" = "0" ]; then
  # Container mode: tooling + dedicated non-root build user.
  if command -v pacman >/dev/null 2>&1; then
    pacman -Syu --needed --noconfirm unzip fakeroot git curl >/dev/null
  fi
  if ! id builder >/dev/null 2>&1; then
    useradd -m builder
  fi
fi

# makepkg 7.x dropped the --srcdest/--pkgdest CLI options. SRCDEST, PKGDEST,
# PKGEXT and SRCEXT are honored as environment variables though: makepkg's
# load_makepkg_config preserves them and re-applies them after sourcing the
# system config (which still supplies CARCH/CHOST and the other defaults).
mkdir -p "$OUT" "$WORK_BASE"
if [ "$(id -u)" = "0" ]; then
  # In container mode makepkg runs as `builder`; the output dirs must be
  # writable by it.
  chown builder:builder "$OUT" "$WORK_BASE"
fi

build_one() {
  local app="$1"      # newbiesteam | newbiesdev
  local zipname="$2"  # newbiesteam-3.1.1-linux-portable.zip
  local pkgdir="$ROOT/scripts/arch/$app"
  local work="$WORK_BASE/$app"

  # The CI artifact preserves the upload dir layout (bundles/out/…), so locate
  # the portable zip wherever it landed inside BUNDLES.
  local zip_path
  zip_path="$(find "$BUNDLES" -type f -name "$zipname" -print -quit)"
  [ -n "$zip_path" ] || { echo "missing bundle: $zipname" >&2; exit 1; }
  [ -f "$ROOT/packaging/icons/$app.png" ] || { echo "missing icon packaging/icons/$app.png" >&2; exit 1; }
  local zip_size
  zip_size=$(stat -c '%s' "$zip_path")
  [ "$zip_size" -gt 1000000 ] || {
    echo "bundle suspiciously small ($zip_size bytes) — not a real release zip" >&2
    exit 1
  }

  rm -rf "$work"
  mkdir -p "$work/src"
  cp "$pkgdir/PKGBUILD" "$work/"
  cp "$ROOT/packaging/icons/$app.png" "$work/$app-icon.png"
  cp "$zip_path" "$work/"

  # makepkg reads SRCDEST/PKGDEST/PKGEXT/SRCEXT/PACKAGER from the environment
  # (load_makepkg_config preserves them over the system config); a wrapper
  # script keeps the quoting intact for the `su` drop-privileges call.
  cat > "$work/run-makepkg.sh" <<EOF
#!/bin/bash
set -e
cd '$work'
export SRCDEST='$work/src'
export PKGDEST='$OUT'
export PKGEXT='.pkg.tar.zst'
export SRCEXT='.src.tar.zst'
export PACKAGER='NewbiesTeam <softbrick@newbiesteam.dev>'
exec makepkg --force --noconfirm --skipinteg
EOF
  chmod 755 "$work/run-makepkg.sh"

  if [ "$(id -u)" = "0" ]; then
    chown -R builder:builder "$work"
    su builder -s /bin/bash -c "bash '$work/run-makepkg.sh'"
  else
    bash "$work/run-makepkg.sh"
  fi
}

rm -rf "$OUT"
build_one newbiesteam "newbiesteam-$VER-linux-portable.zip"
build_one newbiesdev  "newbiesdev-$VER-linux-portable.zip"

rm -rf "$WORK_BASE"
echo
echo "Arch packages ready:"
ls -la "$OUT"
