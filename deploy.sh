#!/bin/bash

##################################################
# Playbot Deployment Script
# Deploys to EC2 instance via SSH
##################################################

set -e  # Exit on any error

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Cleanup function
TARBALL=""
cleanup() {
    if [ -n "$TARBALL" ] && [ -f "$TARBALL" ]; then
        echo -e "${YELLOW}Cleaning up local deployment package...${NC}"
        rm -f "$TARBALL"
    fi
}

# Ensure cleanup on exit
trap cleanup EXIT

# Configuration
EC2_HOST="ec2-3-85-229-94.compute-1.amazonaws.com"
EC2_USER="ec2-user"
PEM_FILE="./playbot.pem"
REMOTE_DIR="/opt/playbot"
SERVICE_NAME="playbot.service"

# Validate PEM file exists
if [ ! -f "$PEM_FILE" ]; then
    echo -e "${RED}ERROR: PEM file not found: $PEM_FILE${NC}"
    exit 1
fi

# Ensure PEM file has correct permissions
chmod 600 "$PEM_FILE"

echo -e "${BLUE}╔═══════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Playbot Deployment Script              ║${NC}"
echo -e "${BLUE}╔═══════════════════════════════════════════╗${NC}"
echo ""

# Step 1: Build the application locally
echo -e "${YELLOW}[1/6] Building application locally...${NC}"
./build.sh --skip-tests
if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Build failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Build completed${NC}"
echo ""


# Step 2: Create deployment package
echo -e "${YELLOW}[2/6] Creating deployment package...${NC}"
DEPLOY_DIR="deploy-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DEPLOY_DIR/target"

# Grab version from pom.xml
JAR_VERSION=$(grep -m 1 '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
JAR_FILE="playbot-${JAR_VERSION}.jar"

# Copy backend JAR as playbot.jar for systemd service
cp "target/${JAR_FILE}" "$DEPLOY_DIR/target/playbot.jar" || {
    echo -e "${RED}ERROR: Failed to copy JAR file: target/${JAR_FILE}${NC}"
    exit 1
}

# Copy frontend dist
cp -r frontend/dist "$DEPLOY_DIR/" || {
    echo -e "${RED}ERROR: Failed to copy frontend dist${NC}"
    exit 1
}

# Copy necessary scripts
cp start.sh "$DEPLOY_DIR/"
chmod +x "$DEPLOY_DIR/start.sh"

# Create tarball
TARBALL="${DEPLOY_DIR}.tar.gz"
tar -czf "$TARBALL" -C "$DEPLOY_DIR" .
rm -rf "$DEPLOY_DIR"
echo -e "${GREEN}✓ Deployment package created: $TARBALL${NC}"
echo ""

# Step 3: Upload to EC2
echo -e "${YELLOW}[3/6] Uploading to EC2...${NC}"
scp -i "$PEM_FILE" "$TARBALL" "${EC2_USER}@${EC2_HOST}:/tmp/" || {
    echo -e "${RED}ERROR: Failed to upload deployment package${NC}"
    exit 1
}
echo -e "${GREEN}✓ Upload completed${NC}"
echo ""

# Step 4: Stop the service
echo -e "${YELLOW}[4/6] Stopping playbot service...${NC}"
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" "sudo systemctl stop $SERVICE_NAME" || {
    echo -e "${RED}ERROR: Failed to stop service${NC}"
    exit 1
}
echo -e "${GREEN}✓ Service stopped${NC}"
echo ""

# Step 5: Extract and deploy
echo -e "${YELLOW}[5/6] Deploying application...${NC}"
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" << 'ENDSSH'
set -e

TARBALL=$(ls -t /tmp/deploy-*.tar.gz | head -1)
REMOTE_DIR="/opt/playbot"
BACKUP_DIR="$REMOTE_DIR/backup-latest"

# Remove any old backup (keep only one for rollback)
echo "Removing old backup..."
rm -rf "$BACKUP_DIR" 2>/dev/null || true

# Create backup of current deployment
echo "Creating backup..."
mkdir -p "$BACKUP_DIR"
if [ -d "$REMOTE_DIR/target" ]; then
    cp -r "$REMOTE_DIR/target" "$BACKUP_DIR/"
fi
if [ -d "$REMOTE_DIR/dist" ]; then
    cp -r "$REMOTE_DIR/dist" "$BACKUP_DIR/"
fi

# Extract new version
echo "Extracting deployment package..."
tar -xzf "$TARBALL" -C "$REMOTE_DIR"

# Clean up
rm -f "$TARBALL"

# Remove any old dated backups if they exist
echo "Cleaning old dated backups..."
cd "$REMOTE_DIR"
ls -dt backup-* 2>/dev/null | grep -v "backup-latest" | xargs rm -rf 2>/dev/null || true

echo "Deployment extracted successfully"
ENDSSH

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Deployment failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Deployment completed${NC}"
echo ""

# Step 6: Start the service
echo -e "${YELLOW}[6/6] Starting playbot service...${NC}"
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" "sudo systemctl start $SERVICE_NAME" || {
    echo -e "${RED}ERROR: Failed to start service${NC}"
    echo -e "${YELLOW}You may need to manually check the service status${NC}"
    exit 1
}

# Wait a moment for service to start
sleep 3

# Check service status
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" "sudo systemctl is-active $SERVICE_NAME" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Service started successfully${NC}"
else
    echo -e "${RED}WARNING: Service may not have started correctly${NC}"
    echo -e "${YELLOW}Check status with: ssh -i $PEM_FILE ${EC2_USER}@${EC2_HOST} 'sudo systemctl status $SERVICE_NAME'${NC}"
fi
echo ""

echo -e "${GREEN}╔═══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║    Deployment Completed Successfully!    ║${NC}"
echo -e "${GREEN}╔═══════════════════════════════════════════╗${NC}"
echo ""
echo -e "${BLUE}Service Status:${NC}"
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" "sudo systemctl status $SERVICE_NAME --no-pager -l" | head -15
echo ""
echo -e "${BLUE}Recent Logs:${NC}"
ssh -i "$PEM_FILE" "${EC2_USER}@${EC2_HOST}" "tail -20 $REMOTE_DIR/logs/playbot.log"
echo ""
echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  View logs:    ${BLUE}ssh -i $PEM_FILE ${EC2_USER}@${EC2_HOST} 'tail -f $REMOTE_DIR/logs/playbot.log'${NC}"
echo -e "  Check status: ${BLUE}ssh -i $PEM_FILE ${EC2_USER}@${EC2_HOST} 'sudo systemctl status $SERVICE_NAME'${NC}"
echo -e "  Restart:      ${BLUE}ssh -i $PEM_FILE ${EC2_USER}@${EC2_HOST} 'sudo systemctl restart $SERVICE_NAME'${NC}"
echo ""
