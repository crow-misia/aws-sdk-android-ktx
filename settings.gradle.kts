plugins {
    id("de.fayard.refreshVersions") version "0.40.1"
}

rootProject.name = "aws-sdk-android-ktx"
include(":aws-sdk-android-core-ktx")
include(":aws-sdk-android-iot-ktx")
include(":sample")
