pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "aws-sdk-android-ktx"
include(":aws-sdk-android-core-ktx")
include(":aws-sdk-android-appsync-ktx")
include(":aws-sdk-android-iot-ktx")
include(":sample")
