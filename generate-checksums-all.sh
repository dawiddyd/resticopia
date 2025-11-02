#!/bin/bash
#
# Helper script to generate SHA256 checksums for ALL external dependencies
# Run this when updating any version numbers
#

set -eo pipefail

# Get versions from download-binaries.sh
RESTIC_VERSION=$(grep "^RESTIC_VERSION=" download-binaries.sh | cut -d= -f2)
RCLONE_VERSION=$(grep "^RCLONE_VERSION=" download-binaries.sh | cut -d= -f2)
PROOT_VERSION=$(grep "^PROOT_VERSION=" download-binaries.sh | cut -d= -f2)
LIBTALLOC_VERSION=$(grep "^LIBTALLOC_VERSION=" download-binaries.sh | cut -d= -f2)

echo "Generating checksums for:"
echo "  restic: $RESTIC_VERSION"
echo "  rclone: $RCLONE_VERSION"
echo "  proot: $PROOT_VERSION"
echo "  libtalloc: $LIBTALLOC_VERSION"
echo ""

TMPDIR=$(mktemp -d)
cd "$TMPDIR"

generateChecksum() {
  local url="$1"
  local filename="$2"
  local varname="$3"
  
  echo "Downloading: $filename"
  if curl -sSfLo "$filename" "$url"; then
    if command -v sha256sum &> /dev/null; then
      hash=$(sha256sum "$filename" | awk '{print $1}')
    elif command -v shasum &> /dev/null; then
      hash=$(shasum -a 256 "$filename" | awk '{print $1}')
    else
      echo "Error: Neither sha256sum nor shasum found"
      exit 1
    fi
    echo "${varname}=\"${hash}\""
  else
    echo "Error: Failed to download $url"
    exit 1
  fi
  echo ""
}

generateTermuxChecksum() {
  local package="$1"
  local version="$2"
  local arch="$3"
  local varname="$4"
  
  local filename="${package}_${version}_${arch}.deb"
  
  # Termux uses different path prefixes for different packages
  local path_prefix
  if [[ "$package" == "libtalloc" ]]; then
    path_prefix="libt"
  else
    path_prefix="${package:0:1}"
  fi
  
  local url="https://packages.termux.dev/apt/termux-main/pool/main/${path_prefix}/${package}/${filename}"
  
  generateChecksum "$url" "$filename" "$varname"
}

echo "# Add these checksums to download-binaries.sh"
echo "#================================================="
echo ""
echo "# RESTIC CHECKSUMS"

generateChecksum "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/restic_${RESTIC_VERSION}_linux_arm64.bz2" \
  "restic_${RESTIC_VERSION}_linux_arm64.bz2" "RESTIC_ARM64_SHA256"

generateChecksum "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/restic_${RESTIC_VERSION}_linux_arm.bz2" \
  "restic_${RESTIC_VERSION}_linux_arm.bz2" "RESTIC_ARM_SHA256"

generateChecksum "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/restic_${RESTIC_VERSION}_linux_amd64.bz2" \
  "restic_${RESTIC_VERSION}_linux_amd64.bz2" "RESTIC_AMD64_SHA256"

generateChecksum "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/restic_${RESTIC_VERSION}_linux_386.bz2" \
  "restic_${RESTIC_VERSION}_linux_386.bz2" "RESTIC_386_SHA256"

echo ""
echo "# RCLONE CHECKSUMS"

generateChecksum "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-arm64.zip" \
  "rclone-v${RCLONE_VERSION}-linux-arm64.zip" "RCLONE_ARM64_SHA256"

generateChecksum "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-arm-v7.zip" \
  "rclone-v${RCLONE_VERSION}-linux-arm-v7.zip" "RCLONE_ARM_SHA256"

generateChecksum "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-amd64.zip" \
  "rclone-v${RCLONE_VERSION}-linux-amd64.zip" "RCLONE_AMD64_SHA256"

generateChecksum "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-386.zip" \
  "rclone-v${RCLONE_VERSION}-linux-386.zip" "RCLONE_386_SHA256"

echo ""
echo "# PROOT CHECKSUMS"

generateTermuxChecksum "proot" "$PROOT_VERSION" "aarch64" "PROOT_AARCH64_SHA256"
generateTermuxChecksum "proot" "$PROOT_VERSION" "arm" "PROOT_ARM_SHA256"
generateTermuxChecksum "proot" "$PROOT_VERSION" "x86_64" "PROOT_X86_64_SHA256"
generateTermuxChecksum "proot" "$PROOT_VERSION" "i686" "PROOT_I686_SHA256"

echo ""
echo "# LIBTALLOC CHECKSUMS"

generateTermuxChecksum "libtalloc" "$LIBTALLOC_VERSION" "aarch64" "LIBTALLOC_AARCH64_SHA256"
generateTermuxChecksum "libtalloc" "$LIBTALLOC_VERSION" "arm" "LIBTALLOC_ARM_SHA256"
generateTermuxChecksum "libtalloc" "$LIBTALLOC_VERSION" "x86_64" "LIBTALLOC_X86_64_SHA256"
generateTermuxChecksum "libtalloc" "$LIBTALLOC_VERSION" "i686" "LIBTALLOC_I686_SHA256"

cd - > /dev/null
rm -rf "$TMPDIR"

echo ""
echo "#================================================="
echo ""
echo "✓ Copy the above checksums to download-binaries.sh"

