import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    /**
     * JVM-only support module consumed by :app-public and :app-dev.
     * Uses the multiplatform plugin so an `android()` target can be wired later
     * without restructuring (same pattern as :shared).
     */
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            // WebView for Compose Multiplatform (KCEF powered)
            api(libs.webview)
            api(libs.kcef)

            api(project(":shared"))

            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization)
            implementation(compose.material3)
            implementation(compose.desktop.common)
            implementation(compose.components.resources)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
