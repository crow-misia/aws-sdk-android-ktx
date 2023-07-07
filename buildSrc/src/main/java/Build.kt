import org.gradle.api.JavaVersion

object Build {
    const val compileSdk = 34
    const val minSdk = 23
    val sourceCompatibility = JavaVersion.VERSION_11
    val targetCompatibility = JavaVersion.VERSION_11
    const val kotlinApiVersion = "1.9"
    const val kotlinLanguageVersion = "1.9"
}
