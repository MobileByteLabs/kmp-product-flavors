/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsBootstrapXcodeTask
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsDoctorTask
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsMigrateFromV27Task
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsXcodeIntegrateTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Phase dispatcher — composes per-platform integrators based on which
 * platform plugins / KMP targets are applied.
 *
 *   phaseAgp     — AGP productFlavors auto-wiring + Android Firebase router (gated on AGP)
 *   phaseKmp     — KMP source set fan-out + KmpFlavorsRuntime codegen + Compose Resources + Android res (gated on KMP)
 *   phaseIos     — per-variant xcconfig + umbrella integrate + pbxproj bootstrap + Firebase iOS router (gated on KMP + apple target)
 *   phaseDesktop — Compose Desktop nativeDistributions wiring + JAR Manifest entries (gated on KMP + compose plugin)
 *   phaseWeb     — Webpack overlay + DefinePlugin constants (gated on KMP + js/wasm target)
 *
 * Doctor task registers unconditionally.
 */
internal object FlavorPhaseDispatcher {

    /**
     * Wires phase capabilities. Returns the count of integrators that fired.
     */
    fun apply(project: Project, ext: KmpFlavorExtension): Int {
        val logger = project.logger
        var fired = 0

        // Tooling tasks register unconditionally
        registerToolingTasks(project)

        val hasAgp = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
        val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        val hasKmp = kmp != null
        val hasCompose = project.pluginManager.hasPlugin("org.jetbrains.compose")

        if (!hasAgp && !hasKmp) {
            logger.info("[KMP Flavors] no AGP or KMP plugin; skipping phase dispatch")
            return 0
        }

        // phaseAgp — Android Firebase + per-flavor res routing.
        // (AGP productFlavors registration runs earlier, from KmpFlavorPlugin.apply()
        // via pluginManager.withPlugin so it lands ahead of AGP's finalizeDsl phase.)
        if (hasAgp) {
            fired += AndroidFirebaseFlavorRouter.apply(project, ext, logger)
            fired += AndroidResPerFlavorRouter.apply(project, ext, logger)
        }

        // phaseKmp — source set wiring + RuntimeApiGenerator
        if (hasKmp) {
            fired += KmpFlavorSourceSetWiring.apply(project, ext, logger)
            fired += generateRuntimeApi(project, ext, kmp!!, logger)
            if (hasCompose) {
                fired += ComposeResourcesPerFlavorRouter.apply(project, ext, logger)
            }
        }

        // phaseIos — xcconfig + xcode integrate task + bootstrap task + iOS firebase
        if (hasKmp && hasAnyAppleTarget(kmp!!)) {
            fired += generateIosXcconfigs(project, ext, logger)
            registerIosTasks(project, ext, logger)
            fired += IosFirebaseFlavorRouter.apply(project, ext, logger)
        }

        // phaseDesktop
        if (hasKmp && hasCompose) {
            fired += DesktopFlavorIntegrator.apply(project, ext, logger)
        }

        // phaseWeb
        if (hasKmp && hasJsOrWasmTarget(kmp!!)) {
            fired += WebFlavorIntegrator.apply(project, ext, logger)
        }

        logger.lifecycle("[KMP Flavors] dispatched: $fired integrator actions")
        return fired
    }

    private fun registerToolingTasks(project: Project) {
        if (project.tasks.findByName("kmpFlavorsDoctor") == null) {
            project.tasks.register("kmpFlavorsDoctor", KmpFlavorsDoctorTask::class.java)
        }
        if (project.tasks.findByName("kmpFlavorsMigrateFromV27") == null) {
            project.tasks.register("kmpFlavorsMigrateFromV27", KmpFlavorsMigrateFromV27Task::class.java)
        }
    }

    private fun registerIosTasks(project: Project, ext: KmpFlavorExtension, logger: Logger) {
        val iosAppDir = project.projectDir.resolve("iosApp").takeIf { it.exists() }
            ?: project.projectDir.resolve("../cmp-ios/iosApp").takeIf { it.exists() }
            ?: return

        val xcconfigOutDir = iosAppDir.resolve("Configs")
        val integrateTaskName = "kmpFlavorsXcodeIntegrate"
        if (project.tasks.findByName(integrateTaskName) == null) {
            val provider = project.tasks.register(
                integrateTaskName,
                KmpFlavorsXcodeIntegrateTask::class.java,
            )
            provider.configure(object : Action<KmpFlavorsXcodeIntegrateTask> {
                override fun execute(task: KmpFlavorsXcodeIntegrateTask) {
                    task.xcconfigGeneratedDir.set("../../build/generated/iosFlavorConfigs")
                    task.outputDir.set(xcconfigOutDir)
                }
            })
        }

        val bootstrapTaskName = "kmpFlavorsBootstrapXcode"
        if (project.tasks.findByName(bootstrapTaskName) == null) {
            val pbxproj = iosAppDir.resolve("iosApp.xcodeproj/project.pbxproj")
            val infoPlist = iosAppDir.resolve("iosApp/Info.plist")
            val provider = project.tasks.register(
                bootstrapTaskName,
                KmpFlavorsBootstrapXcodeTask::class.java,
            )
            provider.configure(object : Action<KmpFlavorsBootstrapXcodeTask> {
                override fun execute(task: KmpFlavorsBootstrapXcodeTask) {
                    task.xcconfigRelativePath.set("Configs/iOSApp.xcconfig")
                    if (pbxproj.exists()) task.pbxprojFile.set(pbxproj)
                    if (infoPlist.exists()) task.infoPlistFile.set(infoPlist)
                }
            })
        }
        logger.info("[KMP Flavors] registered iOS bootstrap + integrate tasks")
    }

    private fun generateIosXcconfigs(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val flavors = ext.flavors.toList()
        val buildTypes = ext.buildTypes.toList().ifEmpty { return 0 }
        val outDir = project.layout.buildDirectory
            .dir("generated/iosFlavorConfigs").get().asFile
        val baseBundleId = (project.findProperty("applicationId") as? String) ?: project.name
        val appDisplayName = project.rootProject.name
        val appVersion = project.version.toString()
        var count = 0
        for (flavor in flavors) {
            for (buildType in buildTypes) {
                val spec = IosXcconfigSpec.from(
                    flavor,
                    buildType,
                    baseBundleId,
                    appDisplayName,
                    appVersion,
                    ext,
                )
                IosXcconfigGenerator.generate(spec, outDir)
                count++
            }
        }
        logger.lifecycle("[KMP Flavors] generated $count xcconfig files")
        return count
    }

    private fun generateRuntimeApi(project: Project, ext: KmpFlavorExtension, kmp: KotlinMultiplatformExtension, logger: Logger): Int {
        val packageName =
            (project.findProperty("kmpFlavorsRuntimePackage") as? String)
                ?: ext.flavors.firstOrNull()?.buildConfigFields?.orNull?.get("PACKAGE")?.value?.trim('"')
                ?: "${project.rootProject.name.lowercase().replace("-", ".")}.kmpflavors"

        // Cross-module election: exactly one module in the build emits KmpFlavorsRuntime
        // for a given package. Subsequent modules find the claim via rootProject extras
        // and skip codegen — they pick up the type via the producer module's classpath.
        // Mirrors the FlavorConfig election in KmpFlavorPlugin.shouldGenerateCodegen.
        val claimKey = "kmpFlavors.runtimeApiClaim:$packageName"
        val rootExtras = project.rootProject.extensions.extraProperties
        val existing =
            if (rootExtras.has(claimKey)) rootExtras.get(claimKey) as? String else null
        if (existing != null && existing != project.path) {
            // Deterministic election: lex-lowest project.path wins. Re-elect if our
            // path is earlier (same rule as the FlavorConfig election so the same
            // module wins both claims).
            if (project.path < existing) {
                rootExtras.set(claimKey, project.path)
                logger.info(
                    "[KMP Flavors] ${project.path} taking over RuntimeApi codegen from $existing",
                )
            } else {
                logger.info(
                    "[KMP Flavors] ${project.path} skipping RuntimeApi codegen — already by $existing",
                )
                return 0
            }
        } else if (existing == null) {
            rootExtras.set(claimKey, project.path)
        }

        val outDir = project.layout.buildDirectory
            .dir("generated/kmpflavors-runtime").get().asFile
        val spec = RuntimeApiSpec(packageName = packageName)
        val files = RuntimeApiGenerator.generate(spec, outDir)
        // Wire generated source dirs into KMP source sets.
        // commonMain (expect) always exists. Platform-specific intermediates
        // (iosMain, desktopMain, etc.) exist only when the consumer either
        // (a) calls `applyDefaultHierarchyTemplate()` or (b) declares them
        // explicitly via `val iosMain by getting`. We resolve this per
        // platform: if the canonical intermediate exists, wire the actual
        // dir to it; otherwise, fan out to each KMP TARGET's main source
        // set so the actual still reaches its platform compilations.
        run {
            // commonMain (expect) — always present in a multiplatform module.
            val commonSrc = outDir.resolve("commonMain")
            if (commonSrc.exists()) {
                kmp.sourceSets.findByName("commonMain")?.kotlin?.srcDir(commonSrc)
            }
        }
        // Platform actuals (androidMain / iosMain / desktopMain / jsMain / wasmJsMain).
        // For each, if the canonical intermediate exists use it; otherwise fall
        // back to per-target main source sets.
        listOf(
            "iosMain" to { t: org.jetbrains.kotlin.gradle.plugin.KotlinTarget -> t.name.startsWith("ios") },
            "androidMain" to { t: org.jetbrains.kotlin.gradle.plugin.KotlinTarget -> t.name == "android" },
            "desktopMain" to { t: org.jetbrains.kotlin.gradle.plugin.KotlinTarget -> t.platformType.name.lowercase() == "jvm" && t.name == "desktop" },
            "jsMain" to { t: org.jetbrains.kotlin.gradle.plugin.KotlinTarget -> t.name == "js" },
            "wasmJsMain" to { t: org.jetbrains.kotlin.gradle.plugin.KotlinTarget -> t.name == "wasmJs" },
        ).forEach { (ssName, targetPredicate) ->
            val platformSrcDir = outDir.resolve(ssName)
            if (!platformSrcDir.exists()) return@forEach
            val canonical = kmp.sourceSets.findByName(ssName)
            if (canonical != null) {
                canonical.kotlin.srcDir(platformSrcDir)
            } else {
                // No canonical intermediate (e.g. iosMain when the consumer skipped
                // applyDefaultHierarchyTemplate). Fan out to each per-target main
                // source set that matches this platform.
                kmp.targets.filter(targetPredicate).forEach { target ->
                    val tMain = kmp.sourceSets.findByName("${target.name}Main")
                    tMain?.kotlin?.srcDir(platformSrcDir)
                }
            }
        }
        // Replay platform-specific actual srcDirs into each per-variant MAIN
        // compilation's defaultSourceSet. CompilationRegistrar.register runs
        // at KMP-plugin-apply (via `plugins.withId`) and snapshots target
        // main's srcDirs BEFORE this generator runs in afterEvaluate, so
        // per-variant compilations miss the runtime API actuals without
        // this second wiring pass. Test compilations are excluded — they
        // inherit the actual via associateWith on the main compilation;
        // adding it as source creates orphaned 'actual' declarations.
        kmp.targets.forEach { target ->
            val ssName = when {
                target.name == "android" -> "androidMain"
                target.name.startsWith("ios") -> "iosMain"
                target.name == "js" -> "jsMain"
                target.name == "wasmJs" -> "wasmJsMain"
                target.platformType.name.lowercase() == "jvm" -> "${target.name}Main"
                else -> return@forEach
            }
            val platformSrcDir = outDir.resolve(ssName)
            if (!platformSrcDir.exists()) return@forEach
            target.compilations
                .matching { c ->
                    c.name != KotlinCompilation.MAIN_COMPILATION_NAME &&
                        c.name != KotlinCompilation.TEST_COMPILATION_NAME &&
                        !c.name.endsWith("Test")
                }
                .configureEach {
                    defaultSourceSet.kotlin.srcDir(platformSrcDir)
                }
        }
        logger.lifecycle(
            "[KMP Flavors] ${project.path} generated ${files.size} runtime API files (package=$packageName)",
        )
        return files.size
    }

    private fun hasAnyAppleTarget(kmp: KotlinMultiplatformExtension): Boolean = kmp.targets.any { it.platformType.name.lowercase() == "native" && it.name.startsWith("ios") }

    private fun hasJsOrWasmTarget(kmp: KotlinMultiplatformExtension): Boolean = kmp.targets.any {
        it.platformType.name.lowercase().let { p -> p == "js" || p == "wasm" }
    }
}
