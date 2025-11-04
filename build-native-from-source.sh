#!/bin/bash
# Build native dependencies from source for F-Droid compliance
# This script compiles restic, rclone, proot, and libtalloc from source

set -eo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=================================${NC}"
echo -e "${BLUE}Building Native Binaries from Source${NC}"
echo -e "${BLUE}=================================${NC}"

# Version pinning for reproducible builds
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
        # Find latest NDK version
        ANDROID_NDK_HOME="$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
    elif [ -d "$HOME/Library/Android/sdk/ndk" ]; then
        ANDROID_NDK_HOME="$(find "$HOME/Library/Android/sdk/ndk" -maxdepth 1 -type d | sort -V | tail -n 1)"
    else
        echo -e "${RED}ERROR: Android NDK not found. Please set ANDROID_NDK_HOME${NC}"
        exit 1
    fi
fi

NDK="${ANDROID_NDK_HOME:-$ANDROID_NDK_ROOT}"
echo -e "${GREEN}Using NDK: $NDK${NC}"

# Check for required tools
command -v go >/dev/null 2>&1 || { echo -e "${RED}ERROR: Go is not installed${NC}"; exit 1; }
echo -e "${GREEN}Go version: $(go version)${NC}"

# Create build directories
mkdir -p "$SOURCE_DIR" "$OUTPUT_DIR"

# Architecture mapping
declare -A ANDROID_ARCHS=(
    ["arm64-v8a"]="arm64"
    ["armeabi-v7a"]="arm"
    ["x86_64"]="x86_64"
    ["x86"]="x86"
)

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

# Minimum Android API level
MIN_API_LEVEL=24

###################
# Download Sources
###################

download_source() {
    local name="$1"
    local url="$2"
    local target_dir="$3"
    
    echo -e "${BLUE}Downloading $name source...${NC}"
    
    if [ -d "$target_dir" ]; then
        echo -e "${GREEN}Source already exists: $target_dir${NC}"
        return 0
    fi
    
    local temp_file="$(mktemp)"
    curl -sSfL "$url" -o "$temp_file"
    
    mkdir -p "$target_dir"
    tar -xzf "$temp_file" -C "$target_dir" --strip-components=1
    rm "$temp_file"
    
    echo -e "${GREEN}✓ Downloaded $name${NC}"
}

###################
# Build Go Projects
###################

build_go_binary() {
    local project_name="$1"
    local source_dir="$2"
    local output_name="$3"
    local android_arch="$4"
    
    local go_arch="${GO_ARCHS[$android_arch]}"
    local ndk_arch="${NDK_ARCH_ABI[$android_arch]}"
    local output_dir="$OUTPUT_DIR/$android_arch"
    
    echo -e "${BLUE}Building $project_name for $android_arch...${NC}"
    
    mkdir -p "$output_dir"
    
    # Set up Go cross-compilation for Android
    export GOOS=android
    export GOARCH="$go_arch"
    export CGO_ENABLED=1
    export CC="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
    export CC=$(eval echo $CC) # Expand the wildcard
    
    if [ ! -f "$CC" ]; then
        echo -e "${RED}ERROR: Compiler not found: $CC${NC}"
        exit 1
    fi
    
    # Build
    pushd "$source_dir" > /dev/null
    
    if [ "$project_name" = "restic" ]; then
        # Restic specific build
        go build -v -ldflags="-s -w" -o "$output_dir/$output_name" ./cmd/restic
    elif [ "$project_name" = "rclone" ]; then
        # Rclone specific build
        go build -v -ldflags="-s -w" -o "$output_dir/$output_name" .
    fi
    
    popd > /dev/null
    
    # Verify the binary was created
    if [ ! -f "$output_dir/$output_name" ]; then
        echo -e "${RED}ERROR: Failed to build $project_name for $android_arch${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Built $project_name for $android_arch${NC}"
}

###################
# Build C Projects with NDK
###################

build_proot() {
    local android_arch="$1"
    local ndk_arch="${NDK_ARCH_ABI[$android_arch]}"
    local output_dir="$OUTPUT_DIR/$android_arch"
    
    echo -e "${BLUE}Building proot for $android_arch...${NC}"
    
    mkdir -p "$output_dir"
    
    local proot_source="$SOURCE_DIR/proot"
    
    # Set up NDK toolchain
    export CC="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
    export CC=$(eval echo $CC)
    export AR="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/llvm-ar"
    export AR=$(eval echo $AR)
    
    pushd "$proot_source/src" > /dev/null
    
    # Build proot
    make clean || true
    make CC="$CC" AR="$AR" CFLAGS="-D__ANDROID_API__=$MIN_API_LEVEL"
    
    # Copy binaries
    cp proot "$output_dir/libdata_proot.so"
    
    if [ -f "loader/loader" ]; then
        cp loader/loader "$output_dir/libdata_loader.so"
    fi
    
    if [ -f "loader/loader-m32" ]; then
        cp loader/loader-m32 "$output_dir/libdata_loader32.so"
    fi
    
    popd > /dev/null
    
    echo -e "${GREEN}✓ Built proot for $android_arch${NC}"
}

build_libtalloc() {
    local android_arch="$1"
    local ndk_arch="${NDK_ARCH_ABI[$android_arch]}"
    local output_dir="$OUTPUT_DIR/$android_arch"
    
    echo -e "${BLUE}Building libtalloc for $android_arch...${NC}"
    
    mkdir -p "$output_dir"
    
    local talloc_source="$SOURCE_DIR/talloc"
    
    # Set up NDK toolchain
    export CC="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/${ndk_arch}${MIN_API_LEVEL}-clang"
    export CC=$(eval echo $CC)
    export AR="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-*/bin/llvm-ar"
    export AR=$(eval echo $AR)
    export CFLAGS="-D__ANDROID_API__=$MIN_API_LEVEL -fPIC -D_FILE_OFFSET_BITS=64"
    
    pushd "$talloc_source" > /dev/null

    # ✅ Create cross answers for Waf
    cat > cross-answers.txt <<'EOF'
talloc_cv_HAVE_VA_COPY=yes
talloc_cv_C99_VSNPRINTF=yes
talloc_cv_HAVE_LIBREPLACE=no
talloc_cv_SIZEOF_OFF_T=8
EOF

    # ✅ Call ./configure, but pass Waf options through
    ./configure \
        --prefix="$BUILD_DIR/talloc-install/$android_arch" \
        --disable-python \
        --cross-compile \
        --cross-answers=cross-answers.txt \
        CC="$CC" AR="$AR" CFLAGS="$CFLAGS"

    make clean || true
    make
    make install

    # Copy built library
    cp "$BUILD_DIR/talloc-install/$android_arch/lib/libtalloc.so"* \
       "$output_dir/libdata_libtalloc.so.2.so"

    popd > /dev/null
    echo -e "${GREEN}✓ Built libtalloc for $android_arch${NC}"
}


###################
# Main Build Process
###################

main() {
    echo -e "${BLUE}Step 1: Downloading source code...${NC}"
    
    # Download sources
    download_source "restic" \
        "https://github.com/restic/restic/archive/refs/tags/v${RESTIC_VERSION}.tar.gz" \
        "$SOURCE_DIR/restic"
    
    download_source "rclone" \
        "https://github.com/rclone/rclone/archive/refs/tags/v${RCLONE_VERSION}.tar.gz" \
        "$SOURCE_DIR/rclone"
    
    download_source "proot" \
        "https://github.com/proot-me/proot/archive/refs/tags/v${PROOT_VERSION}.tar.gz" \
        "$SOURCE_DIR/proot"
    
    download_source "talloc" \
        "https://download.samba.org/pub/talloc/talloc-${LIBTALLOC_VERSION}.tar.gz" \
        "$SOURCE_DIR/talloc"
    
    echo -e "${BLUE}Step 2: Building for all architectures...${NC}"
    
    # Build for each architecture
    for arch in arm64-v8a armeabi-v7a x86_64 x86; do
        echo -e "${BLUE}Building for $arch...${NC}"
        
        # Build Go projects
        build_go_binary "restic" "$SOURCE_DIR/restic" "libdata_restic.so" "$arch"
        build_go_binary "rclone" "$SOURCE_DIR/rclone" "libdata_rclone.so" "$arch"
        
        # Build C projects
        build_libtalloc "$arch"
        build_proot "$arch"
        
        echo -e "${GREEN}✓ Completed $arch${NC}"
    done
    
    echo -e "${GREEN}=================================${NC}"
    echo -e "${GREEN}Build completed successfully!${NC}"
    echo -e "${GREEN}Native libraries are in: $OUTPUT_DIR${NC}"
    echo -e "${GREEN}=================================${NC}"
}

# Run main build process
main "$@"

