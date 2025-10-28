#!/bin/bash
#
# Script to download native binaries using Docker
# This ensures consistent environment and all required tools are available
#

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================="
echo "Downloading Native Binaries"
echo "=================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}Warning: Docker is not installed or not in PATH${NC}"
    echo "Please install Docker from https://www.docker.com/get-started"
    exit 1
fi

echo -e "${BLUE}Step 1: Building Docker image...${NC}"
docker build -f Dockerfile.download-binaries -t restic-android-downloader .

echo ""
echo -e "${BLUE}Step 2: Running download script in Docker container...${NC}"
# Create a temporary container to extract files
CONTAINER_ID=$(docker create restic-android-downloader)

echo ""
echo -e "${BLUE}Step 3: Copying downloaded binaries to local machine...${NC}"
# Copy the jniLibs directory from container to host
docker cp "$CONTAINER_ID:/build/app/src/main/jniLibs" "./app/src/main/"

echo ""
echo -e "${BLUE}Step 4: Cleaning up Docker container...${NC}"
docker rm "$CONTAINER_ID"

echo ""
echo -e "${GREEN}✓ Success! Native binaries downloaded to app/src/main/jniLibs/${NC}"
echo ""
echo "Downloaded architectures:"
for arch_dir in ./app/src/main/jniLibs/*/; do
    arch=$(basename "$arch_dir")
    echo -e "  ${GREEN}✓${NC} $arch"
    ls -lh "$arch_dir" | grep -v "^total" | awk '{printf "    - %-30s %6s\n", $9, $5}'
done

echo ""
echo -e "${GREEN}You can now build the APK with: ./gradlew assembleRelease${NC}"

