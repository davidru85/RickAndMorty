# OBSERVABILITY.md — Logging Contract, Redaction and Debug Diagnostics

- **Status:** Active — the contract, its validating implementation, the request, retry, coalescing and pager events, the favourites events `LOG-018`/`LOG-019` (B3), and the **Android** platform sink and visible panel exist (B4 Phase 4.2, `TASK-044`); the iOS sink and panel (`TASK-051`) and the cache, image, app-start and screen events are target state (see `DOCUMENTATION_AUDIT.md` §5)
- **Last verified:** 2026-10-05
- **Owner:** Security Reviewer (see `../AGENTS.md` §3.7) — this document decides *what may be recorded*
- **Authoritative for:** the shared logging contract (levels, permitted fields, prohibited fields), the `LOG-###` structured event catalogue, redaction rules and their enforcement, the debug-only diagnostics surface, and the metrics vocabulary produced by the logging contract.
- **Not authoritative for:** the failure→state→copy chain (`ERROR_FLOW.md`), the remote contract and its cache policy (`API_SPECS.md` §6–§7, §9), the security policy and prohibitions (`SECURITY.md` §7), the test strategy and test ids (`TESTING.md`), the gate (`DEFINITION.md`), requirements (`REQUIREMENTS.md`).
- **Inputs:** [`REQUIREMENTS.md`](REQUIREMENTS.md) §11 (`REQ-OBS-001`…`REQ-OBS-003`) and §10 (`REQ-SEC-005`), [`API_SPECS.md`](API_SPECS.md) §9 and §6 (`ApiFailure`, `DataResult`, `DataSource`), [`SECURITY.md`](SECURITY.md) §7, [`DESIGN.md`](DESIGN.md) §3–§7, [`DECISION_BOARD.md`](DECISION_BOARD.md) (DEC-038, DEC-039, DEC-052, DEC-053, DEC-054)
- **Normative terms:** `MUST` mandatory · `SHOULD` strong recommendation · `MAY` optional.

> `SECURITY.md` is authoritative for policy and prohibitions; this document is authoritative for what may be recorded and how. Requirements are owned by `REQUIREMENTS.md` and are referenced here by ID, never restated.

The contract is implemented once and consumed by both platforms. The implementing role is the Implementation Engineer (`../AGENTS.md` §3.5). **Placement (amended 2026-10-02 by `DEC-087`, [ADR-0013](adr/0013-observability-placement.md)):** the one contract (`AppLogger`, `LogLevel`, the closed `LogEvent` catalogue; `CONTRACTS.md` `IC-024`) is declared in `:core:domain`, so `:core:data`, `:core:presentation` and the feature state holders all reach the same interface without a data↔presentation edge; the validating, redacting implementation lives in `:core:data` behind an injected `LogSink`; and each app shell provides only the platform sink. The debug-only diagnostic surface of §5 reads the validated records from the separate `:core:diagnostics` module (`DEC-088`). The earlier sentence that split the logger between `:core:data` and `:core:presentation` is superseded: it required either two interfaces, contradicting §2.1, or an edge ADR-0001 forbids (`CONF-67`).

## 1. Purpose, scope and the absence of analytics

This document replaces the proposed `ANALYTICS.md`. That file is **not created** (`README.md` §12), because there is no analytics to document.

Explicit statements of scope:

- **No analytics, tracking, advertising, attribution or telemetry SDK is included, and none may be added** (`REQ-OBS-003`, `NG-006`, DEC-038). The dependency graph `MUST NOT` contain such an artifact; `TEST-UNIT-034` asserts it (`AC-REQ-OBS-003-1`).
- **No `EVT-###` event namespace exists.** DEC-038 dropped it, and this document does not reintroduce it. There is no event-schema registry, no event name taxonomy, no funnel, session, user-property or conversion model, and no remote ingestion endpoint. Nothing in this repository defines a "send to the backend" path, because there is no backend.
- **No identifier that could join a user across sessions or devices exists in this app**: no account id, no device id, no advertising id, no installation id, no fingerprint, no persistent client id. A correlation id (§2) is process-local and request-scoped.
- **No log leaves the device.** There is no remote sink, no collector, no crash reporter and no upload path (`SECURITY.md` §7).
- **Nothing is persisted.** The app writes no log file, no log database and no log buffer beyond what the platform's own sink retains, which is outside the app's control.

The identifiers in this document therefore describe **local diagnostics only**. They are not an analytics contract and MUST NOT be treated as one.

## 2. The shared logging contract

### 2.1 One interface

Both platforms `MUST` log through a single shared abstraction — one interface with a level set and a fixed field envelope. Platform code `MUST NOT` call `android.util.Log`, `os.Logger`/`print`, or a platform crash reporter directly for app diagnostics; the app shells provide an implementation of the shared interface that writes to the platform sink (`Logcat` on Android, the unified log through `NSLog` on iOS). `TEST-UNIT-032` asserts that both platforms use the contract with permitted fields only (`AC-REQ-OBS-001-1`).

```kotlin
// :core:domain — shape only; the canonical signatures are CONTRACTS.md IC-024 (DEC-087, DEC-093)
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface AppLogger {
    fun isEnabled(level: LogLevel): Boolean
    fun log(event: LogEvent)              // at the event's catalogue level
}

/** One data class per §3 row that has an emitter, carrying exactly that row's fields as closed types. */
sealed interface LogEvent {
    val catalogueId: String               // "LOG-001" … "LOG-022"
    val level: LogLevel
}

fun interface LogSink { fun write(record: LogRecord) }   // records carry validated §2.2 fields only
```

Rules for the interface itself:

- It `MUST` be callable from any dispatcher and `MUST NOT` block the caller (§7).
- It `MUST NOT` accept a free-form string message from a feature; a feature selects an event from the catalogue and supplies only permitted fields.
- It `MUST NOT` accept a `Throwable` at release level. Debug builds `MAY` accept an exception **type** (`errorClass`, `cause`), never a stack trace at release level.
- Levels mean exactly: `DEBUG` — debug-build detail; `INFO` — a normal lifecycle or cache event; `WARN` — a degraded but handled condition; `ERROR` — a user-visible failure. A `LogLevel` mapping `MUST NOT` be inferred from the exception type alone.
- Release builds emit `ERROR` only (DEC-039); `DEBUG`/`INFO`/`WARN` calls are compiled or filtered out, not merely dropped at runtime.

**Per-platform threshold and line (`TASK-116`, `DEC-127`).**

- **Android.** The debug variant's Application binds `forDebug` over `LogcatSink`, and the release variant `forRelease`.
- **iOS.** The graph binds `forDebug` in the debug Kotlin framework a Debug build links, and `forRelease` in the release framework. The choice reads `Platform.isDebugBinary`, a property of the compiled binary, so rule 7 of §4.1 holds: nothing at runtime can lower a release build's level.
- **Swift.** Swift code reaches the same contract through `MultiverseBootstrap.logger()`.
- **The line.** Both sinks print one line, `LOG-### name=value …`, with the §2.2 wire names in the catalogue's field order. The iOS sink prefixes the level, because the unified log has no level column, and writes through `NSLog` with a single `%s` argument (a `%@` crashed on the first record, `TEST-UNIT-074`). In a debug build, `protocol=REST` and `protocol=GRAPHQL` therefore find the requests of each protocol in Logcat and in the unified log alike (`TEST-UNIT-072`, `TEST-UNIT-073`, `TEST-UNIT-075`).

### 2.2 Permitted field list

This is the canonical permitted list (`AC-REQ-OBS-001-1`). A field outside it is prohibited by default: adding one is a change to this document first.

| Field | Type | Allowed values / form | Never contains |
| --- | --- | --- | --- |
| `level` | enum | `DEBUG`, `INFO`, `WARN`, `ERROR` | — |
| `protocol` | enum | `REST`, `GRAPHQL` | — |
| `operation` | enum | named operation: `CHARACTER_LIST`, `CHARACTER_DETAIL`, `EPISODE_BATCH` | A request body, a GraphQL document, a raw URL |
| `pathTemplate` | string | a compile-time constant template: `/character`, `/character/{id}`, `/episode/{ids}` | Any interpolated id, page number or query string |
| `page` | int? | the numeric page index | — |
| `filterNames` | sorted set of names | from the allow-list `name`, `status` | Filter **values**, including the search text and the selected status |
| `statusFamily` | enum | `2XX`, `4XX`, `5XX`, `NO_RESPONSE` | The exact status code, the status message, the response body |
| `cacheSource` | enum | `NETWORK`, `MEMORY_CACHE`, `DISK_CACHE`, `NONE` | Any cache key, path or file size |
| `isStale` | bool | — | — |
| `durationMs` | long? | monotonic elapsed milliseconds | A wall-clock timestamp |
| `correlationId` | string? | client-generated, request-scoped (§4.1 rule 5) | Anything derived from user input |
| `outcome` | enum | `SUCCESS`, `EMPTY`, `FAILURE`, `CANCELLED` | — |
| `errorClass` | enum | one of `OFFLINE`, `TIMEOUT`, `NOT_FOUND`, `INVALID_REQUEST`, `RATE_LIMITED`, `SERVER`, `MALFORMED_RESPONSE`, `EMPTY_BODY`, `UNKNOWN` — the REST families of `ApiFailure` (`API_SPECS.md` §6.1) | The failure message, the exception's text, a stack trace |
| `screen` | enum | `SPLASH`, `DISCOVERY`, `CHARACTER_DETAIL`, `FAVORITES`, `EPISODES`, `SETTINGS` | A route string carrying an id |
| `component` | enum | `RESPONSE_CACHE`, `IMAGE_CACHE`, `FAVORITES_STORE`, `PAGER` | A store path or key |
| `retryAfterSeconds` | long? | server-advised delay, already a number | The `Retry-After` header's raw text |
| `appVersion`, `platform`, `buildType` | string | build constants (`VERSION`, `android`/`ios`, `debug`/`release`) | — |
| `cause` | enum | **debug builds only**: the exception **type** | The message, the payload, a stack trace |

### 2.3 Prohibited list

The following `MUST NOT` reach any sink, on any platform, at any level, in any build type, including the debug diagnostics surface (`REQ-SEC-005`, DEC-039):

- Search text or any part of it, in any encoding, including a hash, prefix, length or word count.
- Filter values (the selected `status`, species, gender or type).
- A raw query string or a full URL with parameters.
- Response bodies, JSON fragments, or any field value decoded from a response.
- Image bytes, bitmap data, colour-extraction pixel data, or a base64 rendering of any of these.
- Stack traces at release level; exception messages at release level.
- API keys, tokens or credentials (none exist — `SECURITY.md` §4).
- Anything read from the favourites store beyond an aggregate count.
- Any identifier that could join a user across sessions or devices (§1).

The rationale for the prohibitions is owned by `SECURITY.md` §7.2: a value that reaches a sink leaves the app's control and cannot be retracted.

## 3. Structured event catalogue

Every event has a stable `LOG-###` id. The catalogue is closed: a feature `MUST NOT` emit an event that is not in this table without first extending it.

Legend for the **Visibility** column: `D` = debug builds only, `R` = present in release builds (errors only, DEC-039).

| Id | Event | Level | Fields | Where emitted | Visibility |
| --- | --- | --- | --- | --- | --- |
| `LOG-001` | Request started | `DEBUG` | `operation`, `pathTemplate`, `page`, `filterNames`, `protocol`, `correlationId` | `:core:data`, HTTP client layer | D |
| `LOG-002` | Request completed | `INFO` | `operation`, `pathTemplate`, `page`, `statusFamily`, `durationMs`, `correlationId`, `outcome` | `:core:data`, HTTP client layer | D |
| `LOG-003` | Request failed | `ERROR` | `operation`, `pathTemplate`, `page`, `statusFamily`, `errorClass`, `durationMs`, `correlationId`, `outcome=FAILURE` | `:core:data`, failure mapper before repository return | R |
| `LOG-004` | Foreign host rejected | `ERROR` | `operation`, `errorClass=INVALID_REQUEST`, `screen`, `correlationId` | `:core:data`, URL allow-list guard (`SECURITY.md` §5.1) | R |
| `LOG-005` | Cache hit | `DEBUG` | `cacheSource` (`MEMORY_CACHE` or `DISK_CACHE`), `operation`, `page`, `isStale`, `correlationId` | `:core:data`, response cache read (`TASK-020`) | D |
| `LOG-006` | Cache miss | `DEBUG` | `cacheSource=NONE`, `operation`, `page`, `correlationId` | `:core:data`, response cache read (`TASK-020`) | D |
| `LOG-007` | Stale fallback served | `WARN` | `cacheSource=DISK_CACHE`, `isStale=true`, `operation`, `page`, `errorClass`, `correlationId` | `:core:data`, cache read when a fetch fails with data available (`TASK-020`) | D |
| `LOG-008` | Cache write skipped | `DEBUG` | `component=RESPONSE_CACHE`, `outcome`, `errorClass`, `correlationId` | `:core:data`, cache write guard (`AC-REQ-FUNC-020-3`, `TASK-020`) | D |
| `LOG-009` | Cache entry discarded | `WARN` | `component=RESPONSE_CACHE`, `errorClass=MALFORMED_RESPONSE`, `correlationId` | `:core:data`, cache decode guard (`TASK-020`) | D |
| `LOG-010` | Page loaded | `DEBUG` | `operation=CHARACTER_LIST`, `page`, `outcome`, `durationMs`, `cacheSource`, `correlationId` | `:core:data` shared pager (`IC-014`, `DEC-091`), for a load it publishes | D |
| `LOG-011` | Pagination exhausted | `DEBUG` | `operation=CHARACTER_LIST`, `page`, `outcome=SUCCESS` | `:core:data` shared pager, when `info.next == null` or a paging `404` ends the list | D |
| `LOG-012` | Duplicate request deduplicated | `DEBUG` | `operation`, `page`, `filterNames`, `correlationId` | `:core:data`, single-flight/dedupe guard (`REQ-REL-002`) | D |
| `LOG-013` | Retry scheduled | `WARN` | `operation`, `errorClass`, `statusFamily`, `retryAfterSeconds`, `correlationId` | `:core:data`, retry policy (`REQ-REL-003`) | D |
| `LOG-014` | Request cancelled | `DEBUG` | `operation`, `outcome=CANCELLED`, `correlationId` | `:core:data`, cancellation path | D |
| `LOG-015` | Image request started | `DEBUG` | `component=IMAGE_CACHE`, `screen`, `cacheSource` | Android image-loader interceptor / iOS image-cache wrapper | D |
| `LOG-016` | Image cache outcome | `DEBUG` | `component=IMAGE_CACHE`, `cacheSource`, `durationMs`, `errorClass` | Android image-loader interceptor / iOS image-cache wrapper | D |
| `LOG-017` | Image load failed | `WARN` | `component=IMAGE_CACHE`, `errorClass`, `screen` | Android image-loader error path / iOS image-cache error path | D |
| `LOG-018` | Favorites toggled | `INFO` | `component=FAVORITES_STORE`, `outcome` | `:core:data`, the favourites repository after a successful toggle write | D |
| `LOG-019` | Favorites store degraded | `ERROR` | `component=FAVORITES_STORE`, `errorClass=UNKNOWN`, `screen` | `:core:data`: the favourites repository for a write the store could not complete, and each platform store for a value it could not read (`SECURITY.md` §6.3) | R |
| `LOG-020` | App start | `INFO` | `appVersion`, `platform`, `buildType` | app shell (`:androidApp` `Application` / iOS app entry point) | D |
| `LOG-021` | Screen data source resolved | `DEBUG` | `screen`, `cacheSource`, `isStale`, `outcome` | feature state holders | D |
| `LOG-022` | Unknown remote enum value preserved | `DEBUG` | `operation`, `pathTemplate`, `outcome=SUCCESS` | `:core:data`, tolerant mapper (`AC-REQ-NFR-004-2`) | D |

Rules:

1. An event `MUST NOT` carry a field that is not listed for it. `LOG-003` carries `errorClass`; `LOG-002` explicitly does not, because a completed request is not a failure.
2. `LOG-018` carries **no id**. The favourite character id is user-adjacent state and is not needed to diagnose the store; the aggregate count is available to the debug surface instead (§5).
3. The three `R` rows are the complete release-visible set. A new release-visible event is a change to `SECURITY.md`'s obligations and to this table in the same change.
4. The catalogue describes diagnostics, not product analytics (§1). No row exists for "user opened a screen", "search performed" or "session started".

## 4. Redaction rules and enforcement

### 4.1 The rules

1. **A query string never reaches a sink.** No log call, no diagnostics surface and no error message may contain a request URL with parameters, a query string, or any value that was an input to building one (`REQ-SEC-005`).
2. Redaction happens **at the call site and at the boundary**: the shared logger `MUST` reject-and-drop a field value that fails its allow-list validation rather than logging it, and `MUST NOT` rely on a sink-side filter in the platform. A dropped field `MUST` be counted internally, not printed.
3. Paths are logged as **templates**, never as interpolated URLs. `/character/{id}` is permitted; `/character/1` is not.
4. Filter names are logged as **names only**, from the fixed allow-list in §2.2, and never alongside their values.
5. The `correlationId` is generated client-side, is request-scoped, is not persisted and `MUST NOT` be derived from user input (`SECURITY.md` §7.3).
6. Failure handling logs the **failure type**, not the failure's message. Server-supplied human-readable text is not logged and not treated as control flow (`API_SPECS.md` §6.1).
7. Release builds apply a hard level filter of `ERROR` **and** the §2.3 prohibitions independently. Lowering the level `MUST NOT` be possible at runtime, from a build flag, from a remote configuration or from a debug menu in release.

### 4.2 Enforcement

| Rule | Enforced by |
| --- | --- |
| A query string never reaches a log sink (`AC-REQ-SEC-005-1`) | `TEST-UNIT-029` — `LogRedactionTest` in `:core:data`, on the real search path at `DEBUG`, on the Android host and Apple targets |
| Both platforms use the one contract with permitted fields only (`AC-REQ-OBS-001-1`) | `TEST-UNIT-032` — `ValidatingAppLoggerTest` and `RequestPathLoggingTest` in `:core:data`, run on both platform targets; the platform sinks join when the shells exist (`TASK-044`, `TASK-051`) |
| Release builds emit errors only and contain no debug surface (`AC-REQ-OBS-002-1`) | `TEST-UNIT-033` — the release logger in `:core:data`, `DiagnosticsBoundaryTest` (`R11` debug-only edge and release closure, `R18`) in `build-logic`, and the diagnostic API's own cases in `:core:diagnostics` |
| No analytics artifact in the dependency graph (`AC-REQ-OBS-003-1`) | `TEST-UNIT-034` — `verifyNoAnalytics` in `verifyDependencyPolicy`: the catalog's libraries and plugins, and the resolved `releaseRuntimeClasspath` of `:androidApp`. The iOS framework's graph joins it when `:core:ios` exists (`TASK-078`) |
| A new field or event exists in this document | Review; `SECURITY.md` §7.1 |

`TEST-UNIT-029` `MUST` exercise the real path — a search request carrying a distinctive query value — and assert that the value appears in no captured sink record, including `DEBUG`-level records. A test that only inspects the contract type is not sufficient evidence.

Because `TEST-UNIT-029` and `TEST-UNIT-032` guard a behaviour change, they are written first and observed failing before the logging implementation exists (DEC-053, `DEFINITION.md` Done gate); the merge gate evaluates the final pull-request state, not the red commit (DEC-054).

## 5. Debug-only diagnostics surface

`REQ-OBS-002` requires a diagnostics surface in debug builds that exposes the last failure and the current data source. This section defines its content and its gating.

**Staging and hosting (`DEC-085`, `DEC-088`).** The surface is delivered in three steps. B3 (`TASK-047`, Phase 3.2) delivers the read-only diagnostic **API** in `:core:diagnostics`, proved on real request and pager paths; values that later integrations own (cache counts, the favourites count, the screen) are reported as unavailable, never as an invented zero or a fabricated screen. B4 (`TASK-044`) renders the visible Android panel from a debug-only source set of `:androidApp` — the panel's activity and its Application subclass are declared by `src/debug/AndroidManifest.xml`, and `verifyReleaseArtifact` (`TEST-UNIT-033`) proves both are absent from the real release APK — which is the only Android configuration that declares `:core:diagnostics`. The panel is **not** a launcher entry: the launcher shows the app's one icon, which opens the shell at its splash, and a debug build reaches the panel through a static app shortcut on that icon (long-press) or by its component name, both declared by the same debug overlay so a release APK carries neither (`TASK-117`). On iOS (`TASK-119`, `DEC-147`) `:core:ios` links the module without exporting it, and only the debug Kotlin binary creates the recorder and attaches it to the logger, so the release framework records nothing. A read-only sheet, opened by a toolbar button on the Settings tab, lists the rows `:core:diagnostics` renders for both platforms (`DiagnosticsSnapshot.rows()`). The sheet and its entry compile under `#if DEBUG` only, and a Release run proves the observation absent (`TEST-UI-027`).

**What the API reports (B3 Phase 3.2).** `DiagnosticsRecorder` is a `LogSink`, so it folds only records that passed the validator. From the events this build emits it reports the last failure's `errorClass` (`LOG-003`/`LOG-004`), the current data source of the last published page (`LOG-010`), the last request's `durationMs` and `statusFamily` (`LOG-002`/`LOG-003`), and the pager's page and whether a next page exists (`LOG-010`/`LOG-011`). Every other item is `Unavailable`, naming what delivers it: the failure's screen and the stale indicator (the screen state holders, `LOG-021`), cache state (`TASK-020`), the favourites count (`TASK-040` with the panel host of `TASK-044`) and the build envelope (the shells, `LOG-020`). Whether an append is in flight is carried by no event; the panel host reads it from the pager state it already observes (`TASK-044`), or the catalogue gains a field first.

| Item | Shown | Format |
| --- | --- | --- |
| Last failure | the `ApiFailure` type (`errorClass`) and the screen it occurred on | enum name, no message |
| Current data source | `NETWORK`, `MEMORY_CACHE`, `DISK_CACHE` or none yet | enum name |
| Last request timing | `durationMs` of the most recent request, plus `statusFamily` | number + enum |
| Cache state | per-operation entry count, fresh/stale counts, whether an entry exists for the current screen | aggregate numbers only |
| Stale indicator | whether the currently displayed content is stale | boolean |
| Pager state | current page, whether a next page exists, whether an append is in flight | numbers/booleans |
| Favourites store | aggregate count only | number |
| Build envelope | `appVersion`, `platform`, `buildType` | build constants |

Rules:

- **Gating.** The surface is compiled from a debug-only source set / platform build configuration and `MUST NOT` be reachable in a release artifact. It `MUST NOT` be a runtime flag, an intent extra, a hidden gesture or a build-config boolean that ships in release. `TEST-UNIT-033` asserts its absence from the release variant (`AC-REQ-OBS-002-1`).
- **Redaction applies in full.** The surface may present only the fields in §2.2 plus the aggregates above. It `MUST NOT` show filter values, search text, response content, image data, cache keys or store paths.
- **No export.** The surface `MUST NOT` offer copy-to-clipboard, share, file export or "send diagnostics". Screenshots of a debug build are the developer's responsibility.
- **Acceptance criterion.** `AC-REQ-OBS-002-1`: the surface is absent from release builds. `REQ-OBS-002` additionally requires it to expose the last failure and the current data source for the current screen in a debug build, which the items above provide.
- The surface `MUST NOT` change any behaviour: it is a read-only view over state the app already holds. Toggling it `MUST NOT` trigger a request.

## 6. Metrics vocabulary

`API_SPECS.md` §9 requires the diagnostics to distinguish **network, memory cache, disk cache, stale fallback, empty result, timeout, decoding failure and cancellation**. That list is produced by the logging contract, not by a separate metrics system — there is no metrics SDK and no metrics endpoint (§1). Each item maps to the event that carries it:

| `API_SPECS.md` §9 item | Carrier | Discrimination |
| --- | --- | --- |
| Network | `LOG-002`, `LOG-003`, `LOG-010` | `cacheSource=NETWORK` |
| Memory cache | `LOG-005` | `cacheSource=MEMORY_CACHE` |
| Disk cache | `LOG-005`, `LOG-007` | `cacheSource=DISK_CACHE` |
| Stale fallback | `LOG-007` | `isStale=true` with `errorClass` set |
| Empty result | `LOG-002`, `LOG-010` | `outcome=EMPTY` (REST filtered `404` maps here, not to `FAILURE` — `ERROR_FLOW.md`) |
| Timeout | `LOG-003`, `LOG-013` | `errorClass=TIMEOUT` |
| Decoding failure | `LOG-003`, `LOG-009` | `errorClass=MALFORMED_RESPONSE` |
| Cancellation | `LOG-014` | `outcome=CANCELLED` |

Rules and honest limits:

- Counting is done by aggregating local events in a debug build or by a test harness; the app `MUST NOT` ship a metrics counter, an aggregation buffer, a timer or a sampling mechanism. Any number a developer reports is derived from the events above, and `PERFORMANCE.md` owns the measured performance budgets (`REQ-NFR-003`).
- `durationMs` is elapsed monotonic time around the operation it labels — a request duration for `LOG-002`/`LOG-003`, a decode or cache-write duration where labelled. It `MUST NOT` be used as a performance budget figure; budgets are measured per `PERFORMANCE.md`.
- A metric that cannot be derived from this table is a gap in this document: extend the table (and the catalogue if needed) rather than instrumenting privately.
- Rate-limit signals are represented by `errorClass=RATE_LIMITED` plus `retryAfterSeconds`; no numeric quota is assumed, because none is documented (`API_SPECS.md` §9).

## 7. Failure handling of observability itself

Observability `MUST NOT` become a failure source.

1. **Never throw.** A log call `MUST NOT` throw into its caller. A validation failure, a serialisation failure or a sink failure in the logger is swallowed and, at most, counted internally. Logging is never a reason a feature fails.
2. **Never block the UI.** Log calls `MUST` be non-blocking and `MUST NOT` perform I/O on the main thread. A sink that cannot keep up drops records rather than applying backpressure to the caller.
3. **Never allocate avoidably.** Event payloads `MUST` be constructed only when the level is enabled; a disabled `DEBUG` event must not build strings, format durations or interpolate a template.
4. **Drop, do not grow.** There is no in-memory log queue with unbounded growth, no log file and no retry of a failed emit. If the sink is unavailable, records are lost, and that is acceptable (§1).
5. **Release builds log errors only** (DEC-039). The release threshold is fixed at compile time and `MUST NOT` be changeable at runtime.
6. **A logging defect is a defect.** A call that violates §2.3 is a review blocker, and the fix is removal at the call site — never a wider sink filter.

## 8. Traceability

| Requirement / acceptance criterion | Section here | Test |
| --- | --- | --- |
| `REQ-OBS-001` / `AC-REQ-OBS-001-1` | §2 | `TEST-UNIT-032` |
| `REQ-OBS-002` / `AC-REQ-OBS-002-1` | §5 | `TEST-UNIT-033` |
| `REQ-OBS-003` / `AC-REQ-OBS-003-1` | §1 | `TEST-UNIT-034` |
| `REQ-SEC-005` / `AC-REQ-SEC-005-1` | §4 | `TEST-UNIT-029` |
| `API_SPECS.md` §9 metrics list | §6 | `TEST-UNIT-032` (field contract), `TEST-UNIT-029` (redaction) |
| DEC-039 release-level rule | §2.1, §7 | `TEST-UNIT-033` |

## 9. Change log

| Date | Change | Reference |
| --- | --- | --- |
| 2026-10-05 | §2.1: each platform's threshold and log line stated. iOS now logs every level in a Debug build, through the binary variant, and the sinks share one line with the catalogue's wire names. The iOS sink no longer crashes on a record (it did on the first `ERROR` in release). | `TASK-116`, `DEC-127`, `TEST-UNIT-072`…`075` |
| 2026-10-05 | §5: the Android panel is reached through a debug-only app shortcut on the one launcher icon, not through a second launcher entry; a debug install had shown two icons. | `TASK-117`, `TEST-UNIT-033`, `TEST-UNIT-057` |
| 2026-10-03 | B3 Phase 3.3 (`TASK-040`): `LOG-018` and `LOG-019` are emitted — the repository logs a written toggle and a write the store could not complete, and each platform store a value it could not read. | `TASK-040` |
| 2026-10-02 | B3 Phase 3.2 (`TASK-047`): the contract, its validating implementation and the emitters of `LOG-001`…`LOG-004`, `LOG-010`…`LOG-014` and `LOG-022` exist; §2.1's sketch follows `IC-024`; `LOG-010`/`LOG-011` are emitted by the `:core:data` pager, where `DEC-091` placed it; §4.2 names the implementing tests and records that the iOS framework graph joins `TEST-UNIT-034` with `:core:ios`; §5 states what the diagnostic API reports and what is unavailable. | `TASK-047`, `DEC-087`, `DEC-088`, `DEC-091` |
| 2026-09-29 | Created as the replacement for the never-created `ANALYTICS.md`: shared logging contract with permitted and prohibited fields, `LOG-001`…`LOG-022` catalogue, redaction enforcement, debug diagnostics surface and the metrics mapping for `API_SPECS.md` §9. No analytics SDK and no `EVT-###` namespace. | DEC-038, DEC-039, DEC-052, DEC-053, DEC-054 |
