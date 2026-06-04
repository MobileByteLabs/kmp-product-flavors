/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

/**
 * Bullet-proof reflective setters that work across both the classic-Java-bean
 * AGP DSL surface AND the AGP 9+ Property-of-T surface where many former
 * setters became Gradle `Property<T>` getters.
 *
 * AGP 9.0 release notes:
 *   - `ProductFlavor.setDimension(String)` → replaced by the `dimension` property
 *   - `Installation.installOptions(String)` → replaced by the mutable property
 *   - many similar conversions throughout the DSL
 *
 * For each call, this helper tries (in order):
 *   1. Direct setter `setX(value)` of matching arity — works for AGP < 9 and any
 *      Kotlin `var x: T` that compiles to a JVM setter.
 *   2. Getter `getX(): Property<T>` then call `.set(value)` on the returned
 *      Property — works for AGP 9+ where many former setters became Property.
 *
 * Silent no-op if neither is available — keeps the call site clean and
 * forward-compatible if AGP rearranges the API again.
 */
internal object AgpReflectiveSetters {

    /**
     * Set a property on an AGP DSL object.
     *
     * @param target the AGP DSL object (e.g. ProductFlavor, BuildType, DefaultConfig)
     * @param propertyName camelCase property name (e.g. "dimension", "applicationIdSuffix")
     * @param value the value to set (typically String, Boolean, or Int)
     * @return true if the value was applied via either pattern; false if neither found
     */
    fun set(target: Any, propertyName: String, value: Any?): Boolean {
        val cap = propertyName.replaceFirstChar { it.uppercase() }
        // Pattern 1: direct setter setX(T) — AGP < 9 or auto-generated Kotlin var setter
        runCatching {
            val setter = target.javaClass.methods.firstOrNull {
                it.name == "set$cap" && it.parameterCount == 1 &&
                    (
                        value == null || it.parameterTypes[0].isAssignableFrom(value.javaClass) ||
                            it.parameterTypes[0].isPrimitive
                        )
            }
            if (setter != null) {
                setter.invoke(target, value)
                return true
            }
        }
        // Pattern 2: getX() returns Gradle Property<T>; call .set(value) on it — AGP 9+
        runCatching {
            val getter = target.javaClass.methods.firstOrNull {
                it.name == "get$cap" && it.parameterCount == 0
            } ?: return@runCatching
            val gradleProperty = getter.invoke(target) ?: return@runCatching
            val setMethod = gradleProperty.javaClass.methods.firstOrNull {
                it.name == "set" && it.parameterCount == 1
            } ?: return@runCatching
            setMethod.invoke(gradleProperty, value)
            return true
        }
        return false
    }
}
