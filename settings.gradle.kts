pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "Dougie"

include(
    ":app",
    ":core:model",
    ":core:llm",
    ":core:tool",
    ":core:runtime",
    ":core:memory",
    ":tool:system",
    ":tool:accessibility",
    ":data:preferences",
    ":data:memory",
    ":data:tasks",
    ":feature:chat",
    ":feature:settings",
    ":feature:memory",
    ":feature:history",
    ":feature:debug",
    ":feature:permissions",
)
