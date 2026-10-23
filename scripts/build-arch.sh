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
  mkdir -p "$OUT" "$WORK_BASE"
  chown builder:builder "$OUT" "$WORK_BASE"
fi

build_one() {
  local app="$1"      # newbiesteam | newbiesdev
  local zipname="$2"  # newbiesteam-3.1.1-linux-portable.zip
  local pkgdir="$ROOT/scripts/arch/$app"
  local work="$WORK_BASE/$app"

  [ -f "$BUNDLES/$zipname" ] || { echo "missing bundle: $BUNDLES/$zipname" >&2; exit 1; }
  [ -f "$ROOT/packaging/icons/$app.png" ] || { echo "missing icon packaging/icons/$app.png" >&2; exit 1; }
  local zip_size
  zip_size=$(stat -c '%s' "$BUNDLES/$zipname")
  [ "$zip_size" -gt 1000000 ] || {
    echo "bundle suspiciously small ($zip_size bytes) — not a real release zip" >&2
    exit 1
  }

  rm -rf "$work"
  mkdir -p "$work"
  cp "$pkgdir/PKGBUILD" "$work/"
  cp "$ROOT/packaging/icons/$app.png" "$work/$app-icon.png"
  cp "$BUNDLES/$zipname" "$work/"

  if [ "$(id -u)" = "0" ]; then
    chown -R builder:builder "$work"
    su builder -s /bin/bash -c \
      "cd '$work' && makepkg -f --noconfirm --skipinteg --srcdest '$work/src' --pkgdest '$OUT'"
  else
    ( cd "$work" && makepkg -f --noconfirm --skipinteg --srcdest "$work/src" --pkgdest "$OUT" )
  fi
}

rm -rf "$OUT"
build_one newbiesteam "newbiesteam-$VER-linux-portable.zip"
build_one newbiesdev  "newbiesdev-$VER-linux-portable.zip"

rm -rf "$WORK_BASE"
echo
echo "Arch packages ready:"
ls -la "$OUT"
