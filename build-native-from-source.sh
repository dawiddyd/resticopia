#!/bin/bash
# Build native dependencies from source for F-Droid compliance
# This script compiles restic, rclone, proot

set -eo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}Building Native Binaries from Source${NC}"
echo -e "${BLUE}=================================${NC}"

# Version pinning
RESTIC_VERSION="0.18.1"
RCLONE_VERSION="1.68.2"
PROOT_VERSION="5.4.0"

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/native-build"
SOURCE_DIR="$BUILD_DIR/sources"
OUTPUT_DIR="$SCRIPT_DIR/app/src/main/jniLibs"

# Android NDK configuration
if [ -z "$ANDROID_NDK_HOME" ] && [ -z "$ANDROID_NDK_ROOT" ]; then
    if [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
        ANDROID_NDK_HOME="$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
    elif [ -d "$HOME/Android/Sdk/ndk" ]; then
        ANDROID_NDK_HOME="$(find "$HOME/Android/Sdk/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
    else
        echo -e "${RED}ERROR: Android NDK not found. Please set ANDROID_NDK_HOME.${NC}"
        exit 1
    fi
fi

NDK="${ANDROID_NDK_HOME:-$ANDROID_NDK_ROOT}"

# Detect prebuilt tag
PREBUILT_TAG="linux-x86_64"
if [[ "$OSTYPE" == "darwin"* ]]; then
  PREBUILT_TAG="darwin-x86_64"
fi

# Verify toolchain path
if [ ! -d "$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG" ]; then
  echo -e "${RED}NDK toolchain not found at $NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG${NC}"
  ls -R "$NDK" || true
  exit 1
fi

echo -e "${GREEN}Using NDK: $NDK${NC}"

# Check for required tools
command -v go >/dev/null 2>&1 || { echo -e "${RED}ERROR: Go is not installed${NC}"; exit 1; }
echo -e "${GREEN}Go version: $(go version)${NC}"

mkdir -p "$SOURCE_DIR" "$OUTPUT_DIR"

# Architecture mappings
declare -A GO_ARCHS=(
  ["arm64-v8a"]="arm64"
  ["armeabi-v7a"]="arm"
  ["x86_64"]="amd64"
  ["x86"]="386"
)
declare -A NDK_ARCH_ABI=(
  ["arm64-v8a"]="aarch64-linux-android"
  ["armeabi-v7a"]="armv7a-linux-androideabi"
  ["x86_64"]="x86_64-linux-android"
  ["x86"]="i686-linux-android"
)

MIN_API_LEVEL=24

download_source() {
  local name="$1" url="$2" target_dir="$3"
  echo -e "${BLUE}Downloading $name...${NC}"
  if [ -d "$target_dir" ]; then
    echo -e "${GREEN}Already exists: $target_dir${NC}"
    return
  fi
  local tmp="$(mktemp)"
  curl -sSfL "$url" -o "$tmp"
  mkdir -p "$target_dir"
  tar -xzf "$tmp" -C "$target_dir" --strip-components=1
  rm "$tmp"
}

build_go_binary() {
  local name="$1" src="$2" out="$3" arch="$4"
  local go_arch="${GO_ARCHS[$arch]}"
  local ndk_arch="${NDK_ARCH_ABI[$arch]}"
  local out_dir="$OUTPUT_DIR/$arch"
  mkdir -p "$out_dir"

  export GOOS=android
  export GOARCH="$go_arch"
  export CGO_ENABLED=1

  # ✅ Add talloc include and library paths for CGO
  export CGO_CFLAGS="-I/opt/talloc-arm64/include"
  export CGO_LDFLAGS="-L/opt/talloc-arm64/lib -ltalloc"

  export CC="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/${ndk_arch}${MIN_API_LEVEL}-clang"

  echo -e "${BLUE}Building $name for $arch...${NC}"
  pushd "$src" >/dev/null
  case "$name" in
    restic) go build -v -ldflags="-s -w" -o "$out_dir/$out" ./cmd/restic ;;
    rclone) go build -v -ldflags="-s -w" -o "$out_dir/$out" . ;;
  esac
  popd >/dev/null
  [ -f "$out_dir/$out" ] || { echo -e "${RED}Failed: $name ($arch)${NC}"; exit 1; }
  echo -e "${GREEN}✓ Built $name for $arch${NC}"
}

build_proot() {
  local out_dir="$OUTPUT_DIR/arm64-v8a"
  local src="$SOURCE_DIR/proot"
  mkdir -p "$out_dir"

  export CC=aarch64-linux-gnu-gcc
  export AR=aarch64-linux-gnu-ar
  export STRIP=aarch64-linux-gnu-strip
  export OBJCOPY=aarch64-linux-gnu-objcopy
  export CFLAGS="-O2 -static -I/opt/talloc-arm64/include"
  export LDFLAGS="-L/opt/talloc-arm64/lib -ltalloc -static"

  pushd "$src/src" >/dev/null
  make clean || true
  make CC="$CC" AR="$AR" STRIP="$STRIP" OBJCOPY="$OBJCOPY" CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS"
  cp proot "$out_dir/libdata_proot.so"
  [ -f "$out_dir/libdata_proot.so" ] || { echo -e "${RED}Failed to build proot (arm64-v8a)${NC}"; exit 1; }
  popd >/dev/null
  echo -e "${GREEN}✓ Built proot for arm64-v8a${NC}"
}

main() {
  echo -e "${BLUE}Step 1: Downloading sources${NC}"
  download_source "restic" "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" "$SOURCE_DIR/restic"
  download_source "rclone" "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" "$SOURCE_DIR/rclone"
  download_source "proot" "https://github.com/proot-me/proot/archive/refs/tags/v${PROOT_VERSION}.tar.gz" "$SOURCE_DIR/proot"

  echo -e "${BLUE}Step 2: Building architectures${NC}"
  for arch in arm64-v8a; do
    build_proot "$arch"
    build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
    build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
  done
  echo -e "${GREEN}All native libraries built successfully!${NC}"
}

main "$@"
