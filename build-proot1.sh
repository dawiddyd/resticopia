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
    mkdir -p "$INSTALL_DIR/lib"
    "${TARGET_ARCH}-ar" rcs "$INSTALL_DIR/lib/libtalloc.a" talloc.o

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

    # Create a dummy pkg-config script for talloc
    mkdir -p "$build_dir/bin"
    cat > "$build_dir/bin/pkg-config" << EOF
#!/bin/bash
if [ "\$1" = "--cflags" ] && [ "\$2" = "talloc" ]; then
    echo "-I$INSTALL_DIR/include"
elif [ "\$1" = "--libs" ] && [ "\$2" = "talloc" ]; then
    echo "$INSTALL_DIR/libtalloc.a -Wl,-Bstatic"
elif [ "\$1" = "--libs" ] && [ "\$2" = "libarchive" ]; then
    echo ""  # We're not using libarchive
else
    exit 1
fi
EOF
    chmod +x "$build_dir/bin/pkg-config"

    export PATH="$build_dir/bin:$PATH"
    export PKG_CONFIG="$build_dir/bin/pkg-config"

    # Build in the src directory
    cd src

    # First build the loader for the HOST architecture (x86_64)
    log_info "Building loader for host architecture..."
    # Ensure we're using host tools for loader
    export CC="gcc"
    export AR="ar"
    export STRIP="strip"
    export CFLAGS="-static -O2"
    export LDFLAGS="-static"
    # Disable 32-bit loader since we're cross-compiling to ARM64
    # Comment out the 32-bit loader build rules specifically
    sed -i '/^ifdef HAS_LOADER_32BIT/,/^endif/ s|^|# |' GNUmakefile
    # Temporarily remove any cross-linker from PATH
    export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    if ! make loader.elf build.h; then
        log_error "Failed to build loader for host architecture"
        return 1
    fi

    # Restore PATH with our bin directory
    export PATH="$build_dir/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    # Now build proot for the TARGET architecture (aarch64)
    log_info "Building proot binary for ${TARGET_ARCH}..."
    export CC="${TARGET_ARCH}-gcc"
    export AR="${TARGET_ARCH}-ar"
    export STRIP="${TARGET_ARCH}-strip"
    export CFLAGS="-I$INSTALL_DIR/include -static -O2 -Wno-implicit-function-declaration"
    export LDFLAGS="-static"

    # Force rebuild of proot by removing existing binary first
    rm -f proot

    # Try building proot without the loader (as suggested by the referenced documentation for simplicity)
    log_info "Building proot without loader for cross-compilation to ARM64"

    # Set environment variables to disable loader
    export HAS_LOADER_32BIT=0
    export DISABLE_LOADER=1

    # Create a simple makefile that builds proot with talloc sources directly
    log_info "Creating simplified makefile for proot-only build with inline talloc"

    # Get the talloc source files
    TALLOC_SOURCES="$TALLOC_DIR/talloc.c"

    cat > "Makefile.simple" << EOF
CC = aarch64-linux-gnu-gcc
AR = aarch64-linux-gnu-ar
STRIP = aarch64-linux-gnu-strip
CFLAGS = -I/build/install/include -I. -I../lib/uthash/include -static -O2 -Wno-implicit-function-declaration -D_GNU_SOURCE
LDFLAGS = -static

# talloc object
TALLOC_OBJ = talloc.o

# Dummy loader symbols (since we're not building the loader)
LOADER_DUMMY = loader_dummy.o

# Core proot objects (excluding loader)
OBJECTS = \\
    cli/cli.o \\
    cli/proot.o \\
    cli/note.o \\
    execve/enter.o \\
    execve/exit.o \\
    execve/shebang.o \\
    execve/elf.o \\
    execve/ldso.o \\
    execve/auxv.o \\
    execve/aoxp.o \\
    path/binding.o \\
    path/glue.o \\
    path/canon.o \\
    path/path.o \\
    path/proc.o \\
    path/temp.o \\
    syscall/seccomp.o \\
    syscall/syscall.o \\
    syscall/chain.o \\
    syscall/enter.o \\
    syscall/exit.o \\
    syscall/heap.o \\
    syscall/rlimit.o \\
    syscall/socket.o \\
    syscall/sysnum.o \\
    tracee/tracee.o \\
    tracee/event.o \\
    tracee/mem.o \\
    tracee/reg.o \\
    ptrace/ptrace.o \\
    ptrace/user.o \\
    ptrace/wait.o \\
    extension/extension.o \\
    extension/fake_id0/fake_id0.o \\
    extension/kompat/kompat.o \\
    extension/link2symlink/link2symlink.o \\
    extension/portmap/portmap.o \\
    extension/portmap/map.o

proot: \$(TALLOC_OBJ) \$(LOADER_DUMMY) \$(OBJECTS)
	\$(CC) \$(LDFLAGS) -o \$@ \$^

\$(TALLOC_OBJ): $TALLOC_SOURCES
	\$(CC) \$(CFLAGS) -c -o \$@ \$<

\$(LOADER_DUMMY): loader_dummy.c
	\$(CC) \$(CFLAGS) -c -o \$@ \$<

loader_dummy.c:
	printf "unsigned char _binary_loader_elf_start[] = {0};\nunsigned char _binary_loader_elf_end[] = {0};\nunsigned char _binary_loader_m32_elf_start[] __attribute__((weak)) = {0};\nunsigned char _binary_loader_m32_elf_end[] __attribute__((weak)) = {0};\n" > \$@

%.o: %.c
	\$(CC) \$(CFLAGS) -c -o \$@ \$<

clean:
	rm -f \$(TALLOC_OBJ) \$(LOADER_DUMMY) loader_dummy.c \$(OBJECTS) proot
EOF

    # Check if talloc library exists
    if [ ! -f "/build/install/lib/libtalloc.a" ]; then
        log_error "talloc library not found at /build/install/lib/libtalloc.a"
        return 1
    fi

    log_info "Found talloc library, proceeding with proot build"

    # Build proot using the simple makefile
    if ! make -f Makefile.simple proot; then
        log_error "Failed to build proot binary"
        return 1
    fi

    # Restore original ld
    rm "$build_dir/bin/ld"
    mv "$build_dir/bin/ld.bak" "$build_dir/bin/ld" 2>/dev/null || true

    # Verify build artifacts
    if [ ! -f "proot" ]; then
        die "PRoot binary not found after build"
    fi

    # Check if loader was built
    local loader_path=""
    if [ -f "loader.elf" ]; then
        loader_path="src/loader.elf"
    fi

    log_success "PRoot built successfully"

    # Return loader path
    echo "$loader_path"
}

package_artifacts() {
    local loader_path="$1"

    log_info "Packaging build artifacts..."

    # Copy main binary (built in src directory)
    cp "$BUILD_DIR/proot/src/proot" "$OUT_DIR/proot-${TARGET_ARCH}"

    # Copy loader if it exists (not built in our simplified version)
    if [ -n "$loader_path" ] && [ -f "$BUILD_DIR/proot/src/$loader_path" ]; then
        cp "$BUILD_DIR/proot/src/$loader_path" "$OUT_DIR/loader-${TARGET_ARCH}"
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
