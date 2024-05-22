import org.gradle.api.JavaVersion

object Build {
    const val COMPILE_SDK = 34
    const val MIN_SDK = 23
    const val KOTLIN_API_VERSION = "2.0"
    const val KOTLIN_LANGUAGE_VERSION = "2.0"
    val sourceCompatibility = JavaVersion.VERSION_11
    val targetCompatibility = JavaVersion.VERSION_11
}
