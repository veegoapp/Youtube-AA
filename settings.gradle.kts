pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack is used ONLY for NewPipeExtractor and its own JitPack-hosted dependency (nanojson).
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.(?i:teamnewpipe).*") }
        }
    }
}
rootProject.name = "YouTubeCar"
include(":app")
