#!/bin/bash
# Minimal native build script for Android (restic, rclone, talloc)
# F-Droid compliant build — no prebuilt binaries
set -eo pipefail

# -------------------------------
#  Colors & banners
# -------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}Building Native Binaries from Source${NC}"
echo -e "${BLUE}=================================${NC}"

# -------------------------------
#  Version pins (override via env)
# -------------------------------
RESTIC_VERSION="${RESTIC_VERSION:-0.18.1}"
RCLONE_VERSION="${RCLONE_VERSION:-1.68.2}"
TALLOC_VERSION="${TALLOC_VERSION:-2.4.2}"
MIN_API_LEVEL=24

# -------------------------------
#  Directories
# -------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/native-build"
SOURCE_DIR="$BUILD_DIR/sources"
OUTPUT_DIR="$SCRIPT_DIR/app/src/main/jniLibs"

mkdir -p "$BUILD_DIR" "$SOURCE_DIR" "$OUTPUT_DIR"

# -------------------------------
#  Go setup
# -------------------------------
export PATH=$PATH:/usr/local/go/bin

# -------------------------------
#  Android NDK setup
# -------------------------------
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
PREBUILT_TAG="linux-x86_64"
[[ "$OSTYPE" == "darwin"* ]] && PREBUILT_TAG="darwin-x86_64"

echo -e "${GREEN}Using NDK: $NDK${NC}"

# -------------------------------
#  Architecture mappings
# -------------------------------
GO_ARCHS_arm64_v8a="arm64"
GO_ARCHS_armeabi_v7a="arm"
GO_ARCHS_x86_64="amd64"
GO_ARCHS_x86="386"

NDK_ARCH_ABI_arm64_v8a="aarch64-linux-android"
NDK_ARCH_ABI_armeabi_v7a="armv7a-linux-androideabi"
NDK_ARCH_ABI_x86_64="x86_64-linux-android"
NDK_ARCH_ABI_x86="i686-linux-android"

# -------------------------------
#  Utility: download sources
# -------------------------------
download_source() {
  local name="$1" url="$2" target_dir="$3"
  echo -e "${BLUE}📦 Downloading $name...${NC}"
  if [ -d "$target_dir" ]; then
    echo -e "${GREEN}Already present: $target_dir${NC}"
    return
  fi
  local tmp="$(mktemp)"
  curl -sSfL "$url" -o "$tmp"
  mkdir -p "$target_dir"
  tar -xzf "$tmp" -C "$target_dir" --strip-components=1
  rm "$tmp"
}

# -------------------------------
#  Utility: clone git repository
# -------------------------------
clone_repo() {
  local name="$1" url="$2" target_dir="$3"
  echo -e "${BLUE}📦 Cloning $name repository...${NC}"
  if [ -d "$target_dir" ]; then
    echo -e "${GREEN}Already present: $target_dir${NC}"
    return
  fi
  git clone --depth 1 "$url" "$target_dir"
}

# -------------------------------
#  Build Go-based tools
# -------------------------------
build_go_binary() {
  local name="$1" src="$2" out="$3" arch="$4"
  local arch_var="${arch//-/_}"
  local go_arch_var="GO_ARCHS_${arch_var}"
  local go_arch=$(eval echo "\$$go_arch_var")
  local ndk_arch_var="NDK_ARCH_ABI_${arch_var}"
  local ndk_arch=$(eval echo "\$$ndk_arch_var")
  local out_dir="$OUTPUT_DIR/$arch"
  mkdir -p "$out_dir"

  export GOOS=android
  export GOARCH="$go_arch"
  export CGO_ENABLED=1
  export CC="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/${ndk_arch}${MIN_API_LEVEL}-clang"

  echo -e "${BLUE}🏗️  Building $name for $arch...${NC}"
  pushd "$src" >/dev/null
  case "$name" in
    restic) go build -v -ldflags="-s -w" -o "$out_dir/$out" ./cmd/restic ;;
    rclone) go build -v -ldflags="-s -w" -o "$out_dir/$out" . ;;
  esac
  popd >/dev/null
  [ -f "$out_dir/$out" ] || { echo -e "${RED}✗ Failed to build $name ($arch)${NC}"; exit 1; }
  echo -e "${GREEN}✅ Built $name → $out_dir/$out${NC}"
}

# -------------------------------
#  Build talloc from Samba sources (static)
# -------------------------------
build_talloc() {
  local arch="$1"
  local arch_var="${arch//-/_}"
  local ndk_arch_var="NDK_ARCH_ABI_${arch_var}"
  local ndk_arch=$(eval echo "\$$ndk_arch_var")
  local samba_src="$SOURCE_DIR/samba"
  local install_dir="$BUILD_DIR/install/$arch"

  mkdir -p "$install_dir/lib" "$install_dir/include"

  # Create a build directory to avoid modifying source
  local build_dir="$BUILD_DIR/talloc-$arch"
  mkdir -p "$build_dir"
  pushd "$build_dir" >/dev/null

  echo -e "${BLUE}🏗️  Building talloc (static) for $arch from Samba sources...${NC}"

  # Copy talloc source files from Samba
  echo -e "${BLUE}📋 Copying talloc source files...${NC}"
  cp "$samba_src/lib/talloc/talloc.c" .
  cp "$samba_src/lib/talloc/talloc.h" .

  # Create minimal replace.h for standalone talloc build
  cat > replace.h << 'EOF'
#ifndef _REPLACE_H_
#define _REPLACE_H_

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdarg.h>
#include <unistd.h>
#include <sys/types.h>
#include <errno.h>

#define HAVE_LIBREPLACE 1
#define LINUX 1
#define BOOL_DEFINED 1
#define LIBREPLACE_NETWORK_CHECKS 1

/* Avoid typedef conflicts - useconds_t is defined by system headers */

/* Minimal definitions needed for talloc */
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 4

/* Missing macros and functions */
#ifndef MIN
#define MIN(a,b) ((a)<(b)?(a):(b))
#endif

#ifndef memset_s
#define memset_s(dest, destsz, ch, count) memset(dest, ch, count)
#endif

#endif /* _REPLACE_H_ */
EOF

  # Copy additional replace headers that talloc might need
  cp "$samba_src/lib/replace/hdr_replace.h" . 2>/dev/null || true

  # Copy system headers from replace if needed
  mkdir -p system
  cp -r "$samba_src/lib/replace/system/"* system/ 2>/dev/null || true

  local CC="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
  local CFLAGS="-fPIC -O2 -D__ANDROID_API__=$MIN_API_LEVEL -DNO_CONFIG_H -I."

  echo -e "${BLUE}🔨 Compiling talloc.c...${NC}"
  "$CC" $CFLAGS -c talloc.c -o talloc.o

  echo -e "${BLUE}📚 Creating static library...${NC}"
  ar rcs "$install_dir/lib/libtalloc.a" talloc.o

  # Copy header files
  cp talloc.h "$install_dir/include/"
  cp replace.h "$install_dir/include/"

  echo -e "${GREEN}✅ talloc built from Samba sources → $install_dir/lib/libtalloc.a${NC}"
  popd >/dev/null
}

# -------------------------------
#  Main build pipeline
# -------------------------------
main() {
  echo -e "${BLUE}📥 Step 1: Downloading sources${NC}"
  download_source "restic" "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" "$SOURCE_DIR/restic"
  download_source "rclone" "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" "$SOURCE_DIR/rclone"
  clone_repo "samba" "https://github.com/samba-team/samba.git" "$SOURCE_DIR/samba"

  echo -e "${BLUE}⚙️  Step 2: Building dependencies (talloc)${NC}"
  for arch in arm64-v8a; do
    build_talloc "$arch"
  done

  echo -e "${BLUE}💻 Step 3: Building Go binaries (restic & rclone)${NC}"
  for arch in arm64-v8a; do
    build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
    build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
  done

  echo -e "${GREEN}🎉 All native libraries built successfully!${NC}"
}

main "$@"
