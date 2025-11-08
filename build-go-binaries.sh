#!/bin/bash
# Native build script for Android (restic, rclone, PRoot, talloc)
set -eo pipefail

# -------------------------------
#  Banner
# -------------------------------
echo "================================="
echo "Building Native Binaries from Source"
echo "================================="

# -------------------------------
#  Version pins (override via env)
# -------------------------------
RESTIC_VERSION="${RESTIC_VERSION:-0.18.1}"
RCLONE_VERSION="${RCLONE_VERSION:-1.68.2}"
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
#  Android NDK setup (provided by Docker)
# -------------------------------
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/opt/android-ndk}"
export NDK="$ANDROID_NDK_HOME"
export PREBUILT_TAG="linux-x86_64"

echo "Using NDK: $NDK"

# -------------------------------
#  Architecture mappings (ARM64 only)
# -------------------------------
GO_ARCHS_arm64_v8a="arm64"
NDK_ARCH_ABI_arm64_v8a="aarch64-linux-android"

# -------------------------------
#  Utility: download sources
# -------------------------------
download_source() {
  local name="$1" url="$2" target_dir="$3"
  echo "📦 Downloading $name..."
  if [ -d "$target_dir" ]; then
    echo "Already present: $target_dir"
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
  echo "📦 Cloning $name repository..."
  if [ -d "$target_dir" ]; then
    echo "Already present: $target_dir"
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

  echo "🏗️  Building $name for $arch..."
  pushd "$src" >/dev/null
  case "$name" in
    restic) go build -v -ldflags="-s -w" -o "$out_dir/$out" ./cmd/restic ;;
    rclone) go build -v -ldflags="-s -w" -o "$out_dir/$out" . ;;
  esac
  popd >/dev/null
  [ -f "$out_dir/$out" ] || { echo "✗ Failed to build $name ($arch)"; exit 1; }
  echo "✅ Built $name → $out_dir/$out"
}


# -------------------------------
#  Main build pipeline
# -------------------------------
main() {
  echo "📥 Step 1: Downloading sources"
  download_source "restic" "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" "$SOURCE_DIR/restic"
  download_source "rclone" "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" "$SOURCE_DIR/rclone"

  echo "⚙️  Step 2: Building PRoot"
  ./build-native-binaries.sh

  echo "💻 Step 3: Building Go binaries (restic & rclone)"
  for arch in arm64-v8a; do
    build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
    build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
  done

  echo "🎉 All native libraries built successfully!"
}

main "$@"
