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

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.CustomFieldDeclaration
import com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.5 Phase 3 — Snapshot test for [GenerateBuildConfigTask] codegen output across
 * the four new BuildKonfig DSL features (dimension enum / customField / perTarget
 * / secret placeholder).
 *
 * **Why snapshot tests:** the codegen is string-template based; any drift in
 * formatting (indentation, ordering, doc-comment text) MUST be a conscious choice.
 * Fixture files at `src/test/resources/buildkonfig-snapshots/{name}.kt.txt` capture
 * the exact expected output; the test diffs the generated output against the
 * fixture. Sealed by RULE-PROTO-RENDER-LLM-001 equivalent at framework level —
 * fixture changes require explicit reviewer attention.
 *
 * **Test isolation:** uses `ProjectBuilder` + direct task instantiation to bypass
 * the matrix-mode registrar wiring. Each test sets specific input properties and
 * compares the file output to the committed fixture. Snapshot-fixture format is
 * stable across v2.5+ — drift = test failure.
 *
 * See `plan-layer/.../v25-multidim-targets-buildkonfig/03-codegen.md` AC 14, 15,
 * 16, 20, 24.
 */
class BuildKonfigCodegenSnapshotTest {

    @TempDir
    lateinit var outputDir: File

    private fun newTask(): GenerateBuildConfigTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.create("snapshotTestTask", GenerateBuildConfigTask::class.java).apply {
            packageName.set("com.example.snapshot")
            className.set("BuildKonfig")
            outputDirectory.set(outputDir)
        }
    }

    private fun runAndReadOutput(task: GenerateBuildConfigTask): String {
        task.generate()
        val packageDir = File(outputDir, "com/example/snapshot")
        val generated = File(packageDir, "BuildKonfig.kt")
        assertTrue(generated.exists(), "expected BuildKonfig.kt at ${generated.absolutePath}")
        return generated.readText()
    }

    /**
     * Load the snapshot fixture by name. Fixtures live in
     * `src/test/resources/buildkonfig-snapshots/{name}.kt.txt` and are
     * hand-written to match the exact template output (the codegen uses
     * deterministic string templates — no kotlinpoet — so fixtures are
     * authoritative + reproducible by reading GenerateBuildConfigTask.generate()).
     */
    private fun fixture(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("buildkonfig-snapshots/$name.kt.txt")
            ?: error("snapshot fixture not found: buildkonfig-snapshots/$name.kt.txt")
        return stream.bufferedReader().readText()
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 14 — customField with sealed-class type
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 14 - customField with sealed-class type emits public val with type reference`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid", "dev", "prod"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.customFieldSpecs.set(
            listOf(
                CustomFieldDeclaration(
                    name = "config",
                    typeDescriptor = "com.example.MyConfig",
                    value = "com.example.MyConfig.Default",
                ),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(
            output.contains("val config: com.example.MyConfig = com.example.MyConfig.Default"),
            "Expected sealed-class customField emission in:\n$output",
        )
        assertEquals(fixture("2d-sealed-class"), output)
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 15 — enum(dimension) auto-generated sealed-class
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 15 - enum(dimension) emits sealed class plus active-flavor val`() {
        val task = newTask()
        task.variantName.set("freeDevPhone")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("free"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.dimensionEnumSpecs.set(
            listOf(
                DimensionEnumSpec(
                    dimensionName = "tier",
                    flavorNames = listOf("free", "paid"),
                    activeFlavorName = "free",
                ),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("sealed class Tier {"))
        assertTrue(output.contains("object Free : Tier()"))
        assertTrue(output.contains("object Paid : Tier()"))
        assertTrue(output.contains("val tier: Tier = Tier.Free"))
        assertEquals(fixture("3d-enum-tier"), output)
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 16 — perTarget conditional codegen as nested object
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 16 - perTarget(iosMain) emits nested object PerTarget IosMain`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.perTargetFieldSpecs.set(
            listOf(
                PerTargetFieldDeclaration(
                    name = "BUNDLE_ID_SUFFIX",
                    typeDescriptor = "String",
                    value = "\".dev\"",
                    targetName = "iosMain",
                ),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("object PerTarget {"))
        assertTrue(output.contains("object IosMain {"))
        assertTrue(output.contains("const val BUNDLE_ID_SUFFIX: String = \".dev\""))
        assertEquals(fixture("2d-pertarget-iosmain"), output)
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 20 — customField with List<T> type
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 20 - customField with List String type emits public val with listOf initializer`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.customFieldSpecs.set(
            listOf(
                CustomFieldDeclaration(
                    name = "scopes",
                    typeDescriptor = "List<String>",
                    value = "listOf(\"read\", \"write\")",
                ),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("val scopes: List<String> = listOf(\"read\", \"write\")"))
        assertEquals(fixture("2d-list-of-string"), output)
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 secret placeholder emission (SV15 compliance)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 secret(id) emits placeholder const val for SV15 compliance`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.buildKonfigSecretIds.set(listOf("api-key", "auth.token"))

        val output = runAndReadOutput(task)

        assertTrue(output.contains("const val API_KEY: String = \"<unresolved:see-docs-SECRETS_INTEGRATION>\""))
        assertTrue(output.contains("const val AUTH_TOKEN: String = \"<unresolved:see-docs-SECRETS_INTEGRATION>\""))
        // SV15 — no real secret values
        assertTrue(!output.contains("Bearer ") && !output.contains("sk_live_"))
    }
}
