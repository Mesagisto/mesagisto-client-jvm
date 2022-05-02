plugins {
  id("org.jetbrains.kotlin.jvm") version "1.6.21"
  kotlin("plugin.serialization") version "1.6.21"
  `java-library`
  `maven-publish` // Jitpack
}
java {
  withJavadocJar()
  withSourcesJar()
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(8))
  }
}
publishing {
  publications {
    register("maven", MavenPublication::class) {
      from(components["java"])
      groupId = "org.meowcat"
      artifactId = "mesagisto-client"
      version = "1.3.2"
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

  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")
  implementation("org.rocksdb:rocksdbjni:7.0.4")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.3.2")
  implementation("io.nats:jnats:2.14.0")
  implementation("org.bouncycastle:bcprov-jdk15on:1.70")
  implementation("com.charleskorn.kaml:kaml:0.43.0")
  implementation("org.snakeyaml:snakeyaml-engine:2.3")
}
