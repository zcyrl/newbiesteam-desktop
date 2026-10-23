import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    /**
     * JVM/Windows-mac-linux desktop target today.
     * The source layout mirrors the `android-app` core package on purpose:
     * an `android()` target can be enabled later to share this exact code with the APK apps.
     */
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization)
            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {}

        jvmMain.dependencies {}

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
