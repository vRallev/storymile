#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$REPO_DIR/playback/impl/src/commonMain/composeResources/files/test-episodes"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

mkdir -p "$OUTPUT_DIR"
for number in 1 2 3; do
  name="test-episode-$number"
  say -v Samantha -r 165 -f "$SCRIPT_DIR/$name.txt" -o "$WORK_DIR/$name.aiff"
  ffmpeg -hide_banner -loglevel error -y -i "$WORK_DIR/$name.aiff" \
    -map_metadata -1 -ac 1 -ar 44100 -codec:a libmp3lame -b:a 64k \
    -metadata title="Test episode $number" -metadata artist="Storymile" \
    "$OUTPUT_DIR/$name.mp3"
  ffprobe -v error -show_entries format=duration "$OUTPUT_DIR/$name.mp3" > /dev/null
done
