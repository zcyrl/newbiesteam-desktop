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
        // Same JetBrains-Runtime-with-JCEF contract as :app-public (set by CI).
        runtimeJavaHome?.let { javaHome = it }

        mainClass = "team.newbies.desktop.devapp.MainKt"

        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage,
                TargetFormat.Dmg,
                TargetFormat.Pkg
            )

            packageName = "NewbiesDev"
            packageVersion = appVersionName
            description = "NewbiesDev — Developer Control Center (desktop companion)"
            vendor = "NewbiesTeam"
            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "358fc1bf-c836-4d55-b795-2a901f2b7161"
                iconFile.set(rootProject.file("packaging/icons/newbiesdev.ico"))
            }

            linux {
                menuGroup = "Development"
                shortcut = true
                debMaintainer = "softbrick@newbiesteam.dev"
                debPackageVersion = appVersionName
                rpmPackageVersion = appVersionName
                iconFile.set(rootProject.file("packaging/icons/newbiesdev.png"))
            }

            macOS {
                dockName = "NewbiesDev"
                bundleID = "team.newbies.desktop.devapp"
                val icns = rootProject.file("packaging/icons/newbiesdev.icns")
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
