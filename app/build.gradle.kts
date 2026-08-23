import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun localProp(key: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return ""
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.getProperty(key).orEmpty().trim()
}

fun quotedBuildConfig(key: String): String {
    val escaped = localProp(key)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.dougie.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.dougie.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Optional HTTPS + SHA-256 in local.properties (gitignored). Blank → OfficialModelCatalog.standard() defaults.
        listOf(
            "ASR_MODEL_URL" to "dougie.model.asr.url",
            "ASR_MODEL_SHA256" to "dougie.model.asr.sha256",
            "ASR_TOKENS_URL" to "dougie.model.asr.tokens.url",
            "ASR_TOKENS_SHA256" to "dougie.model.asr.tokens.sha256",
            "TTS_MODEL_URL" to "dougie.model.tts.url",
            "TTS_MODEL_SHA256" to "dougie.model.tts.sha256",
            "TTS_TOKENS_URL" to "dougie.model.tts.tokens.url",
            "TTS_TOKENS_SHA256" to "dougie.model.tts.tokens.sha256",
            "TTS_LEXICON_URL" to "dougie.model.tts.lexicon.url",
            "TTS_LEXICON_SHA256" to "dougie.model.tts.lexicon.sha256",
            "INTENT_MODEL_URL" to "dougie.model.intent.url",
            "INTENT_MODEL_SHA256" to "dougie.model.intent.sha256",
            "INTENT_TOKENIZER_URL" to "dougie.model.intent.tokenizer.url",
            "INTENT_TOKENIZER_SHA256" to "dougie.model.intent.tokenizer.sha256",
            "INTENT_LABELS_URL" to "dougie.model.intent.labels.url",
            "INTENT_LABELS_SHA256" to "dougie.model.intent.labels.sha256",
        ).forEach { (field, prop) ->
            buildConfigField("String", field, quotedBuildConfig(prop))
        }
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
    implementation(project(":feature:debug"))
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
    implementation(libs.androidx.documentfile)
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

        val playApk = flavorDebugApk("play")
        apkEntryNames(playApk).forEach { name ->
            check(!name.contains("models/asr")) {
                "play APK leaked models/asr: $name in $playApk"
            }
            check(!name.contains("models/tts")) {
                "play APK leaked models/tts: $name in $playApk"
            }
            check(!name.endsWith(".onnx")) {
                "play APK leaked .onnx: $name in $playApk"
            }
            checkNoIntentModel(name, playApk)
        }
        val sideloadApk = flavorDebugApk("sideload")
        apkEntryNames(sideloadApk).forEach { name ->
            checkNoIntentModel(name, sideloadApk)
        }
    }
}

fun flavorDebugApk(flavor: String): File {
    val apk = layout.buildDirectory.get().asFile
        .resolve("outputs/apk/$flavor/debug/app-$flavor-debug.apk")
    check(apk.isFile) { "Missing $flavor debug APK at ${apk.absolutePath}" }
    return apk
}

fun apkEntryNames(apk: File): List<String> {
    return ZipFile(apk).use { zip ->
        zip.entries().asSequence().map { it.name.replace('\\', '/') }.toList()
    }
}

fun checkNoIntentModel(name: String, apk: File) {
    check(!name.contains("models/intent")) {
        "APK leaked models/intent: $name in $apk"
    }
    check(!name.endsWith(".gguf")) {
        "APK leaked .gguf: $name in $apk"
    }
}
