// :androidApp — the Android application shell. TASK-044 adds the Application,
// the DI graph, the app-wide navigation graph, the splash and the launcher icon;
// TASK-018 adds the version fields. This change declares the module and its edges
// only.

plugins {
    id("multiverse.android.application")
}

dependencies {
    implementation(project(":feature:discovery"))
    implementation(project(":feature:character-detail"))
    implementation(project(":feature:favorites"))
    implementation(project(":feature:episodes"))
    implementation(project(":feature:settings"))
    implementation(project(":core:designsystem"))
}
