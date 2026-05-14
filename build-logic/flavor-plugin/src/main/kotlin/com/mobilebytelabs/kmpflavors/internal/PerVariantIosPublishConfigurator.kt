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
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * v2.1 Phase 5A — per-variant iOS publishing (RFC §3 Q21-D extension).
 *
 * When `publishMatrix=true` AND at least one `iosX64()` / `iosArm64()` /
 * `iosSimulatorArm64()` target is declared, this configurator registers
 * per-variant iOS Maven publications.
 *
 * **What gets published** (v2.1 scope):
 *   - Per (inactive variant × iOS target): the variant compilation's
 *     klibrary output, bundled in a per-variant Zip artifact and tagged
 *     with `classifier = "{variant}-{iosTarget}"` on the MavenPublication.
 *   - This is the publishable surface — consumers depend on the
 *     classifier-tagged coordinate to pull the right variant for their
 *     iOS target.
 *
 * **What is NOT auto-built in v2.1** (deferred to v2.2):
 *   - Per-variant `XCFramework` aggregation across `iosX64` + `iosArm64`
 *     + `iosSimulatorArm64`. KGP's `XCFramework()` API aggregates
 *     `Framework` binaries; per-variant `Framework` binaries on custom
 *     compilations have known KGP edge cases (linkage to the target's
 *     `main` compilation by default) that warrant a focused v2.2 pass.
 *   - Per-variant `Package.swift` entries in the SPM manifest. The
 *     existing `GenerateSpmManifestTask` ships single-variant SPM in
 *     v2.0; per-variant Package.swift requires per-variant XCFramework
 *     (above) to be solid first.
 *
 * Gated by `pluginManager.withPlugin("maven-publish")` so it stays a
 * no-op when the consumer hasn't applied `maven-publish` (or any plugin
 * that delegates to it, e.g. `com.vanniktech.maven.publish`).
 */
internal object PerVariantIosPublishConfigurator {

    /**
     * Recognised iOS target names. Mirrors KGP's `KonanTarget.IOS_*` family
     * via name matching to avoid a compile-time dependency on `KonanTarget`
     * across the various KGP minor versions we support.
     */
    private val IOS_TARGET_NAMES: Set<String> = setOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",
        "iosArm32",
    )

    fun configure(project: Project, extension: KmpFlavorExtension, inactiveVariants: List<FlavorVariant>, nonAndroidTargets: List<KotlinTarget>) {
        if (!extension.publishMatrix.getOrElse(false)) return
        if (inactiveVariants.isEmpty()) return

        val iosTargets = nonAndroidTargets.filter { it.name in IOS_TARGET_NAMES }
        if (iosTargets.isEmpty()) return

        project.plugins.withId("maven-publish") {
            configureIosPublications(project, inactiveVariants, iosTargets)
        }
    }

    private fun configureIosPublications(project: Project, inactiveVariants: List<FlavorVariant>, iosTargets: List<KotlinTarget>) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return
        val publications = publishing.publications

        var registered = 0
        for (iosTarget in iosTargets) {
            val compilations = iosTarget.compilations
            for (variant in inactiveVariants) {
                val variantCompilation = compilations.findByName(variant.name) ?: continue
                val variantCap = variant.name.replaceFirstChar { it.uppercase() }
                val targetCap = iosTarget.name.replaceFirstChar { it.uppercase() }
                val classifier = "${variant.name}-${iosTarget.name}"

                // Per-variant zip task bundling the variant compilation's klib output
                // for this iOS target. Zip rather than XCFramework because XCFramework
                // aggregation is deferred to v2.2 (see configurator KDoc).
                val zipTaskName = "zip${variantCap}Kotlin$targetCap"
                val zipTask = project.tasks.register(zipTaskName, Zip::class.java) {
                    this.group = "kmpFlavors variants"
                    this.description =
                        "Bundles compilation output of variant '${variant.name}' on iOS target " +
                        "'${iosTarget.name}' as a classifier-tagged Zip for Maven publishing."
                    this.archiveBaseName.set(project.name)
                    this.archiveClassifier.set(classifier)
                    this.archiveExtension.set("zip")
                    from(variantCompilation.output.allOutputs)
                }

                val publicationName = "variant${variantCap}${targetCap}Ios"
                publications.register(publicationName, MavenPublication::class.java) {
                    artifactId = project.name
                    artifact(zipTask) {
                        this.classifier = classifier
                        this.extension = "zip"
                    }
                }
                registered += 1
            }
        }

        project.logger.lifecycle(
            "[KMP Flavors] publishMatrix iOS: registered $registered per-variant iOS " +
                "publication(s) across ${iosTargets.size} iOS target(s) × " +
                "${inactiveVariants.size} inactive variant(s). " +
                "Resolve via `./gradlew publishVariant{Variant}{IosTarget}IosPublicationToMavenLocal`. " +
                "Per-variant XCFramework aggregation is deferred to v2.2 — see docs/PUBLISHING.md.",
        )
    }
}
