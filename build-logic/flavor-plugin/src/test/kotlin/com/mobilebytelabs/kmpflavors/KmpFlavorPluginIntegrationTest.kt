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
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

class KmpFlavorPluginIntegrationTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            """.trimIndent(),
        )
    }

    @Test
    fun `plugin applies successfully to KMP project`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free")
                    register("paid")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("listFlavors"))
        assertTrue(result.output.contains("validateFlavors"))
    }

    @Test
    fun `listFlavors task runs successfully`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavorDimensions {
                    register("tier") { priority.set(0) }
                }

                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("listFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":listFlavors")?.outcome)
        assertTrue(result.output.contains("free"))
        assertTrue(result.output.contains("paid"))
    }

    @Test
    fun `validateFlavors task passes with valid config`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavorDimensions {
                    register("tier") { priority.set(0) }
                    register("environment") { priority.set(1) }
                }

                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                    register("dev") {
                        dimension.set("environment")
                        isDefault.set(true)
                    }
                    register("prod") {
                        dimension.set("environment")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("validateFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validateFlavors")?.outcome)
        assertTrue(result.output.contains("Validation passed"))
    }

    @Test
    fun `generateFlavorBuildConfig creates config file`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.test")
                buildConfigClassName.set("TestConfig")

                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateFlavorBuildConfig", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFlavorBuildConfig")?.outcome)

        val generatedFile = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/test/TestConfig.kt",
        )
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("object TestConfig"))
        assertTrue(content.contains("const val VARIANT_NAME: String = \"free\""))
        assertTrue(content.contains("const val IS_PREMIUM: Boolean = false"))
    }

    @Test
    fun `active variant can be set via gradle property`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.test")

                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateFlavorBuildConfig", "-PkmpFlavor=paid", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFlavorBuildConfig")?.outcome)

        val generatedFile = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/test/FlavorConfig.kt",
        )
        val content = generatedFile.readText()
        assertTrue(content.contains("const val VARIANT_NAME: String = \"paid\""))
        assertTrue(content.contains("const val IS_PREMIUM: Boolean = true"))
    }

    @Test
    fun `plugin creates flavor source sets`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.github.anthropic.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free") {
                        isDefault.set(true)
                    }
                    register("paid")
                }
            }

            tasks.register("printSourceSets") {
                doLast {
                    kotlin.sourceSets.forEach { sourceSet ->
                        println("SourceSet: ${'$'}{sourceSet.name}")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("printSourceSets", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("SourceSet: commonFree"))
        assertTrue(result.output.contains("SourceSet: commonPaid"))
        assertTrue(result.output.contains("SourceSet: desktopFree"))
        assertTrue(result.output.contains("SourceSet: desktopPaid"))
    }
}
