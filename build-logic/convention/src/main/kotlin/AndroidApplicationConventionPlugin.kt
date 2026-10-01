package io.github.davidru85.multiverse.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * `multiverse.android.application` — the Android application shell.
 *
 * Configures the namespace (`<root>.app`), the application id (the package root
 * itself), the SDK levels from the catalog and JVM target 17. `versionName` is the value of the
 * repository's single `VERSION` file (`AC-REQ-NFR-006-2`, `DEC-043`, `DEC-067`); `versionCode` is
 * deliberately not invented, because no authoritative source or accepted decision defines a
 * formula for it. It declares no dependency (OD-3).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val root = packageRoot.get()

            extensions.configure<ApplicationExtension> {
                namespace = "$root.app"
                compileSdk = catalog.version("android-compileSdk")
                defaultConfig {
                    applicationId = root
                    minSdk = catalog.version("android-minSdk")
                    targetSdk = catalog.version("android-targetSdk")
                    versionName = ApplicationVersion.read(this@with).also { value ->
                        checkNotNull(value) {
                            "The `VERSION` file is required at the repository root: it is the single source of the " +
                                "application version (AC-REQ-NFR-006-2, DEC-067)."
                        }
                    }
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
