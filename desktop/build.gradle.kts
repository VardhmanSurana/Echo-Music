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
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation("org.jetbrains.compose.material3:material3:1.9.0")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

        implementation(project(":domain"))
        implementation(project(":metadata"))

        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.json)
        implementation(libs.ktor.client.encoding)
        implementation(libs.brotli)
        implementation(libs.newpipeextractor)
        implementation(libs.pipepipe.extractor)
        implementation("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}
