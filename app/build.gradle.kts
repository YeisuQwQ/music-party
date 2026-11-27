import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustOut = layout.buildDirectory.dir("generated/rustJni")
val rustDir = rootProject.file("core")
val targets = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "dev.yiqidi.mobile"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.yeisu.yiqidi"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
        ndk { abiFilters.addAll(targets) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    sourceSets["main"].jniLibs.srcDir(rustOut)
    packaging { jniLibs.useLegacyPackaging = false }
}

kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

val cargoAndroid by tasks.registering(Exec::class) {
    workingDir(rustDir)
    inputs.files(fileTree(rustDir) { include("Cargo.*", "src/**") })
    outputs.dir(rustOut)
    val sdk = android.sdkDirectory
    val ndk = sdk.resolve("ndk/${android.ndkVersion}")
    environment("ANDROID_NDK_HOME", ndk.absolutePath)
    environment("ANDROID_HOME", sdk.absolutePath)
    val cargo = if (System.getProperty("os.name").startsWith("Windows")) "cargo.exe" else "cargo"
    commandLine(listOf(cargo, "ndk", "--platform", "26") + targets.flatMap { listOf("-t", it) } +
        listOf("-o", rustOut.get().asFile.absolutePath, "build", "--release", "--locked", "--lib"))
}

tasks.matching { it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(cargoAndroid) }

dependencies {
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3:1.5.0-alpha15")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.2")
    implementation("androidx.media3:media3-session:1.9.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.netease.nimlib:basesdk:9.6.3")
    implementation("com.netease.nimlib:chatroom:9.6.3")
}