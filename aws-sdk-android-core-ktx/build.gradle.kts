import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    id("signing")
    id("maven-publish")
}

val mavenName = "aws-sdk-android-core-ktx"

group = Maven.GROUP_ID
version = Maven.VERSION

android {
    namespace = "io.github.crow_misia.aws.core"
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
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
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
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)

    // aws sdk android
    api(libs.aws.android.sdk.core)
    api(platform(libs.aws.smithy.bom))
    api(libs.aws.smithy.runtime)

    // okhttp3
    implementation(libs.okhttp3.android)
    implementation(libs.okio)

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

val dokkaJavadoc by tasks.getting(DokkaTask::class) {
    dokkaSourceSets.named("main") {
        noAndroidSdkLink = false
    }
    dependencies {
        plugins(libs.dokka.javadoc.plugin)
    }
    inputs.dir("src/main/java")
    outputDirectory = layout.buildDirectory.dir("javadoc").get().asFile
}

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles JavaDoc JAR"
    archiveClassifier = "javadoc"
    from(dokkaJavadoc.outputDirectory)
}

publishing {
    publications {
        register<MavenPublication>(mavenName) {
            afterEvaluate {
                from(components.named("release").get())
            }

            groupId = Maven.GROUP_ID
            artifactId = mavenName

            println("""
                |Creating maven publication
                |    Group: $groupId
                |    Artifact: $mavenName
                |    Version: $version
            """.trimMargin())

            artifact(javadocJar)

            pom {
                name = mavenName
                description = Maven.DESCRIPTION
                url = Maven.SITE_URL

                scm {
                    val scmUrl = "scm:git:${Maven.GIT_URL}"
                    connection = scmUrl
                    developerConnection = scmUrl
                    url = Maven.GIT_URL
                    tag = "HEAD"
                }

                developers {
                    developer {
                        id = Maven.DEVELOPER_ID
                        name = Maven.DEVELOPER_NAME
                        email = Maven.DEVELOPER_EMAIL
                        roles = Maven.developerRoles
                        timezone = Maven.DEVELOPER_TIMEZONE
                    }
                }

                licenses {
                    license {
                        name = Maven.LICENSE_NAME
                        url = Maven.LICENSE_URL
                        distribution = Maven.LICENSE_DIST
                    }
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
            url = if (Maven.VERSION.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = project.findProperty("sona.user") as String? ?: providers.environmentVariable("SONA_USER").orNull
                password = project.findProperty("sona.password") as String? ?: providers.environmentVariable("SONA_PASSWORD").orNull
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications.named(mavenName).get())
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    config.setFrom(rootDir.resolve("config/detekt.yml"))
}
