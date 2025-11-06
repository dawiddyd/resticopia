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
readonly TARGET_ARCH="aarch64-linux-gnu"
readonly BUILD_TYPE="static"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Directory structure
readonly SRC_DIR="/build/sources/proot"
readonly TALLOC_DIR="/build/build/native-build/sources/talloc"
readonly BUILD_DIR="/build/build"
readonly INSTALL_DIR="/build/install"
readonly OUT_DIR="/build/out"

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

    # Check for cross-compilation toolchain
    if ! command -v "${TARGET_ARCH}-gcc" >/dev/null 2>&1; then
        missing_deps+=("${TARGET_ARCH}-gcc")
    fi

    if ! command -v "${TARGET_ARCH}-ar" >/dev/null 2>&1; then
        missing_deps+=("${TARGET_ARCH}-ar")
    fi

    if ! command -v "${TARGET_ARCH}-strip" >/dev/null 2>&1; then
        missing_deps+=("${TARGET_ARCH}-strip")
    fi

    # Check for build tools
    for cmd in git make pkg-config; do
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
    mkdir -p "$SRC_DIR" "$TALLOC_DIR" "$BUILD_DIR" "$INSTALL_DIR" "$OUT_DIR"
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

build_talloc() {
    log_info "Building static talloc library for ${TARGET_ARCH}..."

    local build_dir="$BUILD_DIR/talloc"
    mkdir -p "$build_dir"
    cd "$build_dir"

    # Copy source files from existing talloc directory
    if [ ! -f "$TALLOC_DIR/talloc.c" ]; then
        die "talloc.c not found in $TALLOC_DIR"
    fi

    cp "$TALLOC_DIR/talloc.c" .
    cp "$TALLOC_DIR/talloc.h" .
    cp "$TALLOC_DIR/replace.h" . 2>/dev/null || true
    cp "$TALLOC_DIR/build_version.h" . 2>/dev/null || true

    # Compile static library
    log_info "Compiling talloc.c..."
    "${TARGET_ARCH}-gcc" \
        -fPIC \
        -O2 \
        -static \
        -I. \
        -c talloc.c \
        -o talloc.o

    # Create static library
    log_info "Creating libtalloc.a..."
    "${TARGET_ARCH}-ar" rcs "$INSTALL_DIR/libtalloc.a" talloc.o

    # Install headers
    mkdir -p "$INSTALL_DIR/include"
    cp talloc.h "$INSTALL_DIR/include/"
    cp replace.h "$INSTALL_DIR/include/" 2>/dev/null || true

    log_success "talloc built successfully"
}

build_proot() {
    log_info "Building PRoot for ${TARGET_ARCH}..."

    local build_dir="$BUILD_DIR/proot"
    mkdir -p "$build_dir"
    cd "$build_dir"

    # Copy source directory
    cp -r "$SRC_DIR"/* .

    # Set build environment
    export CC="${TARGET_ARCH}-gcc"
    export AR="${TARGET_ARCH}-ar"
    export STRIP="${TARGET_ARCH}-strip"
    export CFLAGS="-I$INSTALL_DIR/include -I. -I../lib/uthash/include -I/usr/aarch64-linux-gnu/include -D_GNU_SOURCE -static -O2 -fPIC"
    export LDFLAGS="-L$INSTALL_DIR -static -ltalloc"

    # Build in the src directory
    cd src

    # Manual compilation approach - compile core PRoot objects directly
    log_info "Compiling PRoot source files manually..."

    # List of core source files (excluding loader for now)
    local core_sources=(
        cli/cli.c
        cli/proot.c
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
        tracee/tracee.c
        ptrace/ptrace.c
    )

    # Compile all core objects
    local objects=()
    for src in "${core_sources[@]}"; do
        local obj="${src%.c}.o"
        log_info "Compiling $src..."
        if ! "$CC" $CFLAGS -c "$src" -o "$obj"; then
            log_error "Failed to compile $src"
            return 1
        fi
        objects+=("$obj")
    done

    # Link the final binary
    log_info "Linking proot binary..."
    if ! "$CC" "${objects[@]}" $LDFLAGS -o proot; then
        log_error "Failed to link proot binary"
        return 1
    fi

    # Verify build artifacts
    if [ ! -f "proot" ]; then
        die "PRoot binary not found after manual build"
    fi

    log_success "PRoot built successfully"

    # Return empty loader path since we're not building the loader
    echo ""
}

package_artifacts() {
    local loader_path="$1"

    log_info "Packaging build artifacts..."

    # Copy main binary
    cp "$BUILD_DIR/proot/proot" "$OUT_DIR/proot-${TARGET_ARCH}"

    # Copy loader if it exists
    if [ -n "$loader_path" ] && [ -f "$BUILD_DIR/proot/$loader_path" ]; then
        cp "$BUILD_DIR/proot/$loader_path" "$OUT_DIR/loader-${TARGET_ARCH}"
    fi

    # Strip binaries for smaller size
    if command -v "${TARGET_ARCH}-strip" >/dev/null 2>&1; then
        log_info "Stripping binaries..."
        "${TARGET_ARCH}-strip" "$OUT_DIR/proot-${TARGET_ARCH}" || true
        [ -f "$OUT_DIR/loader-${TARGET_ARCH}" ] && "${TARGET_ARCH}-strip" "$OUT_DIR/loader-${TARGET_ARCH}" || true
    fi

    log_success "Artifacts packaged to $OUT_DIR"
}

verify_artifacts() {
    log_info "Verifying build artifacts..."

    # Check file exists and is executable
    if [ ! -f "$OUT_DIR/proot-${TARGET_ARCH}" ]; then
        die "PRoot binary not found in output directory"
    fi

    if [ ! -x "$OUT_DIR/proot-${TARGET_ARCH}" ]; then
        die "PRoot binary is not executable"
    fi

    # Check if it's a static binary (basic check)
    if ! file "$OUT_DIR/proot-${TARGET_ARCH}" | grep -q "statically linked"; then
        log_warning "PRoot binary does not appear to be statically linked"
    fi

    # Check architecture
    if ! file "$OUT_DIR/proot-${TARGET_ARCH}" | grep -q "ARM aarch64"; then
        log_warning "PRoot binary does not appear to be for ARM64 architecture"
    fi

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
    # Using existing talloc from build/native-build/sources/talloc

    # Build dependencies
    build_talloc

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
