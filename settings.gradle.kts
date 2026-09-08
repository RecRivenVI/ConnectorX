pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ConnectorX"
include(":ui")
project(":ui").projectDir = file("shared/ui")
include(":app", ":runtime", ":protocol")
include(":windows")
project(":windows").projectDir = file("apps/Windows")
project(":app").projectDir = file("apps/Android")
project(":runtime").projectDir = file("runtime/Android")
project(":protocol").projectDir = file("protocol")
