#!/bin/bash

set -eo pipefail

RESTIC_VERSION=0.18.1
RCLONE_VERSION=1.68.2
# Pin specific versions from Termux repositories for reproducible builds
# These versions MUST match what's available in the Termux repository
# Format: proot uses x.x.x-xx, libtalloc uses x.x.x (check repository for exact format)
PROOT_VERSION=5.1.107-67
LIBTALLOC_VERSION=2.4.3

# Function to verify SHA256 checksum
verifySha256() {
  local file="$1"
  local expected_hash="$2"
  
  if [ -z "$expected_hash" ]; then
    echo "Warning: No checksum provided for $file, skipping verification"
    return 0
  fi
  
  local actual_hash
  if command -v sha256sum &> /dev/null; then
    actual_hash=$(sha256sum "$file" | awk '{print $1}')
  elif command -v shasum &> /dev/null; then
    actual_hash=$(shasum -a 256 "$file" | awk '{print $1}')
  else
    echo "Warning: Neither sha256sum nor shasum found, skipping checksum verification"
    return 0
  fi
  
  if [ "$actual_hash" != "$expected_hash" ]; then
    echo "ERROR: Checksum mismatch for $file"
    echo "  Expected: $expected_hash"
    echo "  Got:      $actual_hash"
    exit 1
  fi
  
  echo "✓ Checksum verified for $file"
}

unpackDebDataFromUrl() {
  local url="$1"
  local checksum="$2"
  shift 2
  local tmpdir
  tmpdir="$(mktemp -d)"
  pushd "$tmpdir"
  curl -sSfLo package.deb "$url"
  verifySha256 "package.deb" "$checksum"
  ar -x package.deb
  xz -dc data.tar.xz | tar -x
  "$@"
  popd
  rm -Rf "$tmpdir"
}

downloadBinaries() {
  local resticArch="$1"
  local packageArch="$2"
  local androidArch="$3"
  local rcloneArch="$4"
  local resticChecksum="$5"
  local rcloneChecksum="$6"
  local prootChecksum="$7"
  local liballocChecksum="$8"
  
  local target="$(pwd)/app/src/main/jniLibs/$androidArch"
  mkdir -p "$target"

  # Download and verify restic
  local resticFile="restic_${RESTIC_VERSION}_linux_${resticArch}.bz2"
  echo "$resticFile"
  local tmpdir
  tmpdir="$(mktemp -d)"
  pushd "$tmpdir"
  curl -sSfLo "$resticFile" "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/$resticFile"
  verifySha256 "$resticFile" "$resticChecksum"
  bzip2 -dc "$resticFile" > "$target/libdata_restic.so"
  popd
  rm -Rf "$tmpdir"
  
  # Download and verify rclone
  local rcloneFile="rclone-v${RCLONE_VERSION}-linux-${rcloneArch}.zip"
  echo "$rcloneFile"
  tmpdir="$(mktemp -d)"
  pushd "$tmpdir"
  curl -sSfLo rclone.zip "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/$rcloneFile"
  verifySha256 "rclone.zip" "$rcloneChecksum"
  unzip -q rclone.zip
  mv "rclone-v${RCLONE_VERSION}-linux-${rcloneArch}/rclone" "$target/libdata_rclone.so"
  popd
  rm -Rf "$tmpdir"
  
  # Use pinned version for reproducible builds
  local prootFile="proot_${PROOT_VERSION}_${packageArch}.deb"
  echo "$prootFile"
  unpackProot() {
    pushd data/data/com.termux/files/usr
    mv bin/proot "$target/libdata_proot.so"
    mv libexec/proot/loader "$target/libdata_loader.so"
    if [[ -f libexec/proot/loader32 ]]; then
      mv libexec/proot/loader32 "$target/libdata_loader32.so"
    fi
    popd
  }
  unpackDebDataFromUrl "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/$prootFile" "$prootChecksum" unpackProot
  
  # Use pinned version for reproducible builds
  local liballocFile="libtalloc_${LIBTALLOC_VERSION}_${packageArch}.deb"
  echo "$liballocFile"
  unpackLibtalloc() {
    pushd data/data/com.termux/files/usr
    mv "$(readlink -f lib/libtalloc.so.2)" "$target/libdata_libtalloc.so.2.so"
    popd
  }
  unpackDebDataFromUrl "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/$liballocFile" "$liballocChecksum" unpackLibtalloc
}

# SHA256 checksums for all external dependencies
# To regenerate checksums when updating versions, run: ./generate-checksums-all.sh

# RESTIC CHECKSUMS
RESTIC_ARM64_SHA256="87f53fddde38764095e9c058a3b31834052c37e5826d2acf34e18923c006bd45"
RESTIC_ARM_SHA256="1ead22ca3b123f11cd1ce74ba4079c324aa616efb25227c715471420a1c2a364"
RESTIC_AMD64_SHA256="680838f19d67151adba227e1570cdd8af12c19cf1735783ed1ba928bc41f363d"
RESTIC_386_SHA256="b6c4b3dc507ac8df840b5611ada36134fa2208a2941be3376096137712659f81"

# RCLONE CHECKSUMS
RCLONE_ARM64_SHA256="c6e9d4cf9c88b279f6ad80cd5675daebc068e404890fa7e191412c1bc7a4ac5f"
RCLONE_ARM_SHA256="88e187cbe7002fefa6b15fdc83dd6828d9f26d250ef028ce04e87205bc66de49"
RCLONE_AMD64_SHA256="0e6fa18051e67fc600d803a2dcb10ddedb092247fc6eee61be97f64ec080a13c"
RCLONE_386_SHA256="8654f19f572ac90c8cf712f3e212ee499b8e5e270e209753f3e82f0b44d9447d"

# PROOT CHECKSUMS (Termux)
PROOT_AARCH64_SHA256="c62c690c8dc87cbb1e7c6e74e8cf721b49fd2c74f4c3ca454fd6a6fddb178711"
PROOT_ARM_SHA256="e10a0c8391480bb85d8ae73eb62cea01e68b84f0ed7a2fdc92d7ebedb712f122"
PROOT_X86_64_SHA256="283e9b304721cc7bcb3f405dded742dd2c0fb1dde5f025209d9a3ef57a3dd200"
PROOT_I686_SHA256="b74ac3a0627458a41cc12929698bb422c1889cb64ac8a9211bb2e58fca89418d"

# LIBTALLOC CHECKSUMS (Termux)
LIBTALLOC_AARCH64_SHA256="ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da"
LIBTALLOC_ARM_SHA256="cd56f87007e487c8025fac2df2a27b2bc58102344040a527eaa6fa7527d18f9b"
LIBTALLOC_X86_64_SHA256="7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628"
LIBTALLOC_I686_SHA256="7b79f8b5e41d597940551ef9bd5a2fef7978f519300af8fc5c498d34a93f575a"

downloadBinaries arm64 aarch64 arm64-v8a arm64 "$RESTIC_ARM64_SHA256" "$RCLONE_ARM64_SHA256" "$PROOT_AARCH64_SHA256" "$LIBTALLOC_AARCH64_SHA256"
downloadBinaries arm arm armeabi-v7a arm-v7 "$RESTIC_ARM_SHA256" "$RCLONE_ARM_SHA256" "$PROOT_ARM_SHA256" "$LIBTALLOC_ARM_SHA256"
downloadBinaries amd64 x86_64 x86_64 amd64 "$RESTIC_AMD64_SHA256" "$RCLONE_AMD64_SHA256" "$PROOT_X86_64_SHA256" "$LIBTALLOC_X86_64_SHA256"
downloadBinaries 386 i686 x86 386 "$RESTIC_386_SHA256" "$RCLONE_386_SHA256" "$PROOT_I686_SHA256" "$LIBTALLOC_I686_SHA256"
