#!/bin/bash

echo "🛑 Stopping Playbot development environment..."

# Get version from pom.xml
VERSION=$(grep -A 1 '<artifactId>playbot</artifactId>' pom.xml | grep '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
if [ -z "$VERSION" ]; then
    echo "  ⚠️  Could not determine version, using generic pattern..."
    JAR_PATTERN="spring.profiles.active=dev.*playbot-.*\.jar"
else
    JAR_PATTERN="spring.profiles.active=dev.*playbot-${VERSION}.jar"
fi

# Kill the dev backend process (matches -Dspring.profiles.active=dev)
if pgrep -f "$JAR_PATTERN" > /dev/null; then
    echo "  Stopping dev backend..."
    pkill -f "$JAR_PATTERN"
    sleep 2

    # Force kill if still running
    if pgrep -f "$JAR_PATTERN" > /dev/null; then
        echo "  ⚠️  Process still running, force killing..."
        pkill -9 -f "$JAR_PATTERN"
        sleep 1
    fi
    echo "  ✅ Backend stopped"
else
    echo "  ℹ️  No dev backend running"
fi

# Restore original .env if backup exists
if [ -f .env.production.backup ]; then
    echo "  Restoring production .env..."
    mv .env.production.backup .env
    echo "  ✅ Production .env restored"
fi

echo ""
echo "✅ Development environment stopped"
