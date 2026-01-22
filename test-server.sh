#!/bin/bash

# Quick Test Server Setup Helper
# This script simplifies the process of setting up test channels and threads

set -e

GUILD_ID=$1
ACTION=${2:-"setup"}

if [ -z "$GUILD_ID" ]; then
  echo "Usage: ./test-server.sh <guild-id> [setup|cleanup]"
  echo ""
  echo "Examples:"
  echo "  ./test-server.sh 123456789              # Setup test channels"
  echo "  ./test-server.sh 123456789 setup        # Setup test channels"
  echo "  ./test-server.sh 123456789 cleanup      # Remove test channels"
  echo ""
  echo "Get your guild ID by right-clicking your Discord server → Copy Server ID"
  exit 1
fi

# Check for .env file
if [ ! -f .env ]; then
  echo "Error: .env file not found!"
  echo "Please create .env with your DISCORD_TOKEN"
  exit 1
fi

# Check for Node.js
if ! command -v node &> /dev/null; then
  echo "Error: Node.js not found. Please install Node.js 18+"
  exit 1
fi

echo "================================================"
echo "Discord Test Server Setup"
echo "================================================"
echo ""
echo "Guild ID: $GUILD_ID"
echo "Action: $ACTION"
echo ""

if [ "$ACTION" = "cleanup" ]; then
  echo "⚠️  This will DELETE test channels from your Discord server."
  echo "Press Ctrl+C to cancel, or press Enter to continue..."
  read -r
  
  node scripts/setup-test-server.js "$GUILD_ID" --cleanup
else
  node scripts/setup-test-server.js "$GUILD_ID"
fi

echo ""
echo "✓ Test server setup complete!"
echo ""
if [ "$ACTION" = "setup" ]; then
  echo "Next steps:"
  echo "  1. Start the app: ./start.sh"
  echo "  2. Navigate to: http://localhost:8080"
  echo "  3. Login with Discord and select your test server"
  echo "  4. Go to QOTD Config to see the channel tree"
  echo ""
  echo "To run E2E tests:"
  echo "  cd frontend && npm run test:e2e -- qotd-channel-tree.spec.ts"
fi
