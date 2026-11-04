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
    }
}
rootProject.name = "Orange"
include(":app")
include(":lite")
// One-off: lists the Home candidates on a TV and can disable the ones that keep taking Home
// back. Not part of the product; see docs/LITE.md.
include(":checkup")
