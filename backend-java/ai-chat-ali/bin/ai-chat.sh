#!/bin/bash
# ai-chat.sh — 应用启动/停止脚本
# 用法: bash ai-chat.sh start | stop

# 固定工作目录到 ai-chat-boot/
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR"

PID_FILE="app.pid"
LOG_FILE="console.log"
JAR_FILE="lib/ai-chat-boot.jar"
ENV_FILE="config/.env"

# 加载外部环境变量（config/.env，每行格式: KEY=VALUE，# 开头为注释）
if [ -f "$ENV_FILE" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    export "$line"
  done < "$ENV_FILE"
fi

case "$1" in
  start)
    if [ -f "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE")
      if kill -0 "$PID" 2>/dev/null; then
        echo "Application is already running (PID: $PID)"
        exit 1
      else
        echo "Stale PID file found, cleaning up..."
        rm -f "$PID_FILE"
      fi
    fi
    echo "Starting ai-chat..."
    nohup java -jar "$JAR_FILE" --spring.config.additional-location=file:config/config.yml >> "$LOG_FILE" 2>&1 &
    PID=$!
    echo "$PID" > "$PID_FILE"
    echo "Application started (PID: $PID), logs: $LOG_FILE"
    ;;

  stop)
    if [ ! -f "$PID_FILE" ]; then
      echo "PID file not found. Application may not be running."
      exit 1
    fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping application (PID: $PID)..."
      kill "$PID"
      rm -f "$PID_FILE"
      echo "Application stopped."
    else
      echo "Process $PID is not running. Cleaning up stale PID file."
      rm -f "$PID_FILE"
      exit 1
    fi
    ;;

  *)
    echo "Usage: bash ai-chat.sh start | stop"
    exit 1
    ;;
esac
