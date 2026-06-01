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

import java.io.Serializable

/**
 * v2.6 Phase 3 — serializable spec for one declared Koin variant module.
 *
 * Output of `kmpFlavors { di { koin { variantModule(name) { ... } } } }`. The spec
 * captures the module name and the per-flavor body code blocks supplied by the
 * consumer. Read at task-execution time by [com.mobilebytelabs.kmpflavors.tasks.GenerateKoinModulesTask].
 *
 * `Serializable` is required for the spec to round-trip through Gradle's
 * configuration cache, matching the pattern of `BuildKonfigSpec` and other
 * task-input data classes.
 */
data class KoinModuleSpec(
    val moduleName: String,
    val variantBindings: Map<String, String>,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
