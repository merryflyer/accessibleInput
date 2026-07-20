plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.batteryoptimization.ocr.api"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // DRouter 注解与运行时；以 api 形式暴露，依赖 :ocr_api 的模块可直接使用 @Service / 寻址
    api("io.github.didi:drouter-api:2.4.6")
}
