#!/bin/bash

set -e

echo "🛑 Stopping Playbot production instance..."

# Helper: stop by strict command match
stop_by_cmd() {
    local pattern="$1"
    if pgrep -f "$pattern" >/dev/null 2>&1; then
        pkill -f "$pattern" || true
        sleep 2
        if pgrep -f "$pattern" >/dev/null 2>&1; then
            echo "⚠️  Process still running for pattern [$pattern], force killing..."
            pkill -9 -f "$pattern" || true
            sleep 1
        fi
        return 0
    fi
    return 1
}

# Prefer systemd if available (common for production)
if command -v systemctl >/dev/null 2>&1; then
    if systemctl list-units --type=service --all 2>/dev/null | grep -qE '^\s*playbot\.service'; then
        echo "Using systemd: stopping playbot.service"
        if systemctl stop playbot >/dev/null 2>&1; then
            echo "✅ Production bot stopped via systemd"
            exit 0
        else
            echo "⚠️  systemd stop failed; falling back to process matching..."
        fi
    fi
fi

# Use a strict absolute path when possible to avoid killing dev instances
VERSION=$(grep -A 1 '<artifactId>playbot</artifactId>' pom.xml | grep '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/' || true)
PROD_DIR="/opt/playbot/target"
KILLED=false

if [ -n "$VERSION" ] && [ -d "$PROD_DIR" ]; then
    if stop_by_cmd "$PROD_DIR/playbot-${VERSION}.jar"; then
        echo "✅ Production bot stopped (matched $PROD_DIR/playbot-${VERSION}.jar)"
        KILLED=true
    fi
fi

# Also handle unversioned JAR convention
if [ -d "$PROD_DIR" ] && [ "$KILLED" = false ]; then
    if stop_by_cmd "$PROD_DIR/playbot.jar"; then
        echo "✅ Production bot stopped (matched $PROD_DIR/playbot.jar)"
        KILLED=true
    fi
fi

# Last-resort: kill any playbot JAR not running from this workspace path
if [ "$KILLED" = false ]; then
    WORKSPACE_DIR="$(pwd)"
    # Find candidate PIDs and commands, exclude this workspace path to protect dev
    MATCHES=$(ps ax -o pid= -o command= | grep -E "java .*playbot-.*\.jar" | grep -F -v "$WORKSPACE_DIR" || true)
    if [ -n "$MATCHES" ]; then
        echo "$MATCHES" | awk '{print $1}' | xargs -r kill || true
        sleep 2
        # Force kill remaining
        REMAIN=$(ps ax -o pid= -o command= | grep -E "java .*playbot-.*\.jar" | grep -F -v "$WORKSPACE_DIR" | awk '{print $1}' || true)
        if [ -n "$REMAIN" ]; then
            echo "$REMAIN" | xargs -r kill -9 || true
        fi
        echo "✅ Production bot stopped (generic pattern)"
        KILLED=true
    fi
fi

if [ "$KILLED" = false ]; then
    echo "ℹ️  No production bot instance running"
fi
