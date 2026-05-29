#!/bin/bash
# Build EasyTier native libraries for Android
# Requires: Rust, Android NDK, cargo-ndk
#
# Usage:
#   1. Install Rust: https://rustup.rs/
#   2. Install Android NDK via Android Studio or sdkmanager
#   3. Set ANDROID_NDK_ROOT environment variable
#   4. Install cargo-ndk: cargo install cargo-ndk
#   5. Add Rust targets: rustup target add aarch64-linux-android armv7-linux-androideabi
#   6. Run this script: ./build-native.sh

set -e

EASYTIER_REPO="https://github.com/EasyTier/EasyTier.git"
EASYTIER_BRANCH="main"
BUILD_DIR="easytier-build"
JNILIBS_DIR="app/src/main/jniLibs"

# Clone EasyTier if not exists
if [ ! -d "$BUILD_DIR" ]; then
    echo "Cloning EasyTier repository..."
    git clone --depth 1 --branch "$EASYTIER_BRANCH" "$EASYTIER_REPO" "$BUILD_DIR"
fi

cd "$BUILD_DIR/easytier-contrib/easytier-android-jni"

# Build for arm64-v8a (most common)
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a build --release 2>&1

# Copy .so files
mkdir -p "../../$JNILIBS_DIR/arm64-v8a"
cp target/aarch64-linux-android/release/libeasytier_android_jni.so "../../$JNILIBS_DIR/arm64-v8a/"
cp target/aarch64-linux-android/release/libeasytier_ffi.so "../../$JNILIBS_DIR/arm64-v8a/" 2>/dev/null || true

echo "Done! Native libraries copied to $JNILIBS_DIR/arm64-v8a/"
echo ""
echo "To build for other ABIs, edit this script and uncomment the desired targets."
