// JVM-only harness that compiles the pure Kotlin `domain/` and `config/` sources of the
// holy-radius-native module and runs JUnit on them. No Android SDK required.
plugins {
  kotlin("jvm") version "2.1.20"
}

repositories {
  mavenCentral()
}

val nativeSrc = "../modules/holy-radius-native/android/src/main/java/com/holyradius/nativecore"

sourceSets {
  main {
    kotlin.srcDirs("$nativeSrc/domain", "$nativeSrc/config")
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
}
