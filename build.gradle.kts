import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

plugins {
    id("java")
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.intelliJPlatformModule) apply false
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.protobuf) apply false
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

val forbiddenApiScan by tasks.registering {
    group = "verification"
    description = "Fails if production sources use forbidden IDEA impl/Internal/TestOnly APIs or JDK private reflection."
    val roots = layout.projectDirectory
    doLast {
        val productionDirs = listOf(
            "plugin/src/main/java",
            "plugin/plugin-core/src/main/java",
            "plugin/plugin-debugger/src/main/java",
            "plugin/plugin-ui/src/main/java",
            "protocol/src/main/java",
            "agent/agent-bootstrap/src/main/java",
            "agent/agent-core/src/main/java",
            "agent/agent-probe/src/main/java",
        ).map { roots.dir(it).asFile }.filter { it.exists() }

        val violations = mutableListOf<String>()
        productionDirs.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .forEach { file ->
                    val rel = file.relativeTo(roots.asFile).path.replace('\\', '/')
                    val text = file.readText()
                    val lines = text.lines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import ") && trimmed.contains(".impl.")) {
                            violations += "$rel:${index + 1}: forbidden impl import: $trimmed"
                        }
                        if (trimmed.contains("sun.instrument") || trimmed.contains("jdk.internal")) {
                            violations += "$rel:${index + 1}: forbidden JDK private API: $trimmed"
                        }
                    }
                    if (rel.contains("plugin") && text.contains("@TestOnly")) {
                        violations += "$rel: production plugin code must not use @TestOnly"
                    }
                    if (rel.contains("plugin") && text.contains("PluginManagerCore")) {
                        violations += "$rel: PluginManagerCore is internal; locate the agent jar from the packaged resource"
                    }
                    if (isDropFrameWhitelist(rel).not() && text.contains("XDropFrameHandler")) {
                        violations += "$rel: XDropFrameHandler must stay isolated in DropFrameCapability"
                    }
                    if (usesIdeaReflection(text)) {
                        violations += "$rel: forbidden reflection into IDEA or JDK private APIs"
                    }
                }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Forbidden API scan failed:\n" + violations.joinToString("\n"))
        }
    }
}

fun isDropFrameWhitelist(path: String): Boolean {
    return path.endsWith("plugin/plugin-debugger/src/main/java/com/runtime/pivot/plugin/debugger/DropFrameCapability.java")
}

fun usesIdeaReflection(source: String): Boolean {
    val hasReflection = source.contains("Class.forName") ||
        source.contains("getDeclaredField") ||
        source.contains("getDeclaredMethod") ||
        source.contains("setAccessible")
    if (!hasReflection) {
        return false
    }
    return source.contains("\"com.intellij.") || source.contains("sun.instrument") || source.contains("jdk.internal")
}

tasks.register("unitTest") {
    group = "verification"
    description = "Runs fast unit tests that do not require an IntelliJ Platform fixture."
    dependsOn(
        ":protocol:test",
        ":agent:agent-core:test",
        ":agent:agent-probe:test",
        ":plugin:unitTest",
    )
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs Agent/OpAMP integration tests and IntelliJ Platform integration tests."
    dependsOn(
        ":integration-tests:test",
        ":plugin:integrationTest",
    )
}

tasks.register("ideaUiTest") {
    group = "verification"
    description = "Runs headless Swing/UI component tests (no RemoteRobot server)."
    dependsOn(":plugin:ideaUiTest")
}

tasks.register("verifyPluginCompat") {
    group = "verification"
    description = "Runs IntelliJ Plugin Verifier via the plugin module."
    dependsOn(":plugin:verifyPlugin")
}

tasks.named("check") {
    dependsOn(forbiddenApiScan, "unitTest", "integrationTest", "ideaUiTest")
}

tasks.register("printPluginDescription") {
    doLast {
        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        val start = "<!-- Plugin description -->"
        val end = "<!-- Plugin description end -->"
        val lines = readme.lines()
        if (!lines.contains(start) || !lines.contains(end)) {
            throw GradleException("Plugin description section not found in README.md")
        }
        println(markdownToHTML(lines.subList(lines.indexOf(start) + 1, lines.indexOf(end)).joinToString("\n")))
    }
}

tasks.register("renderChangelog") {
    doLast {
        println(
            changelog.renderItem(
                (changelog.getOrNull(providers.gradleProperty("pluginVersion").get()) ?: changelog.getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML,
            ),
        )
    }
}
