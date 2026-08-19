import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("VERSION_CODE").get().toInt()

android {
    namespace = "dev.todor.fassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.todor.fassistant"
        minSdk = 23
        // Deliberately low: most background restrictions are gated on the level an app targets,
        // and nothing here ships on Play. See README for what this buys.
        targetSdk = 25
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("local") {
            storeFile = rootProject.file("keystore/fassistant.jks")
            storePassword = "fassistant"
            keyAlias = "fassistant"
            keyPassword = "fassistant"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("local")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("local")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets.getByName("main").kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.register<Copy>("dist") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "fassistant-$appVersionName-$appVersionCode.apk" }
}
