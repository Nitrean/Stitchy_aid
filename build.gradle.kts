// Plugins are put on one shared classpath here so every module sees the same
// Kotlin plugin. The Android Gradle plugin is only added when an SDK exists.
buildscript {
    val kotlinVersion = "2.1.0"
    val agpVersion = "8.7.3"
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        rootProject.file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (hasAndroidSdk) google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (hasAndroidSdk) classpath("com.android.tools.build:gradle:$agpVersion")
    }
}
