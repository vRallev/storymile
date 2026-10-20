#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <packaged-app-executable>" >&2
  exit 2
fi
if [[ ! -x "$1" ]]; then
  echo "Desktop app executable not found: $1" >&2
  exit 2
fi

smoke_home="$(mktemp -d "${TMPDIR:-/tmp}/storymile-desktop-smoke.XXXXXX")"
app_pid=""

stop_app() {
  if [[ -n "$app_pid" ]]; then
    if kill -0 "$app_pid" 2>/dev/null; then
      kill "$app_pid" 2>/dev/null || true
      for ((attempt = 0; attempt < 5; attempt++)); do
        if ! kill -0 "$app_pid" 2>/dev/null; then
          break
        fi
        sleep 1
      done
      if kill -0 "$app_pid" 2>/dev/null; then
        kill -KILL "$app_pid" 2>/dev/null || true
      fi
    fi
    wait "$app_pid" 2>/dev/null || true
    app_pid=""
  fi
}

cleanup() {
  stop_app
  rm -rf "$smoke_home"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Storage encodes the runtime-mode namespace and runtime_mode filename as hex.
preferences="$smoke_home/.storymile/storage/72756e74696d652d6d6f6465/preferences/72756e74696d655f6d6f6465.preferences_pb"
mkdir -p "$(dirname "$preferences")"
# PreferencesProto map: runtime_mode -> Value.string("Fake").
# Reading a nonempty file exercises DataStore's protobuf runtime.
printf '\012\026\012\014runtime_mode\022\006\052\004Fake' > "$preferences"

logs="$smoke_home/output.log"
env -u _JAVA_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_TOOL_OPTIONS="-Duser.home=\"$smoke_home\"" \
  XDG_CACHE_HOME="$smoke_home/cache" \
  SKIKO_RENDER_API=SOFTWARE \
  "$1" > "$logs" 2>&1 &
app_pid=$!

sleep 10
if ! kill -0 "$app_pid" 2>/dev/null; then
  exit_code=0
  wait "$app_pid" || exit_code=$?
  app_pid=""
  cat "$logs"
  echo "Desktop app exited early with code $exit_code." >&2
  exit 1
fi

stop_app
cat "$logs"
if LC_ALL=C grep -Eq '(^|[^[:alnum:]_])([[:alnum:]_.$]*(Exception|Error)|ERROR|FATAL)([^[:alnum:]_]|$)|Caused by:|SIG(SEGV|BUS|ILL|ABRT)' "$logs"; then
  echo "Desktop app reported a runtime error." >&2
  exit 1
fi
echo "Desktop packaged-app smoke test passed."
