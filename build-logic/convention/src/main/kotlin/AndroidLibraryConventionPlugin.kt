package io.github.davidru85.multiverse.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * `multiverse.android.library` — an Android-only library module (Kotlin support
 * comes from AGP's built-in Kotlin, AGP 9).
 *
 * Configures the derived namespace, the SDK levels from the catalog and JVM
 * target 17. It declares no dependency (OD-3).
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val namespace = libraryNamespace().get()

            extensions.configure<LibraryExtension> {
                this.namespace = namespace
                compileSdk = catalog.version("android-compileSdk")
                defaultConfig {
                    minSdk = catalog.version("android-minSdk")
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
