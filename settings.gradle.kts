pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
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
    ":feature:permissions",
)
