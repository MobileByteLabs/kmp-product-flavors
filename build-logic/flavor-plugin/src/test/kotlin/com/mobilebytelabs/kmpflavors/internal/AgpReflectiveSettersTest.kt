/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Direct coverage for [AgpReflectiveSetters] across its two resolution patterns
 * and the silent-no-op fallback.
 *
 * AGP 8.x exposes classic Java-bean setters (Pattern 1: `setX(T)`).
 * AGP 9.x converted many of those setters to Gradle `Property<T>` getters
 * (Pattern 2: `getX(): Property<T>` + `.set(T)`).
 * Targets that have neither shape must produce a silent `false` return
 * (forward-compatibility safety net for future AGP shape changes).
 *
 * Each fixture stays vendored — no AGP classpath dependency — mirroring
 * the [MockAndroidExtension] / [AgpBridgeTest.FakeAndroidExtension] style
 * used by the rest of the test suite.
 */
class AgpReflectiveSettersTest {

    // ---------- Pattern 1: classic Java-bean setter (AGP < 9) ----------

    /** Has `setDimension(String)` only — no `getDimension()` Property. */
    class BeanStyleStringTarget {
        var dimensionValue: String? = null

        @Suppress("unused")
        fun setDimension(value: String) {
            dimensionValue = value
        }
    }

    @Test
    fun `pattern 1 applies bean-style String setter`() {
        val target = BeanStyleStringTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "demo")
        assertTrue(applied)
        assertEquals("demo", target.dimensionValue)
    }

    /** Bean-style Boolean setter — primitive boolean parameter. */
    class BeanStyleBooleanTarget {
        var enabledValue: Boolean? = null

        @Suppress("unused")
        fun setEnabled(value: Boolean) {
            enabledValue = value
        }
    }

    @Test
    fun `pattern 1 applies bean-style Boolean setter via primitive path`() {
        val target = BeanStyleBooleanTarget()
        val applied = AgpReflectiveSetters.set(target, "enabled", false)
        assertTrue(applied)
        assertEquals(false, target.enabledValue)
    }

    /** Bean-style Int setter — primitive int parameter. */
    class BeanStyleIntTarget {
        var targetSdkValue: Int? = null

        @Suppress("unused")
        fun setTargetSdk(value: Int) {
            targetSdkValue = value
        }
    }

    @Test
    fun `pattern 1 applies bean-style Int setter via primitive path`() {
        val target = BeanStyleIntTarget()
        val applied = AgpReflectiveSetters.set(target, "targetSdk", 34)
        assertTrue(applied)
        assertEquals(34, target.targetSdkValue)
    }

    // ---------- Pattern 2: AGP 9 Gradle Property<T> getter surface ----------

    /** Mimics Gradle Property<T> — has `set(value)` of arity 1. */
    class MockProperty<T> {
        var stored: T? = null
        private var observed: Boolean = false

        @Suppress("unused")
        fun set(value: T) {
            stored = value
            observed = true
        }

        fun wasSetCalled(): Boolean = observed
    }

    /** AGP 9-shaped target — exposes `getDimension(): Property<String>` only.
     *  Kotlin auto-generates `getDimension()` from the `val` property. */
    class Agp9PropertyStringTarget {
        val dimension: MockProperty<String> = MockProperty()
    }

    @Test
    fun `pattern 2 routes through Property set when only getter exists`() {
        val target = Agp9PropertyStringTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "prod")
        assertTrue(applied)
        assertTrue(target.dimension.wasSetCalled())
        assertEquals("prod", target.dimension.stored)
    }

    /** AGP 9 Property<Boolean> shape. Kotlin auto-generates `getEnable()`. */
    class Agp9PropertyBooleanTarget {
        val enable: MockProperty<Boolean> = MockProperty()
    }

    @Test
    fun `pattern 2 routes Boolean through Property set when getter returns Property of Boolean`() {
        val target = Agp9PropertyBooleanTarget()
        val applied = AgpReflectiveSetters.set(target, "enable", false)
        assertTrue(applied)
        assertEquals(false, target.enable.stored)
    }

    // ---------- Neither-found: silent no-op ----------

    /** Target with no `setDimension(...)` and no `getDimension(): Property<*>`. */
    class EmptyTarget

    @Test
    fun `returns false when neither setter nor Property getter resolves`() {
        val target = EmptyTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "demo")
        assertFalse(applied)
    }

    /** Getter exists but takes a parameter — must not match Pattern 2. */
    class MismatchedArityGetterTarget {
        @Suppress("unused")
        fun getDimension(@Suppress("UNUSED_PARAMETER") salt: String): String = "ignored"
    }

    @Test
    fun `pattern 2 rejects getter with non-zero arity`() {
        val target = MismatchedArityGetterTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "demo")
        assertFalse(applied)
    }

    /** Property getter returns a value with no `set(value)` method. */
    class NonSettablePropertyTarget {
        @Suppress("unused")
        fun getDimension(): String = "fixed"
    }

    @Test
    fun `pattern 2 silently returns false when Property has no set method`() {
        val target = NonSettablePropertyTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "demo")
        assertFalse(applied)
    }

    /** Property getter returns null — silent no-op, not NPE. */
    class NullPropertyTarget {
        val dimension: MockProperty<String>? = null
    }

    @Test
    fun `pattern 2 silently returns false when Property getter returns null`() {
        val target = NullPropertyTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "demo")
        assertFalse(applied)
    }

    // ---------- Null-value forwarding ----------

    /** Pattern 1 with explicit null forwarding — verifies value=null doesn't crash. */
    class NullableStringTarget {
        var lastValue: String? = "untouched"

        @Suppress("unused")
        fun setLabel(value: String?) {
            lastValue = value
        }
    }

    @Test
    fun `pattern 1 forwards null value to setter`() {
        val target = NullableStringTarget()
        val applied = AgpReflectiveSetters.set(target, "label", null)
        assertTrue(applied)
        assertNull(target.lastValue)
    }

    // ---------- Pattern precedence ----------

    /** Target exposes BOTH bean-style setter AND Property getter — Pattern 1 must win. */
    class DualSurfaceTarget {
        var beanValue: String? = null
        val dimension: MockProperty<String> = MockProperty()

        @Suppress("unused")
        fun setDimension(value: String) {
            beanValue = value
        }
    }

    @Test
    fun `pattern 1 takes precedence over pattern 2 when both resolve`() {
        val target = DualSurfaceTarget()
        val applied = AgpReflectiveSetters.set(target, "dimension", "prod")
        assertTrue(applied)
        // bean setter ran
        assertEquals("prod", target.beanValue)
        // property surface untouched
        assertFalse(target.dimension.wasSetCalled())
    }
}
