pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central first: CI runners often get rate-limited (429)
        // by Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's mirror of Maven Central first: CI runners often get rate-limited (429)
        // by Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "cloudlight-tv"
include(":probe", ":app")
