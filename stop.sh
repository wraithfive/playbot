#!/bin/bash

echo "🛑 Stopping Playbot production instance..."

# Get version from pom.xml
VERSION=$(grep -A 1 '<artifactId>playbot</artifactId>' pom.xml | grep '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
if [ -z "$VERSION" ]; then
    echo "⚠️  Could not determine version from pom.xml, trying generic pattern..."
    # Fallback to any playbot JAR
    if pgrep -f "playbot-.*\.jar" | grep -v "dev" > /dev/null; then
        pkill -f "playbot-.*\.jar"
        sleep 2
        if pgrep -f "playbot-.*\.jar" | grep -v "dev" > /dev/null; then
            pkill -9 -f "playbot-.*\.jar"
        fi
        echo "✅ Production bot stopped"
    else
        echo "ℹ️  No production bot instance running"
    fi
    exit 0
fi

JAR_PATTERN="playbot-${VERSION}.jar"

# Kill the production bot process
if pgrep -f "$JAR_PATTERN" | grep -v "dev" > /dev/null; then
    pkill -f "$JAR_PATTERN"
    sleep 2

    # Force kill if still running
    if pgrep -f "$JAR_PATTERN" | grep -v "dev" > /dev/null; then
        echo "⚠️  Process still running, force killing..."
        pkill -9 -f "$JAR_PATTERN"
        sleep 1
    fi

    echo "✅ Production bot stopped"
else
    echo "ℹ️  No production bot instance running"
fi
