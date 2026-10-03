// Top-level build file where you can add configuration options common to all sub-projects/modules.
// DRouter Gradle 插件不发布到 Gradle 插件门户（无 marker artifact），
// 必须用 buildscript classpath 形式接入（官方文档：AGP 8.x 用 1.4.0）。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.github.didi:drouter-plugin:1.4.0")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}
