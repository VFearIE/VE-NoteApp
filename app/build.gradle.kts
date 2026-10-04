import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.noteVE"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noteVE"
        minSdk = 24
        targetSdk = 34
        versionCode = 212
        versionName = "2.1.2"
    }

    /**
     * 双版本分发（2026-10-03）：
     *
     *  | flavor  | 受众 | 保活方式 |
     *  |---------|------|---------|
     *  | `root`  | 已 root 用户 | 应用自身无特殊机制；由随附的 **Vector/LSPosed 模块** 让它免疫 force-stop |
     *  | `platform` | ROM 厂商 | 应用声明 `sharedUserId=android.uid.system`，**必须用平台密钥签名**；
     *  |         |      | 与系统时钟同级，无需任何保活手段 |
     *
     * 两者共用同一份源码，仅 manifest 占位符不同。
     * ★ 说明：`persistent` 与 `excludeFromRecents` 已弃用 —— 实测 force-stop（=划卡）会清空
     *   AlarmManager 闹钟并置 stopped，二者都挡不住，且 persistent 会让应用崩溃波及 system_server。
     */
    flavorDimensions += "dist"
    productFlavors {
        // root 版：应用侧无特殊声明，保活由随附的 Xposed 模块提供
        create("root") { dimension = "dist" }
        // platform 版：声明 system uid（需平台密钥签名 + src/platform/AndroidManifest.xml）
        create("platform") { dimension = "dist" }
    }

    buildTypes {
        release {
            // 商用预装建议开启 R8 混淆瘦身；首次构建若遇问题可临时改为 false。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 不签名：ROM 厂商用平台密钥自行签名
            signingConfig = null
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 关于页需要读取 BuildConfig.VERSION_NAME / FLAVOR
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    // 单元测试（纯 JVM，无需设备）
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
