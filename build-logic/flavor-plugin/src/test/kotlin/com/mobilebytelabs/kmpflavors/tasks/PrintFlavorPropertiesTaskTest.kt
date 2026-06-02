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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrintFlavorPropertiesTaskTest {

    private fun newTask(): PrintFlavorPropertiesTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("printFlavorProperties", PrintFlavorPropertiesTask::class.java).get()
    }

    @Test
    fun `print runs without throwing when all suffixes set`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.applicationIdSuffix.set(".free.dev")
        task.bundleIdSuffix.set(".free.dev")
        task.versionNameSuffix.set("-free-dev")
        task.desktopTitleSuffix.set("Free Dev")
        task.webTitleSuffix.set("Free Dev")
        task.print()
    }

    @Test
    fun `print runs without throwing when suffixes are unset`() {
        val task = newTask()
        task.variantName.set("paid")
        // Suffixes remain unset.
        task.print()
    }

    @Test
    fun `task group and description set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("platform-specific properties"))
    }
}
