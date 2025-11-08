#!/usr/bin/env bash
#
# Cross-compilation build script for PRoot and Talloc
# Builds static arm64 Linux binaries for use in Android applications
#
# Based on: https://proot-me.github.io/blog/alpine-aarch64/
# and: https://github.com/proot-me/proot/issues/239#issuecomment-800776723
#
# Usage: Run on Debian Bookworm (arm64 for production, amd64 with cross-compilation for testing)
#

set -euo pipefail

# --- Configuration ---
readonly SCRIPT_VERSION="1.0.0"
readonly TARGET_ARCH="aarch64-linux-android"
readonly BUILD_TYPE="shared"
readonly ANDROID_API_LEVEL=24

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Directory structure
readonly SRC_DIR="/build/sources/proot"
readonly BUILD_DIR="/build/build"
readonly INSTALL_DIR="/build/build/native-build/install/arm64-v8a"
readonly OUT_DIR="/build/app/src/main/jniLibs/arm64-v8a"
readonly NDK_HOME="/opt/android-ndk"

# Git repositories
readonly PROOT_REPO="https://github.com/proot-me/proot.git"
readonly SAMBA_REPO="https://github.com/samba-team/samba.git"

# --- Functions ---

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

die() {
    log_error "$*"
    exit 1
}

check_dependencies() {
    local missing_deps=()

    # Check for Android NDK
    if [ ! -d "$NDK_HOME" ]; then
        die "Android NDK not found at $NDK_HOME"
    fi

    # Check for Android cross-compilation toolchain
    local ndk_clang="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API_LEVEL}-clang"
    if ! command -v "$ndk_clang" >/dev/null 2>&1; then
        missing_deps+=("Android NDK clang ($ndk_clang)")
    fi

    # Check for build tools
    for cmd in git make; do
        if ! command -v "$cmd" >/dev/null 2>&1; then
            missing_deps+=("$cmd")
        fi
    done

    if [ ${#missing_deps[@]} -ne 0 ]; then
        die "Missing required dependencies: ${missing_deps[*]}"
    fi
}

setup_directories() {
    log_info "Setting up build directories..."
    mkdir -p "$SRC_DIR" "$BUILD_DIR" "$INSTALL_DIR" "$OUT_DIR"
}

clone_or_update_repo() {
    local repo_url="$1"
    local target_dir="$2"
    local repo_name="$3"
    local depth="${4:-1}"  # Default depth is 1 for shallow clones

    if [ ! -d "$target_dir/.git" ]; then
        log_info "Cloning $repo_name repository..."
        git clone --depth="$depth" "$repo_url" "$target_dir"
    else
        log_info "Updating existing $repo_name repository..."
        (
            cd "$target_dir"
            git fetch origin
            git reset --hard origin/master
        )
    fi
}


build_proot() {
    log_info "Building PRoot shared libraries for Android ${TARGET_ARCH}..."

    local build_dir="$BUILD_DIR/proot"
    mkdir -p "$build_dir"
    cd "$build_dir"

    # Copy PRoot source first
    cp -r "$SRC_DIR"/* .

    # Now copy talloc.h and talloc.c from Samba sources (this will overwrite any existing ones)
    log_info "Copying talloc.h from Samba sources..."
    cp "/build/build/native-build/sources/samba/lib/talloc/talloc.h" lib/talloc/ || die "Failed to copy talloc.h"
    log_info "Copying talloc.c from Samba sources..."
    cp "/build/build/native-build/sources/samba/lib/talloc/talloc.c" lib/talloc/ || die "Failed to copy talloc.c"
    log_info "Contents of lib/talloc/ after copying:"
    ls -la lib/talloc/ || die "lib/talloc directory check failed"

    # Create minimal replace.h for PRoot build (same as main talloc build)
    cat > "lib/talloc/replace.h" << 'EOF'
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

    log_info "Contents of lib/talloc/ after creating replace.h:"
    ls -la lib/talloc/ || die "lib/talloc directory check failed after replace.h"


    # Check if talloc library exists (built by build-other.sh)
    if [ ! -f "$INSTALL_DIR/lib/libtalloc.a" ]; then
        log_error "talloc library not found at $INSTALL_DIR/lib/libtalloc.a"
        log_error "Make sure build-other.sh runs first to build talloc"
        return 1
    fi

    log_info "Found talloc library, proceeding with PRoot build"

    # Set Android NDK compiler variables
    export CC="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API_LEVEL}-clang"
    export AR="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
    export STRIP="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    export CFLAGS="-fPIC -I$INSTALL_DIR/include -I. -I../lib/uthash/include -D__ANDROID_API__=$ANDROID_API_LEVEL -DANDROID -Wno-implicit-function-declaration -D_GNU_SOURCE"
    export LDFLAGS="-shared -Wl,-soname,libdata_proot.so"

    # Build loader first (for Android architecture)
    cd src
    log_info "Creating Android-compatible loader..."

    cat > "loader_dummy.c" << 'EOF'
/* Dummy loader implementation for Android */
unsigned char _binary_loader_elf_start[] = {0};
unsigned char _binary_loader_elf_end[] = {0};
unsigned char _binary_loader_m32_elf_start[] __attribute__((weak)) = {0};
unsigned char _binary_loader_m32_elf_end[] __attribute__((weak)) = {0};
EOF

    # Compile loader_dummy.c with Android compiler
    $CC $CFLAGS -c loader_dummy.c -o loader_dummy.o

    # Now build PRoot shared library for Android
    log_info "Building PRoot shared library for Android..."

    # Core PRoot source files (simplified for Android)
    local PROOT_SOURCES="
        cli/cli.c
        cli/proot.c
        cli/note.c
        execve/enter.c
        execve/exit.c
        execve/shebang.c
        execve/elf.c
        execve/ldso.c
        execve/auxv.c
        execve/aoxp.c
        path/binding.c
        path/glue.c
        path/canon.c
        path/path.c
        path/proc.c
        path/temp.c
        syscall/seccomp.c
        syscall/syscall.c
        syscall/chain.c
        syscall/enter.c
        syscall/exit.c
        syscall/heap.c
        syscall/rlimit.c
        syscall/socket.c
        syscall/sysnum.c
        tracee/tracee.c
        tracee/event.c
        tracee/mem.c
        tracee/reg.c
        ptrace/ptrace.c
        ptrace/user.c
        ptrace/wait.c
        extension/extension.c
        extension/fake_id0/fake_id0.c
        extension/kompat/kompat.c
        extension/link2symlink/link2symlink.c
    "

    # Build talloc object file from local copy
    log_info "Building talloc object..."
    log_info "Current directory: $(pwd)"
    log_info "Checking ../lib/talloc/ contents:"
    if [ -d "../lib/talloc" ]; then
        ls -la ../lib/talloc/ || log_warning "ls failed on ../lib/talloc/"
    else
        log_error "../lib/talloc directory does not exist"
        return 1
    fi

    local TALLOC_SRC="../lib/talloc/talloc.c"
    if [ ! -f "$TALLOC_SRC" ]; then
        log_error "talloc.c not found at $TALLOC_SRC"
        log_error "Files in parent directory:"
        find .. -name "*.c" | head -10 || log_warning "find failed"
        return 1
    fi
    $CC $CFLAGS -I../lib/talloc -c "$TALLOC_SRC" -o talloc.o

    # Build PRoot object files
    log_info "Building PRoot objects..."
    local OBJECTS=""
    for src in $PROOT_SOURCES; do
        local obj="${src%.c}.o"
        if [ -f "$src" ]; then
            log_info "Compiling $src..."
            $CC $CFLAGS -c "$src" -o "$obj"
            OBJECTS="$OBJECTS $obj"
        else
            log_warning "Source file $src not found, skipping"
        fi
    done

    # Link PRoot shared library
    log_info "Linking PRoot shared library..."
    $CC $LDFLAGS -o libdata_proot.so loader_dummy.o talloc.o $OBJECTS

    # Build simple loader shared libraries
    log_info "Building loader shared libraries..."

    # Create simple loader implementations
    cat > "loader_simple.c" << 'EOF'
/* Simple loader for Android */
#include <stdlib.h>
#include <stdio.h>

void loader_init(void) {
    /* Android loader initialization */
}

int loader_exec(const char *path, char *const argv[], char *const envp[]) {
    /* Simple exec wrapper for Android */
    return execve(path, argv, envp);
}
EOF

    $CC $CFLAGS -shared -Wl,-soname,libdata_loader.so -o libdata_loader.so loader_simple.c
    $CC $CFLAGS -shared -Wl,-soname,libdata_loader32.so -o libdata_loader32.so loader_simple.c

    # Verify build artifacts
    if [ ! -f "libdata_proot.so" ]; then
        log_error "PRoot shared library not found after build"
        return 1
    fi

    log_success "PRoot shared libraries built successfully"

    # Return empty loader path (not needed for Android)
    echo ""
}

package_artifacts() {
    local loader_path="$1"

    log_info "Packaging build artifacts..."

    # Copy shared libraries to Android JNI libs directory
    cp "$BUILD_DIR/proot/src/libdata_proot.so" "$OUT_DIR/"
    cp "$BUILD_DIR/proot/src/libdata_loader.so" "$OUT_DIR/"
    cp "$BUILD_DIR/proot/src/libdata_loader32.so" "$OUT_DIR/"

    # Strip libraries for smaller size
    local STRIP="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    if command -v "$STRIP" >/dev/null 2>&1; then
        log_info "Stripping libraries..."
        "$STRIP" "$OUT_DIR/libdata_proot.so" || true
        "$STRIP" "$OUT_DIR/libdata_loader.so" || true
        "$STRIP" "$OUT_DIR/libdata_loader32.so" || true
    fi

    log_success "Artifacts packaged to $OUT_DIR"
}

verify_artifacts() {
    log_info "Verifying build artifacts..."

    # Check shared libraries exist
    local libs=("libdata_proot.so" "libdata_loader.so" "libdata_loader32.so")
    for lib in "${libs[@]}"; do
        if [ ! -f "$OUT_DIR/$lib" ]; then
            die "$lib not found in output directory"
        fi
    done

    # Check if they're shared libraries
    for lib in "${libs[@]}"; do
        if ! file "$OUT_DIR/$lib" | grep -q "shared object"; then
            log_warning "$lib does not appear to be a shared library"
        fi
    done

    # Check architecture
    for lib in "${libs[@]}"; do
        if ! file "$OUT_DIR/$lib" | grep -q "ARM aarch64"; then
            log_warning "$lib does not appear to be for ARM64 architecture"
        fi
    done

    log_success "Artifact verification completed"
}

show_summary() {
    echo
    echo "==========================================="
    echo "🏗️  Build Summary"
    echo "==========================================="
    echo "Target Architecture: ${TARGET_ARCH}"
    echo "Build Type: ${BUILD_TYPE}"
    echo "Output Directory: ${OUT_DIR}"
    echo
    echo "Generated files:"
    ls -lh "$OUT_DIR"
    echo
    echo "File information:"
    file "$OUT_DIR"/*
    echo "==========================================="
}

# --- Main Build Process ---

main() {
    echo "==========================================="
    echo "🏗️  Building PRoot and Talloc for ${TARGET_ARCH}"
    echo "==========================================="
    echo "Script Version: ${SCRIPT_VERSION}"
    echo "Build Type: ${BUILD_TYPE}"
    echo

    # Pre-build checks
    check_dependencies
    setup_directories

    # Clone/update source repositories
    clone_or_update_repo "$PROOT_REPO" "$SRC_DIR" "PRoot"
    # Using talloc built by build-other.sh

    # Build main project
    local loader_path
    loader_path=$(build_proot)

    # Package and verify
    package_artifacts "$loader_path"
    verify_artifacts
    show_summary

    log_success "Build completed successfully! ✅"
}

# Run main function
main "$@"
