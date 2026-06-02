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

import org.gradle.api.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiDslTest {

    private fun newDi(): DiDsl = DiDsl::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    @Test
    fun `koin builder applies action against shared KoinDiConfig`() {
        val di = newDi()
        di.koin(
            Action<KoinDiConfig> {
                variantModule(
                    "network",
                    Action<KoinVariantModuleScope> {
                        "free" {
                            single("FreeNetworkFactory()")
                            bind("NetworkFactory")
                        }
                    },
                )
            },
        )
        val spec = di.koin.variantModules["network"]
        requireNotNull(spec)
        assertEquals("network", spec.moduleName)
        assertTrue(spec.variantBindings.containsKey("free"))
        val body = spec.variantBindings.getValue("free")
        assertTrue(body.contains("single { FreeNetworkFactory() }"))
        assertTrue(body.contains("bind<NetworkFactory>()"))
    }

    @Test
    fun `variantModule rejects blank name`() {
        val di = newDi()
        assertThrows(IllegalArgumentException::class.java) {
            di.koin.variantModule("", Action<KoinVariantModuleScope> { /* no-op */ })
        }
        assertThrows(IllegalArgumentException::class.java) {
            di.koin.variantModule("   ", Action<KoinVariantModuleScope> { /* no-op */ })
        }
    }

    @Test
    fun `KoinModuleBodyScope helpers all emit their lines`() {
        val di = newDi()
        di.koin.variantModule(
            "every",
            Action<KoinVariantModuleScope> {
                "free" {
                    singleOf("::FreeFactory")
                    single("FreeImpl()")
                    bind("Contract")
                    raw("named(\"q\")")
                }
            },
        )
        val body = di.koin.variantModules.getValue("every").variantBindings.getValue("free")
        assertTrue(body.contains("singleOf(::FreeFactory)"))
        assertTrue(body.contains("single { FreeImpl() }"))
        assertTrue(body.contains("bind<Contract>()"))
        assertTrue(body.contains("named(\"q\")"))
    }

    @Test
    fun `variantModule supports multiple flavors with distinct bodies`() {
        val di = newDi()
        di.koin.variantModule(
            "analytics",
            Action<KoinVariantModuleScope> {
                "free" { single("FreeAnalytics()") }
                "paid" { single("PaidAnalytics()") }
            },
        )
        val bindings = di.koin.variantModules.getValue("analytics").variantBindings
        assertEquals(setOf("free", "paid"), bindings.keys)
        assertTrue(bindings.getValue("free").contains("FreeAnalytics"))
        assertTrue(bindings.getValue("paid").contains("PaidAnalytics"))
    }

    @Test
    fun `multiple variantModules are stored independently`() {
        val di = newDi()
        di.koin.variantModule(
            "network",
            Action<KoinVariantModuleScope> { "free" { single("A()") } },
        )
        di.koin.variantModule(
            "analytics",
            Action<KoinVariantModuleScope> { "free" { single("B()") } },
        )
        assertEquals(setOf("network", "analytics"), di.koin.variantModules.keys)
    }

    @Test
    fun `KoinModuleSpec serializable contract preserved`() {
        val spec = KoinModuleSpec(moduleName = "network", variantBindings = mapOf("free" to "    single { X() }"))
        val spec2 = KoinModuleSpec(moduleName = "network", variantBindings = mapOf("free" to "    single { X() }"))
        assertEquals(spec, spec2)
        assertEquals(spec.hashCode(), spec2.hashCode())
    }
}
