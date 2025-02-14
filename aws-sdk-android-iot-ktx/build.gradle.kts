import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.maven.publish)
    id("signing")
}

val artifactId = "aws-sdk-android-iot-ktx"

group = Maven.GROUP_ID
version = Maven.VERSION

android {
    namespace = "io.github.crow_misia.aws.iot"
    compileSdk = Build.COMPILE_SDK

    defaultConfig {
        minSdk = Build.MIN_SDK
        consumerProguardFiles("consumer-proguard-rules.pro")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        textReport = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
    }

    compileOptions {
        sourceCompatibility = Build.sourceCompatibility
        targetCompatibility = Build.targetCompatibility
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("/META-INF/LICENSE*")
            excludes.add("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
            it.testLogging {
                showStandardStreams = true
                events("passed", "skipped", "failed")
            }
        }
    }
}

kotlin {
    compilerOptions {
        javaParameters = true
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    api(project(":aws-sdk-android-core-ktx"))

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)

    // aws sdk android
    api(libs.aws.android.sdk.iot)

    // bouncy castle
    implementation(libs.bouncycastle.bcpkix)

    // okhttp3
    implementation(platform(libs.okhttp3.bom))
    implementation(libs.okhttp3.android)
    implementation(libs.okio)

    // serialization
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)

    // Unit testing
    testImplementation(platform(libs.kotlinx.coroutines.bom))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.ext.truth)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.truth)
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(
        variant = "release",
        publishJavadocJar = true,
        sourcesJar = true,
    ))

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    coordinates(Maven.GROUP_ID, artifactId, Maven.VERSION)

    pom {
        name = artifactId
        description = Maven.DESCRIPTION
        url = "https://github.com/${Maven.GITHUB_REPOSITORY}/"
        licenses {
            license {
                name = Maven.LICENSE_NAME
                url = Maven.LICENSE_URL
                distribution = Maven.LICENSE_DIST
            }
        }
        developers {
            developer {
                id = Maven.DEVELOPER_ID
                name = Maven.DEVELOPER_NAME
                email = Maven.DEVELOPER_EMAIL
            }
        }
        scm {
            url = "https://github.com/${Maven.GITHUB_REPOSITORY}/"
            connection = "scm:git:git://github.com/${Maven.GITHUB_REPOSITORY}.git"
            developerConnection = "scm:git:ssh://git@github.com/${Maven.GITHUB_REPOSITORY}.git"
        }
    }
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    config.from(rootDir.resolve("config/detekt.yml"))
}
