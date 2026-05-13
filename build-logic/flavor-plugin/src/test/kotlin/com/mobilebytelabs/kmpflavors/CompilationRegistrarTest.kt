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

import com.mobilebytelabs.kmpflavors.internal.CompilationRegistrar
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * W1.2 — verifies the load-bearing piece of v2.0: per-variant
 * KotlinCompilation registration on a single KMP target.
 *
 * RFC §3 Q1-B: task naming convention `compileFreeDevKotlinDesktop`
 * is produced automatically by KGP from `compilations.create("freeDev")`
 * — the registrar just supplies the variant name.
 *
 * RFC §3 Q12 acceptance: variant compilations must `associateWith(main)`
 * so the variant resolves main's classpath. Tested here.
 */
class CompilationRegistrarTest {

    private val logger = mockk<Logger>(relaxed = true)
    private val main = mockk<KotlinCompilation<*>>(relaxed = true) {
        every { name } returns "main"
    }
    private lateinit var container: NamedDomainObjectContainer<KotlinCompilation<*>>
    private lateinit var target: KotlinTarget
    private val createdByName = mutableMapOf<String, KotlinCompilation<*>>()

    @BeforeEach
    fun setup() {
        createdByName.clear()
        container = mockk(relaxed = true)
        every { container.getByName("main") } returns main
        // Relaxed mocks return a generic Object for generic-typed returns, which
        // fails the bridge-method checkcast to KotlinCompilation. Explicitly return
        // null for "variant not yet registered" and let tests override per-name
        // when they need an existing compilation.
        every { container.findByName(any<String>()) } returns null
        every { container.create(any<String>()) } answers {
            val name = firstArg<String>()
            val mock = mockk<KotlinCompilation<*>>(relaxed = true) {
                every { this@mockk.name } returns name
            }
            createdByName[name] = mock
            // Wire the new mock to be returned by future findByName lookups so the
            // registrar's idempotency check sees the prior create.
            every { container.findByName(name) } returns mock
            mock
        }
        target = mockk(relaxed = true) {
            every { name } returns "desktop"
            @Suppress("UNCHECKED_CAST")
            every { compilations } returns container as NamedDomainObjectContainer<Nothing>
        }
    }

    @Test
    fun `register creates one compilation per variant`() {
        CompilationRegistrar.register(
            target = target,
            variantNames = listOf("freeDev", "freeProd", "paidDev", "paidProd"),
            logger = logger,
        )

        verify(exactly = 1) { container.create("freeDev") }
        verify(exactly = 1) { container.create("freeProd") }
        verify(exactly = 1) { container.create("paidDev") }
        verify(exactly = 1) { container.create("paidProd") }
        assertEquals(4, createdByName.size)
    }

    @Test
    fun `register skips reserved names main and test`() {
        CompilationRegistrar.register(
            target = target,
            variantNames = listOf("main", "test", "freeDev"),
            logger = logger,
        )

        verify(exactly = 0) { container.create("main") }
        verify(exactly = 0) { container.create("test") }
        verify(exactly = 1) { container.create("freeDev") }
    }

    @Test
    fun `register associates each variant compilation with main (Q12 isolation prerequisite)`() {
        CompilationRegistrar.register(
            target = target,
            variantNames = listOf("freeDev", "paidDev"),
            logger = logger,
        )

        val freeDev = createdByName.getValue("freeDev")
        val paidDev = createdByName.getValue("paidDev")
        verify(exactly = 1) { freeDev.associateWith(main) }
        verify(exactly = 1) { paidDev.associateWith(main) }
    }

    @Test
    fun `register with empty variant list is a no-op`() {
        CompilationRegistrar.register(
            target = target,
            variantNames = emptyList(),
            logger = logger,
        )

        verify(exactly = 0) { container.create(any<String>()) }
    }

    @Test
    fun `register is idempotent across duplicate calls — same variant name not created twice`() {
        CompilationRegistrar.register(target, listOf("freeDev"), logger)
        CompilationRegistrar.register(target, listOf("freeDev"), logger)

        // Existing compilation must be re-used, not duplicated
        verify(exactly = 1) { container.create("freeDev") }
    }
}
