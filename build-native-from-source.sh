#!/bin/bash
# Build native dependencies from source for F-Droid compliance
# This script compiles restic, rclone, proot, and libtalloc from source

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
LIBTALLOC_VERSION="2.4.3"

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
  local arch="$1"
  local ndk_arch="${NDK_ARCH_ABI[$arch]}"
  local out_dir="$OUTPUT_DIR/$arch"
  local src="$SOURCE_DIR/proot"
  mkdir -p "$out_dir"
  export CC="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
  export AR="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/llvm-ar"

  pushd "$src/src" >/dev/null
  make clean || true
  make CC="$CC" AR="$AR" CFLAGS="-D__ANDROID_API__=$MIN_API_LEVEL"
  cp proot "$out_dir/libdata_proot.so" || true
  [ -f "$out_dir/libdata_proot.so" ] || { echo -e "${RED}Failed to build proot ($arch)${NC}"; exit 1; }
  popd >/dev/null
  echo -e "${GREEN}✓ Built proot for $arch${NC}"
}

build_libtalloc() {
  local arch="$1"
  local ndk_arch="${NDK_ARCH_ABI[$arch]}"
  local out_dir="$OUTPUT_DIR/$arch"
  local src="$SOURCE_DIR/talloc"
  mkdir -p "$out_dir"

  export CC="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
  export AR="$NDK/toolchains/llvm/prebuilt/$PREBUILT_TAG/bin/llvm-ar"
  export CFLAGS="-D__ANDROID_API__=$MIN_API_LEVEL -fPIC -D_FILE_OFFSET_BITS=64"

  pushd "$src" >/dev/null

  # Clean up any previous build
  make clean || true

  # Cross-compile configure step with pre-answered tests
  ./configure \
    --prefix="$BUILD_DIR/talloc-install/$arch" \
    --disable-python \
    --host="${ndk_arch}" \
    --build="$(uname -m)-linux-gnu" \
    --enable-static \
    --enable-shared \
    CC="$CC" AR="$AR" CFLAGS="$CFLAGS" \
    ac_cv_func_malloc_0_nonnull=yes \
    ac_cv_func_realloc_0_nonnull=yes \
    ac_cv_func_vsnprintf_works=yes \
    ac_cv_func_snprintf_works=yes \
    ac_cv_func_memcmp_working=yes \
    ac_cv_func_vprintf=yes \
    ac_cv_func_printf=yes \
    ac_cv_func_vfprintf=yes \
    ac_cv_func_fprintf=yes \
    ac_cv_func_strerror_r_char_p=yes \
    ac_cv_func_gettimeofday=yes \
    ac_cv_func_mmap_fixed_mapped=yes \
    ac_cv_func_mmap_anon=yes \
    ac_cv_func_mmap_dev_zero=yes

  # Build quietly but show logs if anything fails
  make -j"$(nproc)" > build.log 2>&1 || {
      echo -e "${RED}Talloc build failed ($arch). Showing last 40 lines of build.log:${NC}"
      tail -n 40 build.log
      exit 1
  }

  make install > install.log 2>&1 || {
      echo -e "${RED}Talloc install failed ($arch). Showing last 40 lines of install.log:${NC}"
      tail -n 40 install.log
      exit 1
  }

  cp "$BUILD_DIR/talloc-install/$arch/lib/libtalloc.so"* "$out_dir/libdata_libtalloc.so.2.so" || {
      echo -e "${RED}Talloc copy failed for $arch${NC}"
      exit 1
  }

  popd >/dev/null
  echo -e "${GREEN}✓ Built libtalloc for $arch${NC}"
}


main() {
  echo -e "${BLUE}Step 1: Downloading sources${NC}"
  download_source "restic" "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" "$SOURCE_DIR/restic"
  download_source "rclone" "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" "$SOURCE_DIR/rclone"
  download_source "proot" "https://github.com/proot-me/proot/archive/refs/tags/v${PROOT_VERSION}.tar.gz" "$SOURCE_DIR/proot"
  download_source "talloc" "https://download.samba.org/pub/talloc/talloc-${LIBTALLOC_VERSION}.tar.gz" "$SOURCE_DIR/talloc"

  echo -e "${BLUE}Step 2: Building architectures${NC}"
  for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
    build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
    build_libtalloc "$arch"
    build_proot "$arch"
  done
  echo -e "${GREEN}All native libraries built successfully!${NC}"
}

main "$@"
