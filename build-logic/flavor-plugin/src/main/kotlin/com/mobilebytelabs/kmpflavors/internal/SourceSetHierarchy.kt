/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

/**
 * Phase 7 — typed model of per-flavor + per-(target × flavor) source set graph.
 */
internal data class SourceSetHierarchy(
    val flavorSourceSets: List<String>,            // "demoMain", "prodMain"
    val perTargetPerFlavor: Map<Pair<String, String>, String>, // (target, flavor) -> "androidDemoMain"
    val dependsOnEdges: List<Pair<String, String>>, // (child, parent)
) {
    companion object {
        fun from(flavors: List<String>, targets: Set<String>): SourceSetHierarchy {
            val flavorSets = flavors.map { "${it}Main" }
            val perTargetMap = flavors.flatMap { f ->
                targets.map { t ->
                    (t to f) to "${t}${f.replaceFirstChar { c -> c.uppercase() }}Main"
                }
            }.toMap()
            val edges = buildList {
                flavors.forEach { add("${it}Main" to "commonMain") }
                perTargetMap.forEach { (pair, ss) ->
                    val (target, flavor) = pair
                    add(ss to "${flavor}Main")
                    add(ss to "${target}Main")
                }
            }
            return SourceSetHierarchy(flavorSets, perTargetMap, edges)
        }
    }
}
