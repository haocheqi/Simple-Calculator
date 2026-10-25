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

rootProject.name = "Calculator"

include(":app")
include(":core:model")
include(":core:math")
include(":core:mathui")
include(":core:designsystem")
include(":data")
include(":feature:calculator")