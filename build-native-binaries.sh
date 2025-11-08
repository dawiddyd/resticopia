#!/bin/bash
# build-native-binaries.sh - Integrated PRoot build using build-proot-android approach

set -euo pipefail

# Colors (matching your existing style)
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration - align with your existing build-go-binaries.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/native-build"
SOURCE_DIR="$BUILD_DIR/sources"
PROOT_BUILD_DIR="$BUILD_DIR/proot-build"
OUTPUT_DIR="$SCRIPT_DIR/app/src/main/jniLibs"
MIN_API_LEVEL=24

# PRoot versions (updated to match build-go-binaries.sh for compatibility)
PROOT_V='0.15_release'
TALLOC_V='2.4.2'

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Building PRoot using build-proot-android${NC}"
echo -e "${BLUE}================================${NC}"

# Use same NDK detection as build-go-binaries.sh
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}"
ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-}"

if [ -z "$ANDROID_NDK_HOME" ] && [ -z "$ANDROID_NDK_ROOT" ]; then
  if [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
    ANDROID_NDK_HOME="$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
  elif [ -d "$HOME/Android/Sdk/ndk" ]; then
    ANDROID_NDK_HOME="$(find "$HOME/Android/Sdk/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
  else
    echo -e "${RED}ERROR: Android NDK not found${NC}"
    exit 1
  fi
fi

NDK="${ANDROID_NDK_HOME:-$ANDROID_NDK_ROOT}"
echo -e "${GREEN}Using NDK: $NDK${NC}"

# Clone build-proot-android if not present
PROOT_BUILD_REPO="$SOURCE_DIR/build-proot-android"
if [ ! -d "$PROOT_BUILD_REPO" ]; then
  echo -e "${BLUE}📦 Cloning build-proot-android repository...${NC}"
  git clone --depth 1 https://github.com/green-green-avk/build-proot-android.git "$PROOT_BUILD_REPO"
fi

# Create build-proot-android config (adapted for restic-android)
cat > "$PROOT_BUILD_REPO/config" << EOF
PROOT_V='$PROOT_V'
TALLOC_V='$TALLOC_V'

# Only build arm64-v8a for restic-android
ARCHS='aarch64'

BASE_DIR="$PROOT_BUILD_DIR"

BUILD_DIR="\$BASE_DIR/build"
mkdir -p "\$BUILD_DIR"

PKG_DIR="\$BASE_DIR/packages"
mkdir -p "\$PKG_DIR"

NDK="$NDK"
TOOLCHAIN="\$NDK/toolchains/llvm/prebuilt/linux-\$(uname -m)"

set-arch() {
	MARCH="\${1%%-*}"
	if [ "\$MARCH" != "\$1" ]
	then SUBARCH="\${1#*-}"
	else SUBARCH=''
	fi

	if [ "\$SUBARCH" == 'pre5' ]
	then API=16
	else API=$MIN_API_LEVEL
	fi

	INSTALL_ROOT="\$BUILD_DIR/root-\$ARCH/root"
	STATIC_ROOT="\$BUILD_DIR/static-\$ARCH/root"

	case "\$MARCH" in
		arm*) MARCH_T='arm' ;;
		*) MARCH_T="\$MARCH" ;;
	esac

	export AR="\$(echo \$TOOLCHAIN/bin/llvm-ar)"
	export AS="\$(echo \$TOOLCHAIN/bin/\$MARCH_T-linux-android*\$API-clang)"
	export CC="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang)"
	export CXX="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang++)"
	export LD="\$(echo \$TOOLCHAIN/bin/llvm-ld)"
	export RANLIB="\$(echo \$TOOLCHAIN/bin/llvm-ranlib)"
	export STRIP="\$(echo \$TOOLCHAIN/bin/llvm-strip)"
	export OBJCOPY="\$(echo \$TOOLCHAIN/bin/llvm-objcopy)"
	export OBJDUMP="\$(echo \$TOOLCHAIN/bin/llvm-objdump)"
}
EOF

# Copy the build scripts
mkdir -p "$PROOT_BUILD_DIR"

# Run the build from the build-proot-android directory
cd "$PROOT_BUILD_REPO"

# Create our custom config for restic-android
cat > "config" << EOF
PROOT_V='$PROOT_V'
TALLOC_V='$TALLOC_V'

# Only build arm64-v8a for restic-android
ARCHS='aarch64'

BASE_DIR='$PROOT_BUILD_DIR'

BUILD_DIR="\$BASE_DIR/build"
mkdir -p "\$BUILD_DIR"

PKG_DIR="\$BASE_DIR/packages"
mkdir -p "\$PKG_DIR"

NDK='$NDK'
TOOLCHAIN="\$NDK/toolchains/llvm/prebuilt/linux-x86_64"

set-arch() {
	MARCH="\${1%%-*}"
	if [ "\$MARCH" != "\$1" ]
	then SUBARCH="\${1#*-}"
	else SUBARCH=''
	fi

	if [ "\$SUBARCH" == 'pre5' ]
	then API=16
	else API=$MIN_API_LEVEL
	fi

	INSTALL_ROOT="\$BUILD_DIR/root-\$ARCH/root"
	STATIC_ROOT="\$BUILD_DIR/static-\$ARCH/root"

	case "\$MARCH" in
		arm*) MARCH_T='arm' ;;
		*) MARCH_T="\$MARCH" ;;
	esac

	export AR="\$(echo \$TOOLCHAIN/bin/llvm-ar)"
	export AS="\$(echo \$TOOLCHAIN/bin/\$MARCH_T-linux-android*\$API-clang)"
	export CC="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang)"
	export CXX="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang++)"
	export LD="\$(echo \$TOOLCHAIN/bin/llvm-ld)"
	export RANLIB="\$(echo \$TOOLCHAIN/bin/llvm-ranlib)"
	export STRIP="\$(echo \$TOOLCHAIN/bin/llvm-strip)"
	export OBJCOPY="\$(echo \$TOOLCHAIN/bin/llvm-objcopy)"
	export OBJDUMP="\$(echo \$TOOLCHAIN/bin/llvm-objdump)"
}
EOF

# Run the build
echo -e "${BLUE}🏗️  Building PRoot...${NC}"
./get-talloc.sh
./get-proot.sh
./make-talloc-static.sh
./make-proot-for-apk.sh

# Copy results to Android JNI libs
echo -e "${BLUE}📦 Copying PRoot binaries to Android JNI libs...${NC}"

# Find the built APK root directory
APK_ROOT_DIR=$(find "$PROOT_BUILD_DIR/build" -name "root-apk" -type d | head -1)

if [ -z "$APK_ROOT_DIR" ]; then
  echo -e "${RED}ERROR: APK root directory not found${NC}"
  echo "Build directory contents:"
  find "$PROOT_BUILD_DIR/build" -type d -name "*apk*" || true
  exit 1
fi

# Create arm64-v8a output directory
ARM64_DIR="$OUTPUT_DIR/arm64-v8a"
mkdir -p "$ARM64_DIR"

# Copy the shared libraries (these will be named lib*.so)
echo "Copying from: $APK_ROOT_DIR/bin/"
ls -la "$APK_ROOT_DIR/bin/" || true

cp "$APK_ROOT_DIR/bin/libproot.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot.so not found"
cp "$APK_ROOT_DIR/bin/libproot-userland.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot-userland.so not found"

# Also copy any loader libraries if they exist
cp "$APK_ROOT_DIR/bin/libproot-loader.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot-loader.so not found"
cp "$APK_ROOT_DIR/bin/libproot-loader32.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot-loader32.so not found"

# Rename files to match app expectations
echo "Renaming files for app compatibility..."
cd "$ARM64_DIR"
mv libproot.so libdata_proot.so 2>/dev/null || echo "libproot.so not renamed"
mv libproot-loader.so libdata_loader.so 2>/dev/null || echo "libproot-loader.so not renamed"  
mv libproot-loader32.so libdata_loader32.so 2>/dev/null || echo "libproot-loader32.so not renamed"

echo -e "${GREEN}✅ PRoot build completed successfully${NC}"
echo "Output files in $ARM64_DIR:"
ls -la "$ARM64_DIR/"

