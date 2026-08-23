import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.todor.fassistant.probe"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "dev.todor.fassistant.probe"
        minSdk = 23
        // Must match the app exactly, or the answers do not transfer.
        targetSdk = 25
        versionCode = 1
        versionName = "probe"
    }

    // Signed with the local debug key. This is a throwaway diagnostic, never upgraded in place.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    sourceSets.getByName("main").kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
