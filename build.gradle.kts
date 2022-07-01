plugins {
  id("org.jetbrains.kotlin.jvm") version "1.6.0"
  kotlin("plugin.serialization") version "1.6.0"
  id("me.him188.maven-central-publish") version "1.0.0-dev-3"
}
java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
group = "org.mesagisto"
version = "0.2.0"

mavenCentralPublish {
  useCentralS01()
  githubProject("Mesagisto", "mesagisto-client-jvm")
  licenseFromGitHubProject("LGPL-2.1", "master")
  developer("Itsusinn")
}
repositories {
  mavenCentral()
  google()
}
tasks.compileKotlin {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
  }
  sourceCompatibility = "1.8"
}

dependencies {
  implementation("org.fusesource.leveldbjni:leveldbjni-all:1.8")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.2.2")
  implementation("io.nats:jnats:2.15.3")
  implementation("org.bouncycastle:bcprov-jdk15on:1.70")
  implementation("com.charleskorn.kaml:kaml:0.45.0")
}
