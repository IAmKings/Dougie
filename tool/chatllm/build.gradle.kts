plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dougie.tool.chatllm"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val compileLitertlmStubs =
    tasks.register<JavaCompile>("compileLitertlmStubs") {
        source = fileTree("src/stub/java")
        classpath = files()
        destinationDirectory.set(layout.buildDirectory.dir("litertlm-stubs"))
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

val litertlmStubJar =
    tasks.register<Jar>("litertlmStubJar") {
        dependsOn(compileLitertlmStubs)
        from(compileLitertlmStubs.map { it.destinationDirectory })
        archiveFileName.set("litertlm-stubs.jar")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }

dependencies {
    // AAR is class file 65; stubs are Java 17 so CI javac 17 can compile. Runtime is the real AAR.
    compileOnly(files(litertlmStubJar.map { it.archiveFile }))
    runtimeOnly(libs.litertlm.android) {
        exclude(group = "org.jetbrains.kotlin")
    }
}
