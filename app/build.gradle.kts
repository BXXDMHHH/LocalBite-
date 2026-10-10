import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 本地签名配置：keystore.properties 不入库，缺失时 release 回退 debug 签名，
// 保证他人 clone 后也能直接构建；正式发布需自行配置该文件。
val releaseProps: Properties? =
    rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { Properties().apply { load(it) } }

android {
    namespace = "com.localbite.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.localbite.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("release") {
            storeFile = releaseProps?.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = releaseProps?.getProperty("storePassword")
            keyAlias = releaseProps?.getProperty("keyAlias")
            keyPassword = releaseProps?.getProperty("keyPassword")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseProps?.getProperty("storeFile") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.google.android.gms:play-services-analytics-impl:18.2.0")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
