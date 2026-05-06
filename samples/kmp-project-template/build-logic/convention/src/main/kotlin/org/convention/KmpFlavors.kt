/*
 * Copyright 2026 Mifos Initiative
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

package org.convention

object KmpFlavors {

    enum class Dimension(val priority: Int) {
        CONSUMER(0),
        TIER(1),
    }

    @Suppress("EnumEntryName")
    enum class ConsumerFlavor(
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
    ) {
        internal(isDefault = true),
        demo(applicationIdSuffix = ".demo", bundleIdSuffix = ".demo"),
        clientA(applicationIdSuffix = ".clienta", bundleIdSuffix = ".clienta"),
        clientB(applicationIdSuffix = ".clientb", bundleIdSuffix = ".clientb"),
    }

    @Suppress("EnumEntryName")
    enum class TierFlavor(val isDefault: Boolean = false) {
        advanced(isDefault = true),
        basic,
    }
}
