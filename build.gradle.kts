import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.31"
    kotlin("kapt") version "1.3.31"
}

group = "arven"
version = "0.3.0"

repositories {
    mavenCentral()
    jcenter()
    maven {
        setUrl("https://jitpack.io")
    }
    maven {
        setUrl("https://repo.spongepowered.org/maven")
    }
    maven {
        setUrl("https://kotlin.bintray.com/kotlinx")
    }
}

dependencies {
    // Kotlin
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.2.1")

    // Sponge
    kapt("org.spongepowered:spongeapi:7.1.0")
    compileOnly("org.spongepowered:spongeapi:7.1.0")

    // Config System
    compileOnly("com.github.TheFrontier:SKD:094ab59a02")

    // Sponge Platform extension methods
    compileOnly("com.github.TheFrontier:SKE:0.3.0")

    // Feature System
    compileOnly("com.github.TheFrontier:SKF:0.3.0")

    // Command Tree System
    compileOnly("com.github.TheFrontier:SKPC:0.3.0")

    // Database ORM
    compileOnly("org.jetbrains.exposed:exposed:0.13.6")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}