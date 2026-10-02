package io.github.davidru85.multiverse.buildlogic.boundaries

/**
 * Rule ids, one per normative statement, so a failure names the exact rule:
 *
 * Graph and purity rules (`TEST-UNIT-017` unless stated):
 * - `R1` `:core:domain` declares no project dependency (ADR-0001, `DEC-066`).
 * - `R2` `:core:data` depends only on `:core:domain`.
 * - `R3` `:core:presentation` depends only on `:core:domain`.
 * - `R4` `:core:designsystem` declares no project dependency.
 * - `R15` `:core:designsystem` declares Compose-only external dependencies (`DESIGN.md` §3.4 rule 4).
 * - `R5` `:core:testing` may depend on `:core:domain` and `:core:data`.
 * - `R6` no production source set consumes `:core:testing`.
 * - `R7` no `:feature:*` depends on another `:feature:*`.
 * - `R8` a feature's shared production source sets depend only on the API cores (`:core:domain`,
 *   `:core:presentation`); never on the implementation module `:core:data` (`DEC-091`).
 * - `R9` a feature's Android UI source set may additionally depend on `:core:designsystem`.
 * - `R10` a feature's test source sets may additionally depend on `:core:testing`.
 * - `R11` `:androidApp` composes the features and `:core:designsystem`, and as the composition root
 *   may depend on `:core:data` (`DEC-091`).
 * - `R12` `:core:ios` has only the ADR-0012 edges, never exposes `:core:data` through `api`
 *   (`DEC-091`), and no Android source set consumes it.
 * - `R13` an unrecognised project or edge fails closed.
 * - `R14` (`TEST-UNIT-012`) `:core:domain` declares no external dependency beyond the Kotlin
 *   standard library and `kotlinx-coroutines-core` (`DEC-066`).
 *
 * - `R16` the build contains the required leaf modules of ADR-0001 (`GAP-014`, `TASK-091`); a
 *   missing module fails closed, and a `:feature:*` path outside the accepted five is unknown.
 *
 * Staged structure rules (`TEST-UNIT-043`, `DEC-068`):
 * - `S1` every accepted feature declares its own navigation destination.
 * - `S2` no feature source references the app-wide `NavHost`.
 * - `S3` a feature that declares production source outside its route declaration has its own
 *   `domain` and `presentation` packages.
 */
internal object ModuleBoundaryRules {

    /**
     * Modules a shared production source set of a feature may depend on (R8): the API cores only.
     * `:core:data` is the implementation module, consumed by the composition roots (`DEC-091`,
     * ADR-0014).
     */
    private val FEATURE_PRODUCTION_CORE = setOf(":core:domain", ":core:presentation")

    /** Modules a feature's test source sets may depend on besides `:core:testing` (R10). */
    private val FEATURE_TEST_CORE = setOf(":core:domain", ":core:data", ":core:presentation")

    /** The shared production cores `:core:ios` links; only the API ones may be exported (R12). */
    private val CORE_IOS_CORE = setOf(":core:domain", ":core:data", ":core:presentation")

    /** The implementation module (`DEC-091`). */
    private const val IMPLEMENTATION_MODULE = ":core:data"

    /**
     * What `:core:ios` may depend on if it is introduced (ADR-0012, R12): the three shared
     * production core modules and the five accepted feature modules, never `:core:designsystem`
     * (Android-only) and never `:core:testing` (test-only). The allow-list names the accepted
     * modules instead of accepting any `:feature:*` prefix, so a path outside the set fails closed
     * (`GAP-014`).
     */
    private fun isCoreIosAllowed(producer: String): Boolean =
        producer in CORE_IOS_CORE || ModuleSet.isAcceptedFeature(producer)

    fun evaluate(
        snapshot: ModuleGraphSnapshot,
        featureSources: Map<String, List<FeatureSource>>,
    ): BoundaryViolationLog {
        val log = BoundaryViolationLog()

        knownModules(snapshot, log)
        requiredTopology(snapshot, log)
        coreDomain(snapshot, log)
        coreData(snapshot, log)
        corePresentation(snapshot, log)
        coreDesignSystem(snapshot, log)
        coreTesting(snapshot, log)
        coreIos(snapshot, log)
        features(snapshot, log)
        androidApp(snapshot, log)
        domainExternalPurity(snapshot, log)
        designSystemComposeOnly(snapshot, log)
        targetSets(snapshot, log)
        structure(snapshot, featureSources, log)

        return log
    }


    /**
     * `R17` — the target set of every KMP module is the accepted one, and the opt-in JVM target is
     * eligible (`DEC-080`, `TASK-101`, `GAP-018`).
     *
     * A target set is architecture: the Android and both Apple targets are what `DEC-054` gates,
     * and `DEC-079`/`DEC-080` permit one extra JVM target on a `:core:*` library — for the scheduled
     * live compilation — and nowhere else. The reproduced bypass was adding
     * `multiverse.jvmTarget=true` to `:feature:settings`, which created a JVM target while this
     * check passed: the rule could not see targets at all.
     *
     * What it enforces:
     *
     * - a `:core:*` or `:feature:*` KMP module declares the accepted Android and both Apple targets,
     *   so a module that has quietly lost one is reported (the positive half, like `R16`);
     * - a target outside the accepted set is reported, so an unapproved platform cannot be added
     *   silently;
     * - the opt-in JVM target is permitted only on a `:core:*` KMP library, and only when the
     *   `multiverse.jvmTarget` property is declared **in that module's own** `gradle.properties`.
     *
     * `:core:designsystem` is Android-only by `DEC-069` and is exempt from the KMP target rule;
     * `:androidApp` is not a KMP module at all.
     */
    private fun targetSets(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.projects.forEach { project ->
            // The KMP libraries are the shared cores and the features; `:androidApp` is not KMP and
            // `:core:designsystem` is Android-only by `DEC-069`.
            val kind = project.moduleKind
            val isKmpLibrary =
                kind == ModuleKind.FEATURE ||
                    (kind != ModuleKind.ANDROID_APP && kind != ModuleKind.CONTAINER && kind != ModuleKind.UNKNOWN &&
                        project.path.startsWith(":core:"))
            if (!isKmpLibrary) return@forEach
            if (kind == ModuleKind.CORE_DESIGN_SYSTEM) return@forEach

            val targets = project.targets.toSet()
            // A snapshot with no targets is a module the capture could not read a Kotlin extension
            // from (an Android-only module, or a plain evaluation fixture). The rule decides on a
            // declared set; the real convention plugin always declares one, and asserting the
            // absence would turn every synthetic fixture into a violation (TASK-101).
            if (targets.isEmpty()) return@forEach
            (ACCEPTED_TARGETS - targets).sorted().forEach { missing ->
                log.add(
                    violation(
                        rule = RULE_TARGETS,
                        consumer = project.path,
                        reason = "the module does not declare the `$missing` target; every current KMP " +
                            "library declares the accepted target set (DEC-054, DEC-080, TEST-UNIT-017)",
                    ),
                )
            }
            val declaredLocally = OPT_IN_PROPERTY in project.moduleLocalProperties
            val allowed = ACCEPTED_TARGETS + (if (declaredLocally) setOf("jvm") else emptySet())
            (targets - allowed - IMPLICIT_TARGETS).sorted().forEach { extra ->
                log.add(
                    violation(
                        rule = RULE_TARGETS,
                        consumer = project.path,
                        reason = "the module declares the `$extra` target, which is not part of the accepted " +
                            "set; an unapproved platform is a decision, not a detail (DEC-080, TEST-UNIT-017)",
                    ),
                )
            }

            // The opt-in JVM target: eligible only on a `:core:*` KMP library, and only when the
            // property is module-local.
            val enabled = targets.any { it == "jvm" }
            if (declaredLocally && kind == ModuleKind.FEATURE) {
                log.add(
                    violation(
                        rule = RULE_TARGETS,
                        consumer = project.path,
                        reason = "the module declares `$OPT_IN_PROPERTY`, which only a `:core:*` KMP library may " +
                            "declare; a feature module has no JVM target to enable (DEC-080, TEST-UNIT-017)",
                    ),
                )
            }
            if (enabled && !declaredLocally) {
                log.add(
                    violation(
                        rule = RULE_TARGETS,
                        consumer = project.path,
                        reason = "the module declares a JVM target without declaring `$OPT_IN_PROPERTY` in its " +
                            "own gradle.properties; the opt-in is module-local, and a global or inherited " +
                            "declaration is rejected (DEC-080, TEST-UNIT-017)",
                    ),
                )
            }
            if (declaredLocally && !enabled) {
                log.add(
                    violation(
                        rule = RULE_TARGETS,
                        consumer = project.path,
                        reason = "the module declares `$OPT_IN_PROPERTY` but no JVM target; a property that " +
                            "selects nothing is a stale declaration (DEC-080, TEST-UNIT-017)",
                    ),
                )
            }
        }
    }

    /** The targets every current KMP library declares (`DEC-054`, `DEC-080`). */
    private val ACCEPTED_TARGETS = setOf("android", "iosArm64", "iosSimulatorArm64")

    /**
     * Targets KMP creates itself and no one deploys to.
     *
     * `metadata` is the common-source-set target every multiplatform module has; reporting it as an
     * unapproved platform would be a false positive on a correct build.
     */
    private val IMPLICIT_TARGETS = setOf("metadata")

    /** The module-local property that selects the opt-in JVM target (`DEC-079`, `DEC-080`). */
    private const val OPT_IN_PROPERTY = "multiverse.jvmTarget"

    /** The rule id the target-set violations carry. */
    private const val RULE_TARGETS = "R17"

    /**
     * `R16` — the build contains exactly the leaf modules ADR-0001 requires today (`GAP-014`,
     * `TASK-091`).
     *
     * The other rules are all prohibitions: they reject an edge, a dependency or a source file.
     * A build that has silently lost a module has less to prohibit, so every prohibition still
     * passes and the check certifies a repository that no longer matches the accepted topology.
     * This rule states the positive half — the required set is present — and is the only rule that
     * can fail because something is **absent**.
     *
     * A planned module is legal but not required until the task that introduces it promotes it, so
     * `TASK-078` adds `:core:ios` to [ModuleSet.REQUIRED] in its own change.
     *
     * Diagnostics are sorted and repository-relative; the check never prints a machine path.
     */
    private fun requiredTopology(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        val present = snapshot.projects.map { it.path }.toSet()
        (ModuleSet.REQUIRED - present).sorted().forEach { missing ->
            log.add(
                topologyViolation(
                    reason = "the required module `$missing` of ADR-0001 is not part of the build; a missing " +
                        "module weakens every other rule, so the topology fails closed (ADR-0001, " +
                        "AC-REQ-NFR-009-3)",
                ),
            )
        }
    }

    private fun topologyViolation(reason: String) = ModuleBoundaryViolation(
        testId = BoundaryTestIds.TOPOLOGY,
        ruleId = "R16",
        consumer = "",
        configuration = "",
        sourceSet = "",
        producer = "",
        reason = reason,
    )

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

    /**
     * R2 / R3 — a core module that may depend only on `:core:domain`.
     *
     * Its test source sets may additionally consume `:core:testing`, which is the consumption rule
     * ADR-0001 states for every consumer of the harness (`DEC-069`, `DEC-089`). The allowance is
     * read from the edge's source-set kind, so an edge inherited into `commonMain` stays production
     * and stays rejected.
     */
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
            project.edges.filterNot { edge ->
                edge.producer == ":core:domain" ||
                    (edge.producer == ":core:testing" && edge.kind == SourceSetKind.TEST)
            }.forEach { edge ->
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
                        reason = "`:core:ios` depends on the five accepted features and the three shared " +
                            "production core modules only (`:core:designsystem` is Android-only and " +
                            "`:core:testing` is test-only; ADR-0012, `DEC-091`)",
                    ),
                )
            }
            // `DEC-091`: the implementation module is linked, never exported, so no implementation
            // type reaches Swift. An `api` declaration — direct or inherited — is what `export` admits.
            project.edges
                .filter { edge ->
                    edge.producer == IMPLEMENTATION_MODULE &&
                        (edge.configuration.endsWith("Api") || edge.originConfiguration.endsWith("Api"))
                }.forEach { edge ->
                    log.add(
                        violation(
                            rule = "R12",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            origin = edge.originConfiguration,
                            reason = "`:core:ios` links `:core:data` as `implementation` and never exports it; the " +
                                "Swift-visible surface is the features, `:core:domain` and `:core:presentation` " +
                                "(DEC-091, ADR-0014)",
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
                            origin = edge.originConfiguration,
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

                    edge.kind == SourceSetKind.TEST && edge.producer in FEATURE_TEST_CORE -> Unit

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

                    edge.kind != SourceSetKind.TEST && edge.producer !in FEATURE_PRODUCTION_CORE -> log.add(
                        violation(
                            rule = "R8",
                            consumer = project.path,
                            configuration = edge.configuration,
                            sourceSet = edge.sourceSet,
                            producer = edge.producer,
                            origin = edge.originConfiguration,
                            reason = "a feature's shared production source sets may depend only on the API cores " +
                                "`:core:domain` and `:core:presentation`; `:core:data` is the implementation module " +
                                "the composition roots wire (ADR-0001, ADR-0014, DEC-091, DESIGN.md §3.4 rule 5)",
                        ),
                    )
                }
            }
        }
    }

    /**
     * R11 — `:androidApp` composes the features and `:core:designsystem` and, as the composition root,
     * may depend on the implementation module (`DEC-091`); never `:core:ios`.
     */
    private fun androidApp(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        val allowed = snapshot.byKind(ModuleKind.FEATURE).map { it.path }.toSet() + ":core:designsystem" + IMPLEMENTATION_MODULE
        snapshot.byKind(ModuleKind.ANDROID_APP).forEach { project ->
            project.edges.filterNot { it.producer in allowed }.forEach { edge ->
                log.add(
                    violation(
                        rule = "R11",
                        consumer = project.path,
                        configuration = edge.configuration,
                        sourceSet = edge.sourceSet,
                        producer = edge.producer,
                        reason = "`:androidApp` composes the `:feature:*` modules and `:core:designsystem`, and as the " +
                            "composition root may depend on `:core:data`; it must not depend on `:core:ios` or another " +
                            "core module (ADR-0001, ADR-0012, ADR-0014, REQ-PLAT-004)",
                    ),
                )
            }
        }
    }

    /**
     * R14 — the external-library half of the `:core:domain` purity assertion (`DEC-066`).
     *
     * A domain **test** source set may also declare the approved test libraries (`DEC-089`); the
     * production allow-list is unchanged, and the test allow-list is closed.
     */
    private fun domainExternalPurity(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_DOMAIN).forEach { project ->
            project.externalDependencies
                .filterNot { dependency ->
                    dependency.coordinates in ModuleDependencyAllowLists.DOMAIN ||
                        (
                            dependency.kind == SourceSetKind.TEST &&
                                dependency.coordinates in ModuleDependencyAllowLists.DOMAIN_TEST
                        )
                }
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

    /**
     * R15 — `:core:designsystem` may declare Compose only (ADR-0001, `DESIGN.md` §3.4 rule 4).
     * The rule covers **effective** externals, so a non-Compose library inherited from a custom
     * configuration fails exactly like a direct one (`GAP-012`).
     */
    private fun designSystemComposeOnly(snapshot: ModuleGraphSnapshot, log: BoundaryViolationLog) {
        snapshot.byKind(ModuleKind.CORE_DESIGN_SYSTEM).forEach { project ->
            project.externalDependencies
                .filterNot { ModuleDependencyAllowLists.isToolchainImplicit(it.coordinates) }
                .filterNot { ModuleDependencyAllowLists.isDesignSystemAllowed(it.coordinates) }
                .forEach { dependency ->
                    log.add(
                        violation(
                            rule = "R15",
                            consumer = project.path,
                            configuration = dependency.configuration,
                            sourceSet = dependency.sourceSet,
                            producer = dependency.coordinates,
                            origin = dependency.originConfiguration,
                            reason = "`:core:designsystem` may declare Compose only; " +
                                "`${dependency.coordinates}` is not a Compose artifact (ADR-0001, DESIGN.md §3.4 rule 4)",
                        ),
                    )
                }
        }
    }

    /** S1–S3 — the staged structure rules of `DEC-068` (`TEST-UNIT-043`), content-aware (`GAP-012`). */
    private fun structure(
        snapshot: ModuleGraphSnapshot,
        featureSources: Map<String, List<FeatureSource>>,
        log: BoundaryViolationLog,
    ) {
        snapshot.byKind(ModuleKind.FEATURE).forEach { project ->
            val sources = featureSources[project.path].orEmpty()
            val navigation = sources.filter { it.path.contains("/navigation/") }
            val destinations = navigation.filter { it.declaresDestination }
            if (destinations.isEmpty()) {
                log.add(
                    structureViolation(
                        rule = "S1",
                        consumer = project.path,
                        reason = "the feature declares no typed navigation destination; a file under `navigation/` " +
                            "counts only when it declares an `@Serializable` destination (`DESIGN.md` §4.2, " +
                            "AC-REQ-NFR-009-2)",
                    ),
                )
            }

            val outsideNavigation = sources.filterNot { it.path.contains("/navigation/") }
            if (outsideNavigation.isNotEmpty()) {
                val packageRoot = "io.github.davidru85.multiverse.feature." +
                    project.path.removePrefix(":feature:").replace("-", "")
                val domain = outsideNavigation.any { it.packageName == "$packageRoot.domain" }
                val presentation = outsideNavigation.any { it.packageName == "$packageRoot.presentation" }
                if (!domain || !presentation) {
                    log.add(
                        structureViolation(
                            rule = "S3",
                            consumer = project.path,
                            reason = "the feature declares production source outside its route declaration, so real " +
                                "Kotlin packages `$packageRoot.domain` and `$packageRoot.presentation` are required; " +
                                "a directory name alone does not satisfy this rule (DEC-068, AC-REQ-NFR-009-2)",
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
        origin: String = "",
        reason: String,
    ) = ModuleBoundaryViolation(
        testId = BoundaryTestIds.GRAPH,
        ruleId = rule,
        consumer = consumer,
        configuration = configuration,
        sourceSet = sourceSet,
        producer = producer,
        originConfiguration = origin,
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
