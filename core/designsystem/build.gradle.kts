// :core:designsystem — Material 3 Expressive tokens and components.
// ADR-0001: an Android-only module that depends on no project module.

plugins {
    id("multiverse.android.library")
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
    // The token objects carry the Compose value types (`Color`, `Dp`, `TextUnit`), so the BOM and
    // `compose.ui` arrive with them (R15 admits `androidx.compose.ui:`). The components of
    // `TASK-043` add the rest of the Compose surface and the compiler plugin.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    // DEC-106: the module's test source sets may declare exactly these test libraries, which
    // carry no image, HTTP, DI or persistence code. The production source set is Compose-only
    // (R15) and every production dependency is declared with the components in phase 4.2.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.serialization.json)
}
