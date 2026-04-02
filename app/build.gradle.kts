import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.player.demo"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.player.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        // 发版时改 versionName / versionCode；APK 输出固定为 AudioPlayer.apk
        versionName = "1.0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

android.applicationVariants.configureEach {
    outputs.configureEach {
        (this as ApkVariantOutputImpl).outputFileName = "AudioPlayer.apk"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

// 构建时自动将项目根目录的 Shape_of_You.mp3 复制到 assets（文件名用小写以满足资源命名规则）
tasks.register("copyBuiltInAudio") {
    val rootMp3 = rootProject.file("Shape_of_You.mp3")
    val dest = file("src/main/assets/shape_of_you.mp3")
    if (rootMp3.exists()) {
        dest.parentFile?.mkdirs()
        rootMp3.copyTo(dest, overwrite = true)
        println("Copied Shape_of_You.mp3 to assets as shape_of_you.mp3")
    }
}
tasks.named("preBuild").configure { dependsOn("copyBuiltInAudio") }
