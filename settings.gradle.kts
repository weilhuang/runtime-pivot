rootProject.name = "runtime-pivot"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "protocol",
    "agent:agent-bootstrap",
    "agent:agent-core",
    "agent:agent-probe",
    "plugin",
    "plugin:plugin-core",
    "plugin:plugin-debugger",
    "plugin:plugin-ui",
    "integration-tests",
    "test-apps",
)
