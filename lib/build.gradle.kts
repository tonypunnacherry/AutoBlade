plugins {
    `java-library`
    idea
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
}

group = "org.tpunn.autoblade"
version = "1.0.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

// 1. SourceSet for VS Code visibility
val kotlinIntTestSourceSet = sourceSets.create("kotlinIntegrationTest") {
    kotlin.setSrcDirs(listOf("src/test/kotlin"))
    java.setSrcDirs(listOf("src/test/kotlin"))

    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

repositories {
    mavenCentral()
}

// 2. Configuration Inheritance & Wiring
configurations {
    named("kotlinIntegrationTestImplementation") {
        extendsFrom(testImplementation.get())
    }
    named("kotlinIntegrationTestRuntimeOnly") {
        extendsFrom(testRuntimeOnly.get())
    }
    named("kaptKotlinIntegrationTest") {
        extendsFrom(configurations.kapt.get())
    }
}

dependencies {
    // Core Dependencies
    api("com.google.dagger:dagger:2.59")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    implementation("com.squareup:javapoet:1.13.0")

    val processorJar = tasks.jar.flatMap { it.archiveFile }

    // --- JAVA INTEGRATION TESTS ---
    testImplementation(files(processorJar))
    testAnnotationProcessor(files(processorJar))
    testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")

    // --- KOTLIN INTEGRATION TESTS ---
    "kaptKotlinIntegrationTest"(files(processorJar))
    "kaptKotlinIntegrationTest"("com.google.dagger:dagger-compiler:2.59")
    
    "kotlinIntegrationTestImplementation"(kotlin("stdlib"))
    "kotlinIntegrationTestImplementation"("org.jetbrains.kotlin:kotlin-test")
    "kotlinIntegrationTestImplementation"("com.google.dagger:dagger:2.59")
    "kotlinIntegrationTestImplementation"("junit:junit:4.13.2")
    "kotlinIntegrationTestImplementation"(files(processorJar))
}

sourceSets {
    getByName("test") {
        kotlin.exclude("**/autobladekt/**")
    }
}

kapt {
    keepJavacAnnotationProcessors = true
    correctErrorTypes = true 
}

// 3. Define the Test Task
val kotlinIntegrationTestTask = tasks.register<Test>("kotlinIntegrationTest") {
    description = "Runs Kotlin integration tests."
    group = "verification"
    testClassesDirs = kotlinIntTestSourceSet.output.classesDirs
    classpath = kotlinIntTestSourceSet.runtimeClasspath
    
    shouldRunAfter(tasks.test)
}

// 4. Task Wiring
tasks.test {
    dependsOn(kotlinIntegrationTestTask)
}

// Ensure the JAR is built before the integration tests try to compile
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("IntegrationTest")) {
        dependsOn(tasks.jar)
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.check { dependsOn(kotlinIntegrationTestTask) }

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

// 5. IDE Support (Helper for VS Code Classpath Resolver)
idea {
    module {
        val kaptGeneratedDir = layout.buildDirectory.dir("generated/source/kapt/kotlinIntegrationTest").get().asFile
        testSources.from(kotlinIntTestSourceSet.kotlin.srcDirs)
        generatedSourceDirs.add(kaptGeneratedDir)
        testSources.from(kaptGeneratedDir)
    }
}
