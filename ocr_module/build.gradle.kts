plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.github.didi.drouter")
}

android {
    namespace = "com.android.batteryoptimization.ocr"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // .nb 模型文件不被压缩（PaddleLite 要求）
    aaptOptions {
        noCompress.addAll(listOf("nb"))
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // 仅依赖 api 模块（接口/数据）与 PaddleOCR 推理库，不直接依赖 :app
    implementation(project(":ocr_api"))
    implementation(project(":PaddleOCR4Android"))

    // DRouter 运行时（:ocr_api 已以 api 形式暴露，这里显式声明以稳妥）
    implementation("io.github.didi:drouter-api:2.4.6")
}
