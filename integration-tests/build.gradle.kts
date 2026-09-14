plugins {
    id("java")
}

group = rootProject.group
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(findProperty("agentJdk")?.toString() ?: "21"))
    }
}

repositories {
    mavenCentral()
}

evaluationDependsOn(":agent:agent-bootstrap")
evaluationDependsOn(":test-apps")
val agentJarTask = project(":agent:agent-bootstrap").tasks.named("shadowJar")
val testAppJarTask = project(":test-apps").tasks.named("jar")

dependencies {
    testImplementation(project(":protocol"))
    testImplementation(project(":agent:agent-core"))
    testImplementation(libs.junit)
    testImplementation(libs.java.websocket)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnit()
    dependsOn(agentJarTask, testAppJarTask)
    systemProperty("java.awt.headless", "true")
    doFirst {
        systemProperty("runtime.pivot.agent.jar", agentJarTask.get().outputs.files.singleFile.absolutePath)
        systemProperty("runtime.pivot.testapp.jar", testAppJarTask.get().outputs.files.singleFile.absolutePath)
        systemProperty("runtime.pivot.test.java.home", System.getProperty("java.home"))
    }
}
