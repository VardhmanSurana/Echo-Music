import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("multiplatform")
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(21)

  jvm()

  sourceSets {
    val jvmMain by getting {
      kotlin.srcDir("../innertube/src/main/kotlin")
      kotlin.srcDir("../lrclib/src/main/kotlin")
      kotlin.srcDir("../kugou/src/main/kotlin")
      kotlin.srcDir("../betterlyrics/src/main/kotlin")
      kotlin.srcDir("../simpmusic/src/main/kotlin")
      kotlin.srcDir("../youlyplus/src/main/kotlin")
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation("org.jetbrains.compose.material3:material3:1.9.0")
        implementation(compose.materialIconsExtended)

        implementation(libs.materialKolor)
        implementation(libs.coil)
        implementation(libs.coil.network.okhttp)

        implementation("net.jthink:jaudiotagger:2.2.5")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

        implementation(project(":domain"))
        implementation(project(":metadata"))
        implementation(project(":playback-core"))
        implementation(project(":unison"))

        implementation("com.github.hypfvieh:dbus-java:3.3.2")
        implementation("org.slf4j:slf4j-simple:2.0.17")

        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.json)
        implementation(libs.ktor.client.encoding)
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.cio)
        implementation(libs.ktor.server.cors)
        implementation(libs.ktor.server.content.negotiation)
        implementation(libs.brotli)
        implementation(libs.newpipeextractor)
        implementation(libs.pipepipe.extractor)
        implementation("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("net.jthink:jaudiotagger:2.2.5")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.json)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "echo.music.desktop.MainKt"
    jvmArgs("-Dfile.encoding=UTF-8")
    nativeDistributions {
      targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
      packageName = "echo-music"
      packageVersion = "1.0.0"
      description = "Echo Music — YouTube Music & local media player for Linux"
      vendor = "Echo Music"
      licenseFile.set(rootProject.file("LICENSE"))
      linux {
        iconFile.set(project.file("packaging/icon.png"))
        menuGroup = "Audio"
        appCategory = "Audio"
        debMaintainer = "Echo Music"
        rpmLicenseType = "GPL-3.0"
      }
    }
  }
}

tasks.register<Tar>("packageTarGz") {
  group = "compose desktop"
  description =
    "Packages the app image as a .tar.gz archive (jpackage has no native tar.gz target)."
  dependsOn("packageAppImage")
  val distributions = compose.desktop.application.nativeDistributions
  archiveFileName.set("${distributions.packageName}-${distributions.packageVersion}.tar.gz")
  destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/tar.gz"))
  from(layout.buildDirectory.dir("compose/binaries/main/app"))
  compression = Compression.GZIP
}
