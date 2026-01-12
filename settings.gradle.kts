pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // Cambia esto si es necesario
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}



rootProject.name = "DriveUp"
include(":app")
