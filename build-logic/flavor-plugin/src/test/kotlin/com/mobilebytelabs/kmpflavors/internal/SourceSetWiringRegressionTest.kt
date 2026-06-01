/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.mobilebytelabs.kmpflavors.internal

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.6 Tier E.1 — regression discipline for the "Unused Kotlin Source Sets"
 * warning fix (Hypothesis D: opt-in flag `createInactiveFlavorSourceSets`,
 * default `false`).
 *
 * Reproduces the consumer scenario surfaced 2026-06-01 (kmp-project-template
 * producing prod-only code in `src/commonProd/kotlin/` while building
 * `demoDebug` as the active variant) and asserts the warning does NOT fire
 * with the v2.6 default behaviour.
 *
 * The test also exercises the opt-in path: with the flag set to `true`, the
 * source set IS created and the consumer accepts that KGP will emit the
 * warning. This locks the contract that opting in restores the v2.5
 * behaviour exactly.
 *
 * Background: Hypothesis A (`dependsOn(commonMain)`) was tried in
 * v2.5.0-alpha.2 and disproved — KGP's check is compilation-membership-based,
 * not `dependsOn`-graph-based. See `docs/SOURCE_SET_DISCIPLINE.md` for the
 * full disproof + Hypothesis D rationale.
 */
class SourceSetWiringRegressionTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun writeSettingsAndBuild(createInactiveFlag: Boolean) {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "source-set-unused-warning-regression"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                ${if (createInactiveFlag) "createInactiveFlavorSourceSets.set(true)" else ""}
                flavors {
                    register("demo") { isDefault.set(true) }
                    register("prod")
                }
            }
            kotlin {
                jvm("desktop")
            }
            """.trimIndent(),
        )
        // Simulate the consumer scenario: prod-only file on disk → inactive
        // `commonProd` source set is a candidate for lazy creation.
        File(testProjectDir, "src/commonProd/kotlin/ProdMarker.kt").apply {
            parentFile.mkdirs()
            writeText("package com.example\ninternal val PROD_MARKER: String = \"prod\"\n")
        }
    }

    private fun runTasks(extraArgs: List<String> = emptyList()) =
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            // TestKit manages the daemon itself, so `--no-daemon` is not a valid arg here.
            // Configuration cache stays off so warning-mode aggregates per-configure properly.
            .withArguments(
                listOf("tasks", "--warning-mode=all", "--no-configuration-cache") + extraArgs,
            )
            .withPluginClasspath()
            .forwardOutput()
            .build()

    @Test
    fun `default (flag=false) — Unused Kotlin Source Sets warning does NOT fire for inactive flavor with on-disk content`() {
        writeSettingsAndBuild(createInactiveFlag = false)
        val result = runTasks()

        // KGP's actual warning line — match the specific phrasing rather than just
        // the heading (the plugin's own skip-log mentions the heading as documentation).
        assertFalse(
            result.output.contains("was configured but not added to any Kotlin compilation"),
            "Expected NO KGP 'Unused Kotlin Source Sets' warning when " +
                "createInactiveFlavorSourceSets=false (v2.6 default). Got output:\n" +
                result.output,
        )
        // The plugin should emit a structured warn explaining the skip so the
        // consumer notices their src/commonProd/ code is currently unreachable.
        assertTrue(
            result.output.contains("Skipping creation of inactive source set 'commonProd'"),
            "Expected the structured skip-log for commonProd; got:\n${result.output}",
        )
        // Positive: the desktop target is still present — active path unaffected.
        // `tasks` (without --all) shows lifecycle tasks like `desktopJar` / `desktopMainClasses`.
        assertTrue(
            result.output.contains("desktopJar") || result.output.contains("desktopMainClasses"),
            "Active path must still produce the desktop target lifecycle tasks:\n${result.output}",
        )
    }

    @Test
    fun `opt-in (flag=true) — source set IS created and KGP warning is accepted`() {
        writeSettingsAndBuild(createInactiveFlag = true)
        val result = runTasks()

        // With opt-in: the source set is created, KGP sees it as orphan, warning fires.
        // This is the contract the consumer opted into; we lock the behaviour so future
        // refactors don't accidentally silence it.
        assertTrue(
            result.output.contains("Unused Kotlin Source Sets") ||
                result.output.contains("commonProd"),
            "With createInactiveFlavorSourceSets=true, KGP should either emit the " +
                "'Unused Kotlin Source Sets' warning OR otherwise reference 'commonProd' " +
                "in its output (proving the source set was created). Got:\n${result.output}",
        )
        // The skip-log must NOT fire when opt-in is on.
        assertFalse(
            result.output.contains("Skipping creation of inactive source set 'commonProd'"),
            "Opt-in path must NOT emit the skip log:\n${result.output}",
        )
    }
}
