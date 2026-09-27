// Liquid Glass 库（源码 vendor 版，Fork 自 QWEA0/Liquid-Glass-Android v2.0.11-main）
//
// 与上游差异：
// - 移除 NDK/CMake 原生加速（NativeGauss / NativeChromatic* 的 JNI 部分），
//   改为纯 Kotlin 实现（本项目无 NDK 构建链；CPU 模糊走 AdvancedFastBlur）
// - BackdropCapture：所有层级采样都隐藏源子树中的其他玻璃（上游只在嵌套层隐藏），
//   修复「玻璃↔内容」RenderNode 引用成环导致的 RenderThread 光栅化无限递归闪退
//
// 上游使用 version catalog（libs.plugins.* / libs.androidx.*），本项目未引入该 catalog，
// 因此这里使用显式坐标。

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.liquidglass"
    // API 36：RuntimeColorFilter / RuntimeXfermode（AGSL 颜色滤镜与混合模式）
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    // compileOnly：仅 DialogBuilder / TabLayout 系用到，使用方未触及时不强制引入
    compileOnly("androidx.appcompat:appcompat:1.7.0")
    compileOnly("com.google.android.material:material:1.12.0")
}
