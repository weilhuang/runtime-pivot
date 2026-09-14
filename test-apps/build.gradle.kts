plugins {
    id("java")
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

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.jar {
    archiveBaseName.set("runtime-pivot-test-app")
    manifest {
        attributes("Main-Class" to "com.runtime.pivot.testapp.SleepMain")
    }
}
