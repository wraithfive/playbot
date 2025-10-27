#!/bin/bash
set -e

echo "🧪 Starting Playbot in DEVELOPMENT mode..."
echo ""

# Check if .env.dev exists
if [ ! -f .env.dev ]; then
    echo "❌ Error: .env.dev file not found!"
    echo "Please create .env.dev with your development bot credentials."
    echo "See DEV_SETUP.md for instructions."
    exit 1
fi

# Get version from pom.xml
VERSION=$(grep -A 1 '<artifactId>playbot</artifactId>' pom.xml | grep '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
if [ -z "$VERSION" ]; then
    echo "❌ Error: Could not determine version from pom.xml"
    exit 1
fi
JAR_FILE="target/playbot-${VERSION}.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: JAR file not found: $JAR_FILE"
    echo "Run ./build.sh first to build the application."
    exit 1
fi

# Create logs directory if it doesn't exist
mkdir -p logs

# Temporarily copy .env.dev to .env (the app will load it)
# Save original .env if it exists
if [ -f .env ]; then
    cp .env .env.production.backup
fi
cp .env.dev .env

echo "✓ Using .env.dev credentials"
echo ""

# Kill any existing dev instance (backend)
JAR_PATTERN="spring.profiles.active=dev.*playbot-${VERSION}.jar"
if pgrep -f "$JAR_PATTERN" > /dev/null; then
    echo "🛑 Stopping existing dev backend..."
    pkill -f "$JAR_PATTERN" || true
    sleep 2
fi

# Start the backend in background with dev profile
# The app will load .env and serve the embedded frontend on port 8080
echo "🚀 Starting development backend (serves frontend on port 8080)..."
nohup java -Dspring.profiles.active=dev -jar "$JAR_FILE" > logs/playbot-dev.log 2>&1 &
BACKEND_PID=$!

# Wait for backend to start
sleep 4

# Check if backend is running
if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo "❌ Backend failed to start!"
    echo "Check logs/playbot-dev.log for details"
    tail -20 logs/playbot-dev.log
    # Restore original .env
    if [ -f .env.production.backup ]; then
        mv .env.production.backup .env
    fi
    exit 1
fi

echo ""
echo "✅ Development environment started!"
echo ""
echo "📍 App URL: http://localhost:8080 (backend + frontend)"
echo ""
echo "📋 Logs:"
echo "   tail -f logs/playbot-dev.log"
echo ""
echo "🛑 Stop: ./stop-dev.sh"
echo ""
echo "Recent backend log output:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
tail -15 logs/playbot-dev.log
