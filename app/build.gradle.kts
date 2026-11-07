plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.karooridemeter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.karooridemeter"
        minSdk = 23        // SDK sample minimum; Karoo 3 runs Android 12L (API 32)
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Official Hammerhead Karoo Extension SDK (latest release: 1.1.9)
    implementation("io.hammerhead:karoo-ext:1.1.9")

    // Mac-side tests: plain JUnit for zone logic, Robolectric (native graphics) to render the Ride Meter to PNG
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
