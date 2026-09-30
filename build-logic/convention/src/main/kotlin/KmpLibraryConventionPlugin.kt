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

            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val namespace = libraryNamespace().get()

            extensions.configure<KotlinMultiplatformExtension> {
                // Only the targets ADR-0002 permits. No jvm, js, wasm, desktop,
                // tvOS or watchOS target, no `iosX64`, and no framework binary.
                iosArm64()
                iosSimulatorArm64()

                explicitApi()

                extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    this.namespace = namespace
                    compileSdk = catalog.version("android-compileSdk")
                    minSdk = catalog.version("android-minSdk")
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
