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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * W3.2 — RFC §3 Q19-B `kmpFlavors.variants` public API acceptance.
 *
 * Consumer's build file exercises:
 *   `kmpFlavors.variants.configureEach { ... }`
 *   `kmpFlavors.variants.matching { it.flavors.contains("paid") }.configureEach { ... }`
 *
 * Verifies that the closures run at the expected configuration phase
 * and that variant fields (`name`, `flavors`, `buildType`) are
 * populated with the right values.
 */
class VariantApiTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "variant-api-test"
            """.trimIndent(),
        )
    }

    private fun writeBuild(extraVariantBlock: String) {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                $extraVariantBlock
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )
    }

    @Test
    fun `variants configureEach exposes name, flavors, and buildType to consumers`() {
        writeBuild(
            extraVariantBlock = """
                variants.configureEach {
                    // Capture variant fields BEFORE entering tasks.register; inside register's
                    // doLast {}, `name` refers to the Task name, not the variant name.
                    val variantName = name
                    val variantFlavors = flavors
                    val variantBuildType = buildType
                    project.tasks.register("describe${'$'}{variantName.replaceFirstChar { it.uppercase() }}Variant") {
                        doLast {
                            println("VARIANT[${'$'}variantName] flavors=${'$'}variantFlavors buildType=${'$'}variantBuildType")
                        }
                    }
                }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("describeFreeVariant", "describePaidVariant")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":describeFreeVariant")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":describePaidVariant")?.outcome)
        assertTrue(
            result.output.contains("VARIANT[free] flavors=[free] buildType=null"),
            "Expected free variant fields in output:\n${result.output}",
        )
        assertTrue(
            result.output.contains("VARIANT[paid] flavors=[paid] buildType=null"),
            "Expected paid variant fields in output:\n${result.output}",
        )
    }

    @Test
    fun `variants matching by predicate filters the collection (consumer customisation pattern)`() {
        writeBuild(
            extraVariantBlock = """
                variants.matching { it.flavors.contains("paid") }.configureEach {
                    val variantName = name
                    project.tasks.register("verify${'$'}{variantName.replaceFirstChar { it.uppercase() }}Paid") {
                        doLast { println("MATCHED: ${'$'}variantName") }
                    }
                }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("verifyPaidPaid")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyPaidPaid")?.outcome)
        assertTrue(result.output.contains("MATCHED: paid"))
    }
}
