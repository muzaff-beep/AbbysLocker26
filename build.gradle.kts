
plugins {
    id("com.android.application") version "8.2.0" apply false
    kotlin("android") version "1.9.24" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

