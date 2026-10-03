// :core:designsystem — Material 3 Expressive tokens and components.
// ADR-0001: an Android-only module that depends on no project module.

plugins {
    id("multiverse.android.library")
    // The components are Compose; the compiler plugin is applied here so the module compiles
    // `@Composable` functions (ADR-0008, `GUIDELINES.md` §5.1). The root build declares the alias
    // `apply false`, so the version has exactly one source (the catalog).
    alias(libs.plugins.kotlin.compose)
}

// The copy-resolution case (TEST-UNIT-008) reads the module's own resources on the JVM host with
// Robolectric, so the unit-test task must carry them; without this the test cannot observe a
// locale-qualified value.
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric reads `SharedSecrets.getJavaIOFileDescriptorAccess()` reflectively, which JDK 25
                // restricts; the daemon JVM is pinned to 25 (`gradle/gradle-daemon-jvm.properties`), so the
                // opens and the native-access grant are declared here rather than downgrading the toolchain.
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

dependencies {
    // The token objects carry the Compose value types (`Color`, `Dp`, `TextUnit`), and the components
    // need the runtime, foundation, UI and Material 3 Expressive surface (R15 admits `androidx.compose.*`).
    // `:core:designsystem` is the only module that declares Material 3 (ADR-0008 rule 2, `GUIDELINES.md` §5.1).
    api(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    // `api`, so a consumer composes with Material 3 while `:core:designsystem` stays the only module
    // that **declares** it (`GUIDELINES.md` §5.1, ADR-0008 rule 2): the shell and the features name
    // M3 types without pinning the alpha themselves, so a rollback stays a one-module change.
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DEC-106: the module's test source sets may declare exactly these test libraries, which
    // carry no image, HTTP, DI or persistence code. The production source set is Compose-only (R15).
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
