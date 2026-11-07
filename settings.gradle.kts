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
        // Official Hammerhead Karoo Extension SDK (GitHub Packages, needs a read:packages token).
        // Only io.hammerhead artifacts are fetched from here, so the token is never sent elsewhere.
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GPR_USER")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GPR_KEY")
            }
            content { includeGroup("io.hammerhead") }
        }
    }
}

rootProject.name = "karoo-ride-meter"
include(":app")
