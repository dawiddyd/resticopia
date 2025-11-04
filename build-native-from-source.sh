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
  local android_arch="$1"
  local ndk_arch="${NDK_ARCH_ABI[$android_arch]}"
  local output_dir="$OUTPUT_DIR/$android_arch"
  local talloc_source="$SOURCE_DIR/talloc"

  echo "Building libtalloc for $android_arch..."

  mkdir -p "$output_dir"
  pushd "$talloc_source" >/dev/null
  

  # enable bash tracing to see every command
  set -x

   # Set up NDK toolchain
    export CC="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
    export CC=$(eval echo $CC)
    export AR="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/llvm-ar"
    export AR=$(eval echo $AR)
    export LFS_FLAGS="-D_FILE_OFFSET_BITS=64 -D_LARGEFILE64_SOURCE -D_LARGE_FILES"
    export CFLAGS="-D__ANDROID_API__=$MIN_API_LEVEL -fPIC -D_FILE_OFFSET_BITS=64 $LFS_FLAGS -D_LARGEFILE64_SOURCE -D_LARGE_FILES"    

    # ✅ Create cross answers for Waf
    cat > cross-answers.txt <<'EOF'
talloc_cv_HAVE_VA_COPY=yes
talloc_cv_C99_VSNPRINTF=yes
talloc_cv_HAVE_LIBREPLACE=no
talloc_cv_SIZEOF_OFF_T=8
talloc_cv_LARGEFILE_SUPPORT=yes
EOF

  # run configure and capture exit code
  ./configure \
      --prefix="$BUILD_DIR/talloc-install/$android_arch" \
      --disable-python \
      --cross-compile \
      --cross-answers=cross-answers.txt \
      CC="$CC" AR="$AR" \
      CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
      TALLOC_CFLAGS="$CFLAGS" TALLOC_LDFLAGS="$LDFLAGS" \
      > configure.log 2>&1
  rc=$?

  set +x
  echo "==== configure exit code: $rc ===="

  if [ $rc -ne 0 ]; then
      echo "libtalloc configure failed for $android_arch"
      echo "--- configure.log (last 100 lines) ---"
      tail -n 100 configure.log || true
      echo "--- end configure.log ---"
      return 1
  fi

  set -x
  make -j"$(nproc)" > build.log 2>&1
  rc=$?
  set +x

  echo "==== make exit code: $rc ===="
  if [ $rc -ne 0 ]; then
      echo "libtalloc make failed for $android_arch"
      tail -n 100 build.log || true
      return 1
  fi

  make install DESTDIR="$BUILD_DIR/talloc-install/$android_arch" > install.log 2>&1
  rc=$?
  echo "==== make install exit code: $rc ===="
  if [ $rc -ne 0 ]; then
      echo "libtalloc install failed for $android_arch"
      tail -n 100 install.log || true
      return 1
  fi

  popd >/dev/null
  echo "✓ Built libtalloc for $android_arch"
}



main() {
  echo -e "${BLUE}Step 1: Downloading sources${NC}"
  download_source "restic" "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" "$SOURCE_DIR/restic"
  download_source "rclone" "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" "$SOURCE_DIR/rclone"
  download_source "proot" "https://github.com/proot-me/proot/archive/refs/tags/v${PROOT_VERSION}.tar.gz" "$SOURCE_DIR/proot"
  download_source "talloc" "https://download.samba.org/pub/talloc/talloc-${LIBTALLOC_VERSION}.tar.gz" "$SOURCE_DIR/talloc"

  echo -e "${BLUE}Step 2: Building architectures${NC}"
  for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    build_libtalloc "$arch"
    build_proot "$arch"
    build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
    build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
  done
  echo -e "${GREEN}All native libraries built successfully!${NC}"
}

main "$@"
