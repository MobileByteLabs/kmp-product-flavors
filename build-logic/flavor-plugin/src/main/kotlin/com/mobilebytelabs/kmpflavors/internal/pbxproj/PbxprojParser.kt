/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal.pbxproj

/**
 * Phase 12 — minimal OpenStep ASCII property list parser for pbxproj.
 * Handles only the subset of pbxproj we need:
 *   - { key = value; ... } dictionaries
 *   - ( a, b, c ) arrays
 *   - identifiers (24-char hex object IDs)
 *   - quoted strings "..."
 *   - bare strings (alphanumeric + dots + underscores + slashes)
 *   - /* comments */
 *
 * Public API: PbxprojParser(text).parse() -> PbxprojDocument
 */
internal class PbxprojParser(private val text: String) {
    private var pos = 0
    private val len = text.length

    fun parse(): PbxprojDocument {
        val header = readHeader()
        val root = parseValue() as? Map<*, *> ?: error("pbxproj root must be a dictionary")

        @Suppress("UNCHECKED_CAST")
        val rootMap = root as Map<String, Any?>
        val rootObject = rootMap["rootObject"]?.toString() ?: error("pbxproj missing rootObject")
        val archiveVersion = rootMap["archiveVersion"]?.toString() ?: "1"
        val objectVersion = rootMap["objectVersion"]?.toString() ?: "56"

        @Suppress("UNCHECKED_CAST")
        val objectsRaw = (rootMap["objects"] as? Map<String, Any?>) ?: emptyMap()
        val objects = mutableMapOf<String, PbxObject>()
        for ((id, valueAny) in objectsRaw) {
            @Suppress("UNCHECKED_CAST")
            val value = valueAny as? Map<String, Any?> ?: continue
            val mutable = LinkedHashMap<String, Any>()
            value.forEach { (k, v) -> if (v != null) mutable[k] = v }
            val isa = value["isa"]?.toString() ?: ""
            val annotation: String? = null
            val obj: PbxObject = when (isa) {
                "XCBuildConfiguration" -> PbxObject.XCBuildConfiguration(
                    id = id,
                    raw = mutable,
                    annotation = annotation,
                    name = value["name"]?.toString() ?: "",
                    baseConfigurationReference = value["baseConfigurationReference"]?.toString(),
                )

                "PBXFileReference" -> PbxObject.PBXFileReference(
                    id = id,
                    raw = mutable,
                    annotation = annotation,
                    path = value["path"]?.toString(),
                    sourceTree = value["sourceTree"]?.toString(),
                )

                "PBXGroup" -> PbxObject.PBXGroup(
                    id = id,
                    raw = mutable,
                    annotation = annotation,
                    children = (value["children"] as? List<*>)?.map { it.toString() }?.toMutableList()
                        ?: mutableListOf(),
                    name = value["name"]?.toString(),
                    path = value["path"]?.toString(),
                )

                else -> PbxObject.Raw(id, mutable, annotation, isa)
            }
            objects[id] = obj
        }
        return PbxprojDocument(rootObject, objects, header, archiveVersion, objectVersion)
    }

    private fun readHeader(): String {
        val start = pos
        // skip leading // !$*UTF8*$! line
        skipWhitespaceAndComments()
        if (pos < len && text[pos] == '/' && pos + 1 < len && text[pos + 1] == '/') {
            // bash-style comment: skip to end of line
            while (pos < len && text[pos] != '\n') pos++
            if (pos < len) pos++
        }
        skipWhitespaceAndComments()
        return text.substring(start, pos)
    }

    private fun parseValue(): Any? {
        skipWhitespaceAndComments()
        if (pos >= len) return null
        return when (val c = text[pos]) {
            '{' -> parseDictionary()
            '(' -> parseArray()
            '"' -> parseQuotedString()
            else -> if (c.isLetterOrDigit() || c in "._/$") parseBareString() else null
        }
    }

    private fun parseDictionary(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        while (true) {
            skipWhitespaceAndComments()
            if (pos >= len) error("unexpected EOF in dictionary")
            if (text[pos] == '}') {
                pos++
                break
            }
            val key = parseValue()?.toString() ?: error("expected key at $pos")
            skipWhitespaceAndComments()
            expect('=')
            val value = parseValue()
            result[key] = value
            skipWhitespaceAndComments()
            if (pos < len && text[pos] == ';') pos++
        }
        return result
    }

    private fun parseArray(): List<Any?> {
        expect('(')
        val result = mutableListOf<Any?>()
        while (true) {
            skipWhitespaceAndComments()
            if (pos >= len) error("unexpected EOF in array")
            if (text[pos] == ')') {
                pos++
                break
            }
            val value = parseValue()
            result += value
            skipWhitespaceAndComments()
            if (pos < len && text[pos] == ',') pos++
        }
        return result
    }

    private fun parseQuotedString(): String {
        expect('"')
        val sb = StringBuilder()
        while (pos < len) {
            val c = text[pos]
            if (c == '\\' && pos + 1 < len) {
                pos++
                sb.append(text[pos])
                pos++
                continue
            }
            if (c == '"') {
                pos++
                return sb.toString()
            }
            sb.append(c)
            pos++
        }
        error("unterminated string")
    }

    private fun parseBareString(): String {
        val start = pos
        while (pos < len) {
            val c = text[pos]
            if (c.isWhitespace() || c == ';' || c == ',' || c == '=' || c == '{' || c == '}' || c == '(' || c == ')') break
            // Allow /* comment */ between identifier and ; or =
            if (c == '/' && pos + 1 < len && text[pos + 1] == '*') break
            pos++
        }
        return text.substring(start, pos)
    }

    private fun skipWhitespaceAndComments() {
        while (pos < len) {
            val c = text[pos]
            when {
                c.isWhitespace() -> pos++

                c == '/' && pos + 1 < len && text[pos + 1] == '*' -> {
                    // block comment
                    pos += 2
                    while (pos + 1 < len && !(text[pos] == '*' && text[pos + 1] == '/')) pos++
                    pos += 2
                }

                else -> return
            }
        }
    }

    private fun expect(c: Char) {
        skipWhitespaceAndComments()
        if (pos >= len || text[pos] != c) {
            error("expected '$c' at $pos, got '${if (pos < len) text[pos] else "EOF"}'")
        }
        pos++
    }
}
