import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 从 local.properties 读取 DashScope 配置
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun localProp(key: String, default: String = ""): String =
    localProperties.getProperty(key, default)

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.wrongbook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wrongbook.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Debug builds can use the local key for development and testing.
            buildConfigField("String", "DASHSCOPE_API_KEY", buildConfigString(localProp("DASHSCOPE_API_KEY")))
            buildConfigField("String", "DASHSCOPE_BASE_URL", buildConfigString(localProp("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")))
            buildConfigField("String", "DASHSCOPE_MODEL", buildConfigString(localProp("DASHSCOPE_MODEL", "qwen3.6-plus")))
            buildConfigField("String", "SYNC_API_URL", buildConfigString(localProp("SYNC_API_URL")))
            buildConfigField("String", "SYNC_TOKEN", buildConfigString(localProp("SYNC_TOKEN")))
            buildConfigField("String", "SYNC_DEVICE_ID", buildConfigString(localProp("SYNC_DEVICE_ID", "android-main")))
        }

        release {
            isMinifyEnabled = false
            // Personal release builds use the local DashScope key.
            buildConfigField("String", "DASHSCOPE_API_KEY", buildConfigString(localProp("DASHSCOPE_API_KEY")))
            buildConfigField("String", "DASHSCOPE_BASE_URL", buildConfigString(localProp("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")))
            buildConfigField("String", "DASHSCOPE_MODEL", buildConfigString(localProp("DASHSCOPE_MODEL", "qwen3.6-plus")))
            buildConfigField("String", "SYNC_API_URL", buildConfigString(localProp("SYNC_API_URL")))
            buildConfigField("String", "SYNC_TOKEN", buildConfigString(localProp("SYNC_TOKEN")))
            buildConfigField("String", "SYNC_DEVICE_ID", buildConfigString(localProp("SYNC_DEVICE_ID", "android-main")))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp (DashScope API 调用)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // On-device OCR for photo-based question entry.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
