/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.9 — pins the "SPM by default, Pods strictly opt-in" contract for the generated
 * per-variant xcconfigs.
 *
 * Before v2.9 the `iosCocoapodsIntegration` flag had ZERO test coverage despite being a
 * public DSL surface, so neither its default nor its emitted text was protected.
 */
class IosPodsXcconfigOptInTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(pods: Boolean): GenerateIosFlavorXcconfigsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks
            .register("genXcconfigs", GenerateIosFlavorXcconfigsTask::class.java).get()
        task.flavorBundleSuffixes.set(mapOf("free" to ".free"))
        task.buildTypeBundleSuffixes.set(mapOf("Debug" to ""))
        task.bundleIdBaseExpr.set("")
        task.appId.set("com.example.app")
        task.developmentTeamExpr.set("")
        task.identityInclude.set("")
        task.cocoapodsIntegration.set(pods)
        task.podsTargetName.set("iosApp")
        task.outputDir.set(File(tempDir, "Configs"))
        return task
    }

    private fun generatedText(pods: Boolean): String {
        newTask(pods).generate()
        val configs = File(tempDir, "Configs").listFiles().orEmpty()
        return configs.joinToString("\n") { it.readText() }
    }

    @Test
    fun `default off — no Pods include is emitted`() {
        val text = generatedText(pods = false)
        assertFalse(text.contains("#include?"), text)
        assertFalse(text.contains("Pods-iosApp"), text)
    }

    @Test
    fun `opt-in emits an OPTIONAL include so a Pods-less clone still builds`() {
        val text = generatedText(pods = true)
        // `#include?` (optional) — never a hard `#include`, which would break any
        // checkout that has not run `pod install`.
        assertTrue(text.contains("#include?"), text)
        assertFalse(text.contains("\n#include \""), text)
        // CocoaPods lowercases the configuration name in its per-config xcconfig.
        assertTrue(text.contains("Pods-iosApp.freedebug.xcconfig"), text)
    }

    @Test
    fun `extension default is opt-out — SPM is the default distribution path`() {
        val project = ProjectBuilder.builder().build()
        val ext = project.objects.newInstance(KmpFlavorExtension::class.java)
        assertFalse(ext.iosIncludePodsXcconfig.get())
        // ...while SPM manifest generation is ON by default.
        assertTrue(ext.spm.generateManifest.get())
    }

    @Test
    fun `deprecated alias reads and writes the same property instance`() {
        val project = ProjectBuilder.builder().build()
        val ext = project.objects.newInstance(KmpFlavorExtension::class.java)
        @Suppress("DEPRECATION")
        ext.iosCocoapodsIntegration.set(true)
        assertTrue(ext.iosIncludePodsXcconfig.get())
        ext.iosIncludePodsXcconfig.set(false)
        @Suppress("DEPRECATION")
        assertFalse(ext.iosCocoapodsIntegration.get())
    }
}
