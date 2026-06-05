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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [V27MigrationDetector] — exercises scan + rewrite logic directly
 * so Kover can instrument the production code paths (TestKit daemon bypass).
 */
class V27MigrationDetectorTest {

    @TempDir
    lateinit var projectDir: File

    // ── AppFlavor.kt detection ────────────────────────────────────────────────

    @Test
    fun `scan detects AppFlavor_kt under cmp-android`() {
        val appFlavor = File(projectDir, "cmp-android/src/androidMain/kotlin/AppFlavor.kt")
        appFlavor.parentFile.mkdirs()
        appFlavor.writeText("class AppFlavor")

        val findings = V27MigrationDetector.scan(projectDir)

        assertEquals(1, findings.size)
        assertTrue(findings[0] is V27MigrationDetector.DeleteFile)
        assertEquals(appFlavor, findings[0].file)
    }

    @Test
    fun `scan does not detect AppFlavor_kt outside cmp-android`() {
        val appFlavor = File(projectDir, "app/src/main/kotlin/AppFlavor.kt")
        appFlavor.parentFile.mkdirs()
        appFlavor.writeText("class AppFlavor")

        val findings = V27MigrationDetector.scan(projectDir)

        assertTrue(findings.isEmpty(), "AppFlavor.kt outside cmp-android should not be detected")
    }

    // ── KMPFlavorsConventionPlugin.kt detection ───────────────────────────────

    @Test
    fun `scan detects KMPFlavorsConventionPlugin with configureFlavors`() {
        val plugin = File(projectDir, "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt")
        plugin.parentFile.mkdirs()
        plugin.writeText(
            """
            import com.android.build.api.dsl.CommonExtension
            class KMPFlavorsConventionPlugin {
                // v2.7 — pure-AGP workaround
                private fun CommonExtension<*, *, *, *, *, *>.configureFlavors(common: CommonExtension<*, *, *, *, *, *>) {
                    common.productFlavors { create("demo") }
                }
            }
            """.trimIndent(),
        )

        val findings = V27MigrationDetector.scan(projectDir)

        assertEquals(1, findings.size)
        assertTrue(findings[0] is V27MigrationDetector.RewriteFile)
    }

    @Test
    fun `scan returns empty when no v2_7 telltales found`() {
        val plugin = File(projectDir, "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt")
        plugin.parentFile.mkdirs()
        plugin.writeText("class KMPFlavorsConventionPlugin { }")

        val findings = V27MigrationDetector.scan(projectDir)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `scan returns both findings when both telltales present`() {
        val appFlavor = File(projectDir, "cmp-android/src/androidMain/kotlin/AppFlavor.kt")
        appFlavor.parentFile.mkdirs()
        appFlavor.writeText("class AppFlavor")

        val plugin = File(projectDir, "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt")
        plugin.parentFile.mkdirs()
        plugin.writeText("fun SomeExtension.configureFlavors() { }")

        val findings = V27MigrationDetector.scan(projectDir)

        assertEquals(2, findings.size)
    }

    // ── DeleteFile ────────────────────────────────────────────────────────────

    @Test
    fun `DeleteFile diff contains DELETE prefix`() {
        val file = File(projectDir, "AppFlavor.kt")
        file.writeText("")
        val finding = V27MigrationDetector.DeleteFile(file)

        assertTrue(finding.diff().startsWith("DELETE"))
    }

    @Test
    fun `DeleteFile apply deletes the file`() {
        val file = File(projectDir, "AppFlavor.kt")
        file.writeText("content")
        val finding = V27MigrationDetector.DeleteFile(file)

        finding.apply()

        assertFalse(file.exists(), "File should be deleted after apply()")
    }

    // ── RewriteFile ───────────────────────────────────────────────────────────

    @Test
    fun `RewriteFile diff contains REWRITE prefix`() {
        val file = File(projectDir, "Plugin.kt")
        file.writeText("old")
        val finding = V27MigrationDetector.RewriteFile(file, "new", "test reason")

        assertTrue(finding.diff().startsWith("REWRITE"))
        assertTrue(finding.diff().contains("test reason"))
    }

    @Test
    fun `RewriteFile apply writes new content`() {
        val file = File(projectDir, "Plugin.kt")
        file.writeText("old content")
        val finding = V27MigrationDetector.RewriteFile(file, "new content", "drop v2.7")

        finding.apply()

        assertEquals("new content", file.readText())
    }

    // ── rewriteConventionPlugin via scan+apply ────────────────────────────────

    @Test
    fun `rewrite removes configureFlavors call site and extension function`() {
        val plugin = File(projectDir, "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt")
        plugin.parentFile.mkdirs()
        plugin.writeText(
            """
            class MyPlugin {
                fun apply() {
                    // v2.7 — pure-AGP workaround
                    configureFlavors(this)
                }

                private fun SomeExtension<*>.configureFlavors(ext: SomeExtension<*>) {
                    ext.productFlavors { create("demo") }
                }
            }
            """.trimIndent(),
        )

        val findings = V27MigrationDetector.scan(projectDir)
        assertEquals(1, findings.size)
        findings[0].apply()

        val rewritten = plugin.readText()
        assertFalse(rewritten.contains("configureFlavors"), "configureFlavors must be removed")
        assertFalse(rewritten.contains("v2.7"), "v2.7 comment must be replaced")
        assertTrue(rewritten.contains("v2.8"), "v2.8 comment must be present")
    }
}
