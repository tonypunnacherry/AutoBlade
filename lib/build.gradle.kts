plugins {
    `java-library`
    idea // Required for IntelliJ to recognize generated test sources
}

group = "org.tpunn.autoblade"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 1. Dagger API: Consumers need this to compile @Inject and @Component
    api("com.google.dagger:dagger:2.59")

    // 2. Annotation Processing Tools
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // 3. Code Generation
    implementation("com.squareup:javapoet:1.13.0")

    // --- INTEGRATION TEST SETUP ---
    // Use the JAR output of this project as a dependency for its own tests
    val processorJar = tasks.jar.flatMap { it.archiveFile }

    testImplementation(files(processorJar))
    testAnnotationProcessor(files(processorJar))

    // Dagger compiler must run AFTER your processor to see the generated Modules
    testAnnotationProcessor("com.google.dagger:dagger-compiler:2.59")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))

    // FIX: Ensure the JAR is actually built before the test compilation tries to use it
    if (name.contains("Test")) {
        dependsOn(tasks.jar)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "AutoBlade",
            "Implementation-Version" to project.version
        )
    }
}