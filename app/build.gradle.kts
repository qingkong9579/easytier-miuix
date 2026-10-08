import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

/**
 * 内置 EasyTier 核心版本，直接读 vendored 检出里的 `[package] version`。
 *
 * 手写常量会和预编译的 libeasytier_android_jni.so 漂移（同步内核后忘了改），
 * 所以这里让构建时去读权威来源；easytier-build 不存在时（干净克隆）降级为 unknown。
 */
val vendoredCoreVersion: String = run {
    val manifest = rootProject.file("easytier-build/easytier/Cargo.toml")
    if (!manifest.exists()) {
        "unknown"
    } else {
        manifest.readText()
            .substringAfter("[package]", "")
            .substringBefore("\n[")
            .let { pkg -> Regex("""(?m)^\s*version\s*=\s*"([^"]+)"""").find(pkg)?.groupValues?.get(1) }
            ?: "unknown"
    }
}

android {
    namespace = "top.easytier.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.easytier.miuix"
        minSdk = 32
        targetSdk = 37
        versionCode = 13
        versionName = "1.3.0"
        buildConfigField("String", "CORE_VERSION", "\"$vendoredCoreVersion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // miuix
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.21")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    // MaterialKolor for dynamic color scheme
    implementation("com.materialkolor:material-color-utilities:4.1.1")
}
