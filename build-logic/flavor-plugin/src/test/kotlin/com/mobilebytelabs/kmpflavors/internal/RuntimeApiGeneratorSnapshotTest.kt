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
 * Tests for [RuntimeApiGenerator].
 *
 * Since v2.8.3 the runtime is a SINGLE concrete `object` in commonMain (no
 * expect/actual). A commonMain `expect` cannot reach a module's per-variant
 * compilations (`compileDevKotlinDesktop` etc.) — the platform `actual`s are
 * replayed into variant compilations but the `expect` can't follow, yielding
 * orphan `actual`s on any KMP module that combines build-type variants with a
 * desktop (jvm) target. The concrete common object compiles on every platform
 * AND every per-variant compilation with the variant values resolved at codegen.
 */
class RuntimeApiGeneratorSnapshotTest {

    @TempDir
    lateinit var outputDir: File

    private val spec = RuntimeApiSpec("com.example.snapshot")
    private val hint = RuntimeVariantHint(
        flavorName = "demo",
        buildTypeName = "debug",
        bundleId = "com.example.app.demo",
        appDisplayName = "Example App",
        appVersion = "1.2.3",
        isDemo = true,
        isDebug = true,
    )

    @Test
    fun `generate returns exactly one commonMain file`() {
        val files = RuntimeApiGenerator.generate(spec, hint, outputDir)
        assertEquals(1, files.size, "Expected exactly 1 generated file (concrete commonMain object)")
        assertEquals("KmpFlavorsRuntime.kt", files.first().name)
        assertTrue(
            files.first().absolutePath.replace(File.separatorChar, '/').contains("/commonMain/"),
            "Runtime file must land in commonMain",
        )
    }

    @Test
    fun `generated runtime is a concrete object with no expect or actual`() {
        val text = RuntimeApiGenerator.generate(spec, hint, outputDir).first().readText()
        assertTrue(text.contains("public object KmpFlavorsRuntime"), "must be a concrete object")
        assertFalse(text.contains("expect"), "must not declare expect")
        assertFalse(text.contains("actual"), "must not declare actual")
        assertTrue(text.contains("package com.example.snapshot"), "must carry the spec package")
    }

    @Test
    fun `generated runtime carries the resolved variant values`() {
        val text = RuntimeApiGenerator.generate(spec, hint, outputDir).first().readText()
        assertTrue(text.contains("flavorName: String = \"demo\""), "flavorName constant from hint")
        assertTrue(text.contains("buildTypeName: String = \"debug\""), "buildTypeName constant from hint")
        assertTrue(text.contains("isDebug: Boolean = true"), "isDebug constant from hint")
        assertTrue(text.contains("isDemo: Boolean = true"), "isDemo constant from hint")
        // Identity fields are populated from the hint — never empty stubs.
        assertTrue(text.contains("bundleId: String = \"com.example.app.demo\""), "bundleId constant from hint")
        assertTrue(text.contains("appDisplayName: String = \"Example App\""), "appDisplayName constant from hint")
        assertTrue(text.contains("appVersion: String = \"1.2.3\""), "appVersion constant from hint")
        assertFalse(text.contains("bundleId: String = \"\""), "bundleId must not be an empty stub")
        assertFalse(text.contains("appDisplayName: String = \"\""), "appDisplayName must not be an empty stub")
        assertFalse(text.contains("appVersion: String = \"\""), "appVersion must not be an empty stub")
        // Full public API surface preserved for source compatibility.
        listOf("applicationId", "fun get(key: String)")
            .forEach { assertTrue(text.contains(it), "runtime API must expose $it") }
    }
}
