package io.github.davidru85.multiverse.buildlogic.boundaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-017` / `TEST-UNIT-012` / `TEST-UNIT-043` — the graph, purity and structure rules.
 *
 * Each rule is pinned in both directions: the prohibited case fails with **its own** rule id, and
 * the closest permitted case produces nothing. The fixtures are plain snapshots, so the evaluation
 * is a pure function of its inputs and needs no Gradle model, no network and no wall-clock time.
 */
class ModuleBoundaryRulesTest {

    private fun snapshot(vararg projects: ProjectSnapshot) = ModuleGraphSnapshot(projects.toList())

    private fun project(
        path: String,
        edges: List<DeclaredEdge> = emptyList(),
        externals: List<DeclaredExternalDependency> = emptyList(),
        targets: List<String> = ACCEPTED,
        moduleLocalProperties: List<String> = emptyList(),
    ) = ProjectSnapshot(
        path = path,
        directory = path.trimStart(':').replace(':', '/'),
        moduleKind = moduleKindOf(path),
        edges = edges,
        externalDependencies = externals,
        targets = targets,
        moduleLocalProperties = moduleLocalProperties,
    )

    /** The accepted target set of every current KMP library (`DEC-080`). */
    private companion object {
        val ACCEPTED = listOf("android", "iosArm64", "iosSimulatorArm64")
    }

    private fun edge(
        consumer: String,
        producer: String,
        configuration: String = "commonMainImplementation",
        sourceSet: String = "commonMain",
        kind: SourceSetKind = SourceSetKind.PRODUCTION,
        origin: String = configuration,
    ) = DeclaredEdge(consumer, configuration, sourceSet, kind, producer, origin)

    private fun external(
        consumer: String,
        coordinates: String,
        configuration: String = "commonMainImplementation",
        sourceSet: String = "commonMain",
        kind: SourceSetKind = SourceSetKind.PRODUCTION,
    ) = DeclaredExternalDependency(
        consumer = consumer,
        configuration = configuration,
        sourceSet = sourceSet,
        kind = kind,
        group = coordinates.substringBefore(':'),
        name = coordinates.substringAfter(':'),
    )

    private fun only(logs: BoundaryViolationLog, rule: String) = logs.all().filter { it.ruleId == rule }

    /** A complete, accepted topology so only the rule under test can contribute a violation. */
    private fun complete(vararg extra: ProjectSnapshot): ModuleGraphSnapshot {
        val leaves = (ModuleSet.CONTAINERS + ModuleSet.REQUIRED)
            .filterNot { path -> extra.any { it.path == path } }
            .map { project(it) }
        return ModuleGraphSnapshot((leaves + extra.toList()).sortedBy { it.path })
    }

    @Test
    fun `R1 rejects a project edge from core domain`() {
        val violation = only(
            ModuleBoundaryRules.evaluate(
                complete(project(":core:domain", edges = listOf(edge(":core:domain", ":core:data")))),
                emptyMap(),
            ),
            "R1",
        )
        assertEquals(1, violation.size)
        assertEquals(":core:domain", violation.single().consumer)
    }

    @Test
    fun `R2 and R3 allow only core domain`() {
        listOf(":core:data" to "R2", ":core:presentation" to "R3").forEach { (module, rule) ->
            val allowed = ModuleBoundaryRules.evaluate(
                complete(project(module, edges = listOf(edge(module, ":core:domain")))),
                emptyMap(),
            )
            assertEquals(emptyList(), only(allowed, rule).map { it.render() }, "$module may depend on :core:domain")

            val rejected = ModuleBoundaryRules.evaluate(
                complete(project(module, edges = listOf(edge(module, ":core:testing")))),
                emptyMap(),
            )
            assertEquals(1, only(rejected, rule).size, "$module may not depend on :core:testing")
        }
    }

    @Test
    fun `R2 and R3 accept core testing from a test source set and keep production closed`() {
        listOf(":core:data" to "R2", ":core:presentation" to "R3").forEach { (module, rule) ->
            val testEdge = ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        module,
                        edges = listOf(
                            edge(
                                module,
                                ":core:testing",
                                configuration = "commonTestImplementation",
                                sourceSet = "commonTest",
                                kind = SourceSetKind.TEST,
                            ),
                        ),
                    ),
                ),
                emptyMap(),
            )
            assertEquals(
                emptyList(),
                only(testEdge, rule).map { it.render() },
                "DEC-089: $module's test source sets may consume the shared harness",
            )
            assertEquals(emptyList(), only(testEdge, "R6").map { it.render() })

            // The same edge inherited into `commonMain` is a production edge, so both rules still fire.
            val inherited = ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        module,
                        edges = listOf(edge(module, ":core:testing", origin = "testHarness")),
                    ),
                ),
                emptyMap(),
            )
            assertEquals(1, only(inherited, rule).size, "DEC-089: production stays closed for $module")
            assertEquals(1, only(inherited, "R6").size, "DEC-089: production never consumes :core:testing")

            // A test source set gains the harness only: any other core module stays rejected.
            val sibling = if (module == ":core:data") ":core:presentation" else ":core:data"
            val otherTestEdge = ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        module,
                        edges = listOf(
                            edge(
                                module,
                                sibling,
                                configuration = "commonTestImplementation",
                                sourceSet = "commonTest",
                                kind = SourceSetKind.TEST,
                            ),
                        ),
                    ),
                ),
                emptyMap(),
            )
            assertEquals(1, only(otherTestEdge, rule).size, "DEC-089: $module's tests may not reach $sibling")
        }
    }

    @Test
    fun `R1 rejects core testing even from a domain test source set`() {
        val violation = only(
            ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        ":core:domain",
                        edges = listOf(
                            edge(
                                ":core:domain",
                                ":core:testing",
                                configuration = "commonTestImplementation",
                                sourceSet = "commonTest",
                                kind = SourceSetKind.TEST,
                            ),
                        ),
                    ),
                ),
                emptyMap(),
            ),
            "R1",
        )
        assertEquals(1, violation.size, "DEC-089: a domain test never reaches the HTTP-bearing harness")
    }

    @Test
    fun `R4 and R15 reject a project edge and a non-Compose external in the design system`() {
        val designSystem = ":core:designsystem"
        val projectEdge = ModuleBoundaryRules.evaluate(
            complete(project(designSystem, edges = listOf(edge(designSystem, ":core:domain")))),
            emptyMap(),
        )
        assertEquals(1, only(projectEdge, "R4").size)

        val nonCompose = ModuleBoundaryRules.evaluate(
            complete(project(designSystem, externals = listOf(external(designSystem, "io.ktor:ktor-client-core")))),
            emptyMap(),
        )
        assertEquals(1, only(nonCompose, "R15").size)

        val compose = ModuleBoundaryRules.evaluate(
            complete(project(designSystem, externals = listOf(external(designSystem, "androidx.compose.runtime:runtime")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(compose, "R15").map { it.render() }, "a Compose artifact is allowed")

        val toolchain = ModuleBoundaryRules.evaluate(
            complete(project(designSystem, externals = listOf(external(designSystem, "org.jetbrains.kotlin:kotlin-stdlib")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(toolchain, "R15").map { it.render() }, "the implicit stdlib is not a choice")
    }

    @Test
    fun `R15 admits the approved test libraries of the design system and rejects every other`() {
        val designSystem = ":core:designsystem"

        // DEC-106: the design-system suites need JUnit, Robolectric and a JSON reader for the
        // tokens.json parity test; they carry no image, HTTP, DI or persistence code.
        val approved = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    designSystem,
                    externals = listOf(
                        external(designSystem, "junit:junit", "testImplementation", "test", SourceSetKind.TEST),
                        external(designSystem, "org.robolectric:robolectric", "testImplementation", "test", SourceSetKind.TEST),
                        external(
                            designSystem,
                            "org.jetbrains.kotlinx:kotlinx-serialization-json",
                            "testImplementation",
                            "test",
                            SourceSetKind.TEST,
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(approved, "R15").map { it.render() }, "DEC-106 approves these test libraries")

        // The same coordinates in a production source set stay rejected: the allowance is test-only.
        val production = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    designSystem,
                    externals = listOf(
                        external(
                            designSystem,
                            "org.jetbrains.kotlinx:kotlinx-serialization-json",
                            "implementation",
                            "main",
                            SourceSetKind.PRODUCTION,
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(production, "R15").size, "the allowance never reaches a production source set")

        // An unapproved test library still fails: the allow-list is closed.
        val unapproved = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    designSystem,
                    externals = listOf(
                        external(designSystem, "io.ktor:ktor-client-core", "testImplementation", "test", SourceSetKind.TEST),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(unapproved, "R15").size, "a test library outside DEC-106 is rejected")
    }

    @Test
    fun `R5 bounds core testing dependencies and R6 keeps it out of production`() {
        val testing = ":core:testing"
        val allowed = ModuleBoundaryRules.evaluate(
            complete(project(testing, edges = listOf(edge(testing, ":core:domain"), edge(testing, ":core:data")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(allowed, "R5").map { it.render() })

        val rejected = ModuleBoundaryRules.evaluate(
            complete(project(testing, edges = listOf(edge(testing, ":core:presentation")))),
            emptyMap(),
        )
        assertEquals(1, only(rejected, "R5").size)

        val production = ModuleBoundaryRules.evaluate(
            complete(project(":feature:discovery", edges = listOf(edge(":feature:discovery", testing)))),
            emptyMap(),
        )
        assertEquals(1, only(production, "R6").size, "production may not consume :core:testing")

        val testOnly = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":feature:discovery",
                    edges = listOf(
                        edge(":feature:discovery", testing, "commonTestImplementation", "commonTest", SourceSetKind.TEST),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(testOnly, "R6").map { it.render() }, "a test source set may consume it")
    }

    @Test
    fun `R7 rejects a feature to feature edge, direct or inherited`() {
        val direct = ModuleBoundaryRules.evaluate(
            complete(project(":feature:discovery", edges = listOf(edge(":feature:discovery", ":feature:favorites")))),
            emptyMap(),
        )
        assertEquals(1, only(direct, "R7").size)

        val inherited = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":feature:discovery",
                    edges = listOf(
                        edge(
                            consumer = ":feature:discovery",
                            producer = ":feature:favorites",
                            origin = "leak",
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        val violation = only(inherited, "R7").single()
        assertEquals(1, only(inherited, "R7").size)
        assertEquals("leak", violation.originConfiguration, "an inherited edge names its declaring configuration")
    }

    @Test
    fun `R8 R9 and R10 bound a feature's source sets by kind`() {
        val feature = ":feature:discovery"
        val sharedAllowed = ModuleBoundaryRules.evaluate(
            complete(project(feature, edges = listOf(edge(feature, ":core:domain"), edge(feature, ":core:presentation")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(sharedAllowed, "R8").map { it.render() })

        // DEC-091 (ADR-0014): the implementation module is never a feature's production dependency.
        val implementationRejected = ModuleBoundaryRules.evaluate(
            complete(project(feature, edges = listOf(edge(feature, ":core:data")))),
            emptyMap(),
        )
        assertEquals(1, only(implementationRejected, "R8").size, "DEC-091: a feature depends on the API, not on :core:data")

        val implementationInTests = ModuleBoundaryRules.evaluate(
            complete(project(feature, edges = listOf(edge(feature, ":core:data", "commonTestImplementation", "commonTest", SourceSetKind.TEST)))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(implementationInTests, "R10").map { it.render() }, "a feature test may reach the shared cores")

        val sharedRejected = ModuleBoundaryRules.evaluate(
            complete(project(feature, edges = listOf(edge(feature, ":core:designsystem")))),
            emptyMap(),
        )
        assertEquals(1, only(sharedRejected, "R9").size, "the design system is Android-UI only")

        val androidAllowed = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    feature,
                    edges = listOf(
                        edge(feature, ":core:designsystem", "androidMainImplementation", "androidMain", SourceSetKind.ANDROID_UI),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(androidAllowed, "R9").map { it.render() })

        val testDesignSystem = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    feature,
                    edges = listOf(edge(feature, ":core:designsystem", "commonTestImplementation", "commonTest", SourceSetKind.TEST)),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(testDesignSystem, "R9").size, "the design system is Android-UI only, tests included")

        val testForeign = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    feature,
                    edges = listOf(edge(feature, ":core:ios", "commonTestImplementation", "commonTest", SourceSetKind.TEST)),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(testForeign, "R10").size, "a feature test set may use :core:testing and the shared core only")
    }

    @Test
    fun `R11 lets the app compose the features and the design system only`() {
        val allowed = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":androidApp",
                    edges = listOf(edge(":androidApp", ":feature:discovery"), edge(":androidApp", ":core:designsystem")),
                ),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(allowed, "R11").map { it.render() })

        // DEC-091 (ADR-0014): the shell is the composition root, so it may wire the implementations.
        val compositionRoot = ModuleBoundaryRules.evaluate(
            complete(project(":androidApp", edges = listOf(edge(":androidApp", ":core:data")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(compositionRoot, "R11").map { it.render() }, "DEC-091: the composition root")

        val rejected = ModuleBoundaryRules.evaluate(
            complete(project(":androidApp", edges = listOf(edge(":androidApp", ":core:domain")))),
            emptyMap(),
        )
        assertEquals(1, only(rejected, "R11").size)
    }

    @Test
    fun `R11 admits only the approved shell test modules from a test source set`() {
        // DEC-106: the shell's tests resolve the graph with the shared harness and drive the copy
        // keys of :core:presentation; neither may reach a production or release configuration.
        val harness = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":androidApp",
                    edges = listOf(
                        edge(":androidApp", ":core:testing", "testImplementation", "test", SourceSetKind.TEST),
                        edge(":androidApp", ":core:presentation", "testImplementation", "test", SourceSetKind.TEST),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(harness, "R11").map { it.render() }, "DEC-106 approves these test modules")

        val production = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":androidApp",
                    edges = listOf(edge(":androidApp", ":core:testing", "implementation", "main", SourceSetKind.PRODUCTION)),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(production, "R11").size, "a production edge to the harness stays rejected")

        val otherCore = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":androidApp",
                    edges = listOf(edge(":androidApp", ":core:domain", "testImplementation", "test", SourceSetKind.TEST)),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(otherCore, "R11").size, "a core module outside DEC-106 stays rejected")
    }

    @Test
    fun `R12 rejects core ios exposing the implementation module through api`() {
        val ios = ":core:ios"
        val exported = ModuleBoundaryRules.evaluate(
            complete(project(ios, edges = listOf(edge(ios, ":core:data", "commonMainApi", "commonMain")))),
            emptyMap(),
        )
        assertEquals(1, only(exported, "R12").size, "DEC-091: :core:data is linked, never exported, so no implementation type reaches Swift")

        val api = ModuleBoundaryRules.evaluate(
            complete(project(ios, edges = listOf(edge(ios, ":core:domain", "commonMainApi", "commonMain")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(api, "R12").map { it.render() }, "the API module is exported")
    }

    @Test
    fun `R14 rejects every external in core domain except coroutines`() {
        val rejected = ModuleBoundaryRules.evaluate(
            complete(project(":core:domain", externals = listOf(external(":core:domain", "io.ktor:ktor-client-core")))),
            emptyMap(),
        )
        assertEquals(1, only(rejected, "R14").size)

        val allowed = ModuleBoundaryRules.evaluate(
            complete(
                project(":core:domain", externals = listOf(external(":core:domain", "org.jetbrains.kotlinx:kotlinx-coroutines-core"))),
            ),
            emptyMap(),
        )
        assertEquals(emptyList(), only(allowed, "R14").map { it.render() })
    }

    @Test
    fun `R14 admits the approved test libraries in a domain test source set only`() {
        val approved = listOf(
            "org.jetbrains.kotlin:kotlin-test",
            "org.jetbrains.kotlin:kotlin-test-junit",
            "org.jetbrains.kotlinx:kotlinx-coroutines-test",
        )
        val inTests = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":core:domain",
                    externals = approved.map {
                        external(
                            ":core:domain",
                            it,
                            configuration = "commonTestImplementation",
                            sourceSet = "commonTest",
                            kind = SourceSetKind.TEST,
                        )
                    },
                ),
            ),
            emptyMap(),
        )
        assertEquals(
            emptyList(),
            only(inTests, "R14").map { it.render() },
            "DEC-089: a domain test may declare the approved test libraries",
        )

        val inProduction = ModuleBoundaryRules.evaluate(
            complete(project(":core:domain", externals = approved.map { external(":core:domain", it) })),
            emptyMap(),
        )
        assertEquals(3, only(inProduction, "R14").size, "DEC-066: a test library is never a production dependency")

        val otherInTests = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":core:domain",
                    externals = listOf(
                        external(
                            ":core:domain",
                            "io.ktor:ktor-client-mock",
                            configuration = "commonTestImplementation",
                            sourceSet = "commonTest",
                            kind = SourceSetKind.TEST,
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(otherInTests, "R14").size, "DEC-089: the test allow-list is closed")
    }

    @Test
    fun `R12 bounds core ios edges and keeps it off Android`() {
        val ios = ":core:ios"
        val allowed = ModuleBoundaryRules.evaluate(
            complete(project(ios, edges = listOf(edge(ios, ":feature:discovery")))),
            emptyMap(),
        )
        assertEquals(emptyList(), only(allowed, "R12").map { it.render() })

        val rejected = ModuleBoundaryRules.evaluate(
            complete(project(ios, edges = listOf(edge(ios, ":core:testing")))),
            emptyMap(),
        )
        assertEquals(1, only(rejected, "R12").size)

        val androidConsumer = ModuleBoundaryRules.evaluate(
            complete(
                project(
                    ":androidApp",
                    edges = listOf(edge(":androidApp", ios, "implementation", "androidMain", SourceSetKind.ANDROID_UI)),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, only(androidConsumer, "R12").size, "no Android source set may consume :core:ios")
    }

    @Test
    fun `R12 accepts the three production cores and the five features, and nothing else`() {
        val ios = ":core:ios"
        (ModuleSet.FEATURES + setOf(":core:domain", ":core:data", ":core:presentation")).forEach { producer ->
            val log = ModuleBoundaryRules.evaluate(
                complete(project(ios, edges = listOf(edge(ios, producer)))),
                emptyMap(),
            )
            assertEquals(emptyList(), only(log, "R12").map { it.render() }, "$producer is an accepted export")
        }
        // :core:designsystem is Android-only and :core:testing is test-only; neither may ship.
        listOf(":core:designsystem", ":core:testing").forEach { producer ->
            val log = ModuleBoundaryRules.evaluate(
                complete(project(ios, edges = listOf(edge(ios, producer)))),
                emptyMap(),
            )
            assertEquals(1, only(log, "R12").size, "$producer must not be exported by :core:ios")
        }
    }

    @Test
    fun `R13 rejects an unknown project and an edge to an absent producer`() {
        val unknown = ModuleBoundaryRules.evaluate(
            complete(project(":services:shared")),
            emptyMap(),
        )
        assertEquals(listOf(":services:shared"), only(unknown, "R13").map { it.consumer })

        val absent = ModuleBoundaryRules.evaluate(
            complete(project(":feature:discovery", edges = listOf(edge(":feature:discovery", ":core:absent")))),
            emptyMap(),
        )
        assertEquals(listOf(":core:absent"), only(absent, "R13").map { it.producer })
    }

    @Test
    fun `S1 needs a real destination and S3 needs real packages`() {
        val feature = ":feature:discovery"
        val noDestination = ModuleBoundaryRules.evaluate(
            complete(project(feature)),
            mapOf(
                feature to listOf(
                    FeatureSource("feature/discovery/src/commonMain/kotlin/…/navigation/CharacterList.kt", null, false, false),
                ),
            ),
        )
        assertEquals(1, only(noDestination, "S1").count { it.consumer == feature })

        val destination = ModuleBoundaryRules.evaluate(
            complete(project(feature)),
            mapOf(feature to listOf(FeatureSource("…/navigation/CharacterList.kt", null, true, false))),
        )
        assertEquals(emptyList(), only(destination, "S1").filter { it.consumer == feature }.map { it.render() })

        val wrongPackages = ModuleBoundaryRules.evaluate(
            complete(project(feature)),
            mapOf(
                feature to listOf(
                    FeatureSource("…/navigation/CharacterList.kt", null, true, false),
                    FeatureSource(
                        "…/domain/Character.kt",
                        "io.github.davidru85.multiverse.wrong",
                        false,
                        false,
                    ),
                ),
            ),
        )
        assertEquals(1, only(wrongPackages, "S3").count { it.consumer == feature }, "a directory name alone does not prove a package")

        val rightPackages = ModuleBoundaryRules.evaluate(
            complete(project(feature)),
            mapOf(
                feature to listOf(
                    FeatureSource("…/navigation/CharacterList.kt", null, true, false),
                    FeatureSource("…/domain/Character.kt", "io.github.davidru85.multiverse.feature.discovery.domain", false, false),
                    FeatureSource("…/presentation/State.kt", "io.github.davidru85.multiverse.feature.discovery.presentation", false, false),
                ),
            ),
        )
        assertEquals(emptyList(), only(rightPackages, "S3").filter { it.consumer == feature }.map { it.render() })
    }

    @Test
    fun `the clean accepted topology produces no graph violation`() {
        val log = ModuleBoundaryRules.evaluate(complete(), emptyMap())
        val graph = log.all().filterNot { it.ruleId.startsWith("S") }
        assertEquals(emptyList(), graph.map { it.render() })
    }

    @Test
    fun `violations render deterministically and name their rule and test id`() {
        val log = ModuleBoundaryRules.evaluate(
            complete(project(":core:domain", edges = listOf(edge(":core:domain", ":core:data")))),
            emptyMap(),
        )
        val rendered = log.render()
        assertTrue(rendered.contains("TEST-UNIT-017 R1 consumer=:core:domain"))
        assertFalse(rendered.contains("/Users/"), "no machine path is ever rendered")
        assertEquals(rendered, log.render(), "two renders of one log are identical")
    }

    // --- R17 (TASK-101, B2-R04, GAP-018): the target set and the opt-in JVM target ---

    @Test
    fun `a KMP library that lost a required target is reported by its own rule`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(project(":feature:settings", targets = listOf("android", "iosSimulatorArm64"))),
                emptyMap(),
            )
        assertEquals(
            listOf("R17"),
            only(log, "R17").map { it.ruleId },
            "GAP-018: a module missing the iosArm64 target must be reported",
        )
        assertTrue(
            only(log, "R17").single().reason.contains("iosArm64"),
            "the diagnostic must name the missing target",
        )
    }

    @Test
    fun `an unapproved target on a feature module is reported`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(project(":feature:settings", targets = ACCEPTED + "wasmJs")),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").any { it.reason.contains("wasmJs") },
            "GAP-018: an unapproved platform must be reported, not silently accepted",
        )
    }

    @Test
    fun `the opt-in JVM target on a feature module is reported even when the property is module-local`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        ":feature:settings",
                        targets = ACCEPTED + "jvm",
                        moduleLocalProperties = listOf("multiverse.jvmTarget"),
                    ),
                ),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").any { it.reason.contains("only a `:core:*` KMP library may declare") },
            "GAP-018 reproduced: :feature:settings opting in must fail, which the shipped check passed",
        )
    }

    @Test
    fun `a core module with the module-local opt-in and a JVM target is accepted`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(
                    project(
                        ":core:data",
                        targets = ACCEPTED + "jvm",
                        moduleLocalProperties = listOf("multiverse.jvmTarget"),
                    ),
                ),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").isEmpty(),
            "DEC-080: a `:core:*` module with a module-local opt-in is the permitted case",
        )
    }

    @Test
    fun `a JVM target without the module-local opt-in is reported`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(project(":core:data", targets = ACCEPTED + "jvm", moduleLocalProperties = emptyList())),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").any { it.reason.contains("module-local") },
            "DEC-080: a JVM target must be selected by the module's own property",
        )
    }

    @Test
    fun `a module-local opt-in that selects nothing is reported`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(project(":core:data", moduleLocalProperties = listOf("multiverse.jvmTarget"))),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").any { it.reason.contains("stale declaration") },
            "DEC-080: a property that selects nothing is a stale declaration",
        )
    }

    @Test
    fun `the implicit metadata target is neither required nor rejected`() {
        val log =
            ModuleBoundaryRules.evaluate(
                complete(project(":core:data", targets = ACCEPTED + "metadata")),
                emptyMap(),
            )
        assertTrue(
            only(log, "R17").isEmpty(),
            "metadata is the common-source-set target KMP always creates; reporting it is a false positive",
        )
    }
}
