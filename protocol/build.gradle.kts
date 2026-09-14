plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
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
    api(libs.protobuf.java)
    implementation(libs.java.websocket)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.junit)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.test {
    useJUnit()
    systemProperty("java.awt.headless", "true")
}
