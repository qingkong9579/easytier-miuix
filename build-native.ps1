# Build EasyTier native library for Android (Windows / PowerShell)
#
# Mirrors build-native.sh for machines without a bash shell.
# Requires: Rust (rustup), Android NDK, cargo-ndk, protoc.
#
# Usage:
#   pwsh -File ./build-native.ps1                 # arm64-v8a (default)
#   pwsh -File ./build-native.ps1 -Abi x86_64     # extra ABI for emulator
#
# 自上游 #2451 (portable core 与 native runtime 分离) 起，
# libeasytier_android_jni.so 静态链接完整核心，不再需要单独的 libeasytier_ffi.so。

[CmdletBinding()]
param(
    [ValidateSet('arm64-v8a', 'x86_64')]
    [string[]] $Abi = @('arm64-v8a'),

    [string] $NdkVersion = '28.0.13004108',
    [string] $ProtocPath = 'C:\tools\protoc\bin\protoc.exe',
    [string] $EasyTierRepo = 'https://github.com/EasyTier/EasyTier.git',
    [string] $EasyTierBranch = 'main',

    # Used only when github.com is unreachable (see the mirror fallback below).
    [string] $GithubMirror = 'https://ghproxy.net/',

    # Optional scratch directory for the C/C++ build scripts (`ring`, `aws-lc-sys`
    # and friends invoke clang, which writes temporary files). Point this at a
    # writable path when the default %TEMP% is restricted, e.g. inside a sandbox:
    #   pwsh -File ./build-native.ps1 -TempDir "$PWD\build\tmp"
    [string] $TempDir
)

$ErrorActionPreference = 'Stop'

$repoRoot    = $PSScriptRoot
$buildDir    = Join-Path $repoRoot 'easytier-build'
$jniLibsDir  = Join-Path $repoRoot 'app\src\main\jniLibs'
$jniCrateDir = Join-Path $buildDir 'easytier-contrib\easytier-android-jni'

$targetMap = @{
    'arm64-v8a' = 'aarch64-linux-android'
    'x86_64'    = 'x86_64-linux-android'
}

# --- toolchain ---------------------------------------------------------------

$cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
if (Test-Path $cargoBin) { $env:Path = "$cargoBin;$env:Path" }

foreach ($tool in @('cargo', 'rustup')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool not found on PATH. Install Rust from https://rustup.rs/"
    }
}

if (-not (Get-Command 'cargo-ndk' -ErrorAction SilentlyContinue)) {
    cargo ndk --version *> $null
    if ($LASTEXITCODE -ne 0) { throw 'cargo-ndk not installed. Run: cargo install cargo-ndk' }
}

# --- NDK / protoc ------------------------------------------------------------

$ndkRoot = $env:ANDROID_NDK_ROOT
if (-not $ndkRoot) { $ndkRoot = $env:ANDROID_NDK_HOME }
if (-not $ndkRoot) { $ndkRoot = $env:NDK_HOME }
if (-not $ndkRoot) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $ndkRoot = Join-Path $sdkRoot "ndk\$NdkVersion"
}
if (-not (Test-Path $ndkRoot)) { throw "Android NDK not found at: $ndkRoot" }
$env:ANDROID_NDK_ROOT = $ndkRoot
$env:ANDROID_NDK_HOME = $ndkRoot

if (-not $env:PROTOC) {
    if (-not (Test-Path $ProtocPath)) { throw "protoc not found at: $ProtocPath (set `$env:PROTOC)" }
    $env:PROTOC = $ProtocPath
}

# cargo's bundled libgit2 can fail TLS on Windows (schannel: "no credentials in
# client certificate") even when the `git` CLI works. Upstream pins `kcp-sys` to
# a git rev, so let cargo shell out to the same git that works.
$env:CARGO_NET_GIT_FETCH_WITH_CLI = 'true'

# github.com is intermittently unreachable from some networks (TCP 443 blocked
# while codeload/api still answer). When the direct route is down, rewrite
# github.com URLs to a mirror for the git subprocesses cargo spawns. Injected
# through the environment so the user's global git config stays untouched.
git ls-remote --exit-code https://github.com/EasyTier/EasyTier.git HEAD *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "github.com unreachable - routing git through $GithubMirror" -ForegroundColor Yellow
    $env:GIT_CONFIG_COUNT = '1'
    $env:GIT_CONFIG_KEY_0 = "url.${GithubMirror}https://github.com/.insteadOf"
    $env:GIT_CONFIG_VALUE_0 = 'https://github.com/'
}

Write-Host "NDK    : $ndkRoot"
Write-Host "protoc : $env:PROTOC"

if ($TempDir) {
    New-Item -ItemType Directory -Force -Path $TempDir | Out-Null
    $env:TEMP = $TempDir
    $env:TMP = $TempDir
    $env:TMPDIR = $TempDir
    Write-Host "temp   : $TempDir"
}

# --- source ------------------------------------------------------------------

if (-not (Test-Path (Join-Path $buildDir '.git'))) {
    Write-Host "Cloning EasyTier ($EasyTierBranch)..."
    git clone --depth 1 --branch $EasyTierBranch $EasyTierRepo $buildDir
}
Write-Host "Core   : $(git -C $buildDir log --oneline -1)"

# --- build -------------------------------------------------------------------

foreach ($abiName in $Abi) {
    $rustTarget = $targetMap[$abiName]
    if (-not $rustTarget) { throw "Unsupported ABI: $abiName" }

    rustup target add $rustTarget
    Write-Host "`nBuilding $abiName ($rustTarget)..." -ForegroundColor Cyan

    Push-Location $jniCrateDir
    try {
        cargo ndk -t $abiName build --release
        if ($LASTEXITCODE -ne 0) { throw "cargo ndk failed for $abiName" }
    }
    finally {
        Pop-Location
    }

    $artifact = Join-Path $buildDir "target\$rustTarget\release\libeasytier_android_jni.so"
    if (-not (Test-Path $artifact)) { throw "Build artifact missing: $artifact" }

    $destDir = Join-Path $jniLibsDir $abiName
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item $artifact $destDir -Force
    Remove-Item (Join-Path $destDir 'libeasytier_ffi.so') -ErrorAction SilentlyContinue

    $so = Get-Item (Join-Path $destDir 'libeasytier_android_jni.so')
    Write-Host ("Copied -> {0} ({1:N0} bytes)" -f $so.FullName, $so.Length) -ForegroundColor Green
}

Write-Host "`nDone." -ForegroundColor Green
