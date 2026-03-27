#!/bin/bash
# frps.sh — frps 服务端启动/停止脚本
# 用法: bash frps.sh start | stop

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

FRPS_BIN="/usr/local/bin/frps"
CONFIG="config/frps.toml"
LOG_FILE="frps.log"
PID_FILE="frps.pid"

case "$1" in
  start)
    if [ -f "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE")
      if kill -0 "$PID" 2>/dev/null; then
        echo "frps is already running (PID: $PID)"
        exit 1
      else
        echo "Stale PID file found, cleaning up..."
        rm -f "$PID_FILE"
      fi
    fi
    if [ ! -f "$FRPS_BIN" ]; then
      echo "frps binary not found: $FRPS_BIN"
      exit 1
    fi
    echo "Starting frps..."
    nohup "$FRPS_BIN" -c "$CONFIG" >> "$LOG_FILE" 2>&1 &
    PID=$!
    echo "$PID" > "$PID_FILE"
    echo "frps started (PID: $PID), logs: $LOG_FILE"
    ;;

  stop)
    if [ ! -f "$PID_FILE" ]; then
      echo "PID file not found. frps may not be running."
      exit 1
    fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping frps (PID: $PID)..."
      kill "$PID"
      rm -f "$PID_FILE"
      echo "frps stopped."
    else
      echo "Process $PID is not running. Cleaning up stale PID file."
      rm -f "$PID_FILE"
      exit 1
    fi
    ;;

  *)
    echo "Usage: bash frps.sh start | stop"
    exit 1
    ;;
esac
