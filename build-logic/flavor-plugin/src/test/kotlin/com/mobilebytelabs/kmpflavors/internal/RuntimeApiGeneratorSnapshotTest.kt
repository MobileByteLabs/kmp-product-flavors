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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.8 Wave B1 — Golden snapshot tests for [RuntimeApiGenerator].
 *
 * Fixtures at `src/test/resources/runtime-api-snapshots/` (6 `.kt` files). A synthetic spec
 * with package `com.example.snapshot` drives generation; each of the 6 emitted
 * files is compared byte-equal (trimmed trailing whitespace) against its golden.
 *
 * To update golden files: delete the relevant fixture, run this test, capture
 * the output, write it back. Intentionally no automated "update" flag —
 * snapshots should only change when template logic changes.
 */
class RuntimeApiGeneratorSnapshotTest {

    @TempDir
    lateinit var outputDir: File

    private val spec = RuntimeApiSpec("com.example.snapshot")

    private fun loadSnapshot(name: String): String = javaClass.classLoader
        .getResourceAsStream("runtime-api-snapshots/$name")
        ?.bufferedReader()?.readText()
        ?: error("Missing snapshot fixture: runtime-api-snapshots/$name")

    @Test
    fun `expect commonMain file matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.expect.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.kt (expect/commonMain) snapshot mismatch",
        )
    }

    @Test
    fun `android actual matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.android.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.android.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.android.kt snapshot mismatch",
        )
    }

    @Test
    fun `ios actual matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.ios.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.ios.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.ios.kt snapshot mismatch",
        )
    }

    @Test
    fun `desktop actual matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.desktop.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.desktop.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.desktop.kt snapshot mismatch",
        )
    }

    @Test
    fun `js actual matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.js.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.js.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.js.kt snapshot mismatch",
        )
    }

    @Test
    fun `wasmJs actual matches golden snapshot`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        val generated = files.first { it.name == "KmpFlavorsRuntime.wasmJs.kt" }
        assertEquals(
            loadSnapshot("KmpFlavorsRuntime.wasmJs.kt").trimEnd(),
            generated.readText().trimEnd(),
            "KmpFlavorsRuntime.wasmJs.kt snapshot mismatch",
        )
    }

    @Test
    fun `generate returns exactly 6 files`() {
        val files = RuntimeApiGenerator.generate(spec, outputDir)
        assertEquals(6, files.size, "Expected 6 generated files (1 expect + 5 actuals)")
    }
}
