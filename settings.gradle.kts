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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // 1. JitPack (Para la librería de gráficas y utilidades)
        maven { url = uri("https://jitpack.io") }

        // 2. Mapbox (Con autenticación)
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // NO CAMBIES ESTE USUARIO
                username = "mapbox"
                // TU TOKEN SECRETO (sk.eyJ...)
                password = "sk.eyJ1IjoianZlbGV6MDAwIiwiYSI6ImNtaWkzZXhkMTBqNXYzZHBtNDU1OHhiMDkifQ.okZpmO3l1k6Y4sqBco07pQ"
            }
        }
    }
}

rootProject.name = "Apex Vision"
include(":app")