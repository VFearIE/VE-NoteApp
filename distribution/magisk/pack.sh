#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC_APK=${1:?Usage: pack.sh signed-root.apk}
python3 "$HERE/pack.py" "$SRC_APK"
