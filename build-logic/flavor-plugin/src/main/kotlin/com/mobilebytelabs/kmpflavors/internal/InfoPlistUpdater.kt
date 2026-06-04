/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Phase 12 — minimal Info.plist XML editor for the bundle ID interpolation + KMPF custom keys.
 * Uses regex-level edits rather than DOM parsing — the file is hand-edited shape that's stable.
 */
internal object InfoPlistUpdater {
    private const val BUNDLE_INTERP = "\$(PRODUCT_BUNDLE_IDENTIFIER)"
    private val BUNDLE_ID_REGEX = Regex(
        """<key>CFBundleIdentifier</key>\s*<string>[^<]*</string>""",
    )

    fun setBundleIdInterpolation(file: File): Boolean {
        val text = file.readText()
        if (Regex("""<key>CFBundleIdentifier</key>\s*<string>\$\(PRODUCT_BUNDLE_IDENTIFIER\)</string>""")
                .containsMatchIn(text)
        ) {
            return false
        }
        val updated = text.replace(
            BUNDLE_ID_REGEX,
            "<key>CFBundleIdentifier</key>\n\t<string>$BUNDLE_INTERP</string>",
        )
        writeAtomic(file, updated)
        return true
    }

    /**
     * Ensures KMPF custom keys are present so Phase 6 iOS actual can read them via NSBundle.
     */
    fun ensureKmpfKeys(file: File): Boolean {
        val text = file.readText()
        if (text.contains("<key>KMPFFlavorName</key>")) return false
        val additions = """
            |	<key>KMPFFlavorName</key>
            |	<string>${'$'}(KMPF_FLAVOR_NAME)</string>
            |	<key>KMPFBuildTypeName</key>
            |	<string>${'$'}(KMPF_BUILD_TYPE_NAME)</string>
            |	<key>KMPFIsDebug</key>
            |	<string>${'$'}(KMPF_IS_DEBUG)</string>
            |	<key>KMPFIsDemo</key>
            |	<string>${'$'}(KMPF_IS_DEMO)</string>
            |
        """.trimMargin()
        val updated = text.replace("</dict>\n</plist>", "$additions</dict>\n</plist>")
        writeAtomic(file, updated)
        return true
    }

    private fun writeAtomic(file: File, content: String) {
        val tempFile = File.createTempFile("plist-", ".tmp", file.parentFile)
        tempFile.writeText(content)
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
