plugins {
  id("org.jetbrains.kotlin.jvm") version "1.5.10"
  kotlin("plugin.serialization") version "1.5.10"
  maven
  `java-library`
  `maven-publish` // Jitpack
}

publishing {
  publications {
    register("mavenJava", MavenPublication::class) {
      from(components["java"])
      groupId = "org.meowcat"
      artifactId = "mesagisto-client"
      version = "1.0.3"
    }
  }
}
repositories {
  mavenCentral()
  google()
  maven("https://jitpack.io")
}
tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
      jvmTarget = "11"
      freeCompilerArgs = listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
    }
    sourceCompatibility = "11"
  }
}
dependencies {
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.rocksdb:rocksdbjni:6.22.1.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.3.0")
  implementation("io.nats:jnats:2.12.0")
  implementation("io.arrow-kt:arrow-core:1.0.0")
  implementation("org.bouncycastle:bcprov-jdk15on:1.69")
  implementation("org.tinylog:tinylog-api-kotlin:2.4.0-M1") {
    isTransitive = false
  }
}
