#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

for tool in sips iconutil python3 swift; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Missing command: $tool (run on macOS)." >&2; exit 1; }
done

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/storymile-icons.XXXXXX")"
trap 'rm -rf -- "$WORK_DIR"' EXIT

resize() {
  local size="$1" destination="$2" source="${3:-images/icon.png}"
  mkdir -p "$(dirname -- "$destination")"
  sips -s format png -z "$size" "$size" "$source" --out "$destination" >/dev/null
}

resize 512 app/android/src/main/res/drawable-nodpi/ic_launcher_artwork.png
resize 1024 app/ios/iosApp/Assets.xcassets/AppIcon.appiconset/icon.png
resize 512 app/desktop/src/desktopMain/resources/icon.png

for size in 192 512; do
  resize "$size" "app/web/src/wasmJsMain/resources/icons/icon-$size.png"
done
resize 180 app/web/src/wasmJsMain/resources/icons/apple-touch-icon.png

swift scripts/app-icon/generate-macos-icon.swift images/icon.png "$WORK_DIR/icon-macos.png"
resize 512 app/desktop/src/desktopMain/resources/icon-macos.png "$WORK_DIR/icon-macos.png"
for size in 16 32 128 256 512; do
  resize "$size" "$WORK_DIR/Storymile.iconset/icon_${size}x${size}.png" "$WORK_DIR/icon-macos.png"
  resize "$((size * 2))" "$WORK_DIR/Storymile.iconset/icon_${size}x${size}@2x.png" "$WORK_DIR/icon-macos.png"
done
mkdir -p app/desktop/icons
iconutil -c icns "$WORK_DIR/Storymile.iconset" -o app/desktop/icons/icon.icns

for size in 16 24 32 48 64 128 256; do
  resize "$size" "$WORK_DIR/$size.png"
done

# Package the resized PNGs into ICO containers without changing their artwork.
python3 - "$WORK_DIR" <<'PY'
import pathlib
import struct
import sys

work = pathlib.Path(sys.argv[1])
for destination, sizes in (
    ("app/desktop/icons/icon.ico", (16, 24, 32, 48, 64, 128, 256)),
    ("app/web/src/wasmJsMain/resources/favicon.ico", (16, 32, 48)),
):
    images = [(size, (work / f"{size}.png").read_bytes()) for size in sizes]
    offset = 6 + 16 * len(images)
    directory = bytearray(struct.pack("<HHH", 0, 1, len(images)))
    for size, data in images:
        dimension = size if size < 256 else 0
        directory.extend(struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32, len(data), offset))
        offset += len(data)
    pathlib.Path(destination).write_bytes(directory + b"".join(data for _, data in images))
PY

echo "Updated app icons from images/icon.png."
