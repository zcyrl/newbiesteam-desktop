import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":desktop-shell"))

            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines)
        }
    }
}

val runtimeJavaHome: String? =
    System.getProperty("newbies.bundle.javaHome")
        ?: System.getenv("NBT_BUNDLE_JAVA_HOME")
        ?: providers.gradleProperty("newbies.bundle.javaHome").orNull

val appVersionName: String =
    providers.gradleProperty("newbies.versionName").orElse("3.1.1").get()

compose.desktop {
    application {
        // Point the packaged launcher at a JetBrains Runtime with JCEF (set by CI
        // or `./gradlew -Dnewbies.bundle.javaHome=...`). Null = system JDK, which
        // makes KCEF download the engine on first run instead (dev-run path).
        runtimeJavaHome?.let { javaHome = it }

        mainClass = "team.newbies.desktop.publicapp.MainKt"

        // Required by the KCEF/AWT bridges the same way the webview library's
        // sample sets them on the packaged launcher.
        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        nativeDistributions {
            /**
             * Formats outside the current OS are skipped by jpackage, so one
             * list serves every release pipeline. Extra archives (Arch
             * package, portable zip) are produced by `scripts/`.
             */
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage,
                TargetFormat.Dmg,
                TargetFormat.Pkg
            )

            packageName = "NewbiesTeam"
            packageVersion = appVersionName
            description = "NewbiesTeam — GT 20 Pro Web Hub (desktop companion)"
            vendor = "NewbiesTeam"
            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "53e32389-3a9c-4b0c-9500-a191f04771f9"
                iconFile.set(rootProject.file("packaging/icons/newbiesteam.ico"))
            }

            linux {
                menuGroup = "Internet"
                shortcut = true
                debMaintainer = "softbrick@newbiesteam.dev"
                debPackageVersion = appVersionName
                rpmPackageVersion = appVersionName
                iconFile.set(rootProject.file("packaging/icons/newbiesteam.png"))
            }

            macOS {
                dockName = "NewbiesTeam"
                bundleID = "team.newbies.desktop.app"
                // The .icns is generated on a macOS CI runner (sips + iconutil
                // from the committed .iconset PNGs) and lands here first.
                val icns = rootProject.file("packaging/icons/newbiesteam.icns")
                if (icns.isFile) iconFile.set(icns)
            }
        }
    }
}

// CMP quirk: the jpackage installer tasks (Dmg/Pkg/Msi/Deb/Rpm) read the shared
// `compose/binaries/main/app` output directory that packageAppImage produces,
// but Gradle's strict task-output validation fails when both run in one build
// ("uses this output of task ':x:packageAppImage' without declaring an
// explicit or implicit dependency"). Declare it explicitly.
afterEvaluate {
    tasks.matching {
        it.name in listOf("packageDmg", "packagePkg", "packageMsi", "packageDeb", "packageRpm")
    }.configureEach {
        dependsOn(tasks.named("packageAppImage"))
    }
}
