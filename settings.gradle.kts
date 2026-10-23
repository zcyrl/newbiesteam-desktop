pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // androidx lifecycle/savedstate/desktop artifacts ship via Google's repo
        google()
        mavenCentral()
        // Desktop WebView (KCEF based) requires Jogamp's native bundles
        maven("https://jogamp.org/deployment/maven")
    }
}

rootProject.name = "NewbiesTeamDesktop"

include(":shared")
include(":desktop-shell")
include(":app-public")
include(":app-dev")
