import org.gradle.api.JavaVersion

object Build {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 23
    const val TARGET_SDK = 36
    val jvmTarget = JavaVersion.VERSION_11
}
