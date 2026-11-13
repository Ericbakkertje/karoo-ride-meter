import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: keystore.properties (not in git) points to the release key, kept outside the repo.
// Without it, release builds are left unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.karooridemeter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.karooridemeter"
        minSdk = 23        // SDK sample minimum; Karoo 3 runs Android 12L (API 32)
        targetSdk = 34
        versionCode = 9
        versionName = "1.4.1"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
