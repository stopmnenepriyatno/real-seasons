plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
    java
}

group = "dev.realseasons"
version = "${providers.gradleProperty("mod_version").get()}+8.0.1"
base.archivesName = "real-seasons"

loom {
    customMinecraftMetadata.set("https://downloads.betterthanadventure.net/bta-client/release/v8.0.1/manifest.json")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.thesignalumproject.net/infrastructure")
}

dependencies {
    minecraft("::8.0.1")
    implementation("net.fabricmc:fabric-loader:0.18.4-bta.11")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

// Keep executable regression checks separate from Loom's game-launch runtime.
val regression by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath = output + compileClasspath
}
dependencies {
    add(regression.implementationConfigurationName, "org.ow2.asm:asm-tree:9.9")
}
val regressionTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named(regression.classesTaskName))
    classpath = regression.runtimeClasspath
    mainClass = "dev.realseasons.RegressionTests"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    enableAssertions = true
}
tasks.check { dependsOn(regressionTest) }

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
    exclude(group = "net.java.jutils")
    exclude(group = "net.java.jinput")
    exclude(group = "net.sf.jopt-simple")
    exclude(group = "net.minecraft", module = "launchwrapper")
}
