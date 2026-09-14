plugins {
    id("java-library")
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
    api(project(":protocol"))
    api(project(":agent:agent-probe"))
    implementation(libs.java.websocket)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.junit)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.test {
    useJUnit()
}
