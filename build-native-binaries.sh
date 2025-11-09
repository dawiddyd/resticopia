#!/bin/bash
# build-native-binaries.sh - Integrated PRoot build using build-proot-android approach

set -euo pipefail


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

echo "================================="
echo "Building PRoot using build-proot-android"
echo "================================="

# Use Docker-provided Android NDK
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/opt/android-ndk}"
export NDK="$ANDROID_NDK_HOME"
echo "Using NDK: $NDK"

# Set reproducible build environment for C/C++ compilation
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(date +%s)}"
echo "Using SOURCE_DATE_EPOCH: $SOURCE_DATE_EPOCH ($(date -r $SOURCE_DATE_EPOCH 2>/dev/null || date -d @$SOURCE_DATE_EPOCH))"

# Deterministic compilation flags
export CFLAGS="-g0 -O2 -Wno-unused-command-line-argument -fdebug-prefix-map=/build= -ffile-prefix-map=/build="
export CXXFLAGS="-g0 -O2 -Wno-unused-command-line-argument -fdebug-prefix-map=/build= -ffile-prefix-map=/build="
export LDFLAGS="-s -w"

# Clone build-proot-android if not present
PROOT_BUILD_REPO="$SOURCE_DIR/build-proot-android"
if [ ! -d "$PROOT_BUILD_REPO" ]; then
  echo "📦 Cloning build-proot-android repository..."
  git clone --depth 1 https://codeberg.org/dawdyd/build-proot-android "$PROOT_BUILD_REPO"
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

# Reproducible build environment is already set above

# Run the build
echo "🏗️  Building PRoot..."
./get-talloc.sh
./get-proot.sh
./make-talloc-static.sh

# Mock git describe for reproducible builds (PRoot embeds git version info)
echo "🔧 Setting up git mock for reproducible PRoot builds..."
mkdir -p /mock
cat > /mock/git << 'EOF'
#!/bin/bash
if [[ "$*" == *"describe"* ]]; then
    echo "v0.15_release"  # Fixed version matching PROOT_V
else
    exec /usr/bin/git "$@"
fi
EOF
chmod +x /mock/git
export PATH="/mock:$PATH"

# Apply reproducible build patch to make-proot-for-apk.sh
echo "🔧 Applying reproducible build patch to make-proot-for-apk.sh..."
sed -i 's/export CFLAGS="-I$STATIC_ROOT\/include -Werror=implicit-function-declaration"/export CFLAGS="$CFLAGS -I$STATIC_ROOT\/include -Werror=implicit-function-declaration"/' make-proot-for-apk.sh

./make-proot-for-apk.sh

# Copy results to Android JNI libs
echo "📦 Copying PRoot binaries to Android JNI libs..."

# Find the built APK root directory
APK_ROOT_DIR=$(find "$PROOT_BUILD_DIR/build" -name "root-apk" -type d | head -1)

if [ -z "$APK_ROOT_DIR" ]; then
  echo "ERROR: APK root directory not found"
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

# Also copy any loader libraries if they exist
cp "$APK_ROOT_DIR/bin/libproot-loader.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot-loader.so not found"
cp "$APK_ROOT_DIR/bin/libproot-loader32.so" "$ARM64_DIR/" 2>/dev/null || echo "libproot-loader32.so not found"

# Rename files to match app expectations
echo "Renaming files for app compatibility..."
cd "$ARM64_DIR"
mv libproot.so libdata_proot.so 2>/dev/null || echo "libproot.so not renamed"
mv libproot-loader.so libdata_loader.so 2>/dev/null || echo "libproot-loader.so not renamed"
mv libproot-loader32.so libdata_loader32.so 2>/dev/null || echo "libproot-loader32.so not renamed"

# Strip build metadata for reproducible builds
echo "Stripping build metadata..."
STRIP_TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
for file in *.so; do
  if [ -f "$file" ]; then
    "$STRIP_TOOL" --strip-all "$file" 2>/dev/null || echo "Could not strip $file"
  fi
done

echo "✅ PRoot build completed successfully"
echo "Output files in $ARM64_DIR:"
ls -la "$ARM64_DIR/"

