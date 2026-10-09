plugins { kotlin("multiplatform") }

kotlin {
  jvmToolchain(21)

  jvm()

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        implementation("net.java.dev.jna:jna:5.17.0")
        implementation("net.java.dev.jna:jna-platform:5.17.0")
        compileOnly("org.freedesktop.gstreamer:gst1-java-core:1.4.0")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
      }
    }
  }
}
