import org.gradle.api.JavaVersion

object Build {
    const val COMPILE_SDK = 34
    const val MIN_SDK = 23
    val sourceCompatibility = JavaVersion.VERSION_1_8
    val targetCompatibility = JavaVersion.VERSION_1_8
}
