/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors.internal

/**
 * v2.6 Tier C — reflection-shaped mock of AGP's `ApplicationExtension` for unit
 * testing [AgpBridge.propagateFlavorsLegacy] and
 * [AgpBridge.propagateFlavorsCrossProduct] without an AGP classpath at runtime.
 *
 * AgpBridge consumes AGP via [Class.getMethods] lookups (no compile-time AGP
 * dep), so this mock just needs methods with the right names and shapes:
 *
 *  - `getFlavorDimensions(): MutableList<String>`
 *  - `getProductFlavors(): MockFlavorContainer`
 *  - `getBuildTypes(): MockBuildTypeContainer`
 *
 * Containers expose `getNames(): Set<String>` + `maybeCreate(String): MockFlavor`
 * matching the reflection pattern AgpBridge already uses. Per-flavor /
 * per-buildType setters are auto-generated from the `var` properties — Kotlin's
 * generated `setX(String)` JVM signature matches AgpBridge's reflection lookup.
 */
class MockAndroidExtension {

    private val flavorDimensions: MutableList<String> = mutableListOf()
    private val productFlavors: MockFlavorContainer = MockFlavorContainer()
    private val buildTypes: MockBuildTypeContainer = MockBuildTypeContainer().apply {
        // AGP default build types — bridge skips them as collision candidates.
        maybeCreate("debug")
        maybeCreate("release")
    }

    fun getFlavorDimensions(): MutableList<String> = flavorDimensions
    fun getProductFlavors(): MockFlavorContainer = productFlavors
    fun getBuildTypes(): MockBuildTypeContainer = buildTypes
}

class MockFlavorContainer {
    private val backing: MutableMap<String, MockFlavor> = linkedMapOf()

    fun getNames(): Set<String> = backing.keys.toSet()

    fun maybeCreate(name: String): MockFlavor =
        backing.getOrPut(name) { MockFlavor(name) }

    fun get(name: String): MockFlavor? = backing[name]

    val all: List<MockFlavor> get() = backing.values.toList()
}

class MockBuildTypeContainer {
    private val backing: MutableMap<String, MockBuildType> = linkedMapOf()

    fun getNames(): Set<String> = backing.keys.toSet()

    fun maybeCreate(name: String): MockBuildType =
        backing.getOrPut(name) { MockBuildType(name) }

    fun get(name: String): MockBuildType? = backing[name]

    val all: List<MockBuildType> get() = backing.values.toList()
}

class MockFlavor(val name: String) {
    // Kotlin generates JVM `setDimension(String)` for these vars, which is what
    // AgpBridge's reflection lookup (`it.name == "setDimension" && parameterTypes[0] == String`)
    // matches. Do NOT add explicit `fun setX()` — that produces a JVM signature
    // clash with the property setter.
    var dimension: String? = null
    var applicationIdSuffix: String? = null
    var versionNameSuffix: String? = null

    private val matchingFallbacks: MutableList<String> = mutableListOf()
    fun getMatchingFallbacks(): MutableList<String> = matchingFallbacks
}

class MockBuildType(val name: String) {
    // `setDebuggable(boolean)` + `setMinifyEnabled(boolean)` auto-generated.
    // AgpBridge's setBooleanProperty matches both `Boolean.TYPE` and the boxed
    // type; Kotlin's `var x: Boolean? = null` generates the boxed-`Boolean`
    // signature, which is one of the two accepted shapes.
    var debuggable: Boolean? = null
    var minifyEnabled: Boolean? = null
    var applicationIdSuffix: String? = null
}
