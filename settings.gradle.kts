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

rootProject.name = "point-and-shoot"
include(":app")
include(":baselineprofile")
include(":pns-core")
include(":pns-fleet")
include(":pns-capture")
include(":pns-preview")

project(":pns-core").projectDir = file("modules/pns-core")
project(":pns-fleet").projectDir = file("modules/pns-fleet")
project(":pns-capture").projectDir = file("modules/pns-capture")
project(":pns-preview").projectDir = file("modules/pns-preview")
