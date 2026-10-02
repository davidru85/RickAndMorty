package io.github.davidru85.multiverse.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `multiverse.kmp.library` — a Kotlin Multiplatform library module with the
 * Android target of ADR-0002 and the two iOS targets ADR-0002 permits.
 *
 * Configures the targets, the derived Android namespace, the SDK levels from the
 * catalog, explicit API mode and JVM target 17. It declares no dependency: every
 * project edge lives in the consuming module's own build script (OD-3).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("com.autonomousapps.dependency-analysis")

            extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
                filter {
                    exclude { element -> element.file.path.contains("/build/generated/") }
                }
            }

            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val namespace = libraryNamespace().get()

            // ADR-0002 as amended (`TASK-027`, `DEC-079`): a module may opt into a JVM target when
            // it needs one to run a JVM-only verification path — the scheduled live contract job is
            // the only case today. The opt-in is a module property, so every other module's target
            // set is unchanged and `verifyModuleBoundaries` keeps classifying the graph.
            val wantsJvmTarget = findProperty("multiverse.jvmTarget") == "true"

            extensions.configure<KotlinMultiplatformExtension> {
                // Only the targets ADR-0002 permits: the Android target, the two Apple targets, and
                // the JVM target when a module opts in. No js, wasm, desktop, tvOS or watchOS
                // target, no `iosX64`, and no framework binary.
                iosArm64()
                iosSimulatorArm64()
                if (wantsJvmTarget) {
                    // TASK-101 (`B2-R04`, `GAP-018`): the opt-in JVM compilation is configured for
                    // the documented bytecode level. Without this it inherits the daemon's target
                    // and produced class-file major version 69 where the contract documents 61.
                    jvm {
                        compilerOptions {
                            jvmTarget.set(JvmTarget.JVM_17)
                        }
                    }
                }

                explicitApi()

                extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    this.namespace = namespace
                    compileSdk = catalog.version("android-compileSdk")
                    minSdk = catalog.version("android-minSdk")

                    // The Android host test target, so `commonTest` really executes on a JVM:
                    // without it a shared test source set compiles nowhere on the Linux runner and
                    // the gate would report a green suite that ran nothing (`TASK-024`/`TASK-025`).
                    withHostTest { }
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }
        }
    }
}

internal fun VersionCatalog.version(alias: String): Int =
    findVersion(alias).orElseThrow { error("Missing version `$alias` in the version catalog") }
        .requiredVersion
        .toInt()
