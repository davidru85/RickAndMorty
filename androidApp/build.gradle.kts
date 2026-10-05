import java.io.File

// :androidApp — the Android shell. It is the composition root (`DEC-091`, ADR-0014): it owns the
// Application, the one activity, the app-wide navigation graph, the splash handoff, the Coil image
// loader and the platform Koin module. `TASK-044` builds all of it.

plugins {
    id("multiverse.android.application")
    // The shell composes screens, so it needs the Compose compiler; the version lives in the catalog.
    alias(libs.plugins.kotlin.compose)
    // The app-wide `NavHost` is composed from the features' typed route declarations, which are
    // `@Serializable`.
    alias(libs.plugins.kotlin.serialization)
    // The app-shell screenshot baselines (`TEST-UI-012`, `TASK-045`): Roborazzi records the committed
    // PNGs under `src/test/snapshots` and verifies them on the JVM host (DEC-024, DEC-034).
    alias(libs.plugins.roborazzi)
}

android {
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--enable-native-access=ALL-UNNAMED",
                )
            }
        }
    }
}

/** The Android SDK root, in the order the Android plugin itself resolves it. */
fun resolveAndroidSdk(root: org.gradle.api.Project): File {
    val local = root.file("local.properties")
    if (local.isFile) {
        val line = local.readLines().firstOrNull { it.trim().startsWith("sdk.dir=") }
        if (line != null) return File(line.substringAfter('=').trim())
    }
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (fromEnv != null) return File(fromEnv)
    error("verifySdkLevels (TEST-UNIT-018) needs an Android SDK: none was found in local.properties, ANDROID_HOME or ANDROID_SDK_ROOT")
}

dependencies {
    // The five features (their route declarations and, from B5, their screens), the design system and
    // the composition root's own `:core:data` edge (`R11`, `DEC-091`).
    implementation(project(":feature:discovery"))
    implementation(project(":feature:character-detail"))
    implementation(project(":feature:favorites"))
    implementation(project(":feature:episodes"))
    implementation(project(":feature:settings"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    // The shared presentation primitives the shell composes: the card-to-detail hand-off (`IC-025`,
    // `DESIGN.md` §4.2). It carries no implementation and no platform type, and `R11` admits it.
    implementation(project(":core:presentation"))

    // The diagnostic API is linked from a `debug*` configuration only: `R11` rejects any other
    // configuration, and the release closure never reaches the module (`DEC-088`, ADR-0013).
    debugImplementation(project(":core:diagnostics"))

    // Material 3 comes through `:core:designsystem` (ADR-0008 rule 2, `GUIDELINES.md` §5.1): the
    // shell declares the Compose runtime, UI, activity, navigation and splash surfaces only.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    // The composition root's own platform integration.
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // The app-shell screenshot harness (`TASK-045`): the capture API and its JUnit rule, declared
    // where the shell snapshots use them (DEC-077, DEC-034).
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    // The Compose rule hosts its content in a provided activity; the artifact supplies it to the test
    // manifest, so a UI case needs no activity of the app's own.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    // `TEST-UNIT-033` inspects the real release artifact, so the check runs against what ships.
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * `TEST-UNIT-033`, the artifact half (`AC-REQ-OBS-002-1`, `DEC-088`, `TASK-044`): the diagnostic API
 * and the panel host must be absent from the **real release APK** and present in the debug one.
 *
 * `R11` already decides the graph, but the criterion asks about the artifact, so this task reads the
 * built APKs' class tables. It is wired into `check`, which makes the proof blocking in the `android`
 * job rather than a described intention.
 */
val verifyReleaseArtifact by tasks.registering(io.github.davidru85.multiverse.buildlogic.policy.VerifyReleaseArtifactTask::class) {
    val debugApkFile = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
    val releaseApkFile = layout.buildDirectory.file("outputs/apk/release/androidApp-release-unsigned.apk")
    debugApk.set(debugApkFile)
    releaseApk.set(releaseApkFile)
    report.set(layout.buildDirectory.file("reports/verifyReleaseArtifact/release-artifact.txt"))
    dependsOn("assembleDebug", "assembleRelease")
}

tasks.named("check") { dependsOn(verifyReleaseArtifact) }

/**
 * `TEST-UNIT-028`, the artifact half (`AC-REQ-SEC-004-1`, `TASK-048`): the **release APK's** merged
 * permission table carries no `RECORD_AUDIO`. The root `verifyNoMicSpeechPermission` reads the source
 * manifests, which cannot show a permission a library manifest merges in; this reads what ships.
 */
val verifyShippedPermissions =
    tasks.register<io.github.davidru85.multiverse.buildlogic.policy.VerifyShippedPermissionsTask>("verifyShippedPermissions") {
        apk.set(layout.buildDirectory.file("outputs/apk/release/androidApp-release-unsigned.apk"))
        report.set(layout.buildDirectory.file("reports/verifyShippedPermissions/permissions.txt"))
        sdkDirectory.set(rootProject.layout.dir(provider { resolveAndroidSdk(rootProject) }))
        rootDirectory.set(rootProject.layout.projectDirectory)
        dependsOn("assembleRelease")
    }

tasks.named("check") { dependsOn(verifyShippedPermissions) }

/**
 * `TEST-UNIT-018` (`AC-REQ-PLAT-002-1`): the SDK levels the **artifact** declares. Reading the APK's
 * binary manifest is what catches a merger result or an overlay that contradicts the build script,
 * which a DSL-only assertion cannot.
 */
val verifySdkLevels by tasks.registering(io.github.davidru85.multiverse.buildlogic.policy.VerifySdkLevelsTask::class) {
    apk.set(layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk"))
    report.set(layout.buildDirectory.file("reports/verifySdkLevels/sdk-levels.txt"))
    // The SDK root, resolved the way the Android plugin resolves it: the project's `local.properties`
    // first, then `ANDROID_HOME`/`ANDROID_SDK_ROOT`. The task fails closed when none of them exists.
    sdkDirectory.set(rootProject.layout.dir(provider { resolveAndroidSdk(rootProject) }))
    dependsOn("assembleDebug")
}

tasks.named("check") { dependsOn(verifySdkLevels) }

/**
 * `TEST-UNIT-019` (`AC-REQ-PLAT-004-1`, `DEC-040`, `REQ-PLAT-004`, `TASK-050`): the Android milestone
 * is releasable with the iOS app absent.
 *
 * Two evidence sources make the criterion a property of this build rather than of a checkout that
 * happens to lack `iosApp/`. The release APK is inspected for iOS or Kotlin/Native payload, and the
 * app's declared release dependency edges are handed to the task so it can fail when any of them
 * names a module that exists only for the Apple target — the regression the criterion exists to
 * prevent, and one an artifact-only scan would not see until the dependency produced output.
 *
 * The edges are read from the build model at configuration time and passed as an `@Input`, which keeps
 * the task configuration-cache compatible; the model (not a resolution) is the authority, because a
 * declared edge is what a review has to catch.
 */
val declaredReleaseDependencyNames: List<String> =
    // `implementation(...)` lands in a variant-independent configuration, so filtering by a `release`
    // name would inspect nothing at all — a check that passes because it looks at the wrong place is
    // worse than no check. Every configuration this project can put on a release classpath is read,
    // and the `debugImplementation` edges are excluded because they are not part of a release artifact.
    configurations
        .filterNot { configuration -> configuration.name.startsWith("debug") }
        .flatMap { configuration ->
            configuration.dependencies
                .filter { dependency -> dependency is org.gradle.api.artifacts.ProjectDependency }
                .map { dependency -> (dependency as org.gradle.api.artifacts.ProjectDependency).path }
        }
        .distinct()
        .sorted()
        .also { names ->
            check(names.isNotEmpty()) {
                "TEST-UNIT-019 cannot inspect an empty edge set: the app must declare its project " +
                    "dependencies somewhere this check can read them."
            }
        }

val verifyMilestoneIndependence by tasks.registering(io.github.davidru85.multiverse.buildlogic.policy.VerifyMilestoneIndependenceTask::class) {
    releaseApk.set(layout.buildDirectory.file("outputs/apk/release/androidApp-release-unsigned.apk"))
    androidReleaseEdges.set(declaredReleaseDependencyNames)
    iosOnlyModules.set(listOf(":core:ios"))
    report.set(layout.buildDirectory.file("reports/verifyMilestoneIndependence/milestone-independence.txt"))
    dependsOn("assembleRelease")
}

tasks.named("check") { dependsOn(verifyMilestoneIndependence) }
