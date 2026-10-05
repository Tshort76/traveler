import java.util.Properties

// Release signing is optional: without keystore.properties the release APK is
// unsigned and everything else still builds. docs/BUILD.md covers making a key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.tlong.traveler"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.tlong.traveler"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // MapLibre ships native code per ABI; phones and the Apple-silicon emulator are arm64.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 off: a stripped serializer fails silently at import time, not at build time.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    // The example trips and the assistant instructions ship inside the app, so "Try the example trip"
    // and "Copy instructions" work offline.
    sourceSets["main"].assets.srcDirs("src/main/assets", "$rootDir/schema/examples", "$rootDir/prompts")

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// The JVM tests read the shared fixtures in ../schema; declare them so Gradle reruns the
// tests when a fixture changes instead of reporting a stale green.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("schema")).withPropertyName("tripFixtures")
    systemProperty("traveler.schemaDir", rootProject.file("schema").absolutePath)
    // Date labels follow the default locale; pin it so the change-summary tests read the same everywhere.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    testImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.room.testing)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
