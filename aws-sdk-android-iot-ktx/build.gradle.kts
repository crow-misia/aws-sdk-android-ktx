import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URI

plugins {
    id("com.android.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.dokka")
    id("signing")
    id("maven-publish")
    kotlin("android")
}

val mavenName = "aws-sdk-android-iot-ktx"

group = Maven.groupId
version = Maven.version

android {
    buildToolsVersion = "33.0.2"
    compileSdk = 33

    defaultConfig {
        namespace = "io.github.crow_misia.aws.iot"
        minSdk = 23
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    lint {
        textReport = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
    }

    libraryVariants.all {
        generateBuildConfigProvider?.configure {
            enabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().all {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        javaParameters.set(true)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
    }
}

dependencies {
    api(project(":aws-sdk-android-core-ktx"))

    implementation(Kotlin.stdlib)
    implementation(KotlinX.coroutines.core)

    // aws sdk android
    api(libs.aws.android.sdk.iot)

    // okhttp3
    implementation(Square.okHttp3)

    // conscrypt
    implementation(libs.conscrypt.android)
}

val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(sourceSets.create("main").allSource)
}

val customDokkaTask by tasks.creating(DokkaTask::class) {
    dokkaSourceSets.getByName("main") {
        noAndroidSdkLink.set(false)
    }
    dependencies {
        plugins(libs.javadoc.plugin)
    }
    inputs.dir("src/main/java")
    outputDirectory.set(buildDir.resolve("javadoc"))
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
            create<MavenPublication>(mavenName) {
                from(components["release"])

                groupId = Maven.groupId
                artifactId = mavenName

                println("""
                    |Creating maven publication
                    |    Group: $groupId
                    |    Artifact: $mavenName
                    |    Version: $version
                """.trimMargin())

                artifact(sourcesJar)
                artifact(javadocJar)

                pom {
                    name.set(mavenName)
                    description.set(Maven.desc)
                    url.set(Maven.siteUrl)

                    scm {
                        val scmUrl = "scm:git:${Maven.gitUrl}"
                        connection.set(scmUrl)
                        developerConnection.set(scmUrl)
                        url.set(Maven.gitUrl)
                        tag.set("HEAD")
                    }

                    developers {
                        developer {
                            id.set("crow-misia")
                            name.set("Zenichi Amano")
                            email.set("crow.misia@gmail.com")
                            roles.set(listOf("Project-Administrator", "Developer"))
                            timezone.set("+9")
                        }
                    }

                    licenses {
                        license {
                            name.set(Maven.licenseName)
                            url.set(Maven.licenseUrl)
                            distribution.set(Maven.licenseDist)
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                val releasesRepoUrl = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                val snapshotsRepoUrl = URI("https://oss.sonatype.org/content/repositories/snapshots")
                url = if (Maven.version.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                val sonatypeUsername: String? by project
                val sonatypePassword: String? by project
                credentials {
                    username = sonatypeUsername.orEmpty()
                    password = sonatypePassword.orEmpty()
                }
            }
        }
    }

    signing {
        sign(publishing.publications.getByName(mavenName))
    }
}

detekt {
    buildUponDefaultConfig = true // preconfigure defaults
    allRules = false // activate all available (even unstable) rules.
    config = files("$rootDir/config/detekt.yml")
}

tasks {
    withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        // Target version of the generated JVM bytecode. It is used for type resolution.
        jvmTarget = "11"

        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }
    }
}
