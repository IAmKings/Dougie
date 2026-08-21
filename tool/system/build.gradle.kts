import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

fun androidSdkDir(): String {
    val local = rootProject.file("local.properties")
    if (local.isFile) {
        local.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("sdk.dir=")) {
                return trimmed.substringAfter("=")
                    .replace("\\:", ":")
                    .replace("\\\\", File.separator)
            }
        }
    }
    return System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: ""
}

val ndkReady = file("${androidSdkDir()}/ndk/27.2.12479018/source.properties").isFile
val sherpaJniDir = layout.buildDirectory.dir("sherpa-jni/jniLibs")

android {
    namespace = "com.dougie.tool.system"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    sourceSets.getByName("main") {
        jniLibs.srcDir(sherpaJniDir)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

if (ndkReady) {
    android {
        ndkVersion = "27.2.12479018"
        defaultConfig {
            externalNativeBuild {
                cmake {
                    arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                }
            }
        }
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }
}

tasks.register("fetchSherpaJni") {
    val outDir = sherpaJniDir.get().asFile
    outputs.dir(outDir)
    doLast {
        val abiDir = outDir.resolve("arm64-v8a")
        val jniSo = abiDir.resolve("libsherpa-onnx-jni.so")
        if (jniSo.isFile && jniSo.length() > 1_000_000L) return@doLast
        abiDir.mkdirs()
        val cache = layout.buildDirectory.get().asFile.resolve("sherpa-jni")
        cache.mkdirs()
        val archive = cache.resolve("sherpa-onnx-android.tar.bz2")
        if (!archive.isFile || archive.length() < 1_000_000L) {
            URI(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/" +
                    "sherpa-onnx-v1.13.4-android-static-link-onnxruntime.tar.bz2",
            ).toURL().openStream().use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val extract = cache.resolve("extract")
        extract.deleteRecursively()
        extract.mkdirs()
        exec {
            commandLine("tar", "-xjf", archive.absolutePath, "-C", extract.absolutePath)
        }
        val found = extract.walkTopDown().firstOrNull { file ->
            file.name == "libsherpa-onnx-jni.so" && file.path.contains("arm64-v8a")
        } ?: error("arm64-v8a libsherpa-onnx-jni.so missing from sherpa archive")
        found.copyTo(jniSo, overwrite = true)
        extract.walkTopDown().firstOrNull { file ->
            file.name == "libonnxruntime.so" && file.path.contains("arm64-v8a")
        }?.copyTo(abiDir.resolve("libonnxruntime.so"), overwrite = true)
    }
}

tasks.named("preBuild").configure {
    dependsOn("fetchSherpaJni")
}
afterEvaluate {
    tasks.matching { task -> task.name.contains("JniLibFolders") }.configureEach {
        dependsOn("fetchSherpaJni")
    }
}

dependencies {
    api(project(":core:tool"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
}
