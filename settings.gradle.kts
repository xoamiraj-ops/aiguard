pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs {
        create("libs") {
            from(files("third_party/llama.cpp/examples/llama.android/gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "AvaKavLocal"
include(":app")
include(":whisperlib")
project(":whisperlib").projectDir = file("third_party/whisper.cpp/examples/whisper.android/lib")
include(":llamalib")
project(":llamalib").projectDir = file("third_party/llama.cpp/examples/llama.android/lib")
