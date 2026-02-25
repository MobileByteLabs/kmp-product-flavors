/*
 * Copyright 2026 Anthropic, Inc.
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

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-product-flavors"

// Samples demonstrating the KMP Product Flavors plugin
include(":samples:basic-flavors") // Minimal plugin demo

// TODO: Enable after integrating flavor plugin
// include(":samples:compose-multiplatform")   // Compose Multiplatform app with flavors
// include(":cmp-library")                     // Shared library for compose-multiplatform

// Note: samples/kmp-project-template is a standalone project with its own build
// Build it separately: cd samples/kmp-project-template && ./gradlew build
