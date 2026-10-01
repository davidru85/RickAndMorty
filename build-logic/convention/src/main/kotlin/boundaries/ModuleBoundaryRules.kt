package io.github.davidru85.multiverse.buildlogic.boundaries

/**
 * Rule ids, one per normative statement, so a failure names the exact rule:
 *
 * Graph and purity rules (`TEST-UNIT-017` unless stated):
 * - `R1` `:core:domain` declares no project dependency (ADR-0001, `DEC-066`).
 * - `R2` `:core:data` depends only on `:core:domain`.
 * - `R3` `:core:presentation` depends only on `:core:domain`.
 * - `R4` `:core:designsystem` declares no project dependency.
 * - `R5` `:core:testing` may depend on `:core:domain` and `:core:data`.
 * - `R6` no production source set consumes `:core:testing`.
 * - `R7` no `:feature:*` depends on another `:feature:*`.
 * - `R8` a feature's shared production source sets depend only on the accepted core modules.
 * - `R9` a feature's Android UI source set may additionally depend on `:core:designsystem`.
 * - `R10` a feature's test source sets may additionally depend on `:core:testing`.
 * - `R11` `:androidApp` composes the features and `:core:designsystem` only.
 * - `R12` `:core:ios` has only the ADR-0012 edges, and no Android source set consumes it.
 * - `R13` an unrecognised project or edge fails closed.
 * - `R14` (`TEST-UNIT-012`) `:core:domain` declares no external dependency beyond the Kotlin
 *   standard library and `kotlinx-coroutines-core` (`DEC-066`).
 *
 * Staged structure rules (`TEST-UNIT-043`, `DEC-068`):
 * - `S1` every `:feature:*` declares its own navigation destination.
 * - `S2` no feature source references the app-wide `NavHost`.
 * - `S3` a feature that declares production source outside its route declaration has its own
 *   `domain` and `presentation` packages.
 */
internal object ModuleBoundaryRules {

    /** Modules a shared production source set of a feature may depend on (R8). */
    private val FEATURE_SHARED_CORE = setOf(":core:domain", ":core:data", ":core:presentation")

    /** The only external library `:core:domain` may declare (`DEC-066`, R14). */
    private val DOMAIN_ALLOWED_EXTERNALS = setOf("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    /** What `:core:ios` may depend on if it is introduced (ADR-0012, R12). */
    private fun isCoreIosAllowed(producer: String): Boolean =
        producer == ":core:domain" || producer == ":core:data" || producer == ":core:presentation" ||
            producer in FEATURE_SHARED_CORE || producer.startsWith(":feature:")

    fun evaluate(snapshot: ModuleGraphSnapshot): BoundaryViolationLog {
        val log = BoundaryViolationLog()

        knownModules(snapshot, log)
        coreDomain(snapshot, log)
        coreData(snapshot, log)
        corePresentation(snapshot, log)
        coreDesignSystem(snapshot, log)
        coreTesting(snapshot, log)
        coreIos(snapshot, log)
        features(snapshot, log)
        androidApp(snapshot, log)
        domainExternalPurity(snapshot, log)
        structure(snapshot, log)

        return log
    }

    /** R13 — an included project the check does not understand fails rather than passes. */
    private fun knownModules(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.projects.filter { it.moduleKind == ModuleKind.UNKNOWN }.forEach { project ->
            log.add(
                violation(
                    rule = "R13",
                    consumer = project.path,
                    reason = "the check does not understand this module; add it to the rules before it " +
                        "joins the build (ADR-0001, DESIGN.md §3.4)",
                ),
            )
        }
        val knownPaths = snapshot.paths.toSet()
        snapshot.projects.forEach { project ->
            project.edges.filterNot { it.producer in knownPaths }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R13",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "the edge targets a project that is not part of the snapshot; an unknown " +
                            "edge fails closed",
                    ),
                )
            }
        }
    }

    /** R1 — `:core:domain` declares no project dependency (ADR-0001, `DEC-066`). */
    private fun coreDomain(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_DOMAIN).forEach { project ->
            project.edges.forEach { edge ->
                log.add(
                    violation(
                        rule = "R1",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:core:domain` declares no project dependency (ADR-0001 rule 1, DEC-066)",
                    ),
                )
            }
        }
    }

    /** R2 / R3 — a core module that may depend only on `:core:domain`. */
    private fun coreData(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) =
        onlyCoreDomain(snapshot, log, ModuleKind.CORE_DATA, "R2", ":core:data")

    private fun corePresentation(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) =
        onlyCoreDomain(snapshot, log, ModuleKind.CORE_PRESENTATION, "R3", ":core:presentation")

    private fun onlyCoreDomain(
        snapshot: ModuleGraphSnapshot,
        log: BoundaryViolationLog,
        kind: ModuleKind,
        rule: String,
        module: String,
    ) {
        snapshot.byKind(kind).forEach { project ->
            project.edges.filterNot { it.producer == ":core:domain" }.forEach { edge ->
                log.add(
                    violation(
                        rule = rule,
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`$module` may depend only on `:core:domain` (ADR-0001, DESIGN.md §3.4)",
                    ),
                )
            }
        }
    }

    /** R4 — `:core:designsystem` declares no project dependency. */
    private fun coreDesignSystem(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_DESIGN_SYSTEM).forEach { project ->
            project.edges.forEach { edge ->
                log.add(
                    violation(
                        rule = "R4",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:core:designsystem` declares no project dependency; it depends on Compose only " +
                            "(ADR-0001, DESIGN.md §3.4 rule 4)",
                    ),
                )
            }
        }
    }

    /** R5 / R6 — `:core:testing`'s own edges, and the rule that production never consumes it. */
    private fun coreTesting(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_TESTING).forEach { project ->
            project.edges.filterNot { it.producer == ":core:domain" || it.producer == ":core:data" }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R5",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:core:testing` may depend on `:core:domain` and `:core:data` only " +
                            "(DESIGN.md §3.1, DEC-069)",
                    ),
                )
            }
        }
        snapshot.projects.forEach { project ->
            project.edges.filter { it.producer == ":core:testing" && it.kind != SourceSetKind.TEST }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R6",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:core:testing` is consumed from test source sets only; production source sets " +
                            "may not depend on it (DESIGN.md §3.4 rule 9, TESTING.md §13.1)",
                    ),
                )
            }
        }
    }

    /** R12 — `:core:ios`, when introduced by `TASK-078`, has only its accepted edges. */
    private fun coreIos(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_IOS).forEach { project ->
            project.edges.filterNot { isCoreIosAllowed(it.producer) }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R12",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:core:ios` exports the five features and the four other core modules only " +
                            "(ADR-0012, DEC-058)",
                    ),
                )
            }
        }
        snapshot.projects
            .filterNot { it.moduleKind == ModuleKind.CORE_IOS }
            .forEach { project ->
                project.edges.filter { it.producer == ":core:ios" && it.kind == SourceSetKind.ANDROID_UI }
                    .forEach { edge ->
                        log.add(
                            violation(
                                rule = "R12",
                                consumer = project.path,
                                configuration = edge.configuration,
                                sourceSet = edge.sourceSet,
                                producer = edge.producer,
                                reason = "no Android source set may depend on `:core:ios` (ADR-0012, REQ-PLAT-004)",
                            ),
                        )
                    }
            }
    }

    /** R7–R10 — the feature rules: no siblings, an accepted core set per source-set kind. */
    private fun features(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        val features = snapshot.byKind(ModuleKind.FEATURE).map { it.path }.toSet()
        snapshot.byKind(ModuleKind.FEATURE).forEach { project ->
            project.edges.forEach { edge ->
                val sibling = features.any { it != project.path && it == edge.producer }
                when {
                    sibling -> log.add(
                        violation(
                            rule = "R7",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            reason = "no `:feature:*` module may depend on another `:feature:*` module " +
                                "(ADR-0001 rule 6, AC-REQ-NFR-009-1)",
                        ),
                    )

                    edge.producer == ":core:designsystem" && edge.kind != SourceSetKind.ANDROID_UI -> log.add(
                        violation(
                            rule = "R9",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            reason = "`:core:designsystem` may be declared by Android UI source sets only " +
                                "(ADR-0001, DESIGN.md §3.4 rule 5)",
                        ),
                    )

                    edge.producer == ":core:testing" && edge.kind == SourceSetKind.TEST -> Unit

                    edge.kind == SourceSetKind.TEST && edge.producer in FEATURE_SHARED_CORE -> Unit

                    edge.kind == SourceSetKind.TEST && edge.producer != ":core:testing" -> log.add(
                        violation(
                            rule = "R10",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            reason = "a feature test source set may depend on `:core:testing` (and the shared core " +
                                "modules) only (DESIGN.md §3.4 rule 9, TESTING.md §13.1)",
                        ),
                    )

                    edge.kind == SourceSetKind.ANDROID_UI && edge.producer == ":core:designsystem" -> Unit

                    edge.kind != SourceSetKind.TEST && edge.producer !in FEATURE_SHARED_CORE -> log.add(
                        violation(
                            rule = "R8",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            reason = "a feature's shared production source sets may depend only on `:core:domain`, " +
                                "`:core:data` and `:core:presentation` (ADR-0001, DESIGN.md §3.4 rule 5)",
                        ),
                    )
                }
            }
        }
    }

    /** R11 — `:androidApp` composes the features and `:core:designsystem`; never `:core:ios`. */
    private fun androidApp(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        val allowed = snapshot.byKind(ModuleKind.FEATURE).map { it.path }.toSet() + ":core:designsystem"
        snapshot.byKind(ModuleKind.ANDROID_APP).forEach { project ->
            project.edges.filterNot { it.producer in allowed }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R11",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:androidApp` composes the `:feature:*` modules and `:core:designsystem` only; it " +
                            "must not depend on `:core:ios` or another core module (ADR-0001, ADR-0012, REQ-PLAT-004)",
                    ),
                )
            }
        }
    }

    /** R14 — the external-library half of the `:core:domain` purity assertion (`DEC-066`). */
    private fun domainExternalPurity(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_DOMAIN).forEach { project ->
            project.externalDependencies
                .filterNot { it.coordinates in DOMAIN_ALLOWED_EXTERNALS }
                .forEach { dependency ->
                    log.add(
                        ModuleBoundaryViolation(
                            testId = BoundaryTestIds.DOMAIN_PURITY,
                            ruleId = "R14",
                            consumer = project.path,
                            configuration = dependency.configuration,
                            sourceSet = dependency.sourceSet,
                            producer = dependency.coordinates,
                            reason = "`:core:domain` may declare the Kotlin standard library and " +
                                "`kotlinx-coroutines-core` only; every other external dependency belongs to " +
                                "another module (DEC-066, ADR-0001)",
                        ),
                    )
                }
        }
    }

    /** S1–S3 — the staged structure rules of `DEC-068` (`TEST-UNIT-043`). */
    private fun structure(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.FEATURE).forEach { project ->
            val sources = project.sourceFiles
            val navigation = sources.filter { it.contains("/navigation/") }
            if (navigation.isEmpty()) {
                log.add(
                    structureViolation(
                        rule = "S1",
                        consumer = project.path,
                        reason = "the feature declares no navigation destination; each feature owns its own route " +
                            "declaration (ADR-0001 rule 7, AC-REQ-NFR-009-2)",
                    ),
                )
            }
            val appWideNavHost = sources.filter { it.contains("NavHost") }
            if (appWideNavHost.isNotEmpty()) {
                log.add(
                    structureViolation(
                        rule = "S2",
                        consumer = project.path,
                        reason = "a feature source names the app-wide `NavHost`; the application shell owns the " +
                            "graph (ADR-0001 rule 7, AC-REQ-NFR-009-2)",
                    ),
                )
            }
            val outsideNavigation = sources.filterNot { it.contains("/navigation/") }
            if (outsideNavigation.isNotEmpty()) {
                val domain = sources.any { it.contains("/domain/") }
                val presentation = sources.any { it.contains("/presentation/") }
                if (!domain || !presentation) {
                    log.add(
                        structureViolation(
                            rule = "S3",
                            consumer = project.path,
                            reason = "the feature declares production source outside its route declaration, so its " +
                                "own `domain` and `presentation` packages are required (DEC-068, AC-REQ-NFR-009-2)",
                        ),
                    )
                }
            }
        }
    }

    private fun violation(
        rule: String,
        consumer: String,
        configuration: String = "",
        sourceSet: String = "",
        producer: String = "",
        reason: String,
    ) = ModuleBoundaryViolation(
        testId = BoundaryTestIds.GRAPH,
        ruleId = rule,
        consumer = consumer,
        configuration = configuration,
        sourceSet = sourceSet,
        producer = producer,
        reason = reason,
    )

    private fun structureViolation(rule: String, consumer: String, reason: String) = ModuleBoundaryViolation(
        testId = BoundaryTestIds.STRUCTURE,
        ruleId = rule,
        consumer = consumer,
        configuration = "",
        sourceSet = "",
        producer = "",
        reason = reason,
    )
}
