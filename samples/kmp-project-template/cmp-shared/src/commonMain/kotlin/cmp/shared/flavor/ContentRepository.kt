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

expect object ContentRepository {
    val dataSource: String
    val requiresAuthentication: Boolean
    val allowsServerUrlOverride: Boolean
    fun getBaseUrl(): String
    fun getSampleData(): List<String>
}
