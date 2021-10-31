plugins {
  id("org.jetbrains.kotlin.jvm") version "1.5.21"
  kotlin("plugin.serialization") version "1.5.21"
  `java-library`
  `maven-publish` // Jitpack
}
java {
  withJavadocJar()
  withSourcesJar()
}
publishing {
  publications {
    register("maven", MavenPublication::class) {
      from(components["java"])
      groupId = "org.meowcat"
      artifactId = "mesagisto-client"
      version = "1.0.9"
    }
  }
}
repositories {
  mavenCentral()
  google()
  maven("https://jitpack.io")
}
tasks.compileKotlin {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
  }
  sourceCompatibility = "1.8"
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.1")
  compileOnly("io.ktor:ktor-client-core:1.5.4")
  implementation("io.ktor:ktor-client-cio-jvm:1.5.4")
  implementation("org.rocksdb:rocksdbjni:6.22.1.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.2.2")
  implementation("io.nats:jnats:2.12.0")
  implementation("io.arrow-kt:arrow-core:1.0.0")
  implementation("org.bouncycastle:bcprov-jdk15on:1.69")
}
