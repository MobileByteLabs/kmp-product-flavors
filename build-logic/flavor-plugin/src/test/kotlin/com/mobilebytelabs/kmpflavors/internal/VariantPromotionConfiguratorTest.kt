/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.VariantPromotion
import com.mobilebytelabs.kmpflavors.VariantPromotionTransform
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VariantPromotionConfiguratorTest {

    @TempDir
    lateinit var tempDir: File

    private fun project() = ProjectBuilder.builder().withProjectDir(tempDir).build()
    private fun newFlavor(name: String): FlavorConfig =
        project().objects.newInstance(FlavorConfig::class.java, name)

    @Test
    fun `no promotions short-circuits without registering tasks`() {
        val proj = project()
        VariantPromotionConfigurator.configure(proj, emptyList(), emptyList(), proj.logger)
        assertEquals(0, proj.tasks.names.count { it.startsWith("promote") })
    }

    @Test
    fun `unknown from flavor warns and skips`() {
        val proj = project()
        val toFlavor = newFlavor("staging")
        val promotion = VariantPromotion(from = "ghost", to = "staging")
        VariantPromotionConfigurator.configure(proj, listOf(toFlavor), listOf(promotion), proj.logger)
        assertEquals(0, proj.tasks.names.count { it.startsWith("promote") })
    }

    @Test
    fun `unknown to flavor warns and skips`() {
        val proj = project()
        val fromFlavor = newFlavor("dev")
        val promotion = VariantPromotion(from = "dev", to = "ghost")
        VariantPromotionConfigurator.configure(proj, listOf(fromFlavor), listOf(promotion), proj.logger)
        assertEquals(0, proj.tasks.names.count { it.startsWith("promote") })
    }

    @Test
    fun `valid promotion registers promoteFromTo task`() {
        val proj = project()
        val dev = newFlavor("dev")
        val staging = newFlavor("staging")
        val promotion = VariantPromotion(from = "dev", to = "staging")
        VariantPromotionConfigurator.configure(proj, listOf(dev, staging), listOf(promotion), proj.logger)
        assertTrue(proj.tasks.names.contains("promoteDevToStaging"))
        val task = proj.tasks.getByName("promoteDevToStaging")
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("dev"))
        assertTrue(task.description!!.contains("staging"))
    }

    @Test
    fun `task action skips when source dir missing`() {
        val proj = project()
        val dev = newFlavor("dev")
        val staging = newFlavor("staging")
        val promotion = VariantPromotion(from = "dev", to = "staging")
        VariantPromotionConfigurator.configure(proj, listOf(dev, staging), listOf(promotion), proj.logger)
        val task = proj.tasks.getByName("promoteDevToStaging")
        // No src/commonDev directory exists → action exits early with warn.
        task.actions.first().execute(task)
        assertFalse(File(tempDir, "src/commonStaging").exists())
    }

    @Test
    fun `task action copies files to target dir`() {
        val proj = project()
        val srcDir = File(tempDir, "src/commonDev").apply { mkdirs() }
        File(srcDir, "Hello.kt").writeText("class Hello")
        val dev = newFlavor("dev")
        val staging = newFlavor("staging")
        val promotion = VariantPromotion(from = "dev", to = "staging")
        VariantPromotionConfigurator.configure(proj, listOf(dev, staging), listOf(promotion), proj.logger)
        val task = proj.tasks.getByName("promoteDevToStaging")
        task.actions.first().execute(task)
        assertTrue(File(tempDir, "src/commonStaging/Hello.kt").exists())
        assertEquals("class Hello", File(tempDir, "src/commonStaging/Hello.kt").readText())
    }

    @Test
    fun `task action applies renamePackage transform`() {
        val proj = project()
        val srcDir = File(tempDir, "src/commonDev").apply { mkdirs() }
        File(srcDir, "Net.kt").writeText("package com.example.dev\nclass Net")
        val dev = newFlavor("dev")
        val staging = newFlavor("staging")
        val promotion = VariantPromotion(from = "dev", to = "staging")
        promotion.transforms.add(VariantPromotionTransform("renamePackage", "com.example.dev", "com.example.staging"))
        VariantPromotionConfigurator.configure(proj, listOf(dev, staging), listOf(promotion), proj.logger)
        val task = proj.tasks.getByName("promoteDevToStaging")
        task.actions.first().execute(task)
        val content = File(tempDir, "src/commonStaging/Net.kt").readText()
        assertTrue(content.contains("package com.example.staging"))
        assertFalse(content.contains("package com.example.dev"))
    }

    @Test
    fun `dry-run mode does not write files`() {
        val proj = project()
        val srcDir = File(tempDir, "src/commonDev").apply { mkdirs() }
        File(srcDir, "X.kt").writeText("x")
        val dev = newFlavor("dev")
        val staging = newFlavor("staging")
        val promotion = VariantPromotion(from = "dev", to = "staging")
        VariantPromotionConfigurator.configure(proj, listOf(dev, staging), listOf(promotion), proj.logger)
        proj.extensions.extraProperties.set("dry-run", "true")
        val task = proj.tasks.getByName("promoteDevToStaging")
        task.actions.first().execute(task)
        // Dry-run: no files written.
        assertFalse(File(tempDir, "src/commonStaging/X.kt").exists())
    }
}
