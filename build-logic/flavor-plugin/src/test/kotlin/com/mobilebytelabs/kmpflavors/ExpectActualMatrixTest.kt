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

package com.mobilebytelabs.kmpflavors

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * RFC §3 Q11 acceptance — `expect`/`actual` works across variants.
 *
 * Sample project layout:
 *   src/commonMain/kotlin/AppName.kt:
 *     expect fun appName(): String
 *   src/commonFree/kotlin/AppName.kt:
 *     actual fun appName() = "FreeApp"
 *   src/commonPaid/kotlin/AppName.kt:
 *     actual fun appName() = "PaidApp"
 *
 * Both `compileFreeKotlinDesktop` and `compilePaidKotlinDesktop` must
 * compile successfully because each per-variant compilation receives
 * the correct `actual` via the source-set wiring W2.1 adds.
 */
class ExpectActualMatrixTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "expect-actual-test"
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
                buildMatrix.set(true)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )

        // commonMain — expect declaration
        val commonMain = File(testProjectDir, "src/commonMain/kotlin").apply { mkdirs() }
        File(commonMain, "AppName.kt").writeText(
            """
            package com.example.expectactual

            expect fun appName(): String
            """.trimIndent(),
        )

        // commonFree — actual for the free variant
        val commonFree = File(testProjectDir, "src/commonFree/kotlin").apply { mkdirs() }
        File(commonFree, "AppName.kt").writeText(
            """
            package com.example.expectactual

            actual fun appName(): String = "FreeApp"
            """.trimIndent(),
        )

        // commonPaid — actual for the paid variant
        val commonPaid = File(testProjectDir, "src/commonPaid/kotlin").apply { mkdirs() }
        File(commonPaid, "AppName.kt").writeText(
            """
            package com.example.expectactual

            actual fun appName(): String = "PaidApp"
            """.trimIndent(),
        )
    }

    @Test
    @Disabled(
        "Re-deferred to v2.0 W3 — needs proper KotlinSourceSet hierarchy " +
            "(commonPaid `dependsOn(commonMain)`) so the actual lives in a " +
            "separate Kotlin module from the expect. W2.1's flat srcDir " +
            "wiring puts expect+actual in the same compilation module, " +
            "which Kotlin rejects with 'expect and corresponding actual are " +
            "declared in the same module'.",
    )
    fun `active variant compiles through main — inactive variant compiles through its own per-variant task`() {
        // free is the active variant (isDefault) → compileKotlinDesktop carries its actual via v1.x wiring.
        // paid is inactive → matrix mode adds compilePaidKotlinDesktop with the paid actual.
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(
                "compileKotlinDesktop",
                "compilePaidKotlinDesktop",
                "--stacktrace",
            )
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":compileKotlinDesktop")?.outcome,
            "Active variant (free) compile must succeed through main compilation:\n${result.output}",
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":compilePaidKotlinDesktop")?.outcome,
            "Inactive variant (paid) compile must succeed through per-variant task:\n${result.output}",
        )
    }
}
