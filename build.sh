#!/bin/bash

# Playbot Build Script
# Builds both backend and frontend for production

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔═══════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         Playbot Build Script         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════╝${NC}"
echo ""

# Parse command line arguments
SKIP_TESTS=false
CLEAN=false
PRODUCTION=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --production)
            PRODUCTION=true
            shift
            ;;
        --help)
            echo "Usage: ./build.sh [options]"
            echo ""
            echo "Options:"
            echo "  --skip-tests     Skip running tests"
            echo "  --clean          Clean build artifacts before building"
            echo "  --production     Build for production (includes optimization)"
            echo "  --help           Show this help message"
            echo ""
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to print section headers
print_section() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

# Set Java 21 home FIRST (before checking versions)
if [ "$(uname)" == "Darwin" ]; then
    # macOS
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)
elif [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    # Linux (common path)
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
elif [ -d "/usr/lib/jvm/java-21" ]; then
    # Linux (alternate path)
    export JAVA_HOME=/usr/lib/jvm/java-21
fi

# Add Java to PATH if JAVA_HOME is set
if [ ! -z "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed!${NC}"
    echo -e "${YELLOW}Please install Java 21 or higher.${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}Error: Java 21 or higher is required!${NC}"
    echo -e "${YELLOW}Current version: Java $JAVA_VERSION${NC}"
    echo -e "${YELLOW}JAVA_HOME: ${JAVA_HOME:-not set}${NC}"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed!${NC}"
    echo -e "${YELLOW}Please install Maven 3.6 or higher.${NC}"
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: Node.js is not installed!${NC}"
    echo -e "${YELLOW}Please install Node.js 18 or higher.${NC}"
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo -e "${RED}Error: npm is not installed!${NC}"
    echo -e "${YELLOW}Please install npm.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Environment checks passed${NC}"
echo -e "${YELLOW}Java version:${NC} $(java -version 2>&1 | head -n 1)"
echo -e "${YELLOW}Maven version:${NC} $(mvn -version | head -n 1)"
echo -e "${YELLOW}Node version:${NC} $(node -v)"
echo -e "${YELLOW}npm version:${NC} $(npm -v)"
echo ""

if [ "$CLEAN" = true ]; then
    print_section "🧹 Cleaning build artifacts"

    echo -e "${YELLOW}Cleaning backend...${NC}"
    mvn clean
    echo -e "${GREEN}✓ Backend cleaned${NC}"

    echo -e "${YELLOW}Cleaning frontend...${NC}"
    if [ -d "frontend/node_modules" ]; then
        rm -rf frontend/node_modules
        echo -e "${GREEN}✓ Removed node_modules${NC}"
    fi
    if [ -d "frontend/dist" ]; then
        rm -rf frontend/dist
        echo -e "${GREEN}✓ Removed dist directory${NC}"
    fi
    echo -e "${GREEN}✓ Frontend cleaned${NC}"
fi

# Build Backend
print_section "🔨 Building Backend"

echo -e "${YELLOW}Building Java application...${NC}"

if [ "$SKIP_TESTS" = true ]; then
    echo -e "${BLUE}Skipping tests...${NC}"
    mvn package -DskipTests
else
    echo -e "${BLUE}Running tests...${NC}"
    mvn package
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Backend built successfully${NC}"

    # Show JAR info
    JAR_FILE="target/playbot-1.0.0.jar"
    if [ -f "$JAR_FILE" ]; then
        JAR_SIZE=$(ls -lh "$JAR_FILE" | awk '{print $5}')
        echo -e "${YELLOW}JAR file:${NC} $JAR_FILE"
        echo -e "${YELLOW}Size:${NC} $JAR_SIZE"
    fi
else
    echo -e "${RED}✗ Backend build failed${NC}"
    exit 1
fi

# Build Frontend
print_section "🎨 Building Frontend"

cd frontend

echo -e "${YELLOW}Installing dependencies...${NC}"
npm install

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Frontend dependency installation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Dependencies installed${NC}"

echo -e "${YELLOW}Building React application...${NC}"

if [ "$PRODUCTION" = true ]; then
    echo -e "${BLUE}Building for production...${NC}"
    npm run build
else
    npm run build
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Frontend built successfully${NC}"

    # Show build info
    if [ -d "dist" ]; then
        DIST_SIZE=$(du -sh dist | awk '{print $1}')
        FILE_COUNT=$(find dist -type f | wc -l | tr -d ' ')
        echo -e "${YELLOW}Build directory:${NC} frontend/dist"
        echo -e "${YELLOW}Size:${NC} $DIST_SIZE"
        echo -e "${YELLOW}Files:${NC} $FILE_COUNT"
    fi
else
    echo -e "${RED}✗ Frontend build failed${NC}"
    cd ..
    exit 1
fi

cd ..

# Summary
print_section "✅ Build Complete"

echo -e "${GREEN}Both backend and frontend built successfully!${NC}"
echo ""
echo -e "${YELLOW}Backend:${NC}"
echo -e "  JAR: target/playbot-1.0.0.jar"
echo -e "  Run: java -jar target/playbot-1.0.0.jar"
echo ""
echo -e "${YELLOW}Frontend:${NC}"
echo -e "  Build: frontend/dist/"
echo -e "  Dev: npm run dev (in frontend directory)"
echo ""

if [ "$PRODUCTION" = true ]; then
    echo -e "${CYAN}Production build completed!${NC}"
    echo -e "${YELLOW}Deployment checklist:${NC}"
    echo -e "  ☐ Set production environment variables"
    echo -e "  ☐ Configure production database (if applicable)"
    echo -e "  ☐ Update OAuth2 redirect URIs in Discord Developer Portal"
    echo -e "  ☐ Configure reverse proxy (nginx/Apache)"
    echo -e "  ☐ Set up SSL certificates"
    echo ""
fi

echo -e "${BLUE}╔═══════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Build process complete!        ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════╝${NC}"
