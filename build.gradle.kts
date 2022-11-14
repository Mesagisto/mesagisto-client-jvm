plugins {
  id("org.jetbrains.kotlin.jvm") version "1.6.0"
  id("me.him188.maven-central-publish") version "1.0.0-dev-3"
  id("io.codearte.nexus-staging") version "0.30.0"
}
java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
group = "org.mesagisto"
version = "1.6.0-rc.2"

mavenCentralPublish {
  nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    stagingProfileId = "9bdaa8e9e83392"
    username = credentials?.sonatypeUsername
    password = credentials?.sonatypePassword
  }
  useCentralS01()
  githubProject("Mesagisto", "mesagisto-client-jvm")
  licenseFromGitHubProject("LGPL-2.1", "master")
  developer("Itsusinn")
}
repositories {
  mavenCentral()
}
tasks.compileKotlin {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
  }
}
tasks.test {
  useJUnitPlatform()
}
dependencies {
  implementation("org.fusesource.leveldbjni:leveldbjni-all:1.8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")

  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.3")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.13.3")

  implementation("com.fasterxml.uuid:java-uuid-generator:4.0.1")

  implementation("org.java-websocket:Java-WebSocket:1.5.3")

  implementation("org.bouncycastle:bcprov-jdk15on:1.70")

  testImplementation(kotlin("test"))
}
