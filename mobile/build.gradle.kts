plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.carlb.split.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carlb.split"
        minSdk = 29
        targetSdk = 36
        // Must increase on every published build or devices reject the upgrade.
        versionCode = 4
        versionName = "0.3.0"
    }

    signingConfigs {
        // Must be the SAME key as :wear -- both apps share an applicationId, so
        // a mismatch would make them uninstallable alongside each other. See
        // the note in wear/build.gradle.kts: this key is deliberately public.
        getByName("debug") {
            storeFile = rootProject.file("signing/split-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        // VERSION_NAME, so the updater can compare against itself.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.tooling.preview)
    implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(libs.graphics.shapes)
    debugImplementation(libs.compose.tooling)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.datastore.preferences)
}
