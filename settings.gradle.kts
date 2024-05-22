pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.5"
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
include(":aws-sdk-android-amplify-ktx")
include(":aws-sdk-android-appsync-ktx")
include(":aws-sdk-android-iot-ktx")
include(":sample")
