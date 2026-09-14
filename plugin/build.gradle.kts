import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

evaluationDependsOn(":agent:agent-bootstrap")
val agentJarTask = project(":agent:agent-bootstrap").tasks.named("shadowJar")

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    implementation(project(":plugin:plugin-core"))
    implementation(project(":plugin:plugin-debugger"))
    implementation(project(":plugin:plugin-ui"))
    implementation(project(":protocol"))
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation(libs.junit.jupiter.api)
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated-agent"))
        }
    }
}

val copyAgentJar by tasks.registering(Copy::class) {
    dependsOn(agentJarTask)
    from(agentJarTask)
    into(layout.buildDirectory.dir("generated-agent/agent"))
    rename { "runtime-pivot-agent.jar" }
}

tasks.processResources {
    dependsOn(copyAgentJar)
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        description = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        failureLevel = VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS.let { compatibility ->
            setOf(
                compatibility,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
                VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            )
        }
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    path.set(rootProject.file("CHANGELOG.md").absolutePath)
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named("publishPlugin") {
    dependsOn(tasks.named("patchChangelog"))
}

intellijPlatformTesting {
    testIde {
        register("unitTest") {
            task {
                group = "verification"
                description = "Runs fast unit tests that do not require an IntelliJ Platform fixture."
                useJUnit()
                filter { includeTestsMatching("com.runtime.pivot.plugin.unit.*") }
                systemProperty("java.awt.headless", "true")
                notCompatibleWithConfigurationCache("IntelliJ Platform test runtime is not configuration-cache compatible")
            }
        }
        register("integrationTest") {
            task {
                group = "verification"
                description = "Runs IntelliJ Platform integration tests (BasePlatformTestCase)."
                useJUnit()
                filter { includeTestsMatching("com.runtime.pivot.plugin.integration.*") }
                systemProperty("java.awt.headless", "true")
                notCompatibleWithConfigurationCache("IntelliJ Platform test runtime is not configuration-cache compatible")
            }
        }
        register("ideaUiTest") {
            task {
                group = "verification"
                description = "Runs headless Swing/UI component tests (no RemoteRobot server)."
                useJUnit()
                filter { includeTestsMatching("com.runtime.pivot.plugin.ui.*") }
                systemProperty("java.awt.headless", "true")
                notCompatibleWithConfigurationCache("IntelliJ Platform test runtime is not configuration-cache compatible")
            }
        }
    }

    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }
            plugins {
                robotServerPlugin()
            }
        }
    }
}
