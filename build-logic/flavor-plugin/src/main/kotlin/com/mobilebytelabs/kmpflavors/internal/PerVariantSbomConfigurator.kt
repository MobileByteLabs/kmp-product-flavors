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

import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * v2.2 Phase 3B — per-variant SBOM (Software Bill of Materials) emission.
 *
 * Per-variant Maven publications (Q21-D shipped in v2.0; iOS / JS extensions
 * in v2.1) ship without SBOM artifacts. Per-variant SBOM emission lets
 * consumers' supply-chain tooling (Snyk, Dependabot, GitHub Dependency Graph)
 * audit per-variant dependency graphs separately — important when `commonPaid`
 * pulls in a payment SDK with a different security profile than `commonFree`.
 *
 * Opt-in via `kmpFlavors.publishMatrixSbom.set(true)`. Requires the CycloneDX
 * Gradle plugin (`org.cyclonedx.bom`) to be applied — generation is delegated
 * to that plugin's `CycloneDxTask` which we attach as an additional artifact
 * to each per-variant `MavenPublication`.
 *
 * No-op when:
 *   - `publishMatrixSbom` is `false` (default).
 *   - `publishMatrix` is `false` (no per-variant publications to attach to).
 *   - `org.cyclonedx.bom` plugin isn't applied.
 *   - No inactive variants exist.
 *
 * Output: alongside each `coordinate:1.0.0:paid` JVM Jar (or `paid-iosArm64`
 * iOS Zip, etc.), a `coordinate:1.0.0:paid-sbom` SBOM JSON artifact in
 * CycloneDX SPDX format.
 */
internal object PerVariantSbomConfigurator {

    private const val CYCLONEDX_PLUGIN_ID: String = "org.cyclonedx.bom"

    fun configure(project: Project, extension: KmpFlavorExtension, inactiveVariants: List<FlavorVariant>, logger: Logger) {
        if (!extension.publishMatrixSbom.getOrElse(false)) return
        if (!extension.publishMatrix.getOrElse(false)) {
            logger.info(
                "[KMP Flavors] Phase 3B — publishMatrixSbom=true but publishMatrix=false; " +
                    "no per-variant publications to attach SBOM artifacts to. Skipping.",
            )
            return
        }
        if (inactiveVariants.isEmpty()) return

        project.pluginManager.withPlugin(CYCLONEDX_PLUGIN_ID) {
            project.plugins.withId("maven-publish") {
                attachSbomToPublications(project, inactiveVariants, logger)
            }
        }
    }

    private fun attachSbomToPublications(project: Project, inactiveVariants: List<FlavorVariant>, logger: Logger) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return
        val cyclonedxTask = project.tasks.findByName("cyclonedxBom") ?: run {
            logger.info(
                "[KMP Flavors] Phase 3B — `cyclonedxBom` task not found despite `$CYCLONEDX_PLUGIN_ID` " +
                    "being applied. CycloneDX plugin version may be incompatible.",
            )
            return
        }

        var attached = 0
        for (variant in inactiveVariants) {
            val variantCap = variant.name.replaceFirstChar { it.uppercase() }
            val publicationName = "variant$variantCap"
            val publication = publishing.publications.findByName(publicationName) as? MavenPublication
                ?: continue

            // Attach the CycloneDX output as an artifact on the variant publication. The
            // CycloneDX plugin produces `build/reports/bom.json` by default — we artifact-tag
            // it with the variant name so Maven Central sees `coordinate:1.0.0:paid-sbom`.
            try {
                publication.artifact(cyclonedxTask) {
                    this.classifier = "${variant.name}-sbom"
                    this.extension = "json"
                }
                attached += 1
            } catch (e: Exception) {
                logger.info(
                    "[KMP Flavors] Phase 3B — failed to attach SBOM to publication '$publicationName' " +
                        "(${e.message}). Skipping this variant.",
                )
            }
        }

        if (attached > 0) {
            logger.lifecycle(
                "[KMP Flavors] Phase 3B — attached SBOM artifacts to $attached per-variant " +
                    "publication(s). Consumers' supply-chain tooling can now audit per-variant " +
                    "dependency graphs via the `${variantName(inactiveVariants[0])}-sbom` classifier.",
            )
        }
    }

    private fun variantName(variant: FlavorVariant): String = variant.name
}
