plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.didi.drouter")
}

android {
    namespace = "com.android.batteryoptimization"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.android.batteryoptimization"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(rootProject.rootDir.parent + "/keystore/batteryoptimization.keystore")
            storePassword = "battery_goold"
            keyAlias = "batteryoptimization"
            keyPassword = "battery_goold"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Don't compress PaddleLite .nb model files (already compressed)
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Prevent compression of .nb model files in assets
    aaptOptions.noCompress.addAll(listOf("nb"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Network (Retrofit & OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // AMap Location SDK (高德定位)
    implementation("com.amap.api:location:6.4.7")

    // OCR 接口/数据（仅 api，不含实现；具体实现在独立 :ocr_module）
    implementation(project(":ocr_api"))

    // OCR 实现（可选）：useOcr=false 时 :app 不依赖 :ocr_module，仍可独立运行（OCR 降级关闭）
    val useOcr = (project.findProperty("useOcr") as? String)?.toBoolean() ?: true
    if (useOcr) {
        implementation(project(":ocr_module"))
    }

    // DRouter 运行时（:ocr_api 已以 api 暴露，这里显式声明）
    implementation("io.github.didi:drouter-api:2.4.6")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
