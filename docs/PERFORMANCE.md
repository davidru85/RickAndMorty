# PERFORMANCE.md - Performance Budgets and Measurement Method

- **Status:** Active - target state. The Android app is runnable since TASK-044, but no budget has been measured: the reference device is unassigned (A-PERF-1) and the harness module is undecided (PERF-Q1), so every budget below is a *target* (DEC-115). No number in this file is a device measurement; the recorded results are the deterministic zero-request half of `PERF-006` and the release APK size of `PERF-009`, a measurement of the build artifact (§5).
- **Last verified:** 2026-10-06
- **Owner:** Implementation Engineer (see `AGENTS.md` §3.5); measurement evidence is recorded by the QA & Validation Engineer (`AGENTS.md` §3.6)
- **Authoritative for:** the numeric performance budgets (`PERF-###`), the reference device definition, the measurement method and tools per platform, the result register, and the regression and budget-change policy. Nothing else in the repository may state a numeric performance budget (DEC-033).
- **Inputs:** [`REQUIREMENTS.md`](REQUIREMENTS.md) §6 (`REQ-NFR-003`) · [`API_SPECS.md`](API_SPECS.md) §8, §12 · [`DESIGN.md`](DESIGN.md) §4, §5 · [`UI_SPEC.md`](UI_SPEC.md) §5, §6 · [`TESTING.md`](TESTING.md) · [`PROJECT_LOG.md`](PROJECT_LOG.md) · [`DECISION_BOARD.md`](DECISION_BOARD.md)
- **Normative terms:** `MUST` mandatory · `SHOULD` strong recommendation · `MAY` optional.

## 1. Scope and constraints

| Constraint | Effect on this document |
| --- | --- |
| Phone portrait only - DEC-027, `NG-004` | Every budget is measured in portrait on the reference device defined in §2. No budget exists for landscape, tablet or foldable; those runs are out of scope, not unmeasured. |
| One 300 × 300 source image per character - `CON-002`, `API_SPECS.md` §4.7 | Budgets never assume a higher-resolution source (`AC-REQ-FUNC-005-3`). The decode budget in `PERF-007` is derived from that exact source size. |
| No analytics, tracking or telemetry SDK - DEC-038, `REQ-OBS-003` | No performance data is collected, transmitted or reported at runtime. Every number here is produced by a developer-run measurement and recorded in the repository (§5). |
| Budgets are measured, not asserted - `REQ-NFR-003` | A budget row is "met" only when a measurement exists with the device, build, date and evidence recorded in §5. Reasoning about the design, code review, and estimates are not evidence. |
| Documentation phase - DEC-046, DEC-049 | This document describes the target state. It is authored on branch `docs/documentation-system`; results are recorded on the branch that ships the measured build. |
| Two platforms - DEC-001, DEC-040 | Android is measured with an automated harness (§4.1). iOS has no automated equivalent and is measured with a recorded manual procedure (§4.2). Budget identifiers are shared: a budget is a product budget, not a platform-specific one, and it `MUST` be recorded separately per platform. |

Requirements that depend on this file: `REQ-NFR-003` and its acceptance criteria `AC-REQ-NFR-003-1` (cold start ≤ 2.0 s p50), `-2` (scrolling sustains the frame-time budget without visible jank) and `-3` (a cached page render issues zero network requests). Those numbers are owned by [`REQUIREMENTS.md`](REQUIREMENTS.md); this file `MUST NOT` loosen them without a requirements change (§7.3).

## 2. Reference device and environment

The reference device is defined by a profile, because the concrete model is not yet chosen. **Assumption A-PERF-1:** the exact model, serial and OS build `MUST` be filled in §2.1 before the first budget measurement; until then, no budget may be reported as met.

### 2.1 Reference device definitions

| | Android (authoritative for `PERF-001`…`PERF-009`) | iOS (authoritative for the iOS column of each budget) |
| --- | --- | --- |
| Model | **Unassigned - assumption A-PERF-1.** `MUST` be a single physical phone with the profile in the next row, and its exact marketing name, build fingerprint and serial recorded here and in the `PROJECT_LOG.md` entry. | **Unassigned - assumption A-PERF-1.** A single physical iPhone with the profile in the next row. |
| Required profile | 64-bit arm64 SoC released 2023 or later, mid-tier or better; 1080 × 2400-class OLED; 8 GB RAM; 120 Hz capable display; Android 14 or newer (`minSdk` 26 / `targetSdk` 37 per DEC-009, measured on the newest Android available at measurement time); 128 GB storage with at least 20 GB free. | A device supported by iOS 18.0 or newer (DEC-008), 60 Hz or 120 Hz capable, with at least 20 GB free. |
| Emulator | An emulator `MAY` be used to verify that the harness works (API 37, x86_64 or arm64, 4 GB+ RAM, hardware acceleration on). Emulator numbers `MUST NOT` be recorded as budget results: Macrobenchmark numbers are device- and compilation-dependent. | A Simulator `MUST NOT` be used for any budget: launch, memory and Core Animation metrics on a Simulator do not represent a device. |
| Device state for every run | Portrait, fixed brightness, battery ≥ 50 % or on power, no other foreground workload, animations at their default scale, Do Not Disturb on, cooled to below 35 °C measured by the platform thermal API before a run starts, and no run started within 10 minutes of a previous run of the same benchmark. | Portrait, Low Power Mode off, battery ≥ 50 % or on power, thermal state "nominal" (`ProcessInfo.thermalState`) before a run, Low Power Mode and Background App Refresh at defaults. |

### 2.2 Standard measurement profiles

| Profile | Definition | Used by |
| --- | --- | --- |
| `NET-PRIMARY` | Wi-Fi with at least 20 Mbit/s down and round-trip latency at or below 50 ms, measured immediately before the run and recorded. | `PERF-001`, `PERF-002`, `PERF-003`, `PERF-005` |
| `NET-THROTTLED` | A recorded throttled profile at approximately 1.5 Mbit/s and 200 ms round-trip latency, applied with the Android emulator network settings or a device Wi-Fi shaper. `MAY` be used to record a second data point for the same budgets. | `PERF-003` (optional second data point) |
| `GRID-POPULATED` | Filter "All", three pages loaded (60 items) through the normal paging path, every visible portrait decoded and resident in the image cache, scroll position at the top. | `PERF-004`, `PERF-006`, `PERF-007`, `PERF-008` |
| `GRID-SCROLL-SCRIPT` | Six fling gestures of roughly 1,500 px each with a 500 ms settle between them, in portrait, repeated for 10 measured iterations. | `PERF-004` |

The live API is used for `PERF-001`, `PERF-002`, `PERF-003` and `PERF-005`, because those budgets include real network behaviour. Every run records the observed `NET-PRIMARY` throughput and latency; a budget result is only comparable with another result recorded under the same profile.

## 3. Budgets

All timings are wall-clock and are reported as p50/p90/p95 over at least 10 iterations, with the first iteration discarded only when the harness reports it as a warm-up. "Frame budget" means 8.33 ms of CPU frame time on a 120 Hz display, which is the reference device's panel rate.

| ID | Budget target | Requirement | Verification | Measurement method | Tool | Where recorded |
| --- | --- | --- | --- | --- | --- | --- |
| `PERF-001` | Cold start to first Discovery content: p50 ≤ 2.0 s, p90 ≤ 2.8 s (never above the 3.0 s splash ceiling of `UI_SPEC.md` §6.1). Cold start means the process is not resident; the splash floor of 1.2 s is compatible with this budget because it is measured from process start, not from splash start. | `AC-REQ-NFR-003-1` | `TEST-PERF-001` (measurement job) | Android: Macrobenchmark startup benchmark with `StartupTimingMetric`, `CompilationMode.None`, 10 iterations, reading `timeToFullDisplayMs` after a `reportFullyDrawn()` call placed when the first Discovery content is committed. iOS: manual launch measurement (§4.2), from tapping the icon to the first Discovery content. | Android: Macrobenchmark (`androidx.benchmark.macro.junit4`). iOS: Instruments App Launch template plus the Xcode Organizer "Launch Time" metric after a TestFlight build. | §5 register (`PERF-001`) + `PROJECT_LOG.md` entry (`LOG-####`) |
| `PERF-002` | Warm start (process resident, activity recreated) to interactive Discovery: p50 ≤ 500 ms, p90 ≤ 900 ms. | `REQ-NFR-003` | Measurement by the same startup harness as `PERF-001`; no separate test id is assigned in `TESTING.md` | Android: Macrobenchmark startup benchmark in `WARM` mode with `StartupTimingMetric` and `CompilationMode.Partial` (the shipped compilation state), 10 iterations. iOS: manual procedure (§4.2), app resumed from the background. | Same tools as `PERF-001`. | §5 register (`PERF-002`) + `PROJECT_LOG.md` entry |
| `PERF-003` | First page, request dispatched to the first card's content committed: p50 ≤ 1.2 s, p90 ≤ 2.0 s on `NET-PRIMARY`. Isolated decode and mapping of one 20-item page (network excluded): p50 ≤ 60 ms, p90 ≤ 120 ms. | `REQ-NFR-003`, `REQ-FUNC-001` | `TEST-PERF-001` (measurement job) with the trace section; the pager behaviours behind it are `TEST-UNIT-016` | Android, network half: a Macrobenchmark custom trace section (`trace("first-page")`) opened before `LoadNextPage` is dispatched and closed when the first card is drawn; run on `NET-PRIMARY`. Android, decode half: the same trace segment replayed against the Ktor `MockEngine` with the committed page fixture (DEC-030), so the result isolates decode, mapping and state reduction. iOS: manual procedure with Instruments. | Android: Macrobenchmark + `MockEngine` fixture (DEC-030). iOS: Instruments Time Profiler with os_signpost intervals. | §5 register (`PERF-003`) + `PROJECT_LOG.md` entry |
| `PERF-004` | Populated grid scrolling in `GRID-POPULATED` under `GRID-SCROLL-SCRIPT`: frame CPU time p50 ≤ 8.33 ms, p90 ≤ 16.7 ms, p95 ≤ 24 ms (one frame of headroom); no frame above 3 × the frame budget (50 ms, a dropped-frame outlier); jank rate, frames exceeding the frame budget, ≤ 1 % of measured frames. | `AC-REQ-NFR-003-2` | `TEST-PERF-002` (measurement job) | Android: Macrobenchmark scroll benchmark with `FrameTimingMetric` over 10 iterations, after which the report's jank percentage is read. iOS: Instruments Core Animation plus Xcode Organizer "Hang Rate", with the same scroll script performed by hand or through an XCUITest driven from the Instruments session. | Android: Macrobenchmark. iOS: Instruments Core Animation, Xcode Organizer. | §5 register (`PERF-004`) + `PROJECT_LOG.md` entry |
| `PERF-005` | Detail screen open with the list data available: the pre-filled header (name, portrait, status) is present in the first composed frame of the destination, i.e. with no dependence on the network - verified with an artificially delayed network (5 s) it `MUST` still be visible within 100 ms of the destination's first frame. The shared-element transition completes within 450 ms + 10 % (the motion spec is `UI_SPEC.md` §7 / `AC-REQ-FUNC-009-1`). Full detail content: p50 ≤ 800 ms, p90 ≤ 1.5 s on `NET-PRIMARY`. | `REQ-FUNC-002`, `AC-REQ-FUNC-002-1`, `AC-REQ-FUNC-009-1` | The header assertion is an instrumented platform test; `TEST-UI-002` covers the retained header and inline retry. The timing halves are measured by the Macrobenchmark harness with a new perf test id to be added to `TESTING.md` (owner: QA & Validation Engineer) before the benchmark is written. | Android: an instrumented test with the repository faked to delay responses by 5 s asserts the header within 100 ms of the first frame (hard assertion, deterministic); a Macrobenchmark trace sections the click-to-detail-content path for the network half. iOS: manual procedure; the header assertion is repeated in the XCUITest-driven Instruments run. | Android: instrumented test + Macrobenchmark. iOS: Instruments Time Profiler, Core Animation. | §5 register (`PERF-005`) + `PROJECT_LOG.md` entry |
| `PERF-006` | Cache-hit page render: zero network requests, and from dispatch to the first committed content p90 ≤ 250 ms. A cache-hit render is a page whose response is inside the 24 h fresh window (`API_SPECS.md` §7). | `AC-REQ-NFR-003-3` | `TEST-PERF-003` (zero-network assertion, in the blocking gate) and `TEST-UNIT-037` (the Discovery repeat-visit path) | The zero-request half is asserted deterministically: an integration test replays a cached read and asserts the HTTP engine recorded zero calls. The timing half is measured with a Macrobenchmark trace around a warm re-entry into a fully cached page. | Android: Ktor `MockEngine` call counter (zero-request half, part of the PR suite) + Macrobenchmark (timing half). iOS: Instruments. | §5 register (`PERF-006`) + `PROJECT_LOG.md` entry |
| `PERF-007` | Image decode memory footprint for the documented 300 × 300 source (`CON-002`): one decoded portrait ≤ 384 KiB (the theoretical 300 × 300 × 4 B = 352 KiB plus allocator overhead); image-cache footprint with 24 portraits resident ≤ 9 MiB; the accent-colour extraction's `allowHardware(false)` software copy is transient and counted at most once per URL. No portrait is decoded above the source resolution. | `AC-REQ-FUNC-005-3`, `REQ-FUNC-021-2`, `CON-002` | `TEST-INT-002` covers image caching independently of the JSON cache; the byte-count assertion is a platform instrumented test whose perf id must be added to `TESTING.md` (owner: QA & Validation Engineer) before it is written | Per-bitmap half: an instrumented test decodes an image through the app's configured image request and asserts `Bitmap.allocationByteCount` against 384 KiB - deterministic and part of the PR suite. Cache-footprint half: a manual heap inspection in the `GRID-POPULATED` state, summing the image-loader cache entries. | Android: instrumented assertion + Android Studio Memory Profiler. iOS: Xcode Instruments Allocations. | §5 register (`PERF-007`) + `PROJECT_LOG.md` entry |
| `PERF-008` | App resident memory ceiling in `GRID-POPULATED`: total PSS ≤ 220 MiB, and no monotonic growth above 5 MiB across 10 consecutive open/close cycles of the detail screen (a leak indicator, not a budget). | `REQ-NFR-003` | Measurement job; no test id is assigned in `TESTING.md` yet, so a perf id must be added before the benchmark is written (owner: QA & Validation Engineer) | Android: `dumpsys meminfo <package>` sampled after a 30 s idle period at the end of the state, three samples, median used; the growth check compares the first and last sample of the cycle script. iOS: Xcode Instruments Allocations/Leaks with the same script. | Android: `dumpsys meminfo` / Android Studio Memory Profiler. iOS: Instruments. | §5 register (`PERF-008`) + `PROJECT_LOG.md` entry |
| `PERF-009` | Release APK (single universal artifact): ≤ 12 MiB. The debug APK is not budgeted. If ABI splits are added, each split is ≤ 8 MiB. | `REQ-NFR-002` (dependency restraint) | Release verification task; not a test id, and not in `TESTING.md` by design | A Gradle verification task over the assembled release APK, reporting the total download size and the per-entry breakdown for the largest 10 entries; run on every release candidate and recorded. The APK is distributed as a file, so its download size is its file size. | `:androidApp:verifyReleaseApkSize` (`DEC-148`), which reads the APK and its entry table and writes `build/reports/verifyReleaseApkSize/apk-size.txt`; `apkanalyzer` (Android SDK command line tools) for a manual drill-down. | §5 register (`PERF-009`) + `PROJECT_LOG.md` entry |

Budget semantics:

- A budget is `met` for a platform only when a measurement is recorded in §5 for that platform; a missing platform is `unmeasured`, never "equal".
- p95 and the "no frame above 3 × budget" clause exist so that a good p50 cannot hide a visibly janky tail (`AC-REQ-NFR-003-2` requires no visible jank, which is a tail property).
- `PERF-007` and the zero-request half of `PERF-006` are deterministic assertions, so they belong in the blocking PR suite (DEC-054) rather than in a device-dependent benchmark run: `TEST-PERF-003` asserts the zero-network cached render and `TEST-INT-002` asserts image caching independently of the JSON cache.
- The device-dependent rows run as measurements, not as PR checks, and map to the measurement ids `TEST-PERF-001` (startup, first content) and `TEST-PERF-002` (scroll frame timing) defined in [`TESTING.md`](TESTING.md) §12. A budget whose measurement has no `TEST-PERF-###` id yet (`PERF-005` timing, `PERF-007` bytes, `PERF-008`) `MUST` have one assigned by the QA & Validation Engineer before its benchmark is written; the id is never invented here.
- Where `TESTING.md` and this file disagree about which perf id covers a budget, `TESTING.md` is authoritative for the identifier and this file is authoritative for the numeric target; the conflict is reported rather than silently resolved (`AGENTS.md` §12).

## 4. Measurement method

### 4.1 Android - Macrobenchmark harness

Macrobenchmark is the only tool that measures the budgets end to end on a device: it reproduces real process starts, real compilation states and real frame timings, which unit tests and screenshot tests cannot.

| Element | Requirement |
| --- | --- |
| Harness module | Macrobenchmark requires a dedicated `com.android.test` module with its own `benchmark` build type (non-debuggable, `signingConfig` for release-like signing, `testBuildType = "benchmark"`). **Open item PERF-Q1:** the current authoritative module list (DEC-052) enumerates only `:core:*`, `:feature:*`, `:androidApp` and `iosApp`, and forbids additional modules. Building the harness therefore requires a new decision (`DEC-###`, proposed: "benchmark harness module") plus the matching `adr/0001-module-boundaries.md` update before any Gradle file is created. Until that decision is accepted, Android budgets are measured manually with `adb` and Perfetto traces, and the automated rows of §3 remain unmeasured. |
| Benchmarks to write | `StartupBenchmark` (`CompilationMode.None` for `PERF-001`, `WARM` + `CompilationMode.Partial` for `PERF-002`, 10 iterations, `StartupTimingMetric`), `ScrollBenchmark` (`GRID-POPULATED` + `GRID-SCROLL-SCRIPT`, `FrameTimingMetric`, 10 iterations), `FirstPageBenchmark` (custom trace section for `PERF-003`), `DetailOpenBenchmark` (trace section + the deterministic header assertion for `PERF-005`), `CacheHitBenchmark` (trace section for the timing half of `PERF-006`), `MemoryBenchmark` (or a scripted `dumpsys meminfo` run for `PERF-008`). |
| Result stability | Fixed brightness, `CompilationMode` stated explicitly, three runs of the whole suite after a cold boot, and the median run recorded; the run-to-run spread is recorded next to the value so a regression threshold can be judged against noise. |
| Baseline profile | The startup budget depends on the profile compiled for the app. The project `SHOULD` generate a baseline profile with the `androidx.baselineprofile` plugin, commit it with the app sources, and use it from the release build. `StartupBenchmark` `MUST` therefore record which compilation mode was used: `CompilationMode.None` shows the worst case (first install), `CompilationMode.Partial(baselineProfileMode = Require)` shows the shipped case, and the `PERF-001`/`PERF-002` register rows `MUST` name the mode they were measured in. |
| Fixtures | Deterministic inputs come from `:core:testing` (shared fakes, committed JSON page fixtures, fake clock, `TestDispatcher` helpers, DEC-030) and from the committed portrait fixtures derived from the design source (`DESIGN.md` §3). Benchmarks never depend on live network content for the `GRID-POPULATED` state; `PERF-001`…`003`/`005` use the live API as stated in §2.2 and record the network profile. |
| Not performance tools | Robolectric and Roborazzi produce deterministic, host-side rendering results and are the visual-regression tools (DEC-024, DEC-034). They measure neither frame time nor app memory, and a Roborazzi pass `MUST NOT` be cited as performance evidence. Likewise, a JVM unit test may prove a cache hit issues zero requests, but it proves nothing about timing. |

### 4.2 iOS - documented manual procedure

There is no automated equivalent of the Macrobenchmark harness on iOS, so the procedure below is performed by hand and recorded by hand. The assumption is recorded as **A-PERF-4**: the procedure is unverified because no iOS app exists yet; the first M2 measurement confirms or amends it.

| Budget | Procedure |
| --- | --- |
| `PERF-001`, `PERF-002` | Build the release configuration, install with Xcode, force-quit the app, then measure with the Instruments **App Launch** template (launch, then the splash-to-Discovery content boundary) for the cold start. For the warm start, background the app, return to it, and read the same interval. Repeat 10 times and take the median. The Xcode Organizer "Launch Time" metric, collected after a TestFlight distribution, `MAY` corroborate the numbers. |
| `PERF-003`, `PERF-005` | Use Instruments **Time Profiler** with os_signpost intervals placed around the page request and the detail request, and around the first committed content; read the interval durations from the trace. The header-visibility assertion of `PERF-005` is repeated with the network delayed by 5 s. |
| `PERF-004` | Use Instruments **Core Animation**, with the display-linked frame rate and hitch readouts enabled, while performing the `GRID-SCROLL-SCRIPT` gestures; record the hitch count and the frame-time distribution. The Xcode Organizer "Hang Rate" metric `MAY` corroborate the result. |
| `PERF-007`, `PERF-008` | Use Instruments **Allocations** and **Leaks** with the `GRID-POPULATED` state and the 10-cycle detail script; read the resident size, the largest live allocations, and whether the cycle script produces monotonic growth. |
| Scope limit | iOS Simulator numbers `MUST NOT` be recorded as budgets (§2.1). Xcode 26 exposes `XCTApplicationLaunchMetric` and `XCTOSSignpostMetric`, which `MAY` complement the manual numbers once the iOS milestone starts; they do not replace the manual boundary definitions because the launch metric measures the app-launch boundary, not the splash-to-Discovery content boundary of `PERF-001`. |

### 4.3 Recording rules

1. Every run records: `PERF-###` id, the `TEST-PERF-###` (or platform test) id that produced it, platform, device model + build fingerprint, OS version, app build (version name and commit), compilation mode, network profile, iteration count, the p50/p90/p95 values, the tool and its version, the date (ISO 8601) and the raw report location.
2. Raw reports (Macrobenchmark JSON/HTML, Instruments traces, `dumpsys` output) are attached to the `PROJECT_LOG.md` entry or stored next to it; they are not pasted into this file.
3. A result that changes a registered value is recorded as a new row in §5 rather than overwriting the previous one, so the trend stays visible.
4. Timings `MUST` be recorded as measured, including a result that misses the budget. A missed budget is reported, not hidden (`AGENTS.md` §4.2).

## 5. Result register

No device measurement exists yet. The Android app is runnable since `TASK-044`, but A-PERF-1 names no reference device and PERF-Q1 leaves the harness undecided, so every timing, frame and memory row stays `unmeasured` (DEC-115); the rows that say "no runnable app" record the state when they were created and are kept for the trend (§4.3 rule 3). The recorded results are the zero-request half of `PERF-006`, a deterministic assertion in the blocking gate, and the release APK size of `PERF-009`, read from the build artifact; neither is a device measurement. The register is the single place where measured values live; the `PROJECT_LOG.md` entry referenced in §3 carries the narrative and the raw evidence.

| ID | Platform | Test id producing the value | Last measured value | Date | Device | Build | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `PERF-001` | Android | `TEST-PERF-001` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-001` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-002` | Android | Same harness as `TEST-PERF-001` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-002` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-003` | Android | `TEST-PERF-001` trace section | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-003` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-004` | Android | `TEST-PERF-002` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-004` | Android | Emulator scroll script (`TASK-128`), not `TEST-PERF-002` | 25 flings after a cold start with cleared data, `dumpsys gfxinfo`: janky frames 1.35 %, 0.08 %, 1.28 % before and 1.16 %, 1.42 % after (p99 20–22 ms before, 22–30 ms after); `DefaultDispatcher` workers' CPU over the session 260 and 230 ms before, 200 and 150 ms after | 2026-10-06 | None - `Pixel_9_Pro` emulator, API 37, on an Apple Silicon host (not the reference device, A-PERF-1) | `0.2.0` release (R8), signed locally with the debug key, before and after `TASK-128` | `PROJECT_LOG.md` LOG-0153 | Indicative only: the emulator does not reproduce the owner's lag; the frame numbers move within noise |
| `PERF-004` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-005` | Android | Instrumented header assertion; perf id pending assignment in `TESTING.md` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-005` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-006` | Android | `TEST-PERF-003` (zero-network) and `TEST-UNIT-037` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-006` | Android | `TEST-PERF-003` (zero-request half only) | 0 engine requests on a cache-hit render, against the real REST adapter and `MockEngine`; a refresh control observes 1 | 2026-10-04 | None - JVM host test (`:core:data:testAndroidHostTest`), not a device run | `0.1.0`, PR #156 (B6 Phase 6.2) | `PROJECT_LOG.md` LOG-0103, LOG-0127 | Zero-request half passing in the blocking gate; timing half unmeasured (A-PERF-1, PERF-Q1, DEC-115) |
| `PERF-006` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-007` | Android | `TEST-INT-002` (image cache); byte-count id pending assignment in `TESTING.md` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-007` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-008` | Android | Perf id pending assignment in `TESTING.md` | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-008` | iOS | Manual procedure (§4.2), no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-009` | Android | Release verification task; no test id | Not measured | Not measured | Not assigned (A-PERF-1) | Build-only; no runnable feature code | None | Unmeasured - no runnable app |
| `PERF-009` | Android | `:androidApp:verifyReleaseApkSize`; no test id | 12 974 772 B (12.37 MiB) universal release APK, unsigned; neither code nor resources shrunk | 2026-10-05 | None - build artifact | `0.1.0`, `TASK-120` red commit | `PROJECT_LOG.md` LOG-0139, LOG-0144; `GAP-034` | Over budget by 391 860 B |
| `PERF-009` | Android | `:androidApp:verifyReleaseApkSize`; no test id | 2 337 826 B (2.23 MiB) universal release APK, unsigned; R8 and resource shrinking | 2026-10-05 | None - build artifact | `0.1.0`, `TASK-120` | `PROJECT_LOG.md` LOG-0144; `apk-size.txt` report | Within budget; blocking in `check` (`DEC-148`) |
| `PERF-009` | Android | `:androidApp:verifyReleaseApkSize`; no test id | 2 354 210 B (2.25 MiB) universal release APK, unsigned; R8 and resource shrinking | 2026-10-05 | None - build artifact | `0.1.0`, `TASK-125` (after `TASK-121`…`TASK-124`) | `PROJECT_LOG.md` LOG-0149 | Within budget; blocking in `check` and in CI (`DEC-151`) |
| `PERF-009` | iOS | Not applicable | - | - | - | - | - | Not applicable - the deliverable is an APK (DEC-043) with no size budget on the iOS archive |

## 6. Levers the design already uses

These are the mechanisms that make the budgets above plausible. Each is owned elsewhere; this section only points at the owner and never restates the rule.

| Lever | Supports | Owner |
| --- | --- | --- |
| Incremental paging - the first screen never waits for the whole catalogue, and pagination stops at `info.next == null` | `PERF-003` | `REQ-FUNC-001`, `API_SPECS.md` §4.3, §8 |
| Cache-first render with an explicit freshness policy, so a repeat visit inside the fresh window renders without a network request | `PERF-006` | DEC-012, DEC-018, `API_SPECS.md` §7.3, `REQ-FUNC-020` |
| Prefetch of at most one page as the user approaches the end of the current page | `PERF-004` | `API_SPECS.md` §8 |
| Decode at the native source size with a crop scale, never upscaling a 300 px source into a larger allocation | `PERF-007`, `PERF-008` | `UI_SPEC.md` §5.1, §5.2, `CON-002`, `AC-REQ-FUNC-005-3` |
| Memoised portrait accent extraction, at most once per character per process, off the main thread | `PERF-004`, `PERF-008` | `UI_SPEC.md` §5.4, `DESIGN.md` §4.4 |
| No bitmap in the JSON response cache: image bytes live only in the image loader's memory and disk caches | `PERF-007`, `PERF-008` | `REQ-FUNC-021-2`, `API_SPECS.md` §7.4, DEC-026 |
| Reuse of list data for the detail header, which removes the network from the first detail frame | `PERF-005` | `API_SPECS.md` §8, `DESIGN.md` §4.2 |
| Request deduplication and bounded concurrency for enrichment (two concurrent remote calls) | `PERF-003`, `PERF-005` | `REQ-REL-002`, `API_SPECS.md` §8 |

## 7. Regression policy

### 7.1 What fails a build

| Check | Blocking? | Rule |
| --- | --- | --- |
| Deterministic performance assertions: the zero-request half of `PERF-006`, the per-bitmap budget of `PERF-007`, the delayed-network header assertion of `PERF-005` | Yes | These are ordinary tests in the required PR suite (DEC-054). A failure blocks the pull request. |
| Macrobenchmark thresholds, once the harness exists and the thresholds are configured (`PERF-001`…`005`, `PERF-008`) | Not in the pull-request gate today | Benchmarks need a physical device with the `benchmark` build type and produce device-dependent numbers, so they run as the scheduled non-blocking measurement job described in [`TESTING.md`](TESTING.md) §10 and §14.3 and are evaluated at milestone and release review; they are not part of the enumerated required checks of DEC-054. If a device runner is provisioned and the checks are added to the required set, DEC-054 makes a failure blocking like any other required check. |
| `PERF-009` APK size | Yes | `:androidApp:verifyReleaseApkSize` runs in `check` (`DEC-148`) and, since `TASK-125`, in CI's `app-artifacts` job, so both the local gate and the `android` required check fail on a release APK over 12 MiB (`DEC-151`). It is a non-timing check over the build artifact and needs no device. |
| iOS manual budgets | No | Manual by construction; recorded at release time and reviewed by the QA & Validation Engineer. |
| Benchmark threshold failure caused only by device thermal state, a background workload, or a network profile outside `NET-PRIMARY` | n/a | The run is invalid, not a regression: it is discarded and repeated under the device-state rules of §2.1. The discarded run `MUST` be named in the `PROJECT_LOG.md` entry. |

A budget regression `MUST` be reported as a regression, not reclassified as noise, unless a repeated run under §2.1 conditions shows the change is inside the recorded run-to-run spread.

### 7.2 What is reviewed manually

- The iOS numbers of every budget, and the iOS procedure itself (assumption A-PERF-4).
- Thermal state, network profile and the recorded run-to-run spread for every Android number.
- The frame-time tail: the p95 and the "no frame above 3 × budget" clause, because a good p50 with a janky tail violates `AC-REQ-NFR-003-2` in practice.
- The image-cache footprint and the memory growth check, which are profiler readings rather than assertions.
- Any new UI surface added to a budgeted screen: adding content to Discovery or Detail invalidates the budgets until the new state is re-measured, and the release checklist treats a missing re-measurement as a gap.

### 7.3 Changing a budget

1. A budget change is a decision: it needs a new `DEC-###` row in [`DECISION_BOARD.md`](DECISION_BOARD.md) and a `PROJECT_LOG.md` entry, plus a row in this file's change log.
2. The proposal `MUST` state: the current number, the measured evidence that makes it unattainable or wrong, the proposed number, the user-visible or delivery consequence, and whether a design lever in §6 will be used instead.
3. A number that is also an acceptance criterion (`AC-REQ-NFR-003-1` cold start ≤ 2.0 s) is owned by [`REQUIREMENTS.md`](REQUIREMENTS.md) and `MUST` be changed there in the same change; this file follows, it does not lead.
4. A budget `MUST NOT` be relaxed by editing a measurement, by measuring on a different device profile, by dropping iterations, or by omitting the failed platform. Those are documentation defects, not budget changes.
5. Adding a new `PERF-###` row follows the same path: new row, new decision or an amendment to this file approved in a pull request, a measurement method, a tool and a register entry. A budget without a measurement method and a tool is not a budget.

## 8. Open items

| ID | Item | Owner | Consequence if unresolved |
| --- | --- | --- | --- |
| A-PERF-1 | The exact reference device model is unassigned; §2.1 defines only the required profile. | Delivery Planner | No budget may be reported as met, and results from different devices are not comparable. |
| A-PERF-2 | **Resolved 2026-10-05 (`TASK-120`, `DEC-148`).** The APK size budget (`PERF-009`) was derived from the pinned dependency set (Compose BOM 2026.09.00, Ktor 3.6.0, Coil 3.6.3, Koin 4.2.2) and never measured. It is now measured on every `check`: 12.37 MiB unshrunk, 2.23 MiB with R8 and resource shrinking (§5). | Implementation Engineer | None: the budget is checked against the artifact that ships. |
| A-PERF-3 | The live API's latency under `NET-PRIMARY` has not been measured with the app, because no app exists. | QA & Validation Engineer | `PERF-001`/`003` results carry the recorded profile with them; they are not portable to another network. |
| A-PERF-4 | The iOS manual procedure is unverified until the iOS milestone starts. | QA & Validation Engineer | iOS budgets stay unmeasured; they are not waived. |
| PERF-Q1 | Macrobenchmark needs a `com.android.test` harness module, which the current module list (DEC-052) forbids; a decision is required before the Gradle module is created. | System Architect | Android budget automation is deferred and manual measurement is the interim method (§4.1). |

## 9. Change log

| Date | Change | Decision |
| --- | --- | --- |
| 2026-10-06 | `TASK-128`: an indicative emulator scroll row (`PERF-004`, Android) before and after the accent change. | `DEC-155`, `LOG-0153` |
| 2026-09-29 | Created: scope and constraints, reference device profile, `PERF-001`…`PERF-009` budgets with method/tool/recording, Android Macrobenchmark harness plan, iOS manual procedure, result register, design levers, regression and budget-change policy, open items. | DEC-033, DEC-027, DEC-038, DEC-052, DEC-054 |
| 2026-09-29 | Added a `Verification` column to the budget table and a `Test id producing the value` column to the result register, so every row names the test or method that produces its number; aligned the regression policy with the measurement job described in `TESTING.md` §10/§14.3; `PERF-Q1` records the missing harness-module decision. | DEC-052, DEC-054 |
| 2026-10-04 | B6 Phase 6.2: §5 records the zero-request half of `PERF-006` (`TEST-PERF-003`, passing in the blocking gate) as a new row, and the status line and §5 state why every device budget stays unmeasured now that the app runs: no reference device (A-PERF-1) and no harness decision (PERF-Q1). | `DEC-115`, `TASK-049`, `LOG-0127` |
| 2026-10-05 | `TASK-120`: `PERF-009` is measured by `:androidApp:verifyReleaseApkSize` and blocking in `check`; §5 records the unshrunk 12.37 MiB and the shrunk 2.23 MiB, §7.1 makes the check blocking in the local gate (CI pending `CONF-92`), and A-PERF-2 is resolved. | `DEC-148`, `GAP-034`, `LOG-0144` |
| 2026-10-05 | `TASK-125`: `PERF-009` runs in CI's `app-artifacts` job, so §7.1 makes it blocking there too; §5 records 2.25 MiB at that head. | `DEC-151`, `CONF-92`, `LOG-0149` |
