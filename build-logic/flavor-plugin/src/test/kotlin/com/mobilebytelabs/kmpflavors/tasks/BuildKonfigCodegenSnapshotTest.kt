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

    // ─────────────────────────────────────────────────────────────────────
    // v2.6 Phase 4 — network() block emission
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-6 network block emits BuildKonfig dot Network with active variant's BASE_URL and TIMEOUT_SECONDS`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                baseUrls = mapOf(
                    "free" to "https://api.free.example.com",
                    "paid" to "https://api.paid.example.com",
                ),
                timeoutSeconds = 30,
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("object Network {"), "Network block expected in output:\n$output")
        assertTrue(
            output.contains("const val BASE_URL: String = \"https://api.free.example.com\""),
            "BASE_URL should resolve to the active variant ('free') URL; output:\n$output",
        )
        assertTrue(
            output.contains("const val TIMEOUT_SECONDS: Int = 30"),
            "TIMEOUT_SECONDS should match the spec value; output:\n$output",
        )
        // Active variant resolution: 'paid' baseUrl must NOT leak into a 'free*' variant.
        assertTrue(
            !output.contains("https://api.paid.example.com"),
            "Inactive variant URL must not appear; output:\n$output",
        )
    }

    @Test
    fun `v2-6 network block resolves paid variant to paid URL`() {
        val task = newTask()
        task.variantName.set("paidProd")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("paid", "prod"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                baseUrls = mapOf(
                    "free" to "https://api.free.example.com",
                    "paid" to "https://api.paid.example.com",
                ),
                timeoutSeconds = 60,
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("const val BASE_URL: String = \"https://api.paid.example.com\""))
        assertTrue(output.contains("const val TIMEOUT_SECONDS: Int = 60"))
        assertTrue(!output.contains("https://api.free.example.com"))
    }

    @Test
    fun `v2-6 network block is omitted when no networkConfigSpec is set`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        // Intentionally do NOT call task.networkConfigSpec.set(...)

        val output = runAndReadOutput(task)

        assertTrue(!output.contains("object Network {"), "Network block must be absent when no spec set")
        assertTrue(!output.contains("BASE_URL"))
        assertTrue(!output.contains("TIMEOUT_SECONDS"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.7 — coverage gap closure for previously-uncovered emit branches
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-7 network block with no active flavor match emits sentinel BASE_URL`() {
        val task = newTask()
        task.variantName.set("ghostDev")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("ghost", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                baseUrls = mapOf("free" to "https://api.free.example.com", "paid" to "https://api.paid.example.com"),
                timeoutSeconds = 30,
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("object Network {"))
        assertTrue(
            output.contains("BASE_URL: String = \"<no baseUrl mapped for active variant>\""),
            "Sentinel placeholder should appear when no flavor matches; output:\n$output",
        )
    }

    @Test
    fun `v2-7 network block with non-default timeout 120 emits TIMEOUT_SECONDS 120`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                baseUrls = mapOf("free" to "https://x"),
                timeoutSeconds = 120,
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("TIMEOUT_SECONDS: Int = 120"))
    }

    @Test
    fun `v2-7 network block with single-entry baseUrl map emits correct URL`() {
        val task = newTask()
        task.variantName.set("singleActive")
        task.allFlavorNames.set(setOf("only"))
        task.activeFlavorNames.set(setOf("only"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(
                baseUrls = mapOf("only" to "https://api.only.example.com"),
                timeoutSeconds = 15,
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("BASE_URL: String = \"https://api.only.example.com\""))
        assertTrue(output.contains("TIMEOUT_SECONDS: Int = 15"))
    }

    @Test
    fun `v2-7 empty networkConfigSpec baseUrls map skips Network block emission`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.networkConfigSpec.set(
            com.mobilebytelabs.kmpflavors.NetworkConfigSpec(baseUrls = emptyMap()),
        )

        val output = runAndReadOutput(task)

        assertTrue(!output.contains("object Network {"), "Empty baseUrls must skip Network block; output:\n$output")
    }

    @Test
    fun `v2-7 customField with primitive type emits public val without type prefix`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.customFieldSpecs.set(
            listOf(
                com.mobilebytelabs.kmpflavors.CustomFieldDeclaration(
                    name = "maxRetries",
                    typeDescriptor = "Int",
                    value = "5",
                ),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("val maxRetries: Int = 5"))
    }

    @Test
    fun `v2-7 multiple customField declarations preserve insertion order in output`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.customFieldSpecs.set(
            listOf(
                com.mobilebytelabs.kmpflavors.CustomFieldDeclaration("first", "String", "\"a\""),
                com.mobilebytelabs.kmpflavors.CustomFieldDeclaration("second", "String", "\"b\""),
                com.mobilebytelabs.kmpflavors.CustomFieldDeclaration("third", "String", "\"c\""),
            ),
        )

        val output = runAndReadOutput(task)

        val idxFirst = output.indexOf("val first:")
        val idxSecond = output.indexOf("val second:")
        val idxThird = output.indexOf("val third:")
        assertTrue(idxFirst in 0..<idxSecond, "first must appear before second in output")
        assertTrue(idxSecond < idxThird, "second must appear before third in output")
    }

    @Test
    fun `v2-7 perTarget block emits one nested PerTarget object per target`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        task.perTargetFieldSpecs.set(
            listOf(
                com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration("RUNTIME_FLAG", "Boolean", "true", "iosMain"),
                com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration("API_TIER", "String", "\"premium\"", "iosMain"),
                com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration("ENABLE_DEBUG", "Boolean", "true", "desktopMain"),
            ),
        )

        val output = runAndReadOutput(task)

        assertTrue(output.contains("object PerTarget"))
        assertTrue(output.contains("RUNTIME_FLAG"))
        assertTrue(output.contains("API_TIER"))
        assertTrue(output.contains("ENABLE_DEBUG"))
    }
}
