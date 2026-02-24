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

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.mobilebytelabs.kmpflavors"
version = "1.0.0-alpha01"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Kotlin Gradle Plugin - only needed at compile time to access KMP APIs
    compileOnly(libs.kotlin.gradle.plugin)

    // Gradle API
    implementation(gradleApi())

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("kmpProductFlavors") {
            id = "io.github.mobilebytelabs.kmp-product-flavors"
            implementationClass = "com.mobilebytelabs.kmpflavors.KmpFlavorPlugin"
            displayName = "KMP Product Flavors"
            description = "Kotlin Multiplatform Product Flavors Gradle Plugin - Bring Android-style product flavors to KMP"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("KMP Product Flavors")
                description.set("Kotlin Multiplatform Product Flavors Gradle Plugin")
                url.set("https://github.com/MobileByteLabs/kmp-product-flavors")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("mobilebytelabs")
                        name.set("MobileByteLabs")
                        email.set("opensource@mobilebytelabs.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/MobileByteLabs/kmp-product-flavors.git")
                    developerConnection.set("scm:git:ssh://github.com/MobileByteLabs/kmp-product-flavors.git")
                    url.set("https://github.com/MobileByteLabs/kmp-product-flavors")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
