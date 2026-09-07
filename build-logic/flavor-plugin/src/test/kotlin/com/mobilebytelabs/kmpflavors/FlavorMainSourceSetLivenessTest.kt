/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * `src/{flavor}Main/` must actually COMPILE for that flavor.
 *
 * `docs/CONSUMER_GUIDE.md` documents it as "sources compiled only for the `free` flavor" and
 * `docs/SOURCE_SET_DISCIPLINE.md` builds the whole single-axis model on it — but the
 * `{F}Main` source sets were created, given srcDirs, wired `dependsOn(commonMain)` and then
 * left with NO consumer. Nothing ever depended on `freeMain`, so code placed there was
 * silently never compiled, and the orphan nodes also showed up in KGP's
 * "Invalid Source Set Dependency Across Trees" output under a `'null' Tree`.
 *
 * The detector below is deliberately inverted: it puts BROKEN code in `src/freeMain/` and
 * requires the build to FAIL. A passing build proves the source set is dead.
 */
class FlavorMainSourceSetLivenessTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "flavor-main-liveness"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
            kmpFlavors {
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `code in src-freeMain is compiled for the active free flavor`() {
        // Deliberately unresolvable — if `freeMain` is live, the build MUST fail here.
        File(testProjectDir, "src/freeMain/kotlin").apply { mkdirs() }
            .let { File(it, "Canary.kt") }
            .writeText(
                """
                package com.example.liveness

                val canary: String = ThisSymbolDoesNotExist.value
                """.trimIndent(),
            )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compileKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("Unresolved reference"),
            "src/freeMain/ was never compiled — the documented `{F}Main` source set is dead:\n" +
                result.output.takeLast(2000),
        )
    }

    @Test
    fun `code in src-paidMain is NOT compiled for the active free flavor`() {
        // The other half of the contract: an inactive flavor's sources must stay out of the
        // active compilation, otherwise `{F}Main` would leak across flavors.
        File(testProjectDir, "src/paidMain/kotlin").apply { mkdirs() }
            .let { File(it, "Canary.kt") }
            .writeText(
                """
                package com.example.liveness

                val paidCanary: String = ThisSymbolDoesNotExist.value
                """.trimIndent(),
            )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compileKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(
            !result.output.contains("Unresolved reference"),
            "src/paidMain/ leaked into the active `free` compilation:\n${result.output.takeLast(2000)}",
        )
    }
}
