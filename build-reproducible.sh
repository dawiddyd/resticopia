#!/bin/bash

set -e

# Build reproducible APKs for F-Droid verification (builds from source)
# Usage: ./build-reproducible.sh <version-tag>
# Example: ./build-reproducible.sh 0.7.5

if [ -z "$1" ]; then
    echo "Error: Version tag required"
    echo "Usage: ./build-reproducible.sh <version-tag>"
    echo "Example: ./build-reproducible.sh 0.7.5"
    exit 1
fi

VERSION_TAG="$1"

echo "=========================================="
echo "Building reproducible APK for version $VERSION_TAG"
echo "=========================================="

# Verify we're on the correct tag
CURRENT_TAG=$(git describe --tags --exact-match 2>/dev/null || echo "")
if [ "$CURRENT_TAG" != "$VERSION_TAG" ]; then
    echo "Warning: Current checkout is not at tag $VERSION_TAG"
    echo "Current: $CURRENT_TAG"
    read -p "Do you want to checkout tag $VERSION_TAG? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git checkout "$VERSION_TAG"
    else
        echo "Aborting. Please checkout the correct tag first."
        exit 1
    fi
fi

# Set SOURCE_DATE_EPOCH to match the commit timestamp
# This is what F-Droid does for reproducible builds
export SOURCE_DATE_EPOCH=$(git log -1 --format=%ct HEAD)
echo "Using SOURCE_DATE_EPOCH: $SOURCE_DATE_EPOCH ($(date -r $SOURCE_DATE_EPOCH 2>/dev/null || date -d @$SOURCE_DATE_EPOCH))"

# Ensure consistent timezone for reproducible builds
export TZ=UTC

# Set JAVA_HOME to JDK 21
if [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
elif [ -d "/usr/local/opt/openjdk@21" ]; then
    export JAVA_HOME="/usr/local/opt/openjdk@21"
elif [ -d "$HOME/.sdkman/candidates/java/21.0.6-tem" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.6-tem"
else
    echo "Error: JDK 21 not found!"
    echo "Please install it:"
    echo "  brew install openjdk@21"
    echo ""
    echo "Your current Java version:"
    java -version
    exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
echo "Using Java: $(java -version 2>&1 | head -n 1)"

# Verify we're using JDK 21
JAVA_VERSION=$(java -version 2>&1 | grep version | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" != "21" ]; then
    echo "Error: Java version $JAVA_VERSION detected, but JDK 21 is required"
    echo "Please install JDK 21: brew install openjdk@21"
    exit 1
fi

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed or not in PATH"
    echo "Please install Docker Desktop from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Build native binaries from source using Docker
echo ""
echo "Building native binaries from source using Docker..."

# Remove old binaries if they exist
rm -rf app/src/main/jniLibs

# Build native binaries using our docker-compose setup
echo "Starting native binary build..."
SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH docker compose -f docker-compose-build.yml up --build --abort-on-container-exit

# The binaries should now be in app/src/main/jniLibs/
if [ ! -d "app/src/main/jniLibs" ]; then
    echo "Error: Native binaries were not built successfully"
    echo "Expected directory: app/src/main/jniLibs/"
    exit 1
fi

echo "Native binaries built from source successfully!"

# Modify build.gradle to match F-Droid build configuration
echo ""
echo "Modifying build.gradle to match F-Droid configuration..."
cd app
cp build.gradle build.gradle.orig
# Only include arm64-v8a architecture
sed -i.bak "s/include 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'/include 'arm64-v8a'/" build.gradle
cd ..

# Set build-tools version to match AGP 9.2.x defaults (37.0.0)
echo ""
echo "Ensuring Android SDK build-tools 37.0.0 is installed..."
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"

if [ -d "$ANDROID_SDK_ROOT/build-tools/37.0.0" ]; then
    echo "✓ build-tools 37.0.0 already installed"
else
    echo "Warning: build-tools 37.0.0 not found"
    echo "Gradle will auto-download it, but it's better to install manually:"
    echo "Or via Android Studio: Tools -> SDK Manager -> SDK Tools -> Show Package Details -> Android SDK Build-Tools 37.0.0"
fi

# Clean previous builds
echo ""
echo "Cleaning previous builds..."
./gradlew clean

# Build release APKs
echo ""
echo "Building release APKs..."
./gradlew assembleRelease

# Restore the original build.gradle
echo ""
echo "Restoring original build.gradle..."
mv app/build.gradle.orig app/build.gradle
rm -f app/build.gradle.bak

# Show output location
echo ""
echo "=========================================="
echo "Build completed successfully!"
echo "=========================================="
echo ""
echo "APK files are located in:"
echo "  app/build/outputs/apk/release/"
echo ""
ls -lh app/build/outputs/apk/release/*.apk
echo ""
echo "To sign the arm64-v8a APK, use:"
echo "  apksigner sign --ks /Users/d.dydlinski/GIT/key.jks \\"
echo "    --ks-key-alias key \\"
echo "    --out resticopia-${VERSION_TAG}-arm64-v8a-release.apk \\"
echo "    app/build/outputs/apk/release/resticopia-${VERSION_TAG}-arm64-v8a-release-unsigned.apk \\"
echo ""
echo "Then upload the signed APK to Codeberg releases."
