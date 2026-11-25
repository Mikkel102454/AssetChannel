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

val javaFormatter = configurations.create("javaFormatter")
dependencies {
    javaFormatter("com.google.googlejavaformat:google-java-format:2.0.0")
}

val formattedJava = files(
    sourceSets.main.get().allJava,
    sourceSets.test.get().allJava,
    fileTree("examples") { include("**/*.java"); exclude("**/build/**", "**/.gradle/**") }
)
fun JavaExec.configureFormatter(checkOnly: Boolean) {
    group = "formatting"
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    listOf("api", "file", "parser", "tree", "util", "code").forEach {
        jvmArgs("--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED")
    }
    args("--aosp", "--cleanup=false")
    if (checkOnly) args("--dry-run", "--set-exit-if-changed") else args("--replace")
    args(formattedJava.files.sortedBy { it.path }.map { it.absolutePath })
}
tasks.register<JavaExec>("formatJava") { configureFormatter(false) }
tasks.register<JavaExec>("checkJavaFormat") {
    configureFormatter(true)
    mustRunAfter("formatJava")
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
version = "1.1.0"

val apiJar = tasks.register<Jar>("apiJar") {
    archiveClassifier = "api"
    from(sourceSets.main.get().output)
    include("nu/miguel/assetChannel/api/**")
    exclude("nu/miguel/assetChannel/api/internal/**")
}

tasks.register<Exec>("clientPreflight") {
    group = "verification"
    description = "Compile imported post-effect shaders through an installed Minecraft 26.3 client."
    commandLine("powershell.exe", "-NoProfile", "-File", file("examples/client-probe/run-native-probe.ps1").absolutePath)
    providers.gradleProperty("preflightPack").orNull?.let { args("-AssetsPath", file(it).absolutePath) }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(apiJar)
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

val apiJavadoc = tasks.register<Javadoc>("apiJavadoc") {
    group = "documentation"
    description = "Generate the complete consumer API reference for MkDocs."
    dependsOn(tasks.classes)
    source = fileTree("src/main/java") {
        include("nu/miguel/*/api/**/*.java")
        exclude("**/api/internal/**")
    }
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    destinationDir = layout.buildDirectory.dir("docs/api").get().asFile
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
        memberLevel = JavadocMemberLevel.PUBLIC
    }
}
tasks.register<Sync>("stageApiDocs") {
    group = "documentation"
    description = "Stage generated API HTML as static files for the MkDocs site."
    dependsOn(apiJavadoc)
    from(apiJavadoc.map { it.destinationDir })
    into(layout.projectDirectory.dir("docs/java"))
}

tasks.named("compileJava") { mustRunAfter("formatJava") }
tasks.named("compileTestJava") { mustRunAfter("formatJava") }
