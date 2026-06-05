import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId("io.github.mobilebytelabs.kmp-product-flavors") {
            target.extensions.configure<com.mobilebytelabs.kmpflavors.KmpFlavorExtension>("kmpFlavors") {
                generateBuildConfig.set(true)
                flavors {
                    register("demo") { isDefault.set(true) }
                    register("prod")
                }
            }
        }
        target.plugins.withId("com.android.application") {
            target.extensions.configure(CommonExtension::class.java) {
                // v2.7 — pure-AGP workaround
                configureFlavors(this)
            }
        }
    }

    private fun CommonExtension<*, *, *, *, *, *>.configureFlavors(common: CommonExtension<*, *, *, *, *, *>) {
        common.productFlavors {
            create("demo") { dimension = "contentType" }
            create("prod") { dimension = "contentType" }
        }
    }
}
