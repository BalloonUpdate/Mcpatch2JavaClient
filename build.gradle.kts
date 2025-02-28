import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

fun getVersionName(tagName: String) = if(tagName.startsWith("v")) tagName.substring(1) else tagName
val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val debugVersion: String get() = System.getenv("DBG_VERSION") ?: "0.0.0"

group = "com.github.balloonupdate"
version = gitTagName?.run { getVersionName(this) } ?: debugVersion

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:2.6")
    implementation("com.formdev:flatlaf-intellij-themes:2.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.lookfirst:sardine:5.12")
    implementation("org.json:json:20231013")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("commons-codec:commons-codec:1.18.0")

}

tasks.withType<ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName = "Mcpatch"

    manifest {
        attributes("Version" to archiveVersion.get())
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Main-Class" to "com.github.balloonupdate.mcpatch.client.Main")
        attributes("Premain-Class" to "com.github.balloonupdate.mcpatch.client.Main")
    }

    archiveClassifier.set("")
}