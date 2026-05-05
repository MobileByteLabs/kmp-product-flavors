/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */

package cmp.shared.flavor

actual object FeatureFlags {
    actual val tier: String = "advanced"
    actual val analytics: Boolean = true
    actual val reports: Boolean = true
    actual val bulkOperations: Boolean = true
}
