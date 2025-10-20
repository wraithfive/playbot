#!/bin/bash

# Playbot Startup Script
# Starts both backend and frontend services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔═══════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Playbot Startup Script        ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════╝${NC}"
echo ""

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${RED}Error: .env file not found!${NC}"
    echo -e "${YELLOW}Please create a .env file with your Discord credentials.${NC}"
    echo -e "${YELLOW}See .env.example for the required format.${NC}"
    exit 1
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
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: Node.js is not installed!${NC}"
    echo -e "${YELLOW}Please install Node.js 18 or higher.${NC}"
    exit 1
fi

# Set Java 21 home (macOS)
if [ "$(uname)" == "Darwin" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)
fi

echo -e "${GREEN}✓ Environment checks passed${NC}"
echo ""

# Build backend if JAR doesn't exist
if [ ! -f "target/playbot-1.0.0.jar" ]; then
    echo -e "${YELLOW}Building backend...${NC}"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}Backend build failed!${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Backend built successfully${NC}"
    echo ""
fi

# Install frontend dependencies if node_modules doesn't exist
if [ ! -d "frontend/node_modules" ]; then
    echo -e "${YELLOW}Installing frontend dependencies...${NC}"
    cd frontend
    npm install
    if [ $? -ne 0 ]; then
        echo -e "${RED}Frontend dependency installation failed!${NC}"
        exit 1
    fi
    cd ..
    echo -e "${GREEN}✓ Frontend dependencies installed${NC}"
    echo ""
fi

# Create log directory
mkdir -p logs

# Cleanup function to kill both processes on exit
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down services...${NC}"
    if [ ! -z "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null
        echo -e "${GREEN}✓ Backend stopped${NC}"
    fi
    if [ ! -z "$FRONTEND_PID" ]; then
        kill $FRONTEND_PID 2>/dev/null
        echo -e "${GREEN}✓ Frontend stopped${NC}"
    fi
    exit 0
}

# Set trap to catch Ctrl+C
trap cleanup SIGINT SIGTERM

# Start backend
echo -e "${BLUE}Starting backend on port 8080...${NC}"
java -jar target/playbot-1.0.0.jar > logs/backend.log 2>&1 &
BACKEND_PID=$!

# Wait a bit for backend to start
sleep 3

# Check if backend is still running
if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo -e "${RED}Backend failed to start!${NC}"
    echo -e "${YELLOW}Check logs/backend.log for details${NC}"
    tail -20 logs/backend.log
    exit 1
fi

echo -e "${GREEN}✓ Backend started (PID: $BACKEND_PID)${NC}"
echo ""

# Start frontend
echo -e "${BLUE}Starting frontend on port 3000...${NC}"
cd frontend
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

# Wait a bit for frontend to start
sleep 2

# Check if frontend is still running
if ! kill -0 $FRONTEND_PID 2>/dev/null; then
    echo -e "${RED}Frontend failed to start!${NC}"
    echo -e "${YELLOW}Check logs/frontend.log for details${NC}"
    tail -20 logs/frontend.log
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

echo -e "${GREEN}✓ Frontend started (PID: $FRONTEND_PID)${NC}"
echo ""

echo -e "${BLUE}╔═══════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Playbot is now running!          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Backend:${NC}  http://localhost:8080"
echo -e "${GREEN}Frontend:${NC} http://localhost:3000"
echo -e "${GREEN}API:${NC}      http://localhost:8080/api/health"
echo ""
echo -e "${YELLOW}Logs:${NC}"
echo -e "  Backend:  logs/backend.log"
echo -e "  Frontend: logs/frontend.log"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop both services${NC}"
echo ""

# Tail logs from both services
tail -f logs/backend.log logs/frontend.log 2>/dev/null &
TAIL_PID=$!

# Wait for user interrupt
wait $BACKEND_PID $FRONTEND_PID
