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
 * v2.1 Phase 5B+5C — per-variant JS / WasmJs publishing (RFC §3 Q21-D extension).
 *
 * Shared configurator for the "JS family" — both `js(IR)` and `wasmJs()` use
 * the same Kotlin → JS distribution infrastructure (webpack bundling, npm
 * tarball production), so the per-variant publishing logic is identical
 * apart from the target detection.
 *
 * **What gets published** (v2.1 scope):
 *   - Per (inactive variant × JS/WasmJs target): the variant compilation's
 *     output bundled as a classifier-tagged Zip MavenPublication.
 *   - Publication name: `variant{Variant}{Target}Js` (e.g.,
 *     `variantPaidJsJs`, `variantPaidWasmJsJs`).
 *   - Classifier: `{variant}-{target}` (e.g., `paid-js`, `paid-wasmJs`).
 *
 * **Out of scope** (consumer-side / v2.2):
 *   - **npm registry publishing**. The plugin doesn't manage `~/.npmrc`
 *     or call `npm publish`. Per the v2.1 plan risk register, this is
 *     intentionally consumer-side. Consumers wire their npm credentials
 *     and use Gradle tasks like `kotlinNpmPublishToRegistry` separately;
 *     this configurator just produces the classifier-tagged Maven
 *     publication for an alternate distribution channel.
 *   - **`package.json` per-variant name customisation** via
 *     `kmpFlavors.npmPackagePrefix`. The basic publication ships in v2.1;
 *     a configurable npm-package-name prefix is a v2.2 follow-up if
 *     consumer survey data shows demand.
 *
 * Gated by `pluginManager.withPlugin("maven-publish")` — same as the
 * other per-variant publish configurators.
 */
internal object PerVariantJsPublishConfigurator {

    fun configure(project: Project, extension: KmpFlavorExtension, inactiveVariants: List<FlavorVariant>, nonAndroidTargets: List<KotlinTarget>) {
        if (!extension.publishMatrix.getOrElse(false)) return
        if (inactiveVariants.isEmpty()) return

        val jsFamilyTargets = nonAndroidTargets.filter { target ->
            val platformType = target.platformType.name
            platformType == "js" || platformType == "wasm"
        }
        if (jsFamilyTargets.isEmpty()) return

        project.plugins.withId("maven-publish") {
            configureJsPublications(project, inactiveVariants, jsFamilyTargets)
        }
    }

    private fun configureJsPublications(project: Project, inactiveVariants: List<FlavorVariant>, jsTargets: List<KotlinTarget>) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return
        val publications = publishing.publications

        var registered = 0
        for (jsTarget in jsTargets) {
            val compilations = jsTarget.compilations
            for (variant in inactiveVariants) {
                val variantCompilation = compilations.findByName(variant.name) ?: continue
                val variantCap = variant.name.replaceFirstChar { it.uppercase() }
                val targetCap = jsTarget.name.replaceFirstChar { it.uppercase() }
                val classifier = "${variant.name}-${jsTarget.name}"

                // Per-variant Zip task bundling the variant compilation's output for this
                // JS-family target. Consumers depending on the classifier-tagged coordinate
                // get the right variant's distribution. Real npm publishing is consumer-side.
                val zipTaskName = "zip${variantCap}Kotlin$targetCap"
                val zipTask = project.tasks.register(zipTaskName, Zip::class.java) {
                    this.group = "kmpFlavors variants"
                    this.description =
                        "Bundles compilation output of variant '${variant.name}' on JS-family " +
                        "target '${jsTarget.name}' as a classifier-tagged Zip for Maven publishing."
                    this.archiveBaseName.set(project.name)
                    this.archiveClassifier.set(classifier)
                    this.archiveExtension.set("zip")
                    from(variantCompilation.output.allOutputs)
                }

                val publicationName = "variant${variantCap}${targetCap}Js"
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
            "[KMP Flavors] publishMatrix JS-family: registered $registered per-variant " +
                "publication(s) across ${jsTargets.size} JS/WasmJs target(s) × " +
                "${inactiveVariants.size} inactive variant(s). " +
                "Resolve via `./gradlew publishVariant{Variant}{Target}JsPublicationToMavenLocal`. " +
                "npm publishing is consumer-side — see docs/PUBLISHING.md.",
        )
    }
}
