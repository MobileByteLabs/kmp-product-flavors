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

package com.mobilebytelabs.kmpflavors

import org.gradle.api.Action

/**
 * v2.6 Phase 3 — top-level `kmpFlavors { di { ... } }` block.
 *
 * Currently supports Koin only. Future-proofed for Kodein-DI / Hilt-KMP / dagger
 * via additional nested config classes (each added behind the same `Action<T>`
 * DSL pattern).
 *
 * The plugin does NOT hard-depend on `io.insert-koin:koin-core` — consumers
 * bring their own Koin dep. Codegen emits `import org.koin.core.module.Module`
 * + `import org.koin.dsl.module` which compile only when the consumer has
 * those types on the classpath.
 *
 * ```kotlin
 * kmpFlavors {
 *     di {
 *         koin {
 *             variantModule("analytics") {
 *                 "free" {
 *                     singleOf("::FreeAnalyticsHelper")
 *                     bind("AnalyticsHelper")
 *                 }
 *                 "paid" {
 *                     singleOf("::PaidAnalyticsHelper")
 *                     bind("AnalyticsHelper")
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
open class DiDsl internal constructor() {
    internal val koin: KoinDiConfig = KoinDiConfig()

    fun koin(action: Action<KoinDiConfig>) {
        action.execute(koin)
    }
}

/**
 * v2.6 Phase 3 — Koin DSL container. Holds one [KoinModuleSpec] per declared
 * `variantModule(name) { ... }`.
 */
open class KoinDiConfig internal constructor() {
    internal val variantModules: MutableMap<String, KoinModuleSpec> = linkedMapOf()

    fun variantModule(name: String, action: Action<KoinVariantModuleScope>) {
        require(name.isNotBlank()) { "variantModule(name) requires a non-blank name" }
        val scope = KoinVariantModuleScope(name)
        action.execute(scope)
        variantModules[name] = KoinModuleSpec(name, scope.bindings.toMap())
    }
}

/**
 * Per-`variantModule(name) { ... }` scope. Inside this scope the consumer
 * writes one `"flavor" { ... }` block per flavor whose module body differs.
 * Flavors not declared in the scope have no actual val emitted (the
 * `expect val` still exists in commonMain — consumers must either declare
 * every flavor's body or accept a compile error on the missing actual, which
 * matches the standard KMP expect/actual contract).
 */
class KoinVariantModuleScope internal constructor(val moduleName: String) {
    internal val bindings: MutableMap<String, String> = linkedMapOf()

    /**
     * `"free" { singleOf(...) }` — register the module body for one flavor name.
     */
    operator fun String.invoke(action: KoinModuleBodyScope.() -> Unit) {
        val body = KoinModuleBodyScope().apply(action).code
        bindings[this] = body.toString()
    }
}

/**
 * Inside the per-flavor body block. Each helper appends one Koin DSL line to
 * the generated module body. `raw(line)` is the escape hatch for any Koin DSL
 * not covered by a higher-level helper.
 */
class KoinModuleBodyScope internal constructor() {
    internal val code: StringBuilder = StringBuilder()

    fun singleOf(constructorRef: String) {
        code.appendLine("    singleOf($constructorRef)")
    }

    fun single(body: String) {
        code.appendLine("    single { $body }")
    }

    fun bind(type: String) {
        code.appendLine("    bind<$type>()")
    }

    fun raw(line: String) {
        code.appendLine("    $line")
    }
}
