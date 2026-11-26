pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// The Android app needs the Android SDK. Machines without it (some CI or cloud
// workspaces) still build and test the plain-Kotlin core module.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        if (hasAndroidSdk) {
            google {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                }
            }
        }
    }
}

rootProject.name = "StitchyAid"
include(":core")
if (hasAndroidSdk) include(":app")
