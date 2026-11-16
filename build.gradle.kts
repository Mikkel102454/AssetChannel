plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.242-stable")
    implementation("ws.schild:jave-all-deps:3.5.0")
    testImplementation("io.papermc.paper:paper-api:26.3.build.242-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier = ""
        // JAVE supplies FFmpeg executables as resources. They must remain in the
        // plugin jar so its locator can extract the right binary at runtime.
        mergeServiceFiles()
    }

    jar {
        archiveClassifier = "plain"
    }

    assemble {
        dependsOn(shadowJar)
    }
}

group = "nu.miguel"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("shadowJar")) {
                classifier = null
            }

            groupId = project.group.toString()
            artifactId = "AssetChannel"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "reposilite"
            url = uri("https://maven.miguel.nu/releases")

            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}