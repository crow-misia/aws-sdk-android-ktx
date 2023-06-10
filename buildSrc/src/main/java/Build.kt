import org.gradle.api.JavaVersion

object Build {
    const val compileSdk = 33
    const val minSdk = 23
    val sourceCompatibility = JavaVersion.VERSION_11
    val targetCompatibility = JavaVersion.VERSION_11
    const val kotlinApiVersion = "1.8"
    const val kotlinLanguageVersion = "1.8"
}
