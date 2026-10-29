#!/bin/bash
# Start or stop the headless 'traveler' emulator.
#
#   emulator.sh start   boot it if it is not running; when started from a Claude Code session,
#                       a watchdog stops it once that session's process exits (KEEP=1 skips it)
#   emulator.sh stop    shut it down
#
# The emulator holds ~1 GB of RAM, so nothing should leave it running after the work is done.
set -u

SDK_DIR=$(sed -n 's/^sdk\.dir=//p' "$(dirname "$0")/../../local.properties")
ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVD=${AVD:-traveler}
LOG=/tmp/traveler-emulator.log

running() { pgrep -f "qemu-system.*-avd $AVD( |$)" >/dev/null; }

claude_ancestor() {
  local pid=$PPID
  while [ "${pid:-1}" -gt 1 ]; do
    case "$(ps -o comm= -p "$pid")" in *claude*) echo "$pid"; return ;; esac
    pid=$(ps -o ppid= -p "$pid" | tr -d ' ')
  done
}

stop() {
  running || { echo "emulator not running"; return 0; }
  "$ADB" emu kill >/dev/null 2>&1
  for _ in $(seq 20); do running || { echo "emulator stopped"; return 0; }; sleep 1; done
  pkill -f "qemu-system.*-avd $AVD( |$)" && echo "emulator killed"
}

start() {
  if running; then
    echo "emulator already running"
  else
    "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim >"$LOG" 2>&1 &
    owner=$(claude_ancestor)
    if [ -n "$owner" ] && [ "${KEEP:-0}" != 1 ]; then
      nohup bash -c "while kill -0 $owner 2>/dev/null; do sleep 30; done; '$0' stop" >/dev/null 2>&1 &
      echo "emulator will stop when Claude session $owner exits (KEEP=1 to keep it)"
    fi
  fi
  "$ADB" wait-for-device && echo "emulator booting; 'make install' once it is up, 'make emulator-stop' when done"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  *) echo "usage: $0 start|stop" >&2; exit 2 ;;
esac
