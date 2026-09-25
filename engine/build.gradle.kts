plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.eraandroid"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    System.getProperty("bigGame")?.let { systemProperty("bigGame", it) }
    jvmArgs("-Xss64m")
}
