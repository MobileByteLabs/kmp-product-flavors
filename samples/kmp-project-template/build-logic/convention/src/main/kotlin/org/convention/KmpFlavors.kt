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

/**
 * Centralized KMP flavor definitions for the project.
 *
 * Two dimensions:
 *  - [Dimension.CONSUMER] — who is this build for (server URLs, URL override)
 *  - [Dimension.TIER]     — what feature set (analytics, reports, bulk ops)
 *
 * Build types (debug / staging / release) determine the active server URL and
 * whether dev-only screens are compiled in. See [KMPFlavorsConventionPlugin].
 */
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
        /** Your own app published to your stores */
        internal(isDefault = true),

        /** Demo app — ALLOW_URL_OVERRIDE=true, runtime server override available */
        demo(applicationIdSuffix = ".demo", bundleIdSuffix = ".demo"),

        /** White-label for Bank A */
        clientA(applicationIdSuffix = ".clienta", bundleIdSuffix = ".clienta"),

        /** White-label for Bank B */
        clientB(applicationIdSuffix = ".clientb", bundleIdSuffix = ".clientb"),
    }

    @Suppress("EnumEntryName")
    enum class TierFlavor(val isDefault: Boolean = false) {
        /** All features enabled */
        advanced(isDefault = true),

        /** Limited feature set — analytics/reports/bulk ops excluded from binary */
        basic,
    }
}
