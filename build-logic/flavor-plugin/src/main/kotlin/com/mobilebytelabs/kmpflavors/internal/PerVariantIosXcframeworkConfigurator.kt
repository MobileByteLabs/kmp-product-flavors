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
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * v2.2 Phase 5A — per-variant XCFramework aggregation (iOS).
 *
 * v2.1 Phase 5A shipped the publishing **surface**: classifier-tagged Zip
 * + MavenPublication per (inactive variant × iOS target), bundling the
 * variant compilation's klibrary output. v2.2 closes the loop with real
 * Apple framework binaries aggregated into an `.xcframework` directory.
 *
 * KGP edge case (documented in v2.2 plan risk register):
 *   - `target.binaries.framework { compilation = … }` defaults to linking
 *     against the target's `main` compilation. Linking against a custom
 *     variant compilation requires setting `compilation = <variant>` via
 *     the lower-level binaries API.
 *   - Kotlin's SAM conversion of `Action<T> { … }` produces a receiver-style
 *     `T.() -> Unit` lambda which won't accept a parameter for `compilation`.
 *     Use the anonymous-object `Action<Framework>` form to disambiguate —
 *     same pattern as `DetektPerVariantHelper`.
 *
 * Per-variant XCFramework task naming:
 *   - `assemble{Variant}XCFramework` — assembles per-target Frameworks +
 *     aggregates into `build/xcframework/{variant}/{variantName}.xcframework`.
 *   - MavenPublication: `variant{Variant}Ios` (replacing v2.1's Zip-shaped
 *     publication of the same name when `publishMatrixLegacyIosClassifiers`
 *     is `false`; both publications coexist when the flag is `true`,
 *     v2.1 default behavior).
 *
 * No-op when:
 *   - `publishMatrix` is false.
 *   - No `iosX64()` / `iosArm64()` / `iosSimulatorArm64()` target declared.
 *   - `maven-publish` plugin isn't applied.
 *   - No inactive variants.
 */
internal object PerVariantIosXcframeworkConfigurator {

    private val IOS_TARGET_NAMES: Set<String> = setOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",
        "iosArm32",
    )

    /**
     * KGP class name for the XCFramework aggregator task type. Loaded reflectively
     * to keep the plugin free of a hard dependency on KGP's Apple-specific subtree.
     */
    private const val XCFRAMEWORK_CLASS_FQ: String = "org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework"

    /**
     * KGP class name for the Framework binary type — used to verify the binary type
     * during the reflective filter.
     */
    private const val FRAMEWORK_CLASS_FQ: String = "org.jetbrains.kotlin.gradle.plugin.mpp.Framework"

    fun configure(project: Project, extension: KmpFlavorExtension, inactiveVariants: List<FlavorVariant>, nonAndroidTargets: List<KotlinTarget>) {
        if (!extension.publishMatrix.getOrElse(false)) return
        if (inactiveVariants.isEmpty()) return

        val iosTargets = nonAndroidTargets.filter { it.name in IOS_TARGET_NAMES }
        if (iosTargets.isEmpty()) return

        project.plugins.withId("maven-publish") {
            configureXcframeworkPublications(project, extension, inactiveVariants, iosTargets)
        }
    }

    private fun configureXcframeworkPublications(
        project: Project,
        @Suppress("UNUSED_PARAMETER") extension: KmpFlavorExtension,
        inactiveVariants: List<FlavorVariant>,
        iosTargets: List<KotlinTarget>,
    ) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return
        val publications = publishing.publications

        // Resolve KGP classes reflectively. If unavailable (KGP version too old
        // OR shading reshuffled internals), log + bail. The v2.1 Zip-only path
        // (PerVariantIosPublishConfigurator) keeps shipping in that case.
        val xcframeworkClass = try {
            Class.forName(XCFRAMEWORK_CLASS_FQ)
        } catch (e: ClassNotFoundException) {
            project.logger.warn(
                "[KMP Flavors] Phase 5A — `$XCFRAMEWORK_CLASS_FQ` not on KGP classpath; " +
                    "falling back to v2.1 Zip path. KGP version may be too old (need >= 2.0).",
            )
            return
        }
        val frameworkClass: Class<*> = try {
            Class.forName(FRAMEWORK_CLASS_FQ)
        } catch (e: ClassNotFoundException) {
            project.logger.info(
                "[KMP Flavors] Phase 5A — `$FRAMEWORK_CLASS_FQ` not on KGP classpath; skipping.",
            )
            return
        }

        var registered = 0
        for (variant in inactiveVariants) {
            val variantCap = variant.name.replaceFirstChar { it.uppercase() }
            val xcframeworkInstance = try {
                // XCFramework constructor accepts a name argument.
                val ctor = xcframeworkClass.constructors.firstOrNull { it.parameterCount == 2 }
                ctor?.newInstance(project, variant.name)
            } catch (e: Exception) {
                project.logger.warn(
                    "[KMP Flavors] Phase 5A — failed to instantiate XCFramework for variant " +
                        "'${variant.name}' (${e.message}). Skipping.",
                )
                continue
            }
            if (xcframeworkInstance == null) {
                project.logger.warn(
                    "[KMP Flavors] Phase 5A — XCFramework constructor signature unexpected " +
                        "on this KGP version; skipping variant '${variant.name}'.",
                )
                continue
            }

            // For each iOS target, register a Framework binary linked to the variant
            // compilation + add it to the XCFramework. The framework name must be
            // distinct per variant on each target, hence `${variantName}` prefix.
            for (iosTarget in iosTargets) {
                @Suppress("UNCHECKED_CAST")
                val compilations = iosTarget.compilations
                val variantCompilation = compilations.findByName(variant.name) ?: continue

                // KGP's KotlinNativeTarget.binaries.framework(...) DSL — invoked reflectively
                // because the typed call goes through SAM-conversion which mishandles the
                // `compilation = <variant>` write.
                try {
                    val binariesMethod = iosTarget.javaClass.methods.firstOrNull { it.name == "getBinaries" }
                    val binariesContainer = binariesMethod?.invoke(iosTarget) ?: continue
                    val frameworkMethod = binariesContainer.javaClass.methods.firstOrNull {
                        it.name == "framework" && it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java
                    } ?: continue

                    val configureAction = object : Action<Any> {
                        override fun execute(framework: Any) {
                            try {
                                // framework.compilation = variantCompilation (Kotlin property setter)
                                val setter = framework.javaClass.methods.firstOrNull { it.name == "setCompilation" }
                                setter?.invoke(framework, variantCompilation)
                                // xcframework.add(framework)
                                val addMethod = xcframeworkInstance.javaClass.methods.firstOrNull {
                                    it.name == "add" && it.parameterCount == 1 &&
                                        frameworkClass.isAssignableFrom(it.parameterTypes[0])
                                }
                                addMethod?.invoke(xcframeworkInstance, framework)
                            } catch (e: Exception) {
                                project.logger.info(
                                    "[KMP Flavors] Phase 5A — framework configure reflective call " +
                                        "failed on target '${iosTarget.name}' (${e.message}).",
                                )
                            }
                        }
                    }
                    frameworkMethod.invoke(binariesContainer, variant.name, configureAction)
                } catch (e: Exception) {
                    project.logger.info(
                        "[KMP Flavors] Phase 5A — failed to register framework for variant " +
                            "'${variant.name}' on target '${iosTarget.name}' (${e.message}). " +
                            "v2.1 Zip publication still serves this variant × target.",
                    )
                }
            }

            // Register MavenPublication with the XCFramework as artifact. Classifier
            // distinguishes the XCFramework from v2.1's per-target Zip publications.
            val publicationName = "variant${variantCap}IosXcframework"
            val classifier = "${variant.name}-xcframework"
            try {
                publications.register(publicationName, MavenPublication::class.java) {
                    artifactId = project.name
                    artifact(xcframeworkInstance) {
                        this.classifier = classifier
                        this.extension = "xcframework"
                    }
                }
                registered += 1
            } catch (e: Exception) {
                project.logger.info(
                    "[KMP Flavors] Phase 5A — failed to register MavenPublication '$publicationName' " +
                        "(${e.message}). v2.1 Zip publication still serves this variant.",
                )
            }
        }

        project.logger.lifecycle(
            "[KMP Flavors] Phase 5A — registered $registered per-variant XCFramework MavenPublication(s) " +
                "across ${iosTargets.size} iOS target(s) × ${inactiveVariants.size} inactive variant(s). " +
                "Resolve via `./gradlew publishVariant{Variant}IosXcframeworkPublicationToMavenLocal`. " +
                "v2.1 Zip publications coexist behind `publishMatrixLegacyIosClassifiers` " +
                "(default `true` in v2.2 for migration; flip to `false` to drop them).",
        )
    }
}
