plugins { java }

repositories {
    google()
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
}

dependencies {
    // Keep aligned with the Android plugin version in ../build.gradle.kts.
    implementation("com.android.tools.build:gradle-api:8.7.3")
    implementation("org.ow2.asm:asm:9.7.1")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
