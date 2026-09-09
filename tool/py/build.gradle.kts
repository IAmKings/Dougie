plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.dougie.tool.py"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            // Sideload-only; Play never depends on this module. One ABI to keep the APK smaller.
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            install("numpy")
            install("pandas")
        }
    }
}

dependencies {
    api(project(":core:tool"))
    implementation(libs.kotlinx.serialization.json)
}
