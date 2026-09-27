#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p build/ios-smoke-test
RESULTS_DIR="$(mktemp -d "$ROOT_DIR/build/ios-smoke-test/run.XXXXXX")"
echo "iOS smoke test results: $RESULTS_DIR"

xcrun simctl list runtimes -j > "$RESULTS_DIR/runtimes.json"
RUNTIME_ID="$(python3 - "$RESULTS_DIR/runtimes.json" <<'PY'
import json
import sys

with open(sys.argv[1]) as file:
    runtimes = [
        runtime for runtime in json.load(file)["runtimes"]
        if runtime["isAvailable"]
        and runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-26-")
    ]
if not runtimes:
    sys.exit("Install an iOS 26 simulator runtime in Xcode before running this test.")
runtime = max(runtimes, key=lambda runtime: tuple(map(int, runtime["version"].split("."))))
print(runtime["identifier"])
PY
)"

SIMULATOR_ID="$(xcrun simctl create "Storymile smoke test" \
  com.apple.CoreSimulator.SimDeviceType.iPhone-17 "$RUNTIME_ID")"
cleanup() {
  local result=$?
  if [[ -d "$RESULTS_DIR/LaunchTests.xcresult" ]]; then
    xcrun xcresulttool export attachments \
      --path "$RESULTS_DIR/LaunchTests.xcresult" \
      --output-path "$RESULTS_DIR/attachments" || true
  fi
  xcrun simctl shutdown "$SIMULATOR_ID" >/dev/null 2>&1 || true
  xcrun simctl delete "$SIMULATOR_ID" >/dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

xcrun simctl bootstatus "$SIMULATOR_ID" -b
export GRADLE_ARGS="${GRADLE_ARGS:-} --quiet"
xcodebuild -quiet \
  -project app/ios/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" \
  -derivedDataPath "$ROOT_DIR/build/ios-smoke-test/DerivedData" \
  -resultBundlePath "$RESULTS_DIR/LaunchTests.xcresult" \
  -collect-test-diagnostics never \
  -parallel-testing-enabled NO \
  -test-timeouts-enabled YES \
  -maximum-test-execution-time-allowance 120 \
  -only-testing:iosAppUITests/LaunchTests \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO \
  test 2>&1 | tee "$RESULTS_DIR/xcodebuild.log"
