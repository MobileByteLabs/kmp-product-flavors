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
 * v2.9 Q17 — INACTIVE matrix-variant compilations must resolve the dependencies declared
 * in `commonMain`.
 *
 * Until v2.9 they inherited those dependencies implicitly, through the
 * `variantSourceSet -> commonFlavor -> commonMain` `dependsOn` chain. That chain is exactly
 * what made KGP emit "Invalid Source Set Dependency Across Trees", because it pulled
 * `commonMain` into every variant's Source Set Tree. Removing the edge silences the warning
 * but would ALSO silently drop every `commonMain` dependency from inactive variant
 * compilations — turning a warning into broken builds for any consumer that uses a library.
 *
 * This test pins the replacement contract: dependencies now flow through
 * `extendsFrom` on the variant source set's own dependency configurations, so a symbol from
 * a `commonMain` library still compiles inside an inactive variant's compilation.
 *
 * It uses `kotlinx-datetime` (a real multiplatform artifact) so the assertion exercises a
 * genuine external classpath rather than a project-local stub.
 */
class MatrixVariantDependencyInheritanceTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "variant-dep-test"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                jvm("desktop")
                sourceSets {
                    val commonMain by getting {
                        dependencies {
                            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                        }
                    }
                }
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                buildMatrix.set(true)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )

        // Code in the INACTIVE flavor (`paid`) that uses the commonMain library. It only
        // compiles if the inactive variant compilation inherited commonMain's dependencies.
        File(testProjectDir, "src/commonPaid/kotlin").apply { mkdirs() }
            .let { File(it, "PaidClock.kt") }
            .writeText(
                """
                package com.example.variantdep

                import kotlinx.datetime.Clock

                object PaidClock {
                    fun nowEpochSeconds(): Long = Clock.System.now().epochSeconds
                }
                """.trimIndent(),
            )
    }

    @Test
    fun `an inactive variant compilation resolves a commonMain library dependency`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compilePaidKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(
            !result.output.contains("Unresolved reference"),
            "commonMain dependency did not reach the inactive variant compilation:\n${result.output}",
        )
    }

    @Test
    fun `per-flavor source-set nodes are no longer shared across variant trees`() {
        // v2.9 fix: variants share flavor DIRECTORIES, not source-set NODES, so no
        // `common{Flavor}` node appears as a cross-tree root any more.
        //
        // KNOWN REMAINING GAP: `commonMain` / `commonTest` are still cross-tree roots.
        // Dropping those edges was implemented and measured (dependencies re-established
        // via `extendsFrom`); it trades 19 cross-tree warnings for 112 "Missing 'dependsOn'
        // in Source Sets" warnings, because KGP already includes commonMain in the variant
        // compilation. Closing it needs the intermediate source sets removed entirely,
        // which collides with expect/actual placement. Asserted here so the improvement
        // that DID land cannot regress.
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--stacktrace")
            .withPluginClasspath()
            .build()

        val flavorNodeRoots = result.output.lines()
            .filter { it.contains("can't depend on") }
            .filter { it.contains("'commonFree'") || it.contains("'commonPaid'") }
        assertTrue(
            flavorNodeRoots.isEmpty(),
            "Per-flavor source sets must not be cross-tree roots any more, got:\n$flavorNodeRoots",
        )
    }
}
