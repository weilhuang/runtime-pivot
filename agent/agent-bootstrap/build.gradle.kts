plugins {
    id("java-library")
    alias(libs.plugins.shadow)
}

group = rootProject.group
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":agent:agent-core"))
    implementation(project(":agent:agent-probe"))
    implementation(project(":protocol"))
    implementation(libs.java.websocket)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("runtime-pivot-agent")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    relocate("com.google.protobuf", "com.runtime.pivot.agent.shaded.protobuf")
    relocate("org.java_websocket", "com.runtime.pivot.agent.shaded.websocket")
    relocate("org.slf4j", "com.runtime.pivot.agent.shaded.slf4j")
    manifest {
        attributes(
            "Premain-Class" to "com.runtime.pivot.agent.bootstrap.AgentMain",
            "Agent-Class" to "com.runtime.pivot.agent.bootstrap.AgentMain",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Title" to "runtime-pivot-agent",
            "Implementation-Version" to project.version.toString(),
        )
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
