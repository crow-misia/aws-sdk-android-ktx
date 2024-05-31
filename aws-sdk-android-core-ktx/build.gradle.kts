import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

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
        isCoreLibraryDesugaringEnabled = true
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
        freeCompilerArgs.addAll("-Xjsr305=strict")
        javaParameters.set(true)
        jvmTarget.set(JvmTarget.JVM_11)
        apiVersion.set(KotlinVersion.fromVersion(Build.KOTLIN_API_VERSION))
        languageVersion.set(KotlinVersion.fromVersion(Build.KOTLIN_LANGUAGE_VERSION))
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)

    // aws sdk android
    api(libs.aws.android.sdk.core)

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

val customDokkaTask by tasks.creating(DokkaTask::class) {
    dokkaSourceSets.getByName("main") {
        noAndroidSdkLink.set(false)
    }
    dependencies {
        plugins(libs.dokka.javadoc.plugin)
    }
    inputs.dir("src/main/java")
    outputDirectory.set(layout.buildDirectory.dir("javadoc"))
}

val javadocJar by tasks.creating(Jar::class) {
    dependsOn(customDokkaTask)
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles JavaDoc JAR"
    archiveClassifier.set("javadoc")
    from(customDokkaTask.outputDirectory)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>(mavenName) {
                from(components["release"])

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
                    name.set(mavenName)
                    description.set(Maven.DESC)
                    url.set(Maven.SITE_URL)

                    scm {
                        val scmUrl = "scm:git:${Maven.GIT_URL}"
                        connection.set(scmUrl)
                        developerConnection.set(scmUrl)
                        url.set(Maven.GIT_URL)
                        tag.set("HEAD")
                    }

                    developers {
                        developer {
                            id.set(Maven.DEVELOPER_ID)
                            name.set(Maven.DEVELOPER_NAME)
                            email.set(Maven.DEVELOPER_EMAIL)
                            roles.set(Maven.developerRoles)
                            timezone.set(Maven.DEVELOPER_TIMEZONE)
                        }
                    }

                    licenses {
                        license {
                            name.set(Maven.LICENSE_NAME)
                            url.set(Maven.LICENSE_URL)
                            distribution.set(Maven.LICENSE_DIST)
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
        sign(publishing.publications.getByName(mavenName))
    }
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    config.setFrom(files("$rootDir/config/detekt.yml"))
}
