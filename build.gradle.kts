import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.21"
    kotlin("kapt") version "1.3.21"
}

group = "arven"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
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
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.1.1")

    kapt("org.spongepowered:spongeapi:7.1.0")
    compileOnly("org.spongepowered:spongeapi:7.1.0")

    // Command Tree System
    compileOnly("com.github.TheFrontier:SKPC:af08cf3870")

    // Config System
    compileOnly("com.github.TheFrontier:SKD:094ab59a02")

    // Sponge Platform extension methods
    compileOnly("com.github.TheFrontier:SKE:3917897ad3")

    // Database ORM
    compileOnly("org.jetbrains.exposed:exposed:0.13.6")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}