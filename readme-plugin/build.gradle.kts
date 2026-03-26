import org.gradle.api.JavaVersion.VERSION_24
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

plugins {
    signing
    `java-library`
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.publish)
}

group = "com.cheroliv"
version = libs.plugins.readme.get().version
kotlin.jvmToolchain(VERSION_24.ordinal)

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    // Active plugin dependencies
    implementation(libs.bundles.readme)
    implementation(libs.bundles.jgit)

    compileOnly(libs.bundles.readme.ai)
    compileOnly(libs.bundles.readme.utils)

    //TODO: readme to html and serve
    implementation(libs.node.gradle)

    // Unit test dependencies
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.slf4j)
    testRuntimeOnly(libs.logback)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.bundles.coroutines)

    // Cucumber dependencies (unit test scope)
    testImplementation(libs.bundles.cucumber)
}

// ── Test logging ─────────────────────────────────────────────────────────────
tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
}

// ── Unit tests — exclude Cucumber scenarios and functional tests ──────────────
tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("readme.scenarios.**")
        excludeTestsMatching("readme.ReadmeGradlePluginFunctionalTests")
    }
}

// ── Functional test source set ────────────────────────────────────────────────
// IMPORTANT: declared via sourceSets.creating so that gradlePlugin.testSourceSets
// can register it — this is what makes Gradle generate plugin-under-test-metadata.properties
// which is required by GradleRunner.withPluginClasspath()
val functionalTest: SourceSet by sourceSets.creating {
    java { srcDirs("src/functionalTest/kotlin") }
    resources { srcDirs("src/functionalTest/resources") }
}

// Register with gradlePlugin so plugin-under-test-metadata.properties is generated
gradlePlugin.testSourceSets.add(functionalTest)

dependencies {
    add(functionalTest.implementationConfigurationName, gradleTestKit())
    add(functionalTest.implementationConfigurationName, kotlin("stdlib-jdk8"))
    add(functionalTest.implementationConfigurationName, kotlin("test-junit5"))
    add(functionalTest.implementationConfigurationName, "org.slf4j:slf4j-api:2.0.17")
    add(functionalTest.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    add(functionalTest.implementationConfigurationName, libs.assertj.core)
    add(functionalTest.implementationConfigurationName, libs.mockito.kotlin)
    add(functionalTest.implementationConfigurationName, libs.mockito.junit.jupiter)
    libs.bundles.coroutines.get().forEach { dep ->
        add(functionalTest.implementationConfigurationName, dep)
    }
}

// Exclude logback from test classpaths to avoid SLF4J conflicts with Gradle's own logging
configurations {
    named("testRuntimeClasspath") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    named("testImplementation") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    named(functionalTest.runtimeClasspathConfigurationName) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
}

tasks.named<ProcessResources>(functionalTest.processResourcesTaskName) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
}

// ── Cucumber source set config ────────────────────────────────────────────────
sourceSets {
    test {
        resources { srcDir("src/test/features") }
        java { srcDir("src/test/scenarios") }
    }
}

configurations.named("testImplementation").configure {
    extendsFrom(configurations.named(functionalTest.implementationConfigurationName).get())
}
configurations.named("testRuntimeOnly").configure {
    extendsFrom(configurations.named(functionalTest.runtimeOnlyConfigurationName).get())
}
dependencies {
    testImplementation(functionalTest.output)
}

// ── Cucumber test task ────────────────────────────────────────────────────────
val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            functionalTest.output +
            sourceSets.main.get().output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }
    dependsOn(functionalTest.classesTaskName)
    dependsOn(tasks.classes)
}

tasks.check {
    dependsOn(functionalTestTask)
    dependsOn(cucumberTest)
}

// ── Gradle plugin metadata ────────────────────────────────────────────────────
gradlePlugin {
    website.set("https://github.com/cheroliv/readme-gradle/")
    vcsUrl.set("https://github.com/cheroliv/readme-gradle.git")

    plugins {
        create("readme") {
            id = libs.plugins.readme.get().pluginId
            implementationClass = "readme.ReadmePlugin"
            displayName = "README helper Plugin"
            description = """
                Generates GitHub-compatible README.adoc files from README_truth.adoc
                source files. Replaces diagram blocks with PNG images committed back
                to the repository via GitHub Actions and JGit.
            """.trimIndent()
            tags.set(
                listOf(
                    "asciidoc",
                    "plantuml",
                    "readme",
                    "documentation",
                    "github",
                    "diagram"
                )
            )
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("readme").displayName)
                    description.set(gradlePlugin.plugins.getByName("readme").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cheroliv")
                            name.set("cheroliv")
                            email.set("cheroliv.developer@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = (if (version.toString().endsWith("-SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"))
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
    if (isReleaseVersion) sign(publishing.publications)
    useGpgCmd()
}