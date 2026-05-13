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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * RFC §3 Q21-D — per-variant Maven publishing mechanism.
 *
 * When `kmpFlavors.publishMatrix.set(true)` AND the consumer's project
 * applies `maven-publish`, this configurator registers:
 *
 *   - A `jar{Variant}Kotlin{Target}` `Jar` task per inactive variant ×
 *     non-Android target. The Jar packages the variant compilation's
 *     output and is tagged with `archiveClassifier = "{variantName}"`.
 *   - A `MavenPublication` named `"variant{Variant}"` per inactive
 *     variant, with that Jar as its primary artifact. Gradle's
 *     maven-publish plugin then derives standard publish tasks
 *     (`publishVariant{Variant}PublicationToMavenLocal` etc.) from it.
 *
 * The plugin itself stays single-published (Q21-A) for its own Maven
 * coordinate. Q21-D ships the mechanism so **consumer libraries** can
 * opt in to classifier-tagged publishing.
 *
 * W4.1 scope: JVM target only. iOS / JS / WasmJs per-variant
 * publishing has KMP-specific complications (e.g., per-target XCFramework
 * bundling on iOS) deferred to W4.2 + a follow-up plan if the survey
 * shows real demand.
 *
 * No-op when:
 *   - `publishMatrix` is not opted in.
 *   - `maven-publish` (or any plugin providing it, e.g.
 *     `com.vanniktech.maven.publish`) hasn't been applied.
 *   - No inactive variants exist (matrix mode off, or single-flavor module).
 */
internal object PerVariantPublishConfigurator {

    fun configure(
        project: Project,
        extension: KmpFlavorExtension,
        inactiveVariants: List<FlavorVariant>,
        nonAndroidTargets: List<KotlinTarget>,
    ) {
        if (!extension.publishMatrix.getOrElse(false)) return
        if (inactiveVariants.isEmpty()) return

        // Defer until maven-publish actually applies. `vanniktech.maven-publish`
        // delegates to maven-publish, so a single withPlugin("maven-publish") hook
        // covers both adoption paths.
        project.plugins.withId("maven-publish") {
            configureWithMavenPublish(project, inactiveVariants, nonAndroidTargets)
        }
    }

    private fun configureWithMavenPublish(
        project: Project,
        inactiveVariants: List<FlavorVariant>,
        nonAndroidTargets: List<KotlinTarget>,
    ) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return
        val publications = publishing.publications

        // JVM-only in W4.1 — the JVM Jar task is the simplest case. iOS / JS /
        // WasmJs per-variant publishing requires per-target archive bundling and
        // is deferred to W4.2 / a follow-up plan.
        val jvmTargets = nonAndroidTargets.filter { it.platformType.name == "jvm" }
        if (jvmTargets.isEmpty()) {
            project.logger.warn(
                "[KMP Flavors] publishMatrix=true but no JVM-typed target detected; " +
                    "no per-variant publications were registered in W4.1.",
            )
            return
        }

        for (target in jvmTargets) {
            @Suppress("UNCHECKED_CAST")
            val container = target.compilations as NamedDomainObjectContainer<KotlinCompilation<*>>

            for (variant in inactiveVariants) {
                val compilation = container.findByName(variant.name) ?: continue
                val variantCap = variant.name.replaceFirstChar { it.uppercase() }
                val targetCap = target.name.replaceFirstChar { it.uppercase() }

                // Per-variant Jar task. Names it `jar{Variant}Kotlin{Target}` so
                // it's visible alongside KGP's other `jar*` tasks.
                val jarTaskName = "jar${variantCap}Kotlin${targetCap}"
                val jarTask = project.tasks.register(jarTaskName, Jar::class.java) {
                    this.group = "kmpFlavors variants"
                    this.description =
                        "Packages compilation output of variant '${variant.name}' " +
                            "on target '${target.name}' as a classifier-tagged JAR."
                    this.archiveBaseName.set(project.name)
                    this.archiveClassifier.set(variant.name)
                    from(compilation.output.allOutputs)
                }

                // Per-variant MavenPublication. Gradle's maven-publish plugin
                // derives all the standard publish tasks from this.
                val publicationName = "variant${variantCap}"
                publications.register(publicationName, MavenPublication::class.java) {
                    artifactId = project.name
                    artifact(jarTask)
                }
            }
        }

        project.logger.lifecycle(
            "[KMP Flavors] publishMatrix: registered ${inactiveVariants.size} per-variant " +
                "publication(s) on ${jvmTargets.size} JVM target(s). " +
                "Resolve via `./gradlew publishVariant{Variant}PublicationToMavenLocal`.",
        )
    }
}
