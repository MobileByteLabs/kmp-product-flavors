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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * RFC §3 Q12 acceptance — cross-variant code isolation (negative test).
 *
 * Symbols declared in `commonPaid/` MUST NOT be resolvable from
 * `commonFree/`. In v1.x active-variant-only mode this "worked" by
 * accident because inactive variant source sets weren't on the
 * classpath. In v2.0 matrix mode BOTH compilations register and run
 * — this test confirms cross-variant references still fail to
 * compile with `Unresolved reference: PaidOnly`.
 *
 * The negative test is critical because it gates the W2 source-set
 * wiring: if `commonFree` accidentally inherits from `commonPaid`
 * (or vice versa) via a wrong `dependsOn` edge, the compile error
 * disappears and we lose the safety net.
 */
class CrossVariantIsolationTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "isolation-test"
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

        // commonPaid declares a symbol; commonFree leaks into it.
        File(testProjectDir, "src/commonPaid/kotlin").apply { mkdirs() }
            .let { File(it, "PaidOnly.kt") }
            .writeText(
                """
                package com.example.isolation

                object PaidOnly { const val SECRET: String = "paid-only" }
                """.trimIndent(),
            )
        File(testProjectDir, "src/commonFree/kotlin").apply { mkdirs() }
            .let { File(it, "LeakySite.kt") }
            .writeText(
                """
                package com.example.isolation

                // Intentional leak: importing a commonPaid-only symbol from commonFree.
                fun leak(): String = PaidOnly.SECRET
                """.trimIndent(),
            )
    }

    @Test
    fun `referencing a commonPaid symbol from commonFree fails to compile`() {
        // free is the active variant → commonFree is wired into the main
        // compilation via v1.x SourceSetConfigurator. LeakySite.kt's reference
        // to PaidOnly (which lives only in commonPaid) must fail to compile.
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compileKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("Unresolved reference") &&
                result.output.contains("PaidOnly"),
            "Expected 'Unresolved reference: PaidOnly' compile error. Got:\n${result.output}",
        )
    }
}
