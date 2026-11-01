pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.thesignalumproject.net/infrastructure")
        gradlePluginPortal()
        mavenCentral()
    }
    // Pin the implementation already verified in the reference build.
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "net.fabricmc.fabric-loom") {
            useModule("net.fabricmc:fabric-loom:1.15.5")
        }
    }
}
rootProject.name = "real-seasons"
