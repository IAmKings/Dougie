plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dougie.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dougie.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "channel"
    productFlavors {
        create("play") {
            dimension = "channel"
            isDefault = true
            applicationId = "com.dougie.app"
            buildConfigField("boolean", "IS_SIDELOAD", "false")
        }
        create("sideload") {
            dimension = "channel"
            applicationIdSuffix = ".sideload"
            buildConfigField("boolean", "IS_SIDELOAD", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:history"))
    implementation(project(":feature:permissions"))
    implementation(project(":data:preferences"))
    implementation(project(":data:memory"))
    implementation(project(":data:tasks"))
    implementation(project(":tool:system"))
    add("sideloadImplementation", project(":tool:accessibility"))
    implementation(project(":core:runtime"))
    implementation(project(":core:llm"))
    implementation(project(":core:tool"))
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}

fun mergedManifestFor(variantDirName: String): File {
    val root = layout.buildDirectory.get().asFile.resolve("intermediates")
    val matches = root.walkTopDown()
        .filter { file ->
            file.name == "AndroidManifest.xml" &&
                file.path.contains(variantDirName) &&
                file.path.contains("merged_manifest")
        }
        .toList()
    check(matches.isNotEmpty()) {
        "No merged manifest for $variantDirName under ${root.absolutePath}"
    }
    return matches.maxBy { it.lastModified() }
}

tasks.register("checkChannelLeak") {
    dependsOn("assemblePlayDebug", "assembleSideloadDebug")
    doLast {
        val playManifestFile = mergedManifestFor("playDebug")
        val playManifest = playManifestFile.readText()
        listOf("AccessibilityService", "BIND_ACCESSIBILITY_SERVICE", "TapSwipeTool").forEach { needle ->
            check(!playManifest.contains(needle)) {
                "play merged manifest leaked $needle in $playManifestFile"
            }
        }
        val sideloadManifest = mergedManifestFor("sideloadDebug").readText()
        check(sideloadManifest.contains("AccessibilityService")) {
            "sideload merged manifest missing AccessibilityService"
        }
        check(sideloadManifest.contains("BIND_ACCESSIBILITY_SERVICE")) {
            "sideload merged manifest missing BIND_ACCESSIBILITY_SERVICE"
        }
        check(Regex("""android:name="[^"]*DougieAccessibilityService"[^>]*android:exported="false"""").containsMatchIn(sideloadManifest.replace("\n", " "))) {
            "sideload DougieAccessibilityService must be exported=false"
        }
        val playClasspath = configurations.getByName("playDebugRuntimeClasspath")
        val leaked = playClasspath.incoming.resolutionResult.allComponents.any { component ->
            component.id.displayName.contains("tool:accessibility")
        }
        check(!leaked) { "playDebugRuntimeClasspath includes :tool:accessibility" }
    }
}
