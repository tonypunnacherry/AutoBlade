plugins {
    `java-library`
}

group = "org.tpunn.autoblade"
version = "1.0.0"

java {
    // Uses your local Java 21 JDK to run the build
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 1. Dagger API: Essential for your extension to interact with Dagger
    api("com.google.dagger:dagger:2.59")

    // 2. Annotation Processing Tools (Auto-Service)
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // 3. JavaPoet for generating Java 8 source code
    implementation("com.squareup:javapoet:1.13.0")

    // 4. Testing with JUnit 4 and Google's Compile-Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    
    // Allows your tests to see the Dagger compiler's behavior
    testImplementation("com.google.dagger:dagger-compiler:2.59")
}

tasks.withType<JavaCompile> {
    // ENFORCE JAVA 8 COMPATIBILITY
    // This tells the Java 21 compiler to only use Java 8 APIs and bytecode
    options.release.set(8)
    
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "AutoBlade",
            "Implementation-Version" to project.version
        )
    }
}