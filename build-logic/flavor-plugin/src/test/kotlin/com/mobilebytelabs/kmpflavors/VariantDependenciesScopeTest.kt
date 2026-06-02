/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariantDependenciesScopeTest {

    private fun newScope(): VariantDependenciesScope =
        ProjectBuilder.builder().build().objects.newInstance(VariantDependenciesScope::class.java)

    @Test
    fun `excludes starts empty`() {
        assertEquals(emptyList<VariantDependenciesScope.Exclude>(), newScope().excludes)
    }

    @Test
    fun `exclude appends an Exclude entry`() {
        val scope = newScope()
        scope.exclude(group = "com.example", module = "premium-sdk")
        assertEquals(1, scope.excludes.size)
        assertEquals("com.example", scope.excludes[0].group)
        assertEquals("premium-sdk", scope.excludes[0].module)
    }

    @Test
    fun `multiple excludes accumulate in order`() {
        val scope = newScope()
        scope.exclude("a", "x")
        scope.exclude("b", "y")
        scope.exclude("c", "z")
        assertEquals(listOf("a", "b", "c"), scope.excludes.map { it.group })
        assertEquals(listOf("x", "y", "z"), scope.excludes.map { it.module })
    }

    @Test
    fun `empty-string group is allowed (wildcard semantics)`() {
        val scope = newScope()
        scope.exclude(group = "", module = "premium-sdk")
        assertEquals("", scope.excludes[0].group)
    }

    @Test
    fun `Exclude data class equality`() {
        val a = VariantDependenciesScope.Exclude("a", "b")
        val b = VariantDependenciesScope.Exclude("a", "b")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
