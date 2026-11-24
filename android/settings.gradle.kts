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
    }
}

rootProject.name = "openfit"

// The workspace lives on a network drive that rejects some of the Kotlin
// compiler's file operations (it cannot write the module metadata into a
// freshly created directory). Build output therefore goes to a local scratch
// directory. Sources and deliverables stay in the workspace.
val scratchRoot = File(System.getProperty("java.io.tmpdir"), "ofit-build")
gradle.beforeProject {
    val relative = if (path == ":") "root" else path.removePrefix(":").replace(':', '-')
    layout.buildDirectory.set(File(scratchRoot, "ofit-$relative"))
}

include(":protocol")
include(":transport")
include(":app")
