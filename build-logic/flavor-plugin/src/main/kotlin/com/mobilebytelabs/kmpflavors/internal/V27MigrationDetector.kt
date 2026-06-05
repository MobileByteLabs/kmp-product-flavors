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

import java.io.File

/**
 * v2.8 Wave B2 — Scans a consumer project directory for v2.7 telltales and
 * produces a list of [Finding]s (delete or rewrite operations) to migrate to v2.8.
 */
internal object V27MigrationDetector {

    sealed interface Finding {
        val file: File
        fun diff(): String
        fun apply()
    }

    data class DeleteFile(override val file: File) : Finding {
        override fun diff(): String = "DELETE  ${file.path}"
        override fun apply() {
            file.delete()
        }
    }

    data class RewriteFile(override val file: File, val newContent: String, val reason: String) : Finding {
        override fun diff(): String = "REWRITE ${file.path} ($reason)"
        override fun apply() {
            file.writeText(newContent)
        }
    }

    /**
     * Scans [projectRoot] for v2.7 migration telltales.
     * Returns an empty list when the project already looks like v2.8.
     */
    fun scan(projectRoot: File): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Telltale 1 — AppFlavor.kt workaround file (v2.7 pure-AGP limitation).
        projectRoot.walk()
            .filter { it.isFile && it.name == "AppFlavor.kt" && it.path.contains("cmp-android") }
            .forEach { findings += DeleteFile(it) }

        // Telltale 2 — KMPFlavorsConventionPlugin.kt that contains the v2.7
        // `configureFlavors` extension function (matches both regular and extension fun syntax).
        projectRoot.walk()
            .filter { it.isFile && it.name == "KMPFlavorsConventionPlugin.kt" }
            .filter { it.readText().contains("configureFlavors(") }
            .forEach { file ->
                val rewritten = rewriteConventionPlugin(file.readText())
                findings += RewriteFile(file, rewritten, "drop v2.7 configureFlavors extension")
            }

        return findings
    }

    private fun rewriteConventionPlugin(v27Content: String): String = v27Content
        // Remove the private extension function definition (handles generic receiver types).
        .replace(
            Regex(
                """(?ms)^\s*private fun\s+[^\n]+\.configureFlavors\([^)]*\)\s*\{.*?^\s*\}\s*\n?""",
            ),
            "",
        )
        // Remove the call site on its own line.
        .replace(Regex("""(?m)^\s*configureFlavors\([^)]*\)\s*\n?"""), "")
        // Replace the v2.7 comment with v2.8.
        .replace("// v2.7 — pure-AGP workaround", "// v2.8 — plugin handles AGP directly")
        .trimEnd() + "\n"
}
