#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

choose() {
  local title="$1" default="$2" answer index=1
  shift 2
  (( $# > 0 )) || fail "No available devices. Connect a device or create an emulator/simulator first."
  printf '\n%s\n' "$title"
  for answer in "$@"; do
    printf '%d) %s\n' "$index" "$answer"
    index=$((index + 1))
  done
  while true; do
    printf 'Selection [1-%d, default %d]: ' "$#" "$default"
    read -r answer || fail "Selection cancelled."
    answer="${answer:-$default}"
    if [[ "$answer" =~ ^[0-9]{1,3}$ ]] && (( 10#$answer >= 1 && 10#$answer <= $# )); then
      SELECTION=$((10#$answer - 1))
      return
    fi
    echo "Invalid selection."
  done
}

choose_target() {
  local kind identifier label
  TARGET_KINDS=()
  TARGET_IDS=()
  TARGET_LABELS=()
  [[ -s "$WORK_DIR/targets" ]] || fail "No available devices. Connect a device or create an emulator/simulator first."
  while IFS=$'\t' read -r kind identifier label; do
    TARGET_KINDS+=("$kind")
    TARGET_IDS+=("$identifier")
    TARGET_LABELS+=("$label")
  done < "$WORK_DIR/targets"
  choose "Which device should run Storymile?" 1 "${TARGET_LABELS[@]}"
  TARGET_KIND="${TARGET_KINDS[$SELECTION]}"
  TARGET_ID="${TARGET_IDS[$SELECTION]}"
}

find_android_tool() {
  local name="$1" relative="$2" sdk
  if command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
    return
  fi
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
    "$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)" \
    "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [[ -n "$sdk" && -x "$sdk/$relative" ]]; then
      printf '%s\n' "$sdk/$relative"
      return
    fi
  done
  return 1
}

find_running_avd() {
  local serial state details name
  "$ADB" devices > "$WORK_DIR/adb-devices"
  while read -r serial state details; do
    if [[ "$serial" == emulator-* && "$state" == device ]]; then
      name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | sed -n '1{s/\r$//;p;}')"
      if [[ "$name" == "$TARGET_ID" ]]; then
        ANDROID_SERIAL_ID="$serial"
        return 0
      fi
    fi
  done < "$WORK_DIR/adb-devices"
  return 1
}

run_android() {
  require_command python3
  ADB="$(find_android_tool adb platform-tools/adb)" || fail "Install Android SDK platform-tools and set ANDROID_HOME."
  EMULATOR="$(find_android_tool emulator emulator/emulator || true)"
  local serial state details name avd deadline emulator_pid booted
  : > "$WORK_DIR/targets"
  : > "$WORK_DIR/running-avds"
  "$ADB" devices -l > "$WORK_DIR/adb-devices"
  while read -r serial state details; do
    [[ -n "$serial" && "$serial" != List ]] || continue
    if [[ "$state" != device ]]; then
      echo "Skipping $serial ($state). Unlock the device and authorize USB debugging if needed." >&2
      continue
    fi
    if [[ "$serial" == emulator-* ]]; then
      name="$("$ADB" -s "$serial" emu avd name | sed -n '1{s/\r$//;p;}')"
      printf '%s\n' "$name" >> "$WORK_DIR/running-avds"
      printf 'device\t%s\t%s — running emulator (%s)\n' "$serial" "$name" "$serial" >> "$WORK_DIR/targets"
    else
      name="$("$ADB" -s "$serial" shell getprop ro.product.model | tr -d '\r')"
      printf 'device\t%s\t%s — connected device (%s)\n' "$serial" "$name" "$serial" >> "$WORK_DIR/targets"
    fi
  done < "$WORK_DIR/adb-devices"
  if [[ -n "$EMULATOR" ]]; then
    "$EMULATOR" -list-avds > "$WORK_DIR/avds"
    while IFS= read -r avd; do
      avd="${avd%$'\r'}"
      [[ -n "$avd" ]] || continue
      if ! grep -Fxq -- "$avd" "$WORK_DIR/running-avds"; then
        printf 'avd\t%s\t%s — start emulator\n' "$avd" "$avd" >> "$WORK_DIR/targets"
      fi
    done < "$WORK_DIR/avds"
  fi
  choose_target
  ANDROID_SERIAL_ID="$TARGET_ID"
  if [[ "$TARGET_KIND" == avd ]]; then
    echo "Starting $TARGET_ID..."
    mkdir -p "$ROOT_DIR/build/run"
    nohup "$EMULATOR" -avd "$TARGET_ID" > "$ROOT_DIR/build/run/android-emulator.log" 2>&1 < /dev/null &
    emulator_pid=$!
    deadline=$((SECONDS + 180))
    until find_running_avd; do
      kill -0 "$emulator_pid" 2>/dev/null || fail "Emulator exited. See build/run/android-emulator.log."
      (( SECONDS < deadline )) || fail "Emulator connection timed out. See build/run/android-emulator.log."
      sleep 2
    done
  fi
  echo "Waiting for Android on $ANDROID_SERIAL_ID..."
  deadline=$((SECONDS + 180))
  while true; do
    booted="$("$ADB" -s "$ANDROID_SERIAL_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    [[ "$booted" == 1 ]] && break
    (( SECONDS < deadline )) || fail "Android did not finish booting within three minutes."
    sleep 2
  done

  ./gradlew --quiet :app:android:assembleDebug
  python3 - "$ROOT_DIR" > "$WORK_DIR/apk" <<'PY'
import json
import pathlib
import sys

directory = pathlib.Path(sys.argv[1]) / "app/android/build/outputs/apk/debug"
metadata = json.loads((directory / "output-metadata.json").read_text())
apks = metadata["elements"]
if len(apks) != 1:
    sys.exit("Expected one debug APK; split APKs need a matching device configuration.")
print(metadata["applicationId"])
print(directory / apks[0]["outputFile"])
PY
  local app_id apk activity
  { read -r app_id; read -r apk; } < "$WORK_DIR/apk"
  "$ADB" -s "$ANDROID_SERIAL_ID" install -r "$apk"
  activity="$("$ADB" -s "$ANDROID_SERIAL_ID" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$app_id" | tr -d '\r' | tail -n 1)"
  [[ "$activity" == */* ]] || fail "Could not resolve the launcher activity for $app_id."
  "$ADB" -s "$ANDROID_SERIAL_ID" shell am start -W -S -n "$activity" | tee "$WORK_DIR/android-launch"
  grep -q '^Status: ok' "$WORK_DIR/android-launch" || fail "Android did not report a successful launch."
}

run_desktop() {
  local size=phone
  choose "Which Desktop window size?" 1 "Phone (480 × 840)" "Tablet (1100 × 760)"
  [[ "$SELECTION" == 0 ]] || size=tablet
  ./gradlew --quiet :app:desktop:hotRunDesktop --auto --args="--window-size=$size"
}

run_ios() {
  [[ "$(uname -s)" == Darwin ]] || fail "iOS requires macOS and Xcode."
  require_command xcodebuild
  require_command xcode-select
  require_command xcrun
  require_command python3
  xcrun xcdevice list --timeout 5 > "$WORK_DIR/apple-devices.json"
  python3 - "$WORK_DIR/apple-devices.json" > "$WORK_DIR/targets" <<'PY'
import json
import sys

devices = json.load(open(sys.argv[1]))
for device in sorted(devices, key=lambda d: (d.get("simulator", False), d.get("name", ""))):
    if not device.get("available") or device.get("ignored"):
        continue
    platform = device.get("platform")
    if platform not in ("com.apple.platform.iphoneos", "com.apple.platform.iphonesimulator"):
        continue
    simulator = device.get("simulator", False)
    if simulator and device.get("architecture") != "arm64":
        continue
    kind = "simulator" if simulator else "device"
    name = device["name"].replace("\t", " ").replace("\n", " ")
    version = device.get("operatingSystemVersion", "")
    identifier = device["identifier"]
    print(f"{kind}\t{identifier}\t{name} — iOS {version}, {kind} ({identifier})")
PY
  choose_target
  local sdk destination derived app_path bundle_id developer_dir device_hub simulator_app
  local signing=() apps=()
  if [[ "$TARGET_KIND" == simulator ]]; then
    sdk=iphonesimulator
    destination="platform=iOS Simulator,id=$TARGET_ID"
    xcrun simctl bootstatus "$TARGET_ID" -b
    developer_dir="$(xcode-select -p)"
    device_hub="$developer_dir/../Applications/DeviceHub.app"
    simulator_app="$developer_dir/Applications/Simulator.app"
    if [[ -d "$device_hub" ]]; then
      open -a "$device_hub" "devices://device/open?id=$TARGET_ID"
    elif [[ -d "$simulator_app" ]]; then
      open -a "$simulator_app" --args -CurrentDeviceUDID "$TARGET_ID"
    else
      fail "Cannot find Device Hub or Simulator in the selected Xcode installation: $developer_dir"
    fi
    signing=(CODE_SIGNING_ALLOWED=NO)
  else
    sdk=iphoneos
    destination="platform=iOS,id=$TARGET_ID"
    echo "Device builds require TEAM_ID in app/ios/Configuration/Config.xcconfig and Xcode signing access."
    signing=(-allowProvisioningUpdates)
  fi
  derived="$ROOT_DIR/build/run/ios"
  GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.logging.level=quiet" \
    xcodebuild -quiet -project "$ROOT_DIR/app/ios/iosApp.xcodeproj" -scheme iosApp \
    -configuration Debug -destination "$destination" -derivedDataPath "$derived" \
    "${signing[@]}" build
  apps=("$derived/Build/Products/Debug-$sdk/"*.app)
  [[ ${#apps[@]} == 1 && -d "${apps[0]}" ]] || fail "Expected one built app in $derived/Build/Products/Debug-$sdk."
  app_path="${apps[0]}"
  bundle_id="$(python3 - "$app_path/Info.plist" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as file:
    print(plistlib.load(file)["CFBundleIdentifier"])
PY
  )"
  if [[ "$TARGET_KIND" == simulator ]]; then
    xcrun simctl install "$TARGET_ID" "$app_path"
    xcrun simctl launch --terminate-running-process "$TARGET_ID" "$bundle_id"
  else
    xcrun devicectl device install app --device "$TARGET_ID" "$app_path"
    xcrun devicectl device process launch --device "$TARGET_ID" --terminate-existing "$bundle_id"
  fi
}

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo "Usage: ./run.sh"
  echo "Choose Android, Desktop (hot reload), iOS, or Wasm, then a device or window size."
  exit 0
fi
[[ $# == 0 ]] || fail "Usage: ./run.sh"

choose "Which platform?" 2 Android Desktop iOS Wasm
PLATFORM="$SELECTION"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/storymile-run.XXXXXX")"
trap 'rm -rf -- "$WORK_DIR"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
case "$PLATFORM" in
  0) run_android ;;
  1) run_desktop ;;
  2) run_ios ;;
  3) ./gradlew --quiet -Pstorymile.enableWasm=true --no-isolated-projects :app:web:wasmJsBrowserDevelopmentRun ;;
esac
