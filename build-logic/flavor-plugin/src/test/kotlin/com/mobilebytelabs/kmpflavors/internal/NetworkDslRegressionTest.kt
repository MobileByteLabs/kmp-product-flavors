/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.7 — TestKit regression for the `buildKonfig { network { baseUrl(); timeout() } }`
 * end-to-end emission path. Exercises the real plugin-application lifecycle (apply
 * plugin → consumer DSL → afterEvaluate → BuildKonfig codegen) so that the
 * `network` block in `KmpFlavorExtension.buildKonfigDsl` actually surfaces in the
 * generated `BuildKonfig.kt` for the active variant.
 *
 * Complements [com.mobilebytelabs.kmpflavors.tasks.BuildKonfigCodegenSnapshotTest]
 * (which tests the task in isolation via `ProjectBuilder`) by validating the
 * extension-to-task wiring works under a real Gradle build.
 */
class NetworkDslRegressionTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun writeFixture() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "network-dsl-regression"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildConfigPackage.set("com.example.net")
                generateBuildConfig.set(true)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                buildKonfig {
                    network {
                        baseUrl(
                            "free" to "https://api.free.example.com",
                            "paid" to "https://api.paid.example.com",
                        )
                        timeout(seconds = 45)
                    }
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )
    }

    private fun runGenerate() = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments("generateFlavorBuildConfig", "--warning-mode=all", "--no-configuration-cache")
        .withPluginClasspath()
        .forwardOutput()
        .build()

    @Test
    fun `network DSL emits BuildKonfig dot Network object with active flavor URL + timeout`() {
        writeFixture()
        runGenerate()

        val generated = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/net/BuildKonfig.kt",
        )
        assertTrue(generated.exists(), "expected BuildKonfig.kt at ${generated.absolutePath}")
        val content = generated.readText()
        assertTrue(content.contains("object Network {"), "Network block expected; output:\n$content")
        assertTrue(
            content.contains("BASE_URL: String = \"https://api.free.example.com\""),
            "BASE_URL should resolve to free (active flavor); output:\n$content",
        )
        assertTrue(
            content.contains("TIMEOUT_SECONDS: Int = 45"),
            "TIMEOUT_SECONDS should be 45 from DSL; output:\n$content",
        )
        assertFalse(
            content.contains("https://api.paid.example.com"),
            "Inactive flavor URL must not leak; output:\n$content",
        )
    }
}
