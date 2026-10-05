# HANDOFF.md — Current State and Continuation Guide

- **Status:** Active. Describes the repository as of 2026-10-05: B1–B9 are merged on `main`, and so are the code-review remediation's phase P0 (PR #192, `TASK-111`), the single launcher entry (PR #193, `TASK-117`) and the request logs (PR #201, `TASK-116`); `TASK-112`…`TASK-115` (PRs #202–#205) are merged; the owner decided `CONF-90` and `CONF-91` (`DEC-146`); `TASK-118`…`TASK-125` are merged (PRs #211–#215, #219–#221, and the bookkeeping in #222; `main` at `9f85e36`), and `TASK-126` (PR #224, `e86a3da`) pins `TASK-125`'s size check in the workflow guard under the owner's authorization (`DEC-152`), so the remediation programme has no open task; the tags and releases are the owner's; must be updated on every handoff (`AGENTS.md` §5 step 7).
- **Last verified:** 2026-10-06
- **Owner:** Delivery Planner (see `AGENTS.md` §3.8)
- **Authoritative for:** the current state of the project, what has and has not been verified, the next actions in priority order, handoff-specific operational risks, and the environment prerequisites for continuing.
- **Not authoritative for:** the plan and its milestones (`TECHNICAL_PLAN.md`), task state and acceptance criteria (`BACKLOG.md`), decision status (`DECISION_BOARD.md`), requirements (`REQUIREMENTS.md`), test strategy and the CI check list (`TESTING.md`), the gate (`DEFINITION.md`).
- **Inputs:** `README.md`, `docs/REQUIREMENTS.md`, `docs/DESIGN.md`, `docs/API_SPECS.md`, `docs/UI_SPEC.md`, `docs/DECISION_BOARD.md`, `docs/DOCUMENTATION_AUDIT.md`, `docs/TESTING.md`, `docs/DEFINITION.md`, the repository tree and its commit history.

> Read this file first if you are taking over. Then read `AGENTS.md` (operating rules and precedence) and the authoritative document for the area you are about to touch. Do not start from memory or from a similar project (`AGENTS.md` §2.1).

### Owner audit remediation — 2026-10-06

The owner's manual audit of 2026-10-05 listed eight Android defects and two iOS defects. Each was reproduced on the API 37 emulator or the iOS 27 simulator on 2026-10-06 and registered as a gap (`GAP-037`…`GAP-046`). Each is one task with its own issue and pull request. The branches are stacked: each starts from the previous task's head, every pull request targets `main`, and the owner reviews them together and merges them in order (`DEC-153`).

| Work | Branch | State |
| --- | --- | --- |
| `TASK-127` — a single splash on Android (`GAP-037`) | `fix/task-127-single-splash`, from `main` at `cd06fa7` | In review (issue #226; `LOG-0152`) |
| `TASK-128` — a smooth Discovery scroll on Android (`GAP-038`) | `fix/task-128-smooth-scroll`, from `TASK-127`'s head | Not started (issue #227) |
| `TASK-129` — the Android count line keeps its space (`GAP-039`) | `fix/task-129-count-line-space`, from `TASK-128`'s head | Not started (issue #228) |
| `TASK-130` — Material Symbols glyphs in the Android navigation bar (`GAP-040`) | `fix/task-130-nav-glyphs`, from `TASK-129`'s head | Not started (issue #229) |
| `TASK-131` — the Episodes placeholder centred on Android (`GAP-041`) | `fix/task-131-centred-placeholder`, from `TASK-130`'s head | Not started (issue #230) |
| `TASK-132` — REST API shown as the selected data source on Android (`GAP-042`) | `fix/task-132-selected-protocol`, from `TASK-131`'s head | Not started (issue #231) |
| `TASK-133` — smaller Detail stat tiles on Android (`GAP-043`) | `fix/task-133-smaller-stat-tiles`, from `TASK-132`'s head | Not started (issue #232) |
| `TASK-134` — a smaller favourite button with the right heart on Android (`GAP-044`) | `fix/task-134-favorite-button`, from `TASK-133`'s head | Not started (issue #233) |
| `TASK-135` — the iOS count line keeps its space (`GAP-045`) | `fix/task-135-ios-count-line-space`, from `TASK-134`'s head | Not started (issue #234) |
| `TASK-136` — the iOS list pages as the user scrolls (`GAP-046`) | `fix/task-136-ios-paging`, from `TASK-135`'s head | Not started (issue #235) |

### Code-review remediation — 2026-10-05

Three code reviews of `main` at `ce0d92a` (`prompts/code_review/`) are consolidated into five phase tasks, `TASK-111`…`TASK-115`, one pull request per phase (`DEC-122`). Two owner requests from the same session are `TASK-116` and `TASK-117`. From `TASK-112` on, the owner reviews at the end: each remaining task's branch starts from the previous task's head, every pull request targets `main`, and they merge in order (`DEC-133`).

| Work | Branch | State |
| --- | --- | --- |
| `TASK-111` P0 — crashes, stuck states, races, iOS image host bypass, live Share/Back | `fix/review-remediation-p0` | **Merged** (PR #192, `63d0652`; issue #194 closed) |
| `TASK-117` — one Android launcher icon; diagnostics through a debug-only shortcut | `fix/single-launcher-entry` | **Merged** (PR #193, `9599434`; issue #200 closed) |
| `TASK-112` P1 — Clear filters, protocol reset, visible revalidation, complete Detail and its recovery, plurals, manual refresh, card→Detail transitions, shared splash gate | `feat/task-112-p1` (from `main` at `b457a71`) | Merged for P1-a and P1-b (PR #202, `701d62f`; `LOG-0138`); P1-c escalated as `CONF-90` |
| `TASK-113` P2-A — Android fidelity to Figma: Roboto Flex, edge to edge, Discovery, card and accent, Detail, Favorites/Episodes titles, Settings, splash, launcher icon | `feat/task-113-p2a` (from `TASK-112`'s head) | Merged (PR #203, `7694c77`; `LOG-0139`) |
| `TASK-114` P2-B — iOS fidelity to Figma: glass tab bar, cosmic canvas, Discovery header and margins, card parallax, portal logo, Detail backdrop and chrome, splash, Settings, off-main portrait pipeline | `feat/task-114-p2b` (from `TASK-113`'s head) | Merged (PR #204, `f2c34e2`; `LOG-0140`) |
| `TASK-115` P3 — iOS observation without polling and one scope per screen, dead code, named copy keys, tokens for every UI measurement, presentation bindings in the roots, Reduce Motion observed, Discovery re-mapping only changed cards | `feat/task-115-p3` (from `TASK-114`'s head) | Merged (PR #205, `50e1574`; `LOG-0141`) |
| `TASK-118`…`TASK-122` — Episodes' copy keys (`CONF-91`), the iOS diagnostics sheet (`CONF-90`), the release APK size (`GAP-034`), the iOS link input (`GAP-033`), the iOS app icon | one branch each, stacked from `main` at `50e1574` (`DEC-133`, `DEC-146`) | **Merged**: PRs #211 (`37cf8c1`), #212 (`d0fa6f1`), #213 (`ee6cf4e`), #214 (`6f5ed54`), #215 (`e84a25e`); issues #206–#210 closed |
| `TASK-123`…`TASK-125` — the GraphQL detail key (`GAP-035`), a long Detail stat value on Android (`GAP-036`), the size check in CI (`CONF-92`) | one branch each, stacked on `TASK-122` (`DEC-151`) | **Merged**: PRs #219 (`fff3046`), #220 (`3bf423a`), #221 (`92ea3b3`); issues #216–#218 closed |
| `TASK-126` — the workflow guard pins the `PERF-009` size check (`DEC-152`) | `build/task-126-pin-size-check`, from `main` at `9f85e36` | **Merged**: PR #224 (`e86a3da`); issue #223 closed |
| `TASK-116` — REST and GraphQL request logs visible in debug builds on both platforms | `feat/debug-request-logs` | **Merged** (PR #201, `b457a71`; issue #199; `LOG-0137`). It also fixed a latent iOS crash: the log sink passed Kotlin strings to `%@` |

**Observed on the `TASK-112` branch (`LOG-0138`):** see that entry for the commands. In short, every touched Kotlin suite and the full Gradle gate pass, every Android Roborazzi baseline verifies, `xcodebuild test` on iPhone 17 / iOS 27.0 after a clean build passes 132 tests with 0 failures, and on that simulator the splash shows at 0.5 s and Discovery at 4.5 s. Not observed: the rendered card→Detail motion on a device (the tests pin its structure, not its frames).

**Found in `TASK-113` (`GAP-034`):** the release APK already measures 12.31 MiB against `PERF-009`'s 12 MiB. The task kept its own addition small (a 66 KB font subset, `DEC-137`); bringing the APK under budget needs release-build changes in `build-logic/**`, so it is left for its own task.

**Resolved by `TASK-120` (`DEC-148`, `LOG-0144`):** the build type lives in `androidApp/build.gradle.kts` after all. R8 and resource shrinking take the release APK to 2.23 MiB, and `verifyReleaseApkSize` checks it in `check`; CI runs it since `TASK-125` (`CONF-92`, `DEC-151`).

**Found in `TASK-120`'s device run, authorized by the owner (`DEC-151`):**

- **`GAP-035` (S1).** Over GraphQL, a Detail can show another character's details: the GraphQL detail cache key omits the id. This affects both platforms and is pre-existing (the debug build shows it too). Fixed in `TASK-123`.
- **`GAP-036` (S3).** On Android, a long Detail stat value breaks inside a word. Fixed in `TASK-124`, with the owner's rule: reduce the value until it fits, never breaking a word.

**Escalated in `TASK-112` (`CONF-90`):** the iOS debug diagnostics sheet of `REQ-OBS-002` needs `:core:ios` to link `:core:diagnostics`. `R12` in `build-logic` forbids that edge, and `build-logic/**` is outside the task's write authorization (`DEC-122`). The recommended option is one authorized `R12` change with a `DEC` that amends `DEC-088` for iOS; a Swift re-implementation of the recorder is rejected as a second implementation. Until the owner decides, `TEST-UI-027` stays reserved.

**Observed on the P0 branch:**
- The documented Gradle set plus `allTests`, ktlint, lint, the contract replay and the iOS framework link passed (BUILD SUCCESSFUL, 1207 tasks). Its reports hold 870 tests and 0 failures, plus `:androidApp`'s 44 up-to-date tests and `:core:data`'s full 197 re-run alone.
- `xcodebuild test` on iPhone 17 / iOS 27.0 → 121 tests, 0 failures.
- Swift lint is clean.

**Not done in P0, by decision:** hiding the iOS system bars on Detail moved to `TASK-114`, which delivers it (`DEC-140`); the edge swipe is verified through the pop recogniser's delegate, not by a performed swipe (`LOG-0140`). `TEST-UNIT-070` asserts the adapter's start, not the Swift-side reconciliation of a stored favourite, which cannot be built from Swift (`LOG-0134`).

**State after the merges (`LOG-0136`):** the combined `main` passes `:androidApp`'s 47 tests, `verifyReleaseArtifact`, `verifyRoborazziDebug`, `verifyModuleBoundaries` and the documentation gates. The owner confirmed `DEC-122` as recorded and resolved `CONF-85` ("Search characters"). Issues #194–#200 track `TASK-111`…`TASK-117`.

The Spanish strings of P0 are approved, and `CONF-86` is resolved: the microphone stays deferred outside the MVP (`LOG-0135`).

### B9 closure — 2026-10-04, reviewed 2026-10-05

**Block 9 is prepared through its three phase pull requests** (`DEC-110`): 9.1 the state-model cross-check and the carried budgets (#182), 9.2 the release-note automation and the documentation gate (#186), and 9.3 the handover and the deferred-register reconciliation (#189). Each is prepared on the previous reviewed head, so they merge in order without conflict: #161 → #178 → #182 → #186 → #189. #164, #166, #170 and #174 carry the same tree as #161 and close after it merges (`DEC-120`).

**The Android launch crash (`GAP-032`, S1) is closed, and `main` is not affected.** `MainActivity` injected the concrete `CoilImageSeam` while the graph registers only the `ImageSeam` port, so Koin failed in `onCreate` from B5 Phase 5.2 (`db4223b`) onward. PR #153's review reproduced the crash (`590c412`), fixed it and added a startup test that launches the real composition root (`3694ddd`); that fix reached `main` before M1, so the `v0.1.0` commit launches. Phase 9.3 found the same crash again on branches that predated that merge. It removed the dead `rememberCoilImageSeam` helper and added a second `TEST-UNIT-057` case that resolves the activity's actual injection points from the production graph. The review observed that case red on the old shape and green on the fix.

**Observed on the running Android product** (the record is `LOG-0118` for Phase 9.3 and `LOG-0125` with `docs/evidence/pr153/` for the PR #153 TalkBack run). These steps were reported by the people who ran them, not re-run in this review:
- Discovery rendering 826 live characters with portraits, badges and filters.
- Detail with its live `/episode/{ids}` enrichment.
- A favourite marked, and it survives `am force-stop` and relaunch.
- "Delete all" through its confirmation to the empty state.
- Cached content served offline.
- `Portal link lost` with a working `Retry`.

**Suite totals observed on the reviewed PR #189 tree (2026-10-05):** the Gradle verification set of `LOG-0133` → **1165 tests, 0 failures** across 193 report files. `xcodebuild test` on iPhone 17 / iOS 27.0 → **105 tests, 0 failures**.

**Carried as unevidenced, named rather than claimed:**
- `M1-4`/`M1-5` (`DEC-115`, `DEC-116`) and `M2-1`/`M2-2`/`M2-4` (`DEC-117`) need a named reference device or an iOS 18 runtime. Xcode 27 offers no iOS 18 runtime for download (18.0 and 18.6 answered "not available for download" on 2026-10-05).
- `TASK-067`'s advisory register stays empty, because `SECURITY.md` §11.1 forbids an invented finding.

**CI defects fixed along the way:**
- `tools/swift-lint.sh` now accepts either spelling of the symlinked `xcode-27` toolchain and still fails closed on a different one (`LOG-0120`).
- `:core:ios`'s formatter now runs in the `ios` job, and `verifyWorkflowGate` requires it (`LOG-0119`).
- The `ios` job now also runs the app's `xcodebuild test` and the version check (PR #161 and PR #178 reviews).

**Open:**
- `GAP-029`: `:androidApp` and the ktlint plugin.
- `GAP-030`: SwiftLint and swift-format disagree on trailing commas.
- `GAP-025`: `TEST-UNIT-046` and `TEST-UNIT-047` each still carry two meanings.
- `GAP-005`, `GAP-010` and `GAP-011`: the reference-device assumption and the dependency-verification work of `TASK-084`/`TASK-085`.
- `GAP-007`: error states not drawn in Figma.
- `CONF-80`, `CONF-81` and `CONF-84` (the last escalated to the owner).
- The owner's steps: merge in order, re-add the `ios` required context (`TASK-108`), tag `v0.1.0` and then `v0.2.0`, enable Dependabot alerts, and close the issues of the `Done` rows.

### B6 closure — 2026-10-04

**Block 6 is merged; the M1 tag and release are the owner's** (`DEC-107`):

| Phase | PR | Members | State |
| --- | --- | --- | --- |
| 6.1 Visual and accessibility evidence | #153 | `TASK-045`, `TASK-046` | Merged as `f77f333`; both `Done` |
| 6.2 Security and performance evidence | #156 | `TASK-048`, `TASK-049` | Merged as `e70def4`; `TASK-048` `Done`, `TASK-049` `In progress` (device budgets, `DEC-115`) |
| 6.3 M1 release | #158 | `TASK-050` | Merged as `644a2b3`; `REL3`/`REL4` (tag `v0.1.0`, publish) are the owner's |

**Delivered evidence:** 52 committed Roborazzi baselines across the component catalogue, the seven `ERROR_FLOW.md` states, the four feature surfaces and the shell (Episodes and the maximum-text captures included), each proved byte-identical in light and dark (`TEST-UI-012`, `TEST-UI-016`); the automated accessibility cases `TEST-A11Y-002`…`006` and the `DEC-119` emulator checklist; the four security policy checks, with `TEST-UNIT-027` covering every shipped persistence site and `TEST-UNIT-028` reading the release APK; `TEST-PERF-003` asserting zero network requests on a cache-hit render against the real transport; and `TEST-UNIT-019` proving the Android milestone assembles with iOS absent, run by the `app-artifacts` worker. The executed checks of each reviewed head are in `PROJECT_LOG.md` LOG-0125…LOG-0128.

**Two real product defects were fixed, and both were required by the criteria the phase verifies** (`DEC-114`): the tone-30 accent container placed its tone on the mean sRGB channel, so containers sat at L* 40–57 and On Surface text over a saturated accent measured **2.79:1** against the 4.5:1 floor — the tone is now solved in CIE L\* and the worst case is **7.33:1**; and both grids hard-coded two columns, so `REQ-UX-006`'s collapse at the largest text sizes never happened — a shared `MultiverseGrid` now decides the count.

**Carried as unmet, named rather than waived (`DEC-115`, `DEC-116`):** `M1-4` (budgets on the named reference device) cannot be evidenced while `A-PERF-1` is unassigned and `PERF-Q1` is undecided. `M1-5` is evidenced by the `DEC-119` emulator checklist. The release is prepared at `v0.1.0` and the tag/publish remain the owner's.

**Still open:** `GAP-029` (`:androidApp` never received the ktlint plugin, so the shell's Kotlin is unchecked by the formatter the codebase names as its owner); `CONF-84` (four feature modules declare the Material 3 alpha that ADR-0008 confines to `:core:designsystem`); `GAP-006`/`GAP-010` unchanged.

**B7 entry condition:** B6 Phase 6.3 merged and the M1 release published by the owner.

### B8 closure — 2026-10-05

**Block 8 is prepared in two pull requests:** Phases 8.1 and 8.2 are delivered inside PR #161 with B7 (`DEC-120`), and Phase 8.3, the M2 evidence and release preparation, is PR #178 (`DEC-109`).

**Delivered:**
- The iOS Discovery, Detail, Favorites and Settings screens over the shared `IC-018`/`IC-019`/`IC-020`/`IC-023` contracts, with the Episodes placeholder.
- The zoom transition with its Reduce Motion fallback, the image cache with both `REQ-FUNC-021` assertions, and the response-cache backend iOS never had.
- Ten committed iOS baselines (five component, five screen) in the `ios` job, and 104 iOS tests green.
- The M1 Android verification set re-run green on the same tree.
- M2 prepared at `v0.2.0` (`DEC-121`).

**Carried as unevidenced, named rather than claimed (`DEC-117`):**
- `M2-1`/`M2-2` need an **iOS 18** simulator or device. Xcode 27 offers no iOS 18 runtime, so neither the deployment-floor run nor the iOS 18 fallback pair can be executed here.
- `M2-4` needs a physical device.
- The on-device checklist claims (VoiceOver traversal, contrast and target measurements) remain unrecorded.

**Open:** `GAP-029`, `GAP-030`, `CONF-79`, `CONF-80`, `CONF-81`, `CONF-84`. `GAP-031` is resolved (PR #161).

**B9 entry condition:** B8 Phase 8.3 merged by the owner.

### PR #150 branch update — 2026-10-04

PR #145 is merged: GH CLI observed merge commit `22cea4cc3c720caf7302becec73af3b44c89efdf` on `main`. The owner explicitly requested rebasing PR #150 onto that base (`DEC-113`). The original phase 5.3 head `3e987fa997b447dbe336155b76b528e1dbfa8164` is retained locally as `chore/pr150-rebase-backup-3e987fa`. `git rebase --rebase-merges origin/main` preserved the original individual TDD commits and resolved one conflict in `DOCUMENTATION_AUDIT.md`: retain `main`'s verified `GAP-028` closure and phase 5.3's `CONF-79` record. The resulting production, test, build and workflow files match Git's independently computed merge tree. PR #150 inherits the modular CI and Linux stall fixes of `DEC-112`.

Local integration evidence is recorded in `PROJECT_LOG.md` LOG-0101. Publication uses an exact lease against the original remote head; the final PR-head `android` result must pass before human review and merge. This branch update does not resolve the pre-existing product-contract question `CONF-79`.

### PR #145 CI decomposition — 2026-10-04 (historical pre-merge evidence)

The owner authorised `TASK-110` / `DEC-112` on `feat/b5-phase-5-2`, covering `.github/workflows/pull-request.yml`, `.github/scripts/`, the workflow guard and its regressions in `build-logic/convention/`, and the affected documents. The workflow contains 17 independent worker checks and the required `android` result. Module scopes use explicit Android/JVM tasks; app tests and APK inspection are separate, and contract replay has its own invocation. Native CI stays suspended under `DEC-083`; settings and merge remain human-controlled.

Observed before this change: run `37191137085` timed out at 60 minutes during the local gate. The earlier `TASK-109` mitigation did not unblock D2. Observed regression evidence: the new modular guard fixture initially reported 3 failures out of 9 tests; after implementation those 9 passed, and one further regression covers step expressions and environment replacement. The final tooling run passed all 164 tests; the result-process suite passed 7 tests, including failure/cancellation/skip/missing-result cases. The full tooling run exposed one reproduction fixture that inserted a duplicate aggregate `if` key; its mutation now replaces the existing condition before adding the negative condition. All Linux worker scopes passed locally, including APK inspection, in 1 minute 21 seconds; the separate replay passed 18 contract cases. Exact commands and coverage comparison are recorded in `PROJECT_LOG.md` LOG-0098. Until the final PR-head `android` result is green, this change is in progress, not Done.

The first parallel run (`37196552961`, head `404e9a8`) passed 15 workers but exposed two real defects: `verifyRepositoryHygiene` deadlocked its Git batch pipes and `verifySdkLevels` snapshotted the entire installed SDK. Their new regressions both failed before the fix; all 166 tooling tests now pass, and the combined real hygiene/APK/SDK verification passed in one minute (`LOG-0099`). The jobs now have 10–15 minute ceilings. The corrected implementation head `6382e1c` passed all 18 checks in run `37198426989`: policies in 2m46s, APK artifacts in 4m27s, and the full run in 4m37s. GH CLI reported PR #145 `MERGEABLE` / `CLEAN` with every check `SUCCESS`. `TASK-110` is in review and `GAP-028` is resolved on that observed evidence (`LOG-0100`); the final documentation head must also pass its required CI before merge. No merge or settings change was performed.

## 1. Current state (2026-10-05)

`main` contains the Android product (B1–B6), and the stack of PRs #161 → #189 adds the iOS app and B9. Together they form a **complete, runnable product**: the documentation set, the Gradle/KMP build skeleton, the full shared core (`:core:domain`, `:core:data`, `:core:presentation`, `:core:designsystem`, `:core:ios`, `:core:testing`, the debug-only `:core:diagnostics`), the five feature modules and two native clients — the Android app (`:androidApp`, Compose, `minSdk` 26) and the iOS app (`iosApp/`, SwiftUI, iOS 18+ over one static framework from `:core:ios`). Every M0 policy and quality check is merged and blocking: `verifyRepositoryHygiene`, `verifyDependencyPolicy`, `verifyModuleBoundaries`, `verifyDependencyPins`, `verifyDocumentedGate`, `verifyDocumentedCompleteness`, `verifyWorkflowGate`, the version catalog and its rationale checks, ktlint, Android Lint and `buildHealth`. The both-runner pull-request gate (`TASK-024`/`TASK-025`/`TASK-028`, PR #52) runs the `android` job as the required context and the restored `ios` job.

Delivery model changed on 2026-10-01: `TASK-016` was the last task executed individually, and the remaining work is delivered in nine execution blocks (`B1`…`B9`, `DEC-063`) owned by `docs/BACKLOG.md` §2.6. **Block 1 is integrated and locally verified, not `Done` under D2**: it merged before the gate existed, so no B1 pull request had a status-check rollup (`GAP-002` resolved by `TASK-025`). `DEC-071` resolves that state model — a check suite becomes mandatory in the change that creates its harness — and the post-merge review's four corrections are merged: `TASK-087` (PR #42), `TASK-088` (PR #43), `TASK-089` (PR #44) and `TASK-090` (PR #45). The dependency-safe source reconciliation keeps `TASK-031` and `TASK-073` together in B2, places `TASK-020` before `TASK-012` and `TASK-075` in B5, and keeps `TASK-021` in B4; no task depends on a later block (`CONF-52`, resolved).


| Area | State | Notes |
| --- | --- | --- |
| Assignment | Present and frozen | `assessment.md`; partially truncated at l.4 and l.10 — its intent is recorded, not guessed (`CON-003`) |
| Documentation system | Merged | The set is integrated on `main` (TASK-032 PR #25, TASK-034 PR #32, TASK-090 PR #45); inventory and open gaps in `docs/DOCUMENTATION_AUDIT.md` §6 |
| Requirements | Written, with acceptance criteria | `docs/REQUIREMENTS.md` — target state |
| Decisions | Recorded | `DEC-001`…`DEC-151` in `docs/DECISION_BOARD.md`; rationale in `docs/adr/` |
| Remote contract | Documented and probed | `docs/API_SPECS.md`; live observations dated 2026-09-29 |
| Visual specification | Written | `docs/UI_SPEC.md`; Figma file access required, PNG exports not committed |
| Architecture | Documented as target | `docs/DESIGN.md`; module layout is DEC-052 (see §3) |
| Implementation | **Complete on both platforms** | Plan: `docs/TECHNICAL_PLAN.md`; work index: `docs/BACKLOG.md`. The shared core (B3), the design system and the Android shell (B4), the Android features and validation (B5–B6), are merged on `main`; the iOS app across B7–B8 (PRs #161, #178) and B9 are merged too, and so is the code-review remediation (`TASK-111`…`TASK-125`, PRs #192–#221). The Android app has four working destinations (`Discovery`, `CharacterDetail`, `Favorites`, `Settings`), the branded splash, the image pipeline and the offline/error states; `iosApp/` builds as a real Xcode target over the `:core:ios` framework, with 165 tests, last run after a clean build at `TASK-122`'s head (`LOG-0146`); no later task changed Swift. The Android app was launched on an emulator in PR #153's review and again in Phase 9.3 (`GAP-032`, §B9 closure) |
| Build, CI, tooling | Every check merged and blocking; the `android` job is the required context | Gradle wrapper 9.7.0, AGP 9.3.1, Kotlin 2.4.20 and the modules of ADR-0001 build (LOG-0026); the version catalog pins the full planned inventory and `verifyDependencyPolicy` enforces it in `check` (TASK-015, LOG-0032); `verifyRepositoryHygiene` (`TEST-UNIT-026`) runs in `check` (TASK-016, LOG-0036…LOG-0039); `verifyModuleBoundaries` enforces the module graph and `verifyDependencyPins` owns the single `VERSION` source (`0.1.0` on `main`, `0.2.0` from PR #178; TASK-017/TASK-018/TASK-088/TASK-089); ktlint, Android Lint and `buildHealth` run and block (TASK-029, PR #65). CI is merged and blocking: `.github/workflows/pull-request.yml` runs the `android` job, which is the required context of the `main protection` ruleset; PR #161 restores the `ios` job, which runs the framework, the app's tests and the Swift tools, and adding its context to the ruleset is the owner's step (TASK-108) |
| Screenshots | **Committed** | 52 Roborazzi baselines on Android across the component catalogue, the seven `ERROR_FLOW.md` states, the four feature surfaces and the shell, each proved byte-identical in light and dark (`TASK-045`); 17 committed iOS baselines — 5 design-system (glass, material, Reduce Transparency, the largest Dynamic Type size) and 12 screen baselines including the seven `ERROR_FLOW.md` §12 states (`TASK-059`, PR #161 and PR #182 reviews). Figma exports under `docs/figma/` |

### 1.1 Verified versus intended

**Verified (observed in this repository, or against a live system on 2026-09-29; technical checks re-run 2026-10-01):**

- The repository tree, its contents and its 14-commit history on `main` (all dated 2026-09-29).
- The live behaviour of the remote API recorded with that date in `docs/API_SPECS.md` §1.1: list totals and page size, `404` for a filtered empty result that is itself cacheable and immutable, immutable detail `404`, out-of-range page `404`, batch requests returning only existing resources, one identifier yielding an object and two an array, GraphQL null for a missing character, `info.next` as `Int`, the empty-filter GraphQL shape, and `400 GRAPHQL_VALIDATION_FAILED` for an unknown field.
- Toolchain versions and platform API availability checked on 2026-09-29 and recorded in `local://decision-brief.md` §4 (not committed to the repository).
- Documentation checks on this repository: link resolution, identifier uniqueness and ordering, and consistency review. See §7.
- The B1 technical checks on 2026-10-01: `./gradlew projects` (14 projects), `verifyModuleBoundaries` (clean, and red under every seeded violation of `TEST-UNIT-017`/`TEST-UNIT-043`), `verifyDependencyPins` (clean, `VERSION` `0.1.0`) and `lintDebug` (clean). The boundary and version seed matrices are recorded in `docs/TESTING.md` §3.3 and `PROJECT_LOG.md` LOG-0049…LOG-0051.
- The Gradle/KMP build (2026-09-30, branch `build/gradle-kmp-skeleton`, then merged): `./gradlew --version`, `projects`, `help --warning-mode=all`, `assemble`, `build`, `:androidApp:assembleDebug` (with no `iosApp/` and from a clean clone), the per-module dependency reports, the `tasks --all` target scan and the APK badging check. Commands and observed results: `PROJECT_LOG.md` LOG-0026. The dependency policy was verified on 2026-09-30 on branch `build/version-catalog-inventory`: `verifyDependencyPolicy` passes and runs in `check`. The repository-hygiene check was verified on 2026-09-30 on branch `build/repository-hygiene` and **corrected** review: every **positive** seed of the original S1–S14 matrix failed its owning rule (S13 and S14 are negative cases and must pass, and explicit `HYG-05` evidence is supplied by R12), the review-regression matrices R1–R15 and then T1–T14 closed the false negatives and fail-open paths two independent reviews found, and the corrected head passes with 0 findings and runs in `check` (`PROJECT_LOG.md` LOG-0036, LOG-0037 for the first two rounds and LOG-0038 for the current evidence). Two review rounds hardened the checks to P1–P8, R1–R7 and I1–I9, and the seeded-violation matrix S1–S53 failed the owning rule and passed once reverted (`PROJECT_LOG.md` LOG-0033, corrected by LOG-0034). The source-text limits that remain are `GAP-011`.

**Verified on the running product (PR #153's TalkBack run under `DEC-119`, then B9 Phase 9.3 on 2026-10-04; recorded by those who ran them):**

- **The Android app launches and its journey runs**: Discovery with 826 live characters; Detail with its live `/episode/{ids}` enrichment; the favourite marking a character and `Favorites` listing it; `am force-stop` + relaunch with the favourite surviving (DataStore); "Delete all" through its confirmation to the empty state; cached content offline inside the freshness window; `Portal link lost` with its own copy, then a working `Retry`. Observed with `adb` against `Pixel_9_Pro` (API 36); screenshots and the `uiautomator` element dumps are the evidence, recorded in `PROJECT_LOG.md` LOG-0118.
- **The suites**, observed on the reviewed PR #189 tree on 2026-10-05: the Gradle verification set of `LOG-0133` → 1165 tests, 0 failures across 193 report files; `xcodebuild test` on iPhone 17 / iOS 27.0 → 105 tests, 0 failures. `:androidApp:assembleDebug` builds; `iosApp/` builds as a real Xcode target.
- **The gates**: `verifyDocumentedCompleteness` (46 documents, 0 findings), `verifyDocumentedGate`, `verifyModuleBoundaries`, `verifyDependencyPolicy`, `verifyRepositoryHygiene`, `verifyNoLiveHosts`, `verifyWorkflowGate`, `buildHealth`, ktlint, Android Lint.
- **Snapshot baselines**: 52 committed on Android, 17 on iOS.

**Still not verified, named rather than claimed:**

- **The performance budgets on a named reference device** (`M1-4`, `M1-5`, `M2-4`): `A-PERF-1` names no device and none is available on this workstation (`DEC-115`, `DEC-116`, `DEC-117`). `TEST-PERF-003`'s cache-hit assertion runs in the suite; the *timings* do not.
- **The iOS 18 floor**: Xcode 27 offers no iOS 18 runtime (18.0 and 18.6 "not available for download", 2026-10-05), so the deployment-floor run and the "iOS 18 selects the fallback" pair are unevidenced (`DEC-117`). The fallback path **is** baselined through the `.multiverseGlassPath` seam, which proves the path renders but not that iOS 18 chooses it.
- **The iOS accessibility checklist on a device** (`TASK-061`, `DEC-117`): VoiceOver traversal, colour-picker contrast and target measurements need a device. The Android checklist (`TASK-046`) was executed on an emulator under `DEC-119`.
- **The dependency advisory register** (`TASK-067`): empty, because `SECURITY.md` §11.1 forbids an invented finding and no real finding exists.
- **The Figma source**: still needs project access; it returned HTTP 403 to an anonymous client on 2026-09-29, so the specification is reproduced from the design work rather than re-read from the source.

Every product command in §8 is now exercisable; the rows marked **executed** carry the date and the observed result.

## 1.2 Block state (2026-10-02)

**Block 2 is complete, its own remediation included.** All nine of its original rows are `Done` under D2 against the checks active at their stage: `TASK-024`,
`TASK-025` and `TASK-028` in PR #52; `TASK-031` in #62; `TASK-030` in #63; `TASK-093` in #64; `TASK-029` in #65; `TASK-073` in #66; `TASK-026` in #67
(merged as `8e58d77`); `TASK-027` in #70 (merged as `4059e1d`). `TASK-094` recorded the closure reconciliation in #69, and `TASK-082` closed with the
detekt outcome (`DEC-075`). An audit of the merged block then reproduced ten defects; all ten (`TASK-098`…`TASK-107`, `B2-R01`…`B2-R10`) are merged
between `4953bde` (#88) and `c57ae85` (#104).

The repository settings that were human-only (`DEC-049`) are **applied**: the `main protection` ruleset carries no bypass actor, and its required contexts are managed with the iOS suspension — after its first B2 packet it required the `android` and `ios` checks, and from B3 Phase 3.1 it requires `android` only, so a head without that check reads `BLOCKED`. The configuration, the observed enforcement and the applied B3 change are in `CONTRIBUTING.md` §5.3 (`LOG-0076`, `LOG-0080`).

`TASK-096` is `Done` (PR #74, merged as `72332a5`): it wires `contractLiveTest` (which holds `TEST-CONTRACT-006`)
into the scheduled job and makes `WorkflowGateGuard` recognise every registered live entry point, proved red →
green with a seed that runs the real task name from a pull-request workflow. `TASK-097`
(`docs/b2-final-reconciliation`) closes the residue the block audit found: six stale current-state claims and
`TESTING.md` §14.2's per-row activation owners. The scheduled job was observed by `workflow_dispatch` on merged
`main` (run `36983953859`, success, both steps green); the cron trigger first fires Monday 06:00 UTC.

## 1.3 B3 Phase 3.1 and the temporary iOS settings packet (2026-10-02)

**Phase 3.1 (`TASK-036`, issue #108; `TASK-037`, issue #109) is implemented on `feat/b3-phase-3-1` and in review; it is not `Done` until the owner merges it** (`DEC-082`). What it delivers, with its evidence in `PROJECT_LOG.md` `LOG-0078`/`LOG-0079` and the phase pull request:

- `:core:domain`: the `IC-001`…`IC-004`, `IC-010` declarations, the `IC-007` repository with its defaulted `PageLoadPolicy` (`DEC-086`), the `IC-008`/`IC-021` interfaces, and `TEST-UNIT-052`.
- `:core:data`: the REST adapter of `IC-011` (now returning `DataResult`, `DEC-090`), the shared client defaults, the OkHttp and Darwin factories with no engine response cache, `TEST-CONTRACT-001`/`003`, `TEST-UNIT-001` and `TEST-UNIT-054`.
- `:core:testing`: `FakeCatalogue`, `FakeCharacterRepository` (`IC-007`) and `FakeRemoteSource` (`IC-011`), with `TEST-UNIT-053`.
- Build and CI: the test-source-set reading of the boundary rules (`DEC-089`), the API/IMPL boundary (`DEC-091`, ADR-0014: no feature depends on `:core:data`), one contract replay entry point per target (`DEC-090`), and the temporary iOS suspension with its restoration tripwire (`DEC-083`).
- Decisions `DEC-082`…`DEC-091`, ADR-0013 and ADR-0014; `CONF-54` and `CONF-63`…`CONF-70` resolved; `GAP-024`/`GAP-025` registered.

**The settings step that preceded the merge is applied (owner action on 2026-10-02, outside the repository tree).** The pull request's workflow no longer reports `ios`, and the `main protection` ruleset required it, so GitHub would have waited for a check that never runs. The owner applied the packet below; observed before the change (`gh api repos/davidru85/RickAndMorty/rulesets/24241444`): rules `deletion`, `non_fast_forward`, `pull_request` (merge only, 0 approvals, thread resolution required) and `required_status_checks` with `android` and `ios`, both on `integration_id 15368`; no bypass actor. The applied change removes **only** the `ios` context:

```bash
gh api -X PUT repos/davidru85/RickAndMorty/rulesets/24241444 --input - <<'JSON'
{
  "name": "main protection",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": { "ref_name": { "include": ["refs/heads/main"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "pull_request", "parameters": {
        "allowed_merge_methods": ["merge"], "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false, "require_extra_approval_for_unattributed_changes": true,
        "require_last_push_approval": false, "required_approving_review_count": 0,
        "required_review_thread_resolution": true, "required_reviewers": [] } },
    { "type": "required_status_checks", "parameters": {
        "do_not_enforce_on_create": false, "strict_required_status_checks_policy": false,
        "required_status_checks": [ { "context": "android", "integration_id": 15368 } ] } }
  ]
}
JSON
gh api repos/davidru85/RickAndMorty/rulesets/24241444 --jq '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks'
```

**Applied and observed on 2026-10-02.** The second command prints exactly one context, `android`, bound to `integration_id 15368`; every other rule, the empty bypass list and merge-only delivery are unchanged. Pull request #110 therefore awaits `android` only — the `ios` context is no longer reported as an outstanding requirement. Its run on `13e66a45` (run `37050825465`) concluded green, and so did the re-run of that head the owner requested (attempt 2); each later documentation-only head is gated by its own `android` run, so the pull request's check rollup is the current evidence. `TASK-051` restores `ios` with the same request and `{ "context": "ios", "integration_id": 15368 }` added back, in the change that introduces the app target; `TASK-108` owns that settings half and its read-back.

## 1.4 B3 Phase 3.2 (2026-10-02)

**Phase 3.1 is integrated** (PR #110, merged as `2ed9c43`). **Phase 3.2 (`TASK-038`, issue #111; `TASK-039`, issue #112; `TASK-047`, issue #113) is integrated** (PR #114, merged as `b38766a`, `android` green on its head); its three rows are `Done`. Each behaviour landed as an observed red commit followed by its green one; the evidence is in `PROJECT_LOG.md` `LOG-0081` and the phase pull request.

- **`TASK-038`:** `RemoteCharacterRepository` composes `IC-007` over the REST adapter with one bounded retry policy (three attempts, ≈500/1,500 ms with jitter in [0.8, 1.2], one `429` retry only after advice of at most 60 s; `DEC-084`) and request coalescing whose scope ownership is fixed (`IC-007`); the shared client rejects a foreign host, cleartext, a sub-domain and a non-default port before transport, rejects every `3xx`, and OkHttp retries nothing underneath. `TEST-UNIT-021`, `022`, `025`, `055`.
- **`TASK-039`:** `IC-014` is declared in `:core:domain` with `retry()` (`DEC-092`) and implemented by `RepositoryCharacterPager` in `:core:data`; refresh uses `ForceNetwork`, proved against the freshness-aware `FakeCharacterRepository`. `TEST-UNIT-016` and the pager half of `TEST-UNIT-007`.
- **`TASK-047`:** `IC-024` is declared in `:core:domain` and implemented by `ValidatingAppLogger` in `:core:data`; the adapter, retry policy, single flight and pager emit `LOG-001`…`004`, `010`…`014` and `022`; `:core:diagnostics` exists with `DiagnosticsRecorder`, and `R11`/`R18` make its release exclusion a property of the graph (`DEC-094`); `verifyNoAnalytics` joins `verifyDependencyPolicy`. `TEST-UNIT-029`, `032`, `033`, `034`.
- **Recorded:** `DEC-094`; `CONF-72`, `CONF-73` resolved; `GAP-026` (who authors `coreModule`) registered; since 2026-10-03 it is part of `TASK-044`'s acceptance.

**Obligations later work inherits:** `TASK-020` makes the production cache honour `ForceNetwork` and gains the cache events `LOG-005`…`009` (the `TASK-020` row); `TASK-044` builds the Logcat sink, picks `ValidatingAppLogger.forRelease`/`forDebug` from the variant source sets, fans the debug sink out to `DiagnosticsRecorder`, declares `:core:diagnostics` through `debugImplementation` only, renders the panel — reading whether an append is in flight from the pager state, which no event carries — and, if the owner accepts `GAP-026`'s recommendation, authors `coreModule`; `TASK-051` does the same for iOS; `TASK-078` extends `TEST-UNIT-034` to the iOS framework's graph. **Phase 3.3** (`TASK-040`, `TASK-041`) starts from merged `main` after this merge; `TESTING.md` §6.1 still describes the favourites toggle as idempotent, which `TASK-040` corrects.

## 1.5 B3 Phase 3.3 (2026-10-03) — integrated

**Phase 3.3 (`TASK-040`, issue #115; `TASK-041`, issue #116) is integrated** (PR #117, merged as `f1fc88f` at 2026-10-03T08:50:48Z, `android` green on its head; both issues closed), so **B3 is complete**. Each behaviour landed as an observed red commit followed by its green one; the evidence is in `PROJECT_LOG.md` `LOG-0082` and the phase pull request.

- **`TASK-040`:** `IC-013` in `:core:data` with its two real stores — Preferences DataStore on Android, `UserDefaults` on Apple — measured by one `TEST-INT-003` suite on both, plus each platform's `TEST-INT-004`; `LocalFavoritesRepository` implements `IC-008` (serialised toggles, one flip per call, a failed write logged as `LOG-019` and not thrown, a written toggle logged as `LOG-018` without the id); `ObserveFavoriteIds` lives in `:core:domain`; `FakeFavoritesStore` joins `:core:testing`. `SECURITY.md` §3 names the persisted key on each platform.
- **`TASK-041`:** `:core:presentation` holds `LoadState`, `CharacterCardUi`, `DisplayText`, `CopyKeys` and `DefaultPresentationFormatters` (`IC-015`…`IC-017`, `DEC-095`); its compiled public surface is checked for platform, HTTP and DTO types; and the copy-parity verifier (`TEST-UNIT-036`) is proved on controlled Android- and Apple-format inputs, including a deliberate divergence.
- **Recorded:** `DEC-095`; `CONF-74`…`CONF-77` resolved; `GAP-027` (the rate-limit countdown formatter) registered. On the owner's decision of 2026-10-03 both follow-ups are backlog work: `GAP-026` in `TASK-044`'s acceptance and `GAP-027` in `TASK-022`'s. The exclusion register lost the `:core:presentation` row, which `TASK-041` consumed, and its feature rows that named `TASK-039`/`TASK-041` now name the feature tasks that consume them.
- **Scope note:** the root build's dependency-analysis block gained one bundle for `androidx.datastore`, so `:core:data` declares the artifact ADR-0007 names; the blocking gate required it (brief §5.1).

**Obligations later work inherits:** `TASK-013` and `TASK-060` pass the real resource folders and `CopyKeys.all` to `CopyParity.verify`, and register the feature keys they add in `CopyKeys`; `TASK-044`/`TASK-051` build the favourites store over the app's DataStore file or `NSUserDefaults.standardUserDefaults` and `ObserveFavoriteIds` over its repository (`DESIGN.md` §5); the per-failure error strings stay unenumerated until `UI_SPEC.md` §8 lists them (`ERROR_FLOW.md` §4.1). **B3 closed with the merge of this phase (PR #117, `f1fc88f`).**



**Phase 4.1 (`TASK-042`, issue #120; `TASK-013`, issue #121; `TASK-035`, issue #122) is implemented on `feat/b4-phase-4-1` and in review; it is not `Done` until the owner merges it** (`DEC-096`). What it delivers:

- **`TASK-042`:** `docs/figma/tokens.json`, the read-only export of the three collections (86 variables), and the Kotlin token objects in `:core:designsystem` that `TEST-UNIT-035` compares in both directions; `TEST-A11Y-002` records the measured contrast of the specified B4 surface pairs. Closes `GAP-003` and `CONF-50`'s blocker.
- **`TASK-013`:** one Android copy set in `:core:designsystem` (`values{,-es}/strings.xml`) behind a compile-checked name→`R.string` resolver, `CopyKeys` carrying the fourteen B4 keys, and `TEST-UNIT-036`/`TEST-UNIT-008` proving the shipped folder complete in both locales and locale-following (Robolectric 4.17, needing `--add-opens java.base/jdk.internal.access` and `--enable-native-access=ALL-UNNAMED` on the pinned daemon JVM 25).
- **`TASK-035`:** the 32 PNG exports of every `UI_SPEC.md` §1.1/§1.2 frame, dimension-verified at 2×, closing `CON-005`/`GAP-004` and partially `GAP-008`.
- **C5 (`DEC-106`):** the boundary rules admit the approved test libraries and shell test modules from a test source set, with red→green regression cases; every production restriction is unchanged.
- **The B4 packet:** `DEC-097`…`DEC-105` and ADR-0015 record the owner's nine answers; `docs/DESIGN.md`, `UI_SPEC.md`, `ERROR_FLOW.md` and `CONTRACTS.md` are reconciled in the same change.

**Obligations the later phases inherit:** `TASK-043` adds the rest of the Compose surface and the compiler plugin to `:core:designsystem` with the components; `TASK-044` keeps the copy resolver name-keyed, builds the shell over `:core:designsystem`, and owns the `coreModule`; `TASK-060` wires the Apple resource folder into `CopyParity.verify`; `TASK-005` adds the tone-30 accent pairs to `TEST-A11Y-002` and lands the vendored colour subset. The Spanish copy shipped in this phase is drafted for owner review (listed in the phase pull request).

## 1.7 B4 Phase 4.2 (2026-10-03) — in review

**Phase 4.2 (`TASK-043`, issue #124; `TASK-044`, issue #125) is implemented on `feat/b4-phase-4-2` and in review; it is not `Done` until the owner merges it** (`DEC-096`). What it delivers:

- **`TASK-043`:** `MultiverseTheme` with the single appearance and the type scale, the component vocabulary of `UI_SPEC.md` §4.1 (status badge, character card and skeleton, Cookie-9 illustration and empty state, stat tiles, info-list item, navigation bar) and the Compose-only `ImageSeam` of `DEC-097`. `TEST-UI-004` drives the real portrait renderer through a recording seam; the module's suite stands at 25 tests.
- **`TASK-044`:** the composition root — `MultiverseApplication` starting `coreModule` plus the shell's platform modules, `MainActivity` with the system-splash handoff and edge-to-edge, the app-wide `NavHost` over the five typed destinations, the adaptive launcher icon, the `INTERNET` permission and the bundled Material Symbols glyphs (`DEC-103`). `coreModule` itself is authored in `:core:data` with `TEST-UNIT-056` (both Android host and Apple simulator).
- **New blocking checks:** `verifyReleaseArtifact` (`TEST-UNIT-033`, the real release APK carries neither the diagnostic recorder nor the panel host, and the debug APK carries both) and `verifySdkLevels` (`TEST-UNIT-018`, `aapt2 dump badging`: minSdk 26, targetSdk/compileSdk 37). Both are wired into `check`.
- **Device evidence, local:** the debug APK installs and launches on API 37 and on API 26; the panel host opens in the debug build and renders every value it cannot know as unavailable, naming its deliverer.

**Obligations the later phases inherit:** `TASK-005` implements the seam over Coil and adds the accent policy (`DEC-097`); `TASK-008` replaces the four section placeholders with the Episodes and Favorites content (`DEC-099`); the feature Koin modules arrive with the B5 task that gives each a binding; `TASK-051` supplies the iOS sink and panel.

## 1.8 B4 Phase 4.3 (2026-10-03) — in review

**Phase 4.3 (`TASK-005`, issue #127; `TASK-021`, issue #128; `TASK-007`, issue #129; `TASK-008`, issue #130) is implemented on `feat/b4-phase-4-3` and in review; it is not `Done` until the owner merges it** (`DEC-096`). What it delivers:

- **`TASK-005`/`TASK-021`:** the portrait transport (Coil over the same allow-listed Ktor client, keyed by the URL verbatim, with a memory cache and the documented 64 MB disk budget) and the accent policy — the vendored Apache-2.0 quantize→score→palette chain with the chroma clamp and the tone-30 container, computed on an injected dispatcher with a one-per-URL LRU and the Portal Green fallback. Closes `TASK-083` and `CONF-50`.
- **`TASK-007`:** the readiness gate (1.2 s floor, 3 s ceiling, completion on the first-page outcome) and the branded splash, exposed as an indeterminate progress indicator labelled "Loading characters" with the Reduce Motion pulse path.
- **`TASK-008`:** the four reachable destinations and both placeholder screens, with `DEC-104`'s `S3` exemption for a stateless UI placeholder and the consumed exclusion rows removed.
- **Device evidence, local:** on API 37 the branded splash renders with its wordmark and tagline, each destination's tab is marked selected, and both placeholders render their glyphs, with no crash record.

**Defects the phase's own checks and device run found and fixed:** the memo returned a cache hit without refreshing its order; the palette's HSL conversion compressed the chroma so the clamp measured 14.5 % instead of the requested 24 %; the splash gate raced a monotonic clock against the coroutine clock; `MainActivity` never bound the gate, so the branded splash never appeared; the placeholder illustration rendered as a white square; and the Compose compiler ran on the Apple compilations of a KMP feature module, which has no Compose runtime.

**Obligations the later blocks inherit:** B5 replaces the two placeholders (`TASK-006`, `TASK-074`) and the Characters and Settings section titles (`TASK-001`, `TASK-074`); `TASK-009` adds the shared-element transition; the tone-30 accent pairs are already in `TEST-A11Y-002` and the placement of the portraits on the grid joins `TASK-001`/`TASK-002` (`DEC-099`).

## 1.11 B5 Phase 5.3 — in review in PR #150
**Phase 5.3 (`TASK-006`, #146; `TASK-074`, #147; `TASK-075`, #148; `TASK-076`, #149) is implemented and locally verified on `feat/b5-phase-5-3`, rebased onto merged `main` after PR #145 on 2026-10-04** (`DEC-106`, `DEC-113`). What it delivers:
- **`TASK-006` — Favorites:** `IC-020`'s declarations, the shared reducer and state holder, and the section that renders the same cards as Discovery. It resolves each favourite id through `IC-007`'s cached single-id read with bounded concurrency, which is the reading `DESIGN.md` §4.5 gives and `CONF-79` records against `IC-020`'s "no remote request" sentence.
- **`TASK-074` — Settings:** `IC-021` over `IC-022` on both platforms, with a DataStore store on Android, a `UserDefaults` store on Apple, one contract proved once, and the `§6.5` screen with its three settings and its confirmation dialog.
- **`TASK-075` — the protocol switch:** the third `IC-011` adapter posts the checked-in documents to `/graphql` with every user input as a variable, and the repository resolves its adapter **per request** from `IC-021`, so the protocol joins the request identity and the cache key; the pager observes a change as a new generation. No Apollo artifact resolves.
- **`TASK-076` — Delete favorites:** the confirmation with Cancel and a destructive Delete, one clear that empties Favorites and Detail without a refresh, disabled when there are no favourites.
- **The shell:** `startKoin` loads the feature modules, `MainActivity` builds the one `ImageSeam` and the one `DetailHandoff`, and the Favorites and Settings routes replace their placeholders.
- **Observed gate:** `./gradlew check buildHealth verifyDependencyPolicy verifyDependencyInventory verifyDependencyPins` — BUILD SUCCESSFUL. `:core:data` 161 JVM / 182 Android-host, `:feature:favorites` 27, `:feature:settings` 18, `:androidApp` 26, all green.
**B5 is complete when this phase merges:** all fifteen of its members are then `Done`, and B6 (Android validation and release) is the next block.
## 1.10 B5 Phase 5.2 (2026-10-03) — implemented on `feat/b5-phase-5-2`, awaiting its pull request
**Phase 5.2 (`TASK-001`, #138; `TASK-002`, #139; `TASK-003`, #140; `TASK-004`, #141; `TASK-023`, #142; `TASK-009`, #143; `TASK-010`, #144) is implemented and locally verified on `feat/b5-phase-5-2`; it is not `Done` until the owner merges the phase pull request** (`DEC-106`). What it delivers:
- **`TASK-001`/`TASK-003`/`TASK-004`/`TASK-010` — Discovery:** `IC-018`'s state and intents, a shared reducer that owns the 300 ms debounce, `distinctUntilChanged`, the cancellation of superseded work and the page-1 reset, and the `§6.2` screen: the search field, the count line formatted once through `IC-017.charactersCount`, four filter chips, a two-column staggered grid of the design system's cards, six skeletons while loading, the paging indicator, the stale banner and both the error surface and the designed empty-results state.
- **`TASK-002`/`TASK-023` — Detail:** `IC-019`'s declarations, the shared reducer and state holder, the hero portrait through the `DEC-097` seam, the three connected stat tiles, the info list with the enrichment-aware rows, the favourite toggle reconciled with `ObserveFavoriteIds`, and the inline error that keeps the header.
- **`TASK-009` — the transition:** `PortraitMotion` fixes the 450 ms Emphasized Decelerate container transform, the shared-element key derived from the canonical id, and the cross-fade Reduce Motion substitutes (`AC-REQ-FUNC-009-2` in the form a test can decide). The keyed modifier itself stays `:core:designsystem`'s.
- **`IC-025`:** the shared card-to-detail hand-off `DESIGN.md` §4.2 required and no contract declared. It lives in `:core:presentation`, the shell owns the single instance, Discovery publishes and Detail consumes.
- **Observed gate:** `./gradlew check buildHealth verifyDependencyPolicy verifyDependencyInventory verifyDependencyPins` — BUILD SUCCESSFUL. `:core:data` 137 JVM / 158 Android-host, `:feature:discovery` 13, `:feature:character-detail` 24, `:androidApp` 26, all green. Red was observed before each green.
**Obligations phase 5.3 inherits:** its shell wiring must compose the two new routes rather than the placeholders; the card-to-detail hand-off instance it creates is the one both features share; and `TASK-075`'s per-request protocol selection is the identity change the Discovery pager already observes.
## 1.9 B5 Phase 5.1 (2026-10-03) — implemented on `feat/b5-phase-5-1`, awaiting its pull request
**Phase 5.1 (`TASK-020`, issue #133; `TASK-012`, issue #134; `TASK-022`, issue #135; `TASK-011`, issue #136) is implemented and locally verified on `feat/b5-phase-5-1`; it is not `Done` until the owner merges the phase pull request** (`DEC-106`). What it delivers:
- **`TASK-020`:** `IC-012` is implemented as the application-level response cache in `:core:data` — `CacheKeyBuilder` (the complete normalized request identity), `CachePolicy` (24 h fresh / 7 d stale-while-revalidate / 30 d offline fallback, injectable), `ResponseCache` (the read policy of `API_SPECS.md` §7.3, the never-cache write guard, `isStale` and `DataResult.source`), the Android `FileCacheStorage` under the app's private cache directory, the `FakeCacheStorage` and `NoCacheStorage` doubles (`DEC-072`), and the `LOG-005`…`LOG-009` emitters. The shell supplies the store through `CoreGraphInputs.cacheStorage`. `ForceNetwork` bypasses every band, so `IC-014.refresh()` is proved against the production cache.
- **`TASK-022`:** `IC-017` maps every `ApiFailure` to its own copy key, states the recovery table's automatic-retry answer per class, and closes `GAP-027` with `rateLimitCountdown`: the countdown of `error_message_rate_limited` is an argument of the key rather than interpolated text, so both platforms substitute the same number from their own resource file.
- **`TASK-012` / `TASK-011`:** their acceptance is evidence over the production cache — a refresh and a retry each reach the network despite a fresh entry, a failed refresh and a failed append keep their items, and a cancellation reaches no state field. No production code was needed for either.
- **Observed gate:** `./gradlew check buildHealth verifyDocumentedGate` — BUILD SUCCESSFUL. `:core:data` 130 JVM + 133 Android-host tests, `:core:presentation` 25 host tests, all green. Red was observed before each green (9 cases for the cache policy, 4 for the failure map).
- **Defects the phase's own checks found and fixed:** the offline fallback bypassed `ResponseCache.serveFallback`, so `LOG-007` was never emitted; the new cache suite named the live API host in a fixture URL, which `verifyNoLiveHosts` rejected; a retry case's expected item count was wrong, and the append-versus-page-1 distinction it was meant to assert is now pinned explicitly.
**Obligations phase 5.2 inherits:** the discovery and detail screens consume `IC-012` through `CharacterRepository` and must render `isStale` and `DataResult.source`; `TASK-001` and `TASK-002` own the presentation state holders that map the failure chain to `IC-018`/`IC-019`; `TASK-010` owns the designed empty-results state the filtered `404` reaches.

**Not verified:** the phase pull request's CI run (not opened yet); the iOS simulator run of the touched KMP modules, which `DEC-083` suspends in CI and this session did not execute.

## 1.12 PR #145's earlier gate mitigation (2026-10-04) — superseded by DEC-112
**Prior mitigation, superseded by `DEC-112`: `GAP-028`/`DEC-111` (`TASK-109`) was implemented on PR #145's head** (`ci/pr145-gate-fit` branched from `1191b33d`) and **not `Done` under D2 until the run it triggers is observed green**. What that attempt changed:
- **`.github/workflows/pull-request.yml`:** the `android` job runs `./gradlew check -x iosSimulatorArm64Test`, and its `timeout-minutes` rises 45 → 60. The exclusion removes no check that could run: KMP **disables** the suite on Linux and the dependency chain it carried was the whole Kotlin/Native download plus the Apple compiles.
- **`WorkflowGateGuard`:** a new `HOST_DISABLED_EXCLUSIONS` allow-list (the `android` job may exclude `iosSimulatorArm64Test`, the `ios` job may exclude nothing), enforced per job and per step, so the fix cannot be widened into a general `-x`; the existing execution filter now strips the sanctioned exclusion before deciding a step still executes its other commands.
- **Observed locally:** red first — 3 guard-test failures against the unfixed guard; green after — the 40-test `WorkflowGateGuardTest` class and `:build-logic:convention:check ktlintCheck` all pass. `check --dry-run`: 1158 → 982 tasks, 30 → 0 Kotlin/Native tasks, with every Linux-executable step unchanged.
**Obligation this change inherits:** its own pull-request run must be observed, and `GAP-028` is closed only on that evidence (`LOG-0097`).

## 1.13 PR #153 sequential review (2026-10-04)

TASK-045/TASK-046 now carry actual maximum-text regressions, complete component catalogues, Episodes coverage, a real composition-root startup test and reduced-motion integration tests. The review fixed the concrete image-type injection crash, unused NavHost policy, frozen scale-zero pulse, clipped stats/empty actions, system-bar overlap and split navigation labels. The owner accepted emulator checklist execution through DEC-119; [the evidence](evidence/pr153/README.md) retains screenshots, TalkBack focus records and measured contrast. The source fixes are new commits; the original commits are preserved.

The owner merged PR #153 as `f77f333` on 2026-10-04; TASK-045/TASK-046 are Done with the reviewed head's successful CI and retained checklist. PR #156 follows against that merged main (§1.14), then PR #158 against the merged #156. The PR description names the executed checks and final-head CI result. This review does not declare M1 released or replace performance-device evidence.


## 1.14 PR #156 conflict resolution after Phase 6.1 (2026-10-04)

GH CLI confirms PR #153 was merged by the owner as `f77f333349599a0d1698ffac8306991545ed90fb`. The owner explicitly requested resolving PR #156's conflicts next. The branch update merges that main commit into the existing Phase 6.2 head `197a20014b8ab3621297aaa4b309d23a25f8da5f`, preserving both histories. `CONF-83` records the two documentation resolutions: keep both log entries, the zero-network test activation and the corrected 96-pair contrast coverage. Product, test, build and workflow files combine without content conflicts.

The branch-update verification commands and observed results are recorded in `PROJECT_LOG.md` LOG-0126 and the PR description. This compatibility update does not measure the remaining performance budgets: `DEC-115` and TASK-049 retain that limitation. Review and merge PR #156 before starting PR #158. No emulator or simulator is needed for this conflict-resolution work.

## 1.15 PR #156 sequential review (2026-10-04)

PR #156 was reviewed against `main` containing the PR #153 merge. Two security checks were narrower than their criteria and are now fixed with red-then-green commits: `TEST-UNIT-027` also searches every shipped source for a persistence site outside the inventoried stores, and `TEST-UNIT-028` gains `:androidApp:verifyShippedPermissions`, which reads the release APK's merged permission table and is pinned in the `app-artifacts` worker. A stray `iosApp/` Xcode workspace file was removed, and the README baseline count, the `PERFORMANCE.md` result register and `TESTING.md` §17 now match the code. `PROJECT_LOG.md` LOG-0127 has the commits and the observed verification.

`TASK-048` and `TASK-049` stay In review until the owner merges. `TASK-049`'s device budgets remain unmeasured under `DEC-115`: the owner must name the reference device (A-PERF-1) and decide the harness module (PERF-Q1) before `M1-4` can be evidenced. Next is PR #158, reviewed against `main` containing the #156 merge. **When PR #161 merges `main`, it must keep `iosApp/MultiverseExplorer.xcodeproj/project.xcworkspace/contents.xcworkspacedata`:** Git drops it silently because that branch inherited it from `197a200` without modifying it.

## 1.16 PR #158 sequential review (2026-10-04)

The owner merged PR #156 as `e70def4`; PR #158 then conflicted in `HANDOFF.md` and `PROJECT_LOG.md` only, and a merge commit resolves both, keeping every log entry. The review found that `TEST-UNIT-019` had never run in CI: since `DEC-112` no worker runs `:androidApp:check`. The `app-artifacts` worker now runs it, `ModularWorkflowGate` pins it, and its rule moved into a tested `MilestoneIndependencePolicy` (red-then-green commits). Four statements went stale or were wrong, and are now corrected: `DEC-116`, `LOG-0104` and this file's B6 closure said `M1-5` was unmet, although `DEC-119` evidences it; `README.md` §11 said no module declares the Material 3 alpha, but five do (`CONF-84`, escalated); the `TASK-050` row was still `Proposed`; and no release-note draft existed, so the PR description now carries one, generated by the command in `PROJECT_LOG.md` LOG-0128.

Owner actions after merging PR #158: tag `v0.1.0` on the merge commit (`REL3`), publish the GitHub Release with the release APK and the note generated by LOG-0128's command for that commit (`REL4`, `REL5`), close issues #154 and #157, and decide `CONF-84`, `A-PERF-1` and `PERF-Q1`. Next is PR #161, reviewed against `main` containing the #158 merge; it must keep its `iosApp/…/contents.xcworkspacedata` (§1.15).

## 1.17 PR #161 review: B7 Phases 7.1–7.3 and B8 Phases 8.1–8.2 combined (2026-10-05)

A back-merge of `feat/b8-phase-8-2` into the 7.1 branch (`7d7edaf`) left PR #161 carrying five phases and PRs #164, #166, #170 and #174 tree-identical to it. The owner chose to review it as one delivery (`DEC-120`). The review merged `main` (after PR #158), kept the Xcode workspace file the merge would have dropped, and fixed the defects `PROJECT_LOG.md` LOG-0129 lists, each with a failing case first where a case is possible:
- Discovery never loaded, and the grid then kept its skeleton cells (`GAP-031`).
- A tapped card never opened its detail, and a favourite did nothing.
- The detail hero covered the whole screen.
- Cards, the filter control and the hero buttons clipped at the largest text size.
- Device and Release builds linked a simulator object.
- The holders re-rendered every frame and leaked their Kotlin scopes.
- The `ios` job never built the app or ran its tests and baselines.

The `ios` job now runs `xcodebuild test` on iPhone 17 / iOS 27.0, the only iPhone runtime the runner provides. The iOS baselines are re-recorded there with a perceptual tolerance; an off-screen render was blank for some surfaces and a window render of Liquid Glass is not deterministic, so window-rendered baselines use the material path (`TESTING.md` §8.3).

Owner actions after merging PR #161:
- Add the `ios` context to the `main protection` ruleset (`TASK-108`, `DEC-049`).
- Close PRs #164, #166, #170 and #174, which then carry no change of their own (`DEC-120`).

Next is PR #178 (B8 Phase 8.3), prepared on PR #161's final head so it merges cleanly once #161 is merged.

## 1.18 PR #178 review: B8 Phase 8.3, the M2 evidence and release preparation (2026-10-05)

PR #178 is prepared on PR #161's reviewed head, so it merges cleanly once #161 is merged. Phase 8.3 had no code of its own; the merge commit takes #161's tree and re-applies the phase's four documents. The review:
- Corrected its stale facts: 104 iOS tests, ten baselines, and `GAP-031` resolved, so `M2-7` no longer carries that exception.
- Recorded why `M2-1`/`M2-2` cannot be evidenced. With the owner's agreement, the iOS 18 runtime download was attempted; Xcode 27 does not offer one.
- Added `tools/ios-version.sh --check` to the `ios` job, red first, so the iOS version cannot drift from `VERSION`.
- Prepared M2 at `v0.2.0` (`DEC-121`); the PR description carries the draft note for the range after `v0.1.0`.

Owner actions after merging PR #178:
- Tag `v0.2.0` on the merge commit and publish the GitHub Release with the APK and the regenerated note.
- Decide whether M2 is published with `M2-1`/`M2-2`/`M2-4` named as unevidenced (`DEC-117`).

Next is PR #182 (B9 Phase 9.1), prepared on PR #178's head.

## 1.19 PR #182 review: B9 Phase 9.1, hardening and advisory review (2026-10-05)

PR #182 is prepared on PR #178's head. Phase 9.1 had no code of its own. The review made its three tasks evidence-based:
- `TASK-064` compared iOS "render something" cases rather than baselines. It now has seven committed iOS state baselines for `ERROR_FLOW.md` §12, matching the Android set.
- `TASK-067` claimed a scan configuration that did not exist. It now has a recorded advisory review (270 pinned packages, 0 advisories, OSV, 2026-10-05) and `.github/dependabot.yml` for security updates.
- `TASK-065` states its own reason (`DEC-115`, `DEC-117`) instead of a copied one.

Owner actions after merging PR #182:
- Enable Dependabot alerts and security updates in the repository settings; until then the configuration raises nothing (`SECURITY.md` §9.3).
- Name the reference devices that `TASK-065` needs.

Next is PR #186 (B9 Phase 9.2), prepared on PR #182's head.

## 1.20 PR #186 review: B9 Phase 9.2, the completeness gate and the release note (2026-10-05)

PR #186 is prepared on PR #182's head. The review found that both of the phase's deliverables checked less than they claimed (`LOG-0132`):
- `verifyDocumentedCompleteness` (`TASK-068`) had no tests, and its `DOC6` half had never run. It now has `TEST-UNIT-063` and decides `DOC1`, `DOC2`, `DOC4`, `DOC6` and `DOC8` over table rows and every kind of link. Its first honest run repaired seven audit rows.
- `tools/release-notes.sh` (`TASK-066`) dropped breaking changes and ignored the previous tag. It now has `TEST-UNIT-064` and renders the `CONTRIBUTING.md` §3.6 note. Running `./gradlew releaseNotes` before tagging `v0.2.0` gives the range since `v0.1.0`, once that tag exists.
- `TASK-069` is verified in full: all 59 non-deferred requirements have a test row and a task.
- `TEST-UNIT-060` names only the protocol-switch case again. The 24 merged B4/B5 rows and `TASK-109`/`TASK-110` are `Done`.

Owner actions after merging PR #186:
- Close the B4/B5 issues the backlog now records as `Done`: #120–#122, #124, #125, #127–#130, #133–#136, #138–#144 and #146–#149.

Next is PR #189 (B9 Phase 9.3), prepared on PR #186's head.

## 1.21 PR #189 review: B9 Phase 9.3, the handover and the deferred-register reconciliation (2026-10-05)

PR #189 is prepared on PR #186's head and is the last of the stack (`LOG-0133`):
- **`GAP-032` has its real history.** The launch crash was reproduced and fixed by PR #153's review before M1. 9.3 found it again on older branches and adds the production-graph case, which this review observed red on the old shape.
- **The deferred registers no longer say M2 is released.** It is prepared at `v0.2.0`, so `DEF-002`/`DEF-003` wait for the owner's tag. `DEF-004` was re-verified on kotlinlang.org (Swift export is Alpha). `DEC-080`/`DEC-081` sit in §2 (closing `GAP-024`).
- **This handover's numbers are re-observed:** 1165 Gradle tests, 105 iOS tests, 52 Android and 17 iOS baselines, 46 documents with 0 findings. `README.md`, `README.es.md` and `AGENTS.md` §1 no longer say there is no application.
- **Four audit gaps whose work had landed are resolved:** `GAP-001`, `GAP-006`, `GAP-024` and `GAP-026`.

Owner actions after merging the stack: merge #161 → #178 → #182 → #186 → #189 in order, then close #164, #166, #170 and #174. Add the `ios` context to the ruleset (`TASK-108`). Tag `v0.1.0` on `644a2b3` and `v0.2.0` on the merged head. Enable Dependabot alerts. Close the issues of the `Done` rows. Decide `CONF-84`, `A-PERF-1` and `PERF-Q1`.

## 2. Completed work

1. **Documentation baseline.** The specification set exists and each topic has exactly one authoritative owner (`AGENTS.md` §2): requirements, architecture, remote contract, visual specification, internal contracts, failure→state→copy chain, performance, observability, security, testing, gates, guidelines, contribution process, plan, backlog, decision board, this file, and the audit. `README.md` and `README.es.md` are the entry points.
2. **Decision baseline.** `DEC-001`…`DEC-071` are recorded with category, status, urgency, blocking impact and ADR pointer, together with rejected and superseded alternatives and deferred decisions (`docs/DECISION_BOARD.md`).
3. **Requirements with acceptance criteria.** `docs/REQUIREMENTS.md` carries stable identifiers, `AC-<REQ-ID>-n` criteria, MoSCoW priorities, scope, non-goals, deferred items, constraints, risks and assessment traceability.
4. **Process baseline.** The TDD phase-and-commit protocol (DEC-053, amending DEC-041), the mandatory both-platform pull-request gate (DEC-054, superseding DEC-028) and the definitions of Ready and Done are decided and documented.
5. **Figma-aligned visual baseline.** The Figma file map, tokens, per-platform component specifications, screens, motion, states, iconography and accessibility expectations are written down (`docs/UI_SPEC.md`), on the basis of the two design briefs in `docs/design/`.

6. **Navigation and Settings redesign (2026-09-30).** In Figma, Settings replaced Locations as the fourth destination, and both navigation bars became reusable components: `Android/Navigation bar` `117:887` and `iOS/Glass tab item` `117:1369` inside `iOS/Glass tab bar` `102:255`. The Settings screens hold three settings built from each platform's kit: Sounds (off by default), a REST API/GraphQL choice (REST by default) and "Delete favorites", which opens a confirmation frame. Recorded as DEC-055 (ADR-0010) and DEC-056 (ADR-0011), with `REQ-FUNC-033`…`REQ-FUNC-035`, `IC-021`…`IC-023` and `TASK-074`…`TASK-077`. The Figma edits were checked by screenshot through the Figma API; the "Delete favorites" disabled state is specified in `docs/UI_SPEC.md` §8 but not drawn.

## 3. In progress and not started

- **Blocks 1 through 9 are prepared.** B1–B6 are merged; B7–B9 are in review in PRs #161, #178, #182, #186 and #189, to merge in that order (`DEC-108`…`DEC-110`, `DEC-120`). There is no remaining planned block work: `docs/BACKLOG.md` is the index, and every open row is either a carried decision or a named gap.
- **Plan:** the M0→M3 milestone plan, sequencing and quality gates live in `docs/TECHNICAL_PLAN.md`, authored in the same change as this file. Treat that document, not this summary, as the plan.
- **Work index:** the first implementation tasks with their acceptance criteria live in `docs/BACKLOG.md`, authored in the same change. Take work from there, not from this file.
- **Module layout is propagated and complete (DEC-052, superseding DEC-019):** **feature-per-module plus Clean Architecture packages inside each feature**. The core modules are `:core:domain`, `:core:data`, `:core:presentation`, `:core:designsystem` (Android-only) and `:core:testing`; the five feature modules are `:feature:discovery`, `:feature:character-detail`, `:feature:favorites`, `:feature:episodes` and `:feature:settings` (the latter replacing `:feature:locations` per `DEC-055`); the app shell is `:androidApp`. The build, `docs/DESIGN.md` §3, ADR-0001 and the boundary check all describe this same set, and `R16` of `verifyModuleBoundaries` fails when a required module is absent, so a silent deletion can no longer pass. `iosApp` and `:core:ios` (ADR-0012) are B7 work.
- **The gate is real and merged (PR #52, `7044869`; TASK-028):** `.github/workflows/pull-request.yml` runs on every pull request and every push to `main`, on an Ubuntu and a macOS runner with every action pinned by commit SHA, and `TASK-028` verifies the configuration cannot silently narrow (`TEST-UNIT-044`). Its `android` context is the required check of the `main protection` ruleset, with no bypass actor; the `ios` job is restored on the branch and its required context is the owner's remaining step (`TASK-108`, `DEC-049`).
- **Activated checks, and the rows still awaiting their harness:** the workflow runs `./gradlew check` (so the Android library and app unit tests, the `commonTest` suites, the build-logic regression suite and every policy task run inside it), `buildHealth`, `verifyDocumentedGate`, the contract replay on the Android host, the Android assemble and the Apple compiles. The snapshot, accessibility, performance, static-analysis and dependency-analysis rows stay recorded against their activation tasks in `TESTING.md` §14.2 (`DEC-071`) rather than being faked by an always-green job.
- **Merged before the gate existed, and the genuinely absent items:** a check that existed before `TASK-025` has no rollup of its own, so it is recorded as *integrated and locally verified*; everything from `TASK-024`/`TASK-025`/`TASK-028` onward runs under real required checks (`DEC-071`).
  - CI workflows (both platform workflows plus the fixture/replay contract job) — DEC-054;
  - the module-boundary enforcement check and its seeded violations — **merged** (TASK-017, PR #33; hardened by TASK-088, PR #43);
  - the module-boundary **topology completeness** rule and the durable build-logic regression suite — **merged** (`TASK-091`, `TASK-092`, PR #50 as `9cf6217`): `R16` fails when a required module is missing, `:feature:*` classification is restricted to the accepted five, and `./gradlew check` runs the 59-test suite;
  - the `VERSION` source — **merged** (TASK-018, PR #34; every artifact task consumes its validation since TASK-089, PR #44);
  - the repository-hygiene check that completes `.gitignore` is **merged in TASK-016** (PR #13, 2026-09-30): `verifyRepositoryHygiene` holds the ignore-rule and credential rules and runs in `check`;
  - the `tokens.json` parity test — DEC-022;
  - rendered PNG exports under `docs/figma/` — DEC-045;
  - `README.es.md` review as the only translation — DEC-047.

## 4. Next recommended actions, in priority order

Each action names the first concrete step and the document that owns it. A task is not started until its first failing test exists (DEC-053); the exceptions are documentation, build/CI configuration and tooling changes, and their use must be stated explicitly.

From 2026-10-02 the actions map onto the execution blocks of `BACKLOG.md` §2.6 (`DEC-063`). **B1 is closed** and **B2's nine rows are all `Done`**: the gate, the quality toolchain, the templates and both contract paths are merged (PRs #52, #62–#67, #69, #70, #72, #74, #76). An **audit of the merged block reproduced eight defects** in those deliverables — the workflow gate accepts configurations in which verification cannot run, the replay path can report an untested success, the JVM opt-in is unenforced, the macOS job covers four of nine modules, the documented gate is not substantiated, the observation path fails open in several ways, the exclusion lifecycle diverges from its decision, and the Swift scripts fail open — each registered as `GAP-016`…`GAP-023` and `TASK-098`…`TASK-105`; the human ruleset settings are `TASK-106`. Work continues block by block, and no task in a block depends on a later block.

1. **B1 closed.** All seven tasks and their corrections merged (PRs #25, #31–#36, #42–#45); the audit's open items are now `CONF-50`, `CONF-54` and the open `GAP-*` rows. Owner: `docs/DOCUMENTATION_AUDIT.md`.
2. **B2 is closed, remediation included.** Its nine original rows merged in PRs #52, #62–#70; its own audit then reproduced ten defects and registered them as `TASK-098`…`TASK-107` (`B2-R01`…`B2-R10`). All ten are now `Done` under D2, merged between `4953bde` (#88) and `a1de389` (#102): the workflow gate (`#88`), the documented gate (`#89`), the Swift toolchain (`#90`), the contract replay (`#91`), the exclusion register (`#92`), the target model (`#98`), the macOS coverage (`#99`), the live observations (`#100`), the repository settings (`#101`) and the document reconciliation (`#102`). The audit's open-gap count fell from 16 to 8, none of them a remediation finding. Owner: `docs/DOCUMENTATION_AUDIT.md` §6.2.1.
3. **B2 is closed, formally.** Its ten corrective rows are `Done` (PRs #88–#92, #98–#102) and the closure check that followed found and fixed three defects in the remediation's own documentation (PR #105, `LOG-0077`). The block's state, the traceability and the mechanical documentation guards are in `docs/DOCUMENTATION_AUDIT.md` §6.2.1–§6.2.2.
4. **B3 is complete, in three phase pull requests** (`DEC-082`, `BACKLOG.md` §2.6). **Phase 3.1** (`TASK-036`, `TASK-037`) is merged (PR #110, `2ed9c43`). **Phase 3.2** (`TASK-038`, `TASK-039`, `TASK-047`) is merged (PR #114, `b38766a`; §1.4) and discharged these obligations, recorded when it opened: the transport-wide host and redirect policy and a TLS/offline classifier (the adapter maps unclassified transport failures to `Unknown` today); `Retry-After` in HTTP-date form with an injected clock (the adapter parses delta-seconds only); OkHttp's own `retryOnConnectionFailure` neutralised so the three-attempt budget of `DEC-084` is not multiplied; the REST repository composition and `coreModule` behind the domain interfaces (`DEC-091`); `IC-014` implemented in `:core:data` against its new `:core:domain` declaration, deciding the paging-`404` end of `ERROR_FLOW.md` §5.2; and `:core:diagnostics` (`DEC-088`). **Phase 3.3** (`TASK-040`, `TASK-041`) is merged (PR #117, `f1fc88f`; §1.5) and **B3 is complete**: it added the favourites store and `ObserveFavoriteIds` (`DEC-090`), the presentation primitives and the parity verifier (`CONF-70`). The register rows that named `TASK-039`/`TASK-041` for feature edges now name the feature tasks that consume them (§1.5); the `TASK-043` rows are re-pointed when B4 lands. Owner: `docs/BACKLOG.md` §4.
6. **Then the delivery blocks** in order: B4 (design system and Android shell), B5/B6 (Android features and validation), B7–B9 (iOS and closure). Each block's members and its intra-block order are in `docs/BACKLOG.md` §2.6.

## 5. Unresolved decisions and deferred items

Do not implement a deferred item. The authoritative lists are the ones below, and they were **reconciled** in Phase 9.3 (`DEC-118`, corrected in review): `DEC-025` is closed after its condition fired, `DEF-002`/`DEF-003` wait for M2's release by the owner's `v0.2.0` tag and then for a new decision because they are Could-have, and `DEF-001`, `DEF-004`, `DEF-005` and `DEC-029` stay deferred with their conditions re-read. The authoritative lists are:

- `docs/DECISION_BOARD.md` §4 — decisions with status `Deferred`, with the condition that reopens each one.
- `docs/REQUIREMENTS.md` §1.3 — deferred scope items `DEF-001`…`DEF-005`, with their re-entry conditions re-read on 2026-10-04 (`DEC-118`). `docs/REQUIREMENTS.md` §14 records that nothing there is blocking.

- The decisions B1 needed are recorded (`DEC-064`…`DEC-068`, `LOG-0042`): `CONF-46`, `CONF-45`, `CONF-47`, `CONF-48` and `CONF-42` are resolved, and the `VERSION` acceptance is split for `TASK-018`/`TASK-051` (`DEC-067`). Still open and indexed as work rows in `docs/BACKLOG.md` §9: `GAP-010` (who owns dependency-artifact verification, and when); `GAP-011` (whether and when to rebuild the policy checks on Gradle's dependency model); and the OD-6 blocked item `CONF-50` (`CONF-51` is resolved as detekt target state by `DEC-075`), each with its own row.

Two consequences worth knowing before planning work: voice search is deferred and no microphone or speech permission may be added (`REQ-SEC-004`); Episodes ships as a placeholder (`DEC-005`), and Settings replaces Locations with three real settings, including a runtime REST/GraphQL switch (`DEC-055`, `DEC-056`).

## 6. Known risks

Product, contract and delivery risks are owned by `docs/REQUIREMENTS.md` §13 as `RISK-###`, with likelihood, impact, mitigation and owner. Do not restate or duplicate them here.

Two **operational** risks are specific to taking this repository over and are not covered there:

| Risk | Impact | Mitigation |
| --- | --- | --- |
| The Figma source file requires project access and returned HTTP 403 to an anonymous client on 2026-09-29. | `docs/UI_SPEC.md` cites Figma pages and node identifiers that a new contributor cannot open; the design becomes uncorroborable. | Obtain read access before design-dependent work; commit the rendered PNG exports under `docs/figma/` (DEC-045) so the specification stays checkable without access. |
| One toolchain artifact is pinned pre-release (Material 3 Expressive `1.5.0-alpha29`, `DESIGN.md` §3.5; the alpha-only `androidx.lifecycle` KMP and DataStore KMP of `CON-004` are **not** adopted). | An upgrade can break the build or change rendering, and the pinned versions age quickly. | Keep versions pinned and centralised in the version catalog, record the accepted alpha risk per `docs/adr/0008-alpha-dependencies.md`, and let the mandatory gate (DEC-054) catch breakage at upgrade time rather than at review time. |

One further operational note: `main` is the only integrated branch and it now carries a **branch ruleset** (`main protection`, active), configured by the owner on 2026-09-30: no deletion, no force-push, and a pull request required. Integration uses a **merge commit** and no other method, so the branch record survives (DEC-059); linear history is deliberately **not** required, because it would refuse merge commits. The ruleset names the required checks and they bind: the B2 packet (2026-10-02) put `android` and `ios` in it with no bypass actor, and from B3 Phase 3.1 it requires `android` only while the `ios` job is suspended (`DEC-083`) — a red, skipped or absent required check blocks the merge through GitHub; the exact list and the applied packets are in `CONTRIBUTING.md` §5.3. **No required approvals** are configured, because the repository has a single collaborator who cannot approve their own pull request.

**Tracker drift, observed 2026-10-03 (C2).** Five GitHub issues remain **open** although their backlog rows read `Done`: #80, #81, #83, #85 and #86 (`TASK-101`, `TASK-102`, `TASK-104`, `TASK-106`, `TASK-107`). Closing another task's issue needs an owner instruction (`DEC-044`, `CONTRIBUTING.md` §4), so the drift is reported rather than fixed. It does not affect a check: the backlog is canonical and the rows are correct.

**Reading the history, and recovering branch names.** Merges #1–#4 are real merge commits, so `git log --graph --all` shows those branches and their commits even though the refs are gone. Merges #6 and #7 were rebase merges and left **no** branch structure: `main`'s history is linear across them and their branch tips are unrecoverable from the graph, which is precisely what DEC-059 prevents from recurring. Every historical branch name is nevertheless recoverable, because a pull request keeps its head ref name permanently: `gh pr list --state all --json number,state,headRefName,baseRefName` lists all seven PRs with their branches (`docs/figma-designs`, `docs/module-and-ci-decisions`, `docs/settings-destination`, `docs/data-sources`, `build/gradle-kmp-skeleton`, `docs/ios-framework-export`, `docs/merge-commit-integration`). Repository settings and branch protection are human actions (DEC-049).

## 7. Validation status

**What has been verified in this repository, and how:**

- **Link checking** — relative links between documents were resolved by inspection as part of the documentation review; the inventory and any unresolved finding are in `docs/DOCUMENTATION_AUDIT.md`.
- **Identifier uniqueness** — `LOG-####` entries are sequential and unique; the requirements, decision, risk, contract and test identifier schemes are checked for uniqueness and cross-reference by the same review, which is the check class recorded in `docs/DOCUMENTATION_AUDIT.md` §6.
- **Consistency review** — statements that a document does not own were traced to their owner and de-duplicated; conflicts found are recorded as `CONF-###` in `docs/DOCUMENTATION_AUDIT.md` rather than silently resolved.
- **Remote contract verification** — live probes against the public API on 2026-09-29; the observed results and their date are in `docs/API_SPECS.md` §1.1.
- **Repository history** — read from the repository itself, not recalled.

**What has NOT been verified:** the performance budgets on a named reference device (no device exists here — `DEC-115`, `DEC-116`, `DEC-117`); the iOS 18 deployment-floor run (no iOS 18 runtime is available for Xcode 27); the iOS device accessibility checklist of `TASK-061`; and the dependency advisory register, which stays empty because no real finding exists to record (`TASK-067`, `SECURITY.md` §11.1). Every other row of this document that claims verification was observed by executing the named command on the named tree, and the observed result is in `PROJECT_LOG.md`.

## 8. Relevant commands

The interface as it stands today. The build skeleton exists (TASK-014), so the wrapper and the module names below are real; the feature, test and release commands are the intended interface and are unexercised. Module names follow DEC-052.

Commands marked **executed** were run on the branch named with the date and are recorded with their observed results in `PROJECT_LOG.md` (`LOG-0026`, `LOG-0066`…`LOG-0076`).

| Purpose | Command | State |
| --- | --- | --- |
| Build the Android debug app | `./gradlew :androidApp:assembleDebug` | **Executed 2026-09-30** — succeeded, with no `iosApp/` present and from a clean clone |
| Build every module, including the iOS klibs | `./gradlew assemble` · `./gradlew build` | **Executed 2026-09-30** — succeeded; no Android Lint error |
| List the module set | `./gradlew projects` | **Executed 2026-09-30** — exactly the 11 modules of ADR-0001 |
| Inspect the resolved module graph | `./gradlew :<module>:dependencies --configuration <sourceSet>Implementation` | **Executed 2026-09-30** — matches the module table |
| Inspect the APK manifest | `aapt2 dump badging androidApp/build/outputs/apk/debug/androidApp-debug.apk` | **Executed 2026-09-30** — package `io.github.davidru85.multiverse`, `minSdk` 26, `targetSdk` 37, no permission |
| Install on a connected device or emulator | `./gradlew :androidApp:installDebug` | **Executed 2026-10-04** — installed on `Pixel_9_Pro` (API 36) and launched; the journey, the offline states and the favourite persistence were exercised by hand (`LOG-0118`) |
| Build the shared framework for the iOS simulator | `./gradlew :core:ios:linkDebugFrameworkIosSimulatorArm64` | **Executed** — one static framework exports the five features plus the other core modules (`DEC-058`, [ADR-0012](adr/0012-ios-framework-export.md)); the `iosApp` target links it (`TASK-078`, `TASK-051`) |
| Build the iOS app | `xcodebuild -project iosApp/MultiverseExplorer.xcodeproj -scheme MultiverseExplorer -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0' build` | **Executed** — builds over the exported framework (`TASK-051`); the `ios` job builds it on every pull request |
| Shared and unit tests, plus the build-logic regression suite | `./gradlew allTests :build-logic:convention:test` | Executed 2026-10-02: the shared suites run on the JVM host-test target and both Apple targets (`:core:testing:testAndroidHostTest`, `:core:testing:iosSimulatorArm64Test`) and the build-logic regression suite runs in the included build. The earlier row documented `./gradlew test`, which selects only the Android unit-test tasks and reaches neither the shared KMP suites nor the build-logic suite (`TEST-UNIT-015`; `TASK-024`, `TASK-103`) |
| Android screenshot verification | `./gradlew :feature:discovery:verifyRoborazziDebug` | **Executed** — 52 committed baselines across the component catalogue, the seven `ERROR_FLOW.md` states, the surfaces and the shell, each proved byte-identical in light and dark (`TASK-045`) |
| Record new Android screenshot baselines (review the diff before committing) | `./gradlew :feature:discovery:recordRoborazziDebug` | **Executed** — the recording route the committed baselines came from (`TASK-045`) |
| Formatting, static analysis, dependency checks | `./gradlew ktlintCheck lintDebug buildHealth` (detekt is **not** registered: `DEC-075` keeps it target state, so it is not part of any executed command) | Executed 2026-10-01: ktlint, Android Lint and `buildHealth` run and pass (`TASK-029`, PR #65) |
| Dependency-policy checks (exact pins, rationale, inventory) | `./gradlew verifyDependencyPolicy` | **Executed 2026-09-30** — passes; also runs inside `./gradlew check` and `./gradlew build` (TASK-015, LOG-0032) |
| Repository and secret hygiene check | `./gradlew verifyRepositoryHygiene` | **Executed 2026-10-01 on merged `main`** — passes with 0 findings over the working set, every reachable blob and every unique historical path; runs inside `./gradlew check` and `./gradlew build` (TASK-016, LOG-0036…LOG-0039) |
| iOS tests and snapshots | `xcodebuild test -project iosApp/MultiverseExplorer.xcodeproj -scheme MultiverseExplorer -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0'` | **Executed 2026-10-05** on the reviewed PR #189 tree — 105 tests, 0 failures; the destination pins the runtime the `ios` job uses, so the committed baselines compare like for like |
| Contract suite — fixture/replay mode, per target | `./gradlew :core:data:contractTestReplayAndroidHost` (the `android` job) · `./gradlew :core:data:contractTestReplayIosSimulator` (local macOS while `DEC-083` suspends `ios`) · `./gradlew :core:data:contractTestReplay` (both) | **Executed 2026-10-02** on `feat/b3-phase-3-1`: 18 contract cases per target, 36 for the aggregate (`TASK-037`) |
| Performance benchmarks (requires a device) | Task and module are defined in `docs/TECHNICAL_PLAN.md`; the budget and measurement method are in `docs/PERFORMANCE.md` | Not run: the harness module needs a decision first (CONF-41) |

Every row above that is not marked executed, and every row added since this table was written, remains unexercised and is marked accordingly; the skeleton's own verification is LOG-0026.

The complete required-check list for a pull request, and what each check blocks, are owned by `docs/TESTING.md` and `docs/DEFINITION.md`. As of DEC-054 every check in that list is blocking on both platforms, including the iOS suites; the red commit of a TDD cycle is expected to fail tests, because the gate evaluates the final state of the pull request.

## 9. Credentials and prerequisites

**No credentials are required, and none exist.** The API is public, unauthenticated and read-only; no API key, token, secret, keystore or environment variable is needed to build or run the application, and committing one is prohibited (`AGENTS.md` §4.2, `REQ-SEC-002`).

Two prerequisites involve an account or access decision, and neither is a secret:

| Prerequisite | Need | Status |
| --- | --- | --- |
| Figma file (`nFQdxd23Kk4rNI7G4iHDUr`) | Read access, so the design of record can be opened and the PNG exports can be produced | Not held by default — the file returned HTTP 403 to an anonymous client on 2026-09-29 |
| Package registry | None beyond network access: Kotlin, AndroidX, Ktor, Coil, Koin and the test tooling come from public repositories, and no private feed or license is involved | Satisfied by the public repositories; no account required |

Local toolchain prerequisites (JDK, Android SDK, Xcode, Kotlin supplied by the Gradle toolchain) are listed in `README.md` §6. A macOS host is required to run the iOS suites, which DEC-054 makes mandatory on every pull request.

## 10. Key documentation links

| Document | Why you need it |
| --- | --- |
| [`README.md`](../README.md) | Project overview, features, structure, setup, intended commands |
| [`AGENTS.md`](../AGENTS.md) | Precedence, roles, permissions, workflow, escalation, completion rules |
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | What the product must do, with acceptance criteria, scope, risks |
| [`DESIGN.md`](DESIGN.md) | Architecture, modules, state contracts, navigation |
| [`API_SPECS.md`](API_SPECS.md) | Remote contract, DTOs, errors, caching policy |
| [`UI_SPEC.md`](UI_SPEC.md) | Tokens, components, screens, motion, accessibility |
| [`DECISION_BOARD.md`](DECISION_BOARD.md) | Every decision, its status, its ADR, and what is deferred |
| [`TECHNICAL_PLAN.md`](TECHNICAL_PLAN.md) | Milestones, sequencing, quality gates |
| [`BACKLOG.md`](BACKLOG.md) | The canonical work index with task-level acceptance criteria |
| [`TESTING.md`](TESTING.md) | Test strategy, layers, tooling, required CI checks, traceability |
| [`DEFINITION.md`](DEFINITION.md) | Ready, Done, pull-request and release gates |
| [`DOCUMENTATION_AUDIT.md`](DOCUMENTATION_AUDIT.md) | Inventory, ownership, open gaps and conflicts |
| [`PROJECT_LOG.md`](PROJECT_LOG.md) | Why the project evolved as it did (`LOG-####`) |
