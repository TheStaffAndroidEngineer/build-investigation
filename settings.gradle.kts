pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "build-investigation"
include(":app", ":feature-cart", ":feature-saved",
    ":cart-api", ":cart-impl", ":build-lab")

buildCache {
    local { directory = file(".local-build-cache") }
}
