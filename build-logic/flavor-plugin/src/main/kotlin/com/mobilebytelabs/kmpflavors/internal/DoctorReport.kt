/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

/**
 * Phase 13 — consolidated validator report.
 */
internal enum class DoctorStatus { PASS, WARN, ERROR, SKIP }

internal data class DoctorResult(val code: String, val description: String, val status: DoctorStatus, val message: String? = null)

internal data class DoctorReport(val results: List<DoctorResult>) {
    val passes: List<DoctorResult> get() = results.filter { it.status == DoctorStatus.PASS }
    val warnings: List<DoctorResult> get() = results.filter { it.status == DoctorStatus.WARN }
    val errors: List<DoctorResult> get() = results.filter { it.status == DoctorStatus.ERROR }
    val skips: List<DoctorResult> get() = results.filter { it.status == DoctorStatus.SKIP }
}

internal object DoctorReportFormatter {

    fun toJson(report: DoctorReport): String = buildString {
        append("{\n")
        append("  \"totals\": {\n")
        append("    \"pass\": ${report.passes.size},\n")
        append("    \"warn\": ${report.warnings.size},\n")
        append("    \"error\": ${report.errors.size},\n")
        append("    \"skip\": ${report.skips.size}\n")
        append("  },\n")
        append("  \"results\": [\n")
        report.results.forEachIndexed { i, r ->
            append("    {")
            append("\"code\": \"${r.code}\", ")
            append("\"description\": \"${escapeJson(r.description)}\", ")
            append("\"status\": \"${r.status.name}\"")
            r.message?.let { append(", \"message\": \"${escapeJson(it)}\"") }
            append("}")
            if (i < report.results.size - 1) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}\n")
    }

    fun toHuman(report: DoctorReport): String = buildString {
        appendLine("━━━ kmp-product-flavors Doctor ━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        for (r in report.results) {
            val glyph = when (r.status) {
                DoctorStatus.PASS -> "✓"
                DoctorStatus.WARN -> "⚠"
                DoctorStatus.ERROR -> "✗"
                DoctorStatus.SKIP -> "·"
            }
            appendLine("  $glyph ${r.code} — ${r.description}")
            r.message?.let { appendLine("      $it") }
        }
        appendLine()
        appendLine(
            "  Totals: ${report.passes.size} PASS · ${report.errors.size} FAIL · " +
                "${report.warnings.size} WARN · ${report.skips.size} SKIP",
        )
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
}
