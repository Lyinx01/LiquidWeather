pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LiquidWeather"
include(":app")
// Liquid Glass 库（源码 vendor 版，Fork 自 QWEA0/Liquid-Glass-Android 并修复采样成环）
include(":liquidglass")
