/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal.pbxproj

/**
 * Phase 12 — pretty-print pbxproj document back to OpenStep ASCII format.
 * Preserves the basic shape used by Xcode (tabs for indent, newline + tab key=value;).
 */
internal class PbxprojWriter(private val document: PbxprojDocument) {
    private val sb = StringBuilder()

    fun write(): String {
        sb.append("// !$*UTF8*$!\n")
        sb.append("{\n")
        indent(1)
        sb.append("archiveVersion = ${document.archiveVersion};\n")
        indent(1)
        sb.append("classes = {\n")
        indent(1)
        sb.append("};\n")
        indent(1)
        sb.append("objectVersion = ${document.objectVersion};\n")
        indent(1)
        sb.append("objects = {\n")
        for ((id, obj) in document.objects.toSortedMap()) {
            writeObject(id, obj, 2)
        }
        indent(1)
        sb.append("};\n")
        indent(1)
        sb.append("rootObject = ${document.rootObject};\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun writeObject(id: String, obj: PbxObject, level: Int) {
        indent(level)
        sb.append(id)
        obj.annotation?.let { sb.append(" /* $it */") }
        sb.append(" = {\n")
        for ((k, v) in obj.raw.toSortedMap()) {
            indent(level + 1)
            sb.append("$k = ")
            writeValue(v, level + 1)
            sb.append(";\n")
        }
        indent(level)
        sb.append("};\n")
    }

    private fun writeValue(v: Any?, level: Int) {
        when (v) {
            null -> sb.append("\"\"")

            is Map<*, *> -> {
                sb.append("{\n")
                @Suppress("UNCHECKED_CAST")
                val map = v as Map<String, Any?>
                for ((k, vv) in map.toSortedMap()) {
                    indent(level + 1)
                    sb.append("$k = ")
                    writeValue(vv, level + 1)
                    sb.append(";\n")
                }
                indent(level)
                sb.append("}")
            }

            is List<*> -> {
                sb.append("(\n")
                for (item in v) {
                    indent(level + 1)
                    writeValue(item, level + 1)
                    sb.append(",\n")
                }
                indent(level)
                sb.append(")")
            }

            else -> sb.append(formatScalar(v.toString()))
        }
    }

    private fun formatScalar(s: String): String {
        if (s.isEmpty()) return "\"\""
        // Quote if contains space or special characters
        val needsQuotes = s.any { it.isWhitespace() || it in "\"\\'" }
        return if (needsQuotes) "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else s
    }

    private fun indent(level: Int) {
        repeat(level) { sb.append('\t') }
    }
}
