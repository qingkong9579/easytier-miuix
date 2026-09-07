#!/bin/bash
# Build EasyTier native library for Android
# Requires: Rust, Android NDK, cargo-ndk, protoc
#
# Usage:
#   1. Install Rust: https://rustup.rs/
#   2. Install Android NDK via Android Studio or sdkmanager
#   3. Set ANDROID_NDK_ROOT environment variable
#   4. Install cargo-ndk: cargo install cargo-ndk
#   5. Add Rust targets: rustup target add aarch64-linux-android
#   6. Install protoc and set PROTOC to its path (prost-build requirement)
#   7. Run this script: ./build-native.sh
#
# 自上游 #2451 (portable core 与 native runtime 分离) 起，
# libeasytier_android_jni.so 静态链接完整核心，不再需要单独的 libeasytier_ffi.so。

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

if [ -z "$PROTOC" ]; then
    echo "Error: PROTOC environment variable is not set (e.g. PROTOC=/path/to/protoc.exe)" >&2
    exit 1
fi

cd "$BUILD_DIR/easytier-contrib/easytier-android-jni"

# Build for arm64-v8a (most common)
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a build --release 2>&1

# Copy .so file
mkdir -p "../../$JNILIBS_DIR/arm64-v8a"
cp target/aarch64-linux-android/release/libeasytier_android_jni.so "../../$JNILIBS_DIR/arm64-v8a/"
rm -f "../../$JNILIBS_DIR/arm64-v8a/libeasytier_ffi.so"

echo "Done! Native library copied to $JNILIBS_DIR/arm64-v8a/"
echo ""
echo "To build for other ABIs, edit this script and uncomment the desired targets."
