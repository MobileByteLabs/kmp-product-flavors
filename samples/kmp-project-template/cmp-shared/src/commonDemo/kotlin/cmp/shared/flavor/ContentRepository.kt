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

import org.openmf.kmptemplate.FlavorConfig

actual object ContentRepository {
    actual val dataSource: String = "demo-remote"
    actual val requiresAuthentication: Boolean = false
    actual val allowsServerUrlOverride: Boolean = true
    actual fun getBaseUrl(): String = FlavorConfig.API_URL_RELEASE
    actual fun getSampleData(): List<String> = listOf(
        "Demo item 1",
        "Demo item 2",
        "Demo item 3",
    )
}
