# API_SPECS.md - REST and GraphQL Technical Specification

- **Status:** Active — target state (implementation not started; see `DOCUMENTATION_AUDIT.md` §5)
- **Last verified:** 2026-09-29
- **Owner:** API Architect (see `AGENTS.md`)
- **Authoritative for:** the remote data contract — endpoints, DTOs, failure taxonomy, retry, response and image caching policy, contract identifiers.
- **Not authoritative for:** architecture (`DESIGN.md`), internal Kotlin seams (`CONTRACTS.md`), failure-to-copy behaviour (`ERROR_FLOW.md`), UI (`UI_SPEC.md`).
- **Inputs:** [`REQUIREMENTS.md`](REQUIREMENTS.md), [`DESIGN.md`](DESIGN.md), [`adr/`](adr/)
- **Primary source:** [official API documentation](https://rickandmortyapi.com/documentation)
- **GraphQL schema source:** [official `typeDefs.js`](https://github.com/afuh/rick-and-morty-api/blob/master/graphql/typeDefs.js)

## 1. Purpose and scope

This document defines the remote data contract for the Multiverse Explorer client (Android and iOS, over the shared Kotlin Multiplatform core). It covers both public interfaces offered by The Rick and Morty API:

- REST: `https://rickandmortyapi.com/api`
- GraphQL: `https://rickandmortyapi.com/graphql`

Both APIs are read-only, use HTTPS, return JSON, and expose the same three resources: characters, locations, and episodes. No authentication contract is documented. The public API is unversioned, so automated contract tests are required.

The app requires:

- a paginated list of every character;
- a detail view for a selected character;
- search and filters;
- resilient error handling;
- response and image caching;
- efficient network usage.

Character operations are the MVP. Location and episode operations are documented because they can enrich the character detail, but they are not required to render the basic MVP.

### 1.1 Contract language

- **API guarantee** means behavior declared by the official documentation or schema.
- **Observed behavior** means behavior verified against the live service on the date above. It may change without notice.
- **App policy** means a client-side decision made for this project.

Current resource totals must not be hard-coded. They are data, not schema. The client must rely on pagination metadata.

## 2. Protocol decision

The production MVP should use **REST as its default remote source**. Its fixed payload already satisfies the list and basic detail, it maps cleanly to Android paging, and successful `GET` responses are cacheable by the standard HTTP stack.

GraphQL is a supported alternative when a screen needs a tailored or nested payload, such as character details plus episode names in one request. Documenting both protocols does not justify shipping two complete networking stacks. Select one implementation per build or replace the remote adapter behind the same repository interface. Do not mix REST and GraphQL opportunistically per screen because that duplicates cache entries, mapping, error handling, and tests.

| Concern | REST | GraphQL |
| --- | --- | --- |
| Endpoint | `/api/...` | `/graphql` |
| App request method | `GET` | `POST` with a JSON envelope |
| Payload | Fixed by endpoint | Selected fields only |
| Related data | URLs, followed explicitly or fetched in batches | Nested fields in one operation |
| Pagination link | Absolute `next`/`prev` URL | Nullable next/previous page numbers |
| Identifier representation | JSON number | GraphQL `ID`, decoded as `String` |
| No matching filtered results | Observed `404` with an error body | Observed `200`, empty `results`, nullable metadata |
| Standard Android HTTP cache | Directly applicable to `GET` | `POST` requires an application-level cache |
| Recommended use | MVP list, detail, filters | Optional enriched detail or protocol variant |

## 3. Shared domain contract

Network DTOs/generated GraphQL models belong in the data layer and must never be exposed to presentation code. Both remote adapters map to the same domain types.

```kotlin
@JvmInline value class CharacterId(val value: String)
@JvmInline value class LocationId(val value: String)
@JvmInline value class EpisodeId(val value: String)

data class CharacterPage(
    val characters: List<CharacterSummary>,
    val page: Int,
    val pageCount: Int?,
    val totalCount: Int?,
    val nextPage: Int?,
    val previousPage: Int?,
)

data class CharacterSummary(
    val id: CharacterId,
    val name: String,
    val status: CharacterStatus,
    val species: String,
    val type: String?,
    val gender: CharacterGender,
    val lastKnownLocation: LocationSummary,
    val imageUrl: String,
)

data class CharacterDetails(
    val id: CharacterId,
    val name: String,
    val status: CharacterStatus,
    val species: String,
    val type: String?,
    val gender: CharacterGender,
    val origin: LocationSummary,
    val lastKnownLocation: LocationSummary,
    val imageUrl: String,
    val episodeIds: List<EpisodeId>,
    val episodeSummaries: List<EpisodeSummary>?,
    val createdAt: Instant?,
)

data class LocationSummary(
    val id: LocationId?,
    val name: String,
    val type: String? = null,
    val dimension: String? = null,
)

data class EpisodeSummary(
    val id: EpisodeId,
    val name: String,
    val code: String,
    val airDate: String,
)
```

Use canonical string IDs in the domain because GraphQL `ID` is opaque. REST integer IDs are converted with `toString()`. Never infer an ID from array position.

`status` and `gender` arrive as strings in both protocols, not closed API enums. Map known values and preserve forward compatibility:

```kotlin
sealed interface CharacterStatus {
    data object Alive : CharacterStatus
    data object Dead : CharacterStatus
    data object Unknown : CharacterStatus
    data class Unsupported(val raw: String) : CharacterStatus
}
```

Apply the same pattern to gender. Treat an empty `type` as absent in the domain, but preserve the raw value in network fixtures. A missing/invalid date may map to `null`; it must not prevent the rest of the character from rendering.

`episodeSummaries == null` means enrichment was not requested; an empty list means enrichment completed and the character has no known appearances. REST always supplies `episodeIds` from URLs and fills summaries only after a batch episode request. The documented GraphQL detail operation supplies both in one response.

The repository returns the outcome as a sealed value rather than throwing: an expected remote failure is data, not control flow. This keeps errors visible to the compiler, avoids Kotlin exceptions crossing the Kotlin→Swift boundary for the iOS state holders (`DEC-013`), and leaves the data-source metadata available on both outcomes.

```kotlin
sealed interface DataResult<out T> {
    val source: DataSource
    val warnings: List<ApiWarning>

    data class Success<T>(
        val value: T,
        override val source: DataSource,
        val isStale: Boolean,                      // cached entry served past freshness
        override val warnings: List<ApiWarning> = emptyList(),
    ) : DataResult<T>

    data class Failure(
        val failure: ApiFailure,
        override val source: DataSource,           // the source that produced the failure, never a cache hit
        override val warnings: List<ApiWarning> = emptyList(),
    ) : DataResult<Nothing>
}

enum class DataSource { NETWORK, MEMORY_CACHE, DISK_CACHE }
data class ApiWarning(val code: String, val detail: String? = null)
```

Invariants: a `Failure` never carries a partial value; `isStale` exists on `Success` only; `CancellationException` is rethrown and never converted into a `Failure`; warnings never replace required data. The normative interface signatures are in [`CONTRACTS.md`](CONTRACTS.md).

## 4. REST API

### 4.1 Transport

- Base URL: `https://rickandmortyapi.com/api/`
- Method: every documented REST operation uses `GET`.
- Media type: `application/json; charset=utf-8` was observed.
- Root discovery: `GET /api` returns absolute URLs for `characters`, `locations`, and `episodes`.
- Authentication: none is documented or required by observed public calls.
- Versioning: no version segment or compatibility policy is published.

The app should configure the fixed base URL at build time rather than discovering it from `/api` on every launch.

### 4.2 Endpoint map

| Resource | List/filter | Single | Batch |
| --- | --- | --- | --- |
| Character | `GET /character` | `GET /character/{id}` | `GET /character/{id1},{id2},...` |
| Location | `GET /location` | `GET /location/{id}` | `GET /location/{id1},{id2},...` |
| Episode | `GET /episode` | `GET /episode/{id}` | `GET /episode/{id1},{id2},...` |

The official documentation also shows bracket notation for batches, for example `/character/[1,2,3]`. The app contract uses comma-separated IDs because it is simpler to encode and is explicitly supported.

Do not implement a single polymorphic batch decoder: a single-resource endpoint returns an object, while a multi-ID endpoint returns an array. Define separate service methods and response types.

REST batch routes must receive at least two IDs. For an empty request, return an empty list without a network call. For one ID, call the single-resource endpoint and wrap the object in a list. This also applies to the final chunk after splitting a large ID collection.

### 4.3 Pagination

List endpoints return up to 20 resources per page. The page size is controlled by the server and cannot be supplied by the client.

Request:

```http
GET /api/character?page=2
```

Response envelope:

```json
{
  "info": {
    "count": 826,
    "pages": 42,
    "next": "https://rickandmortyapi.com/api/character?page=3",
    "prev": "https://rickandmortyapi.com/api/character?page=1"
  },
  "results": []
}
```

Rules:

- Omitted `page` means page 1.
- `info.next == null` is the authoritative end-of-pagination signal.
- `info.prev == null` identifies the first page.
- `next` and `prev` are absolute URLs. Do not follow an arbitrary returned host; parse the page number and rebuild the request against the configured HTTPS base URL.
- Preserve active filters on every subsequent page.
- An out-of-range page currently returns `404`; map it to end-of-pagination only when reached through a valid paging sequence. A user-entered invalid page is a client error.

### 4.4 Character operations

#### List and filter characters

```http
GET /api/character?page=1&name=rick&status=alive
```

| Parameter | Type | Allowed/expected values | Notes |
| --- | --- | --- | --- |
| `page` | positive integer | `1..info.pages` | Server defaults to 1. |
| `name` | string | free text | Server-side partial matching. |
| `status` | string | `alive`, `dead`, `unknown` | UI must send canonical lowercase values. |
| `species` | string | free text | May be combined with other filters. |
| `type` | string | free text | May be combined with other filters. |
| `gender` | string | `female`, `male`, `genderless`, `unknown` | UI must send canonical lowercase values. |

Only include non-blank filters. URL-encode all values. A changed search/filter resets pagination to page 1.

#### Get a character

```http
GET /api/character/2
```

- `200`: one `RestCharacterDto`.
- `404`: observed body `{"error":"Character not found"}`; map to `NotFound(CharacterId)`.

#### Get characters by IDs

```http
GET /api/character/1,2,3
```

Returns a JSON array of characters when at least two IDs are supplied. Use this only when the UI already has a bounded ID set. Chunk large collections to avoid excessive URL lengths; a maximum chunk of 20 IDs is the app policy, with a singleton final chunk routed through the single-character endpoint.

### 4.5 Location operations

Filters for `GET /location`:

| Parameter | Type | Description |
| --- | --- | --- |
| `page` | positive integer | Page number. |
| `name` | string | Location name filter. |
| `type` | string | Location type filter. |
| `dimension` | string | Dimension filter. |

Single and batch operations follow the endpoint shapes in section 4.2. Location retrieval is optional for the MVP because each REST character already includes origin and last-known-location names.

### 4.6 Episode operations

Filters for `GET /episode`:

| Parameter | Type | Description |
| --- | --- | --- |
| `page` | positive integer | Page number. |
| `name` | string | Episode name filter. |
| `episode` | string | Episode code filter, for example `S01E01` or `S01`. |

Single and batch operations follow section 4.2. For a detail screen that needs episode names, extract IDs from the character's episode URLs and use a batch request. Do not issue one request per episode.

### 4.7 REST DTOs

The following Kotlin-shaped definitions are normative for serialization. Names may vary by serializer, but JSON field names and nullability must remain explicit.

```kotlin
data class RestPageDto<T>(
    val info: RestPageInfoDto,
    val results: List<T>,
)

data class RestApiIndexDto(
    val characters: String,
    val locations: String,
    val episodes: String,
)

data class RestPageInfoDto(
    val count: Int,
    val pages: Int,
    val next: String?,
    val prev: String?,
)

data class RestResourceRefDto(
    val name: String,
    val url: String,
)

data class RestCharacterDto(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val type: String,
    val gender: String,
    val origin: RestResourceRefDto,
    val location: RestResourceRefDto,
    val image: String,
    val episode: List<String>,
    val url: String,
    val created: String,
)

data class RestLocationDto(
    val id: Int,
    val name: String,
    val type: String,
    val dimension: String,
    val residents: List<String>,
    val url: String,
    val created: String,
)

data class RestEpisodeDto(
    val id: Int,
    val name: String,
    @SerialName("air_date") val airDate: String,
    val episode: String,
    val characters: List<String>,
    val url: String,
    val created: String,
)

data class RestErrorDto(val error: String)
```

Important edge cases:

- An unknown origin/location can be `{"name":"unknown","url":""}`. Empty URL is valid input and maps to a location summary without an ID.
- Character `type` is frequently an empty string.
- Character images are documented as 300 x 300 pixels. No separate thumbnail or high-resolution image endpoint is documented.
- Relationship arrays contain absolute URLs, not embedded resources.
- `created` is contractually a string and was observed in ISO-8601 form; parse it tolerantly. `air_date` is a display string such as `December 2, 2013`, not ISO-8601.
- REST fields are documented as present, but decoding failures must still map to `MalformedResponse` instead of crashing.

## 5. GraphQL API

### 5.1 Transport and request envelope

- Endpoint: `https://rickandmortyapi.com/graphql`
- The Android contract uses `POST`.
- Header: `Content-Type: application/json`.
- Body fields: `operationName`, `query`, and `variables`.
- The live endpoint advertises `POST`, `GET`, `HEAD`, and `OPTIONS`, but GET query transport is not part of this app contract.
- The official schema defines queries only: there are no mutations or subscriptions.

```json
{
  "operationName": "CharacterPage",
  "query": "query CharacterPage($page: Int, $filter: FilterCharacter) { ... }",
  "variables": {
    "page": 1,
    "filter": { "name": "rick", "status": "alive" }
  }
}
```

Generic response envelope:

```kotlin
data class GraphQlResponse<T>(
    val data: T?,
    val errors: List<GraphQlError>?,
    val extensions: JsonObject?,
)

data class GraphQlError(
    val message: String,
    val path: List<JsonElement>?,
    val locations: List<GraphQlLocation>?,
    val extensions: JsonObject?,
)
```

GraphQL can return `200 OK` with `errors`, with partial `data`, or with nullable requested fields. HTTP success alone is not application success.

### 5.2 Root query map

| Operation | Arguments | Return type |
| --- | --- | --- |
| `character` | `id: ID!` | `Character` |
| `characters` | `page: Int`, `filter: FilterCharacter` | `Characters` |
| `charactersByIds` | `ids: [ID!]!` | `[Character]` |
| `location` | `id: ID!` | `Location` |
| `locations` | `page: Int`, `filter: FilterLocation` | `Locations` |
| `locationsByIds` | `ids: [ID!]!` | `[Location]` |
| `episode` | `id: ID!` | `Episode` |
| `episodes` | `page: Int`, `filter: FilterEpisode` | `Episodes` |
| `episodesByIds` | `ids: [ID!]!` | `[Episode]` |

All root return types are nullable in the published schema. Batch lists and their elements are also nullable. Generated client types must retain that nullability.

### 5.3 GraphQL pagination and filters

List wrappers:

```graphql
type Characters { info: Info, results: [Character] }
type Locations  { info: Info, results: [Location] }
type Episodes   { info: Info, results: [Episode] }

type Info {
  count: Int
  pages: Int
  next: Int
  prev: Int
}
```

Unlike REST, `next` and `prev` are page numbers, not URLs. All fields in `Info` are nullable. The same server-controlled page size applies; the client cannot request a custom size.

```graphql
input FilterCharacter {
  name: String
  status: String
  species: String
  type: String
  gender: String
}

input FilterLocation {
  name: String
  type: String
  dimension: String
}

input FilterEpisode {
  name: String
  episode: String
}
```

These are string inputs rather than GraphQL enums. Apply the same canonical values and validation described for REST.

Observed behavior for a filter with no matches or an out-of-range page is `data.characters.results == []` with nullable metadata such as `info.count == null`. For an empty first page, expose an empty result and allow the UI to display its empty state. For an out-of-range page, preserve metadata from the last valid page and stop pagination; do not overwrite a previously known total with zero. `CharacterPage.totalCount` and `pageCount` remain nullable when the server has not established them.

### 5.4 GraphQL object types

| Type | Fields | Nullability notes |
| --- | --- | --- |
| `Character` | `id`, `name`, `status`, `species`, `type`, `gender`, `origin`, `location`, `image`, `episode`, `created` | Scalar/linked fields nullable; `episode: [Episode]!` makes only the list non-null. |
| `Location` | `id`, `name`, `type`, `dimension`, `residents`, `created` | Scalar fields nullable; `residents: [Character]!` makes only the list non-null. |
| `Episode` | `id`, `name`, `air_date`, `episode`, `characters`, `created` | Scalar fields nullable; `characters: [Character]!` makes only the list non-null. |

GraphQL relationship fields expose nested objects instead of REST URLs. REST-only `url` fields are not present in the GraphQL schema. Request only the relationship fields needed by the screen.

### 5.5 Required operations

#### Character page

```graphql
query CharacterPage($page: Int, $filter: FilterCharacter) {
  characters(page: $page, filter: $filter) {
    info {
      count
      pages
      next
      prev
    }
    results {
      id
      name
      status
      species
      type
      gender
      image
      location { id name }
    }
  }
}
```

The list intentionally omits `episode` and any other field not rendered. Load relationship data in the detail operation instead of multiplying list payload and query cost.

#### Character detail

```graphql
query CharacterDetail($id: ID!) {
  character(id: $id) {
    id
    name
    status
    species
    type
    gender
    image
    created
    origin { id name type dimension }
    location { id name type dimension }
    episode { id name episode air_date }
  }
}
```

An unknown ID was observed as `200` with `{"data":{"character":null}}` and no GraphQL error. Map it to `NotFound(CharacterId)`.

#### Character batch

```graphql
query CharactersByIds($ids: [ID!]!) {
  charactersByIds(ids: $ids) {
    id
    name
    status
    image
  }
}
```

Preserve caller order explicitly if the screen depends on it; do not assume response order without a test. Handle nullable list elements defensively.

### 5.6 Query cost and depth

Nested GraphQL relationships are cyclic (`Character -> Episode -> Character`, for example). The live service enforces a query-depth limit and may reject a deep operation. Therefore:

- use named, static operations checked into source control;
- keep list queries shallow;
- limit detail nesting to one relationship level;
- never recursively request residents/characters/episodes;
- split an oversized detail into a second batch query when necessary;
- use variables rather than interpolating user input into the query text.

Apollo Kotlin is the recommended Android client only if GraphQL is selected. It provides generated, operation-specific models and schema validation. Do not add Apollo alongside Retrofit solely because both APIs are documented.

## 6. Error contract and domain mapping

```kotlin
sealed interface ApiFailure {
    data object Offline : ApiFailure
    data object Timeout : ApiFailure
    data class NotFound(val resource: String, val id: String) : ApiFailure
    data class InvalidRequest(val detail: String?) : ApiFailure
    data class RateLimited(val retryAfterSeconds: Long?) : ApiFailure
    data class Server(val statusCode: Int) : ApiFailure
    data class GraphQl(val codes: Set<String>, val messages: List<String>) : ApiFailure
    data object MalformedResponse : ApiFailure
    data object EmptyBody : ApiFailure
    data class Unknown(val cause: Throwable?) : ApiFailure
}
```

Cancellation is control flow, not a failure: rethrow `CancellationException` so obsolete searches and closed screens stop work immediately.

### 6.1 REST mapping

| Condition | Mapping | Retry |
| --- | --- | --- |
| `200..299` + valid body | Success | No |
| `200..299` + empty body | `EmptyBody` | No automatic retry |
| Character detail `404` | `NotFound` | No |
| Filtered list `404` with no matches | Successful empty page / empty UI state | No |
| Paging `404` after a valid last page | End of pagination | No |
| Other `400..499` | `InvalidRequest` | No |
| `408` | `Timeout` | Yes, bounded |
| `429` | `RateLimited` | Only after `Retry-After`, if present |
| `500..599` | `Server` | Yes, bounded |
| DNS/socket/connectivity failure | `Offline` or `Unknown` | Yes when connectivity returns |
| Read/connect timeout | `Timeout` | Yes, bounded |
| TLS failure | `Unknown` | No automatic retry |
| JSON/schema failure | `MalformedResponse` | No |

Observed batch behavior is more permissive than single-resource lookup: a batch containing valid and invalid IDs returns the resources that exist, while an all-invalid batch returns `200` with `[]`. Reconcile results by ID and report missing requested IDs as warnings when the caller needs completeness.

The official documentation does not publish a complete error-code contract. The table above is defensive app policy. Parse `RestErrorDto` for diagnostics but do not use human-readable server text as control flow.

### 6.2 GraphQL mapping

Evaluate in this order:

1. Map transport failures and non-success HTTP codes using the REST families above.
2. Decode the GraphQL envelope even for HTTP success.
3. If `data == null` and `errors` is non-empty, return `ApiFailure.GraphQl`.
4. If the required root field is null without errors, map a single-resource query to `NotFound`; map a list query to `MalformedResponse` unless `results` explicitly indicates an empty set.
5. If usable data and errors coexist, return the data with warnings only when all fields required by the screen are valid. Never cache this partial response.
6. If a required mapped field is absent, return `MalformedResponse`.

Observed validation errors use HTTP `400` and an `errors` array with extension code `GRAPHQL_VALIDATION_FAILED`. Excessive query depth was observed as HTTP `413` with `GCDN_QUERY_DEPTH_LIMIT`. Treat both as non-retryable invalid queries, but code against the envelope shape and status family rather than assuming these are the only possible extension strings.

### 6.3 Retry policy

- Maximum two automatic retries after the original attempt.
- Exponential backoff with jitter: approximately 500 ms, then 1,500 ms.
- Retry only I/O failures, `408`, `429` when instructed, and `5xx`.
- Never retry schema/validation errors, other `4xx`, decoding errors, or cancellations.
- REST `GET` is safe to retry.
- GraphQL operations in this schema are read-only queries, but generic clients do not automatically retry `POST`; any retry interceptor must verify the operation is a query.
- A user-initiated retry starts a fresh attempt budget.

Recommended timeouts: 10 seconds connect, 15 seconds read, and 20 seconds total call timeout. These are app defaults, not server guarantees.

## 7. Response caching

Response caching and image caching are separate concerns. JSON responses must never contain cached bitmaps, and the HTTP response cache must not replace the image loader's memory/disk cache.

### 7.1 REST cache

**The shipped cache is application-level and lives in `:core:data` (`DEC-018`, [`adr/0005-caching-strategy.md`](adr/0005-caching-strategy.md)).** It stores successfully decoded responses under an explicit key and owns the freshness policy; it does not delegate freshness to the HTTP layer. Rationale: `DataResult.source`, `isStale` and the "never cache errors or partial responses" rules are application semantics that an HTTP cache cannot express, and an app-level cache is testable in `commonTest` with an injected clock.

Cache key: the complete normalized request identity — resource path, page, every filter value (canonical lowercase) and the protocol — so that pages and filter combinations can never collide (`REQ-REL-001`).

Because freshness is app-owned, the Ktor engine's own cache is **disabled** for JSON responses: the server's `Cache-Control: public, max-age=7776000, immutable` (observed 2026-09-29, not a documented guarantee) would otherwise pin a character page for 90 days and make the app's freshness tests meaningless. The engine cache, where used at all, must not retain JSON API responses; images are cached by the platform image loader (§7.4).

App policy:

- freshness windows: fresh for 24 h, stale-while-revalidate online up to 7 d, stale offline fallback up to 30 d (`DEC-012`); these are injectable configuration, never constants in the transport layer;
- only successfully decoded, domain-valid responses enter the cache; errors, empty bodies and partial GraphQL responses are never stored;
- an explicit pull-to-refresh revalidates over the network regardless of freshness, retaining the previous content if it fails;
- an offline read of an entry past freshness but inside the 30 d window is returned with `isStale = true`; with no entry at all the repository fails with `Offline` rather than an empty page;
- **hard requirement:** the REST `404` returned for a filtered list with no matches is cacheable *by the server's headers* (observed: `Cache-Control: public, max-age=7776000, immutable`). It must never be stored, and the response must be rewritten to `Cache-Control: no-store` before any HTTP layer can evaluate it. Excluding it from the app cache is not sufficient on its own — a stale `404` would hide later data. This behaviour is covered by an integration test (`TEST-INT-001`);
- concurrent identical requests are deduplicated in the repository (`REQ-REL-002`);

### 7.2 GraphQL cache

OkHttp's normal disk cache does not satisfy the app's GraphQL caching requirement because the app contract uses `POST`. Use the GraphQL client's normalized cache or an equivalent data-layer store.

Cache identity:

- for Apollo normalized caching, explicitly configure type policies (or a `CacheKeyGenerator`) that key `Character`, `Location`, and `Episode` by `id`; the effective entity key is `__typename + id`;
- list/page fields must incorporate their arguments so every page and filter combination remains isolated;
- if an additional operation-response cache is implemented, its key is SHA-256 of the operation name, exact checked-in query document, and canonical JSON variables;
- canonical variables sort object keys and omit values not sent to the server;
- the operation hash is not a substitute for entity keys in a normalized cache.

App defaults for this slowly changing catalogue:

- fresh for 24 hours;
- stale-while-revalidate online for up to 7 days;
- stale offline fallback for up to 30 days;
- manual refresh always performs a network request;
- cache only complete, error-free responses that pass domain mapping;
- do not cache `data: null`, partial responses with `errors`, validation failures, or transport errors.

These durations are project decisions because the official GraphQL schema does not define client cache lifetime. Keep them as injectable configuration so tests use a fake clock.

### 7.3 Cache read policy

| Context | Policy |
| --- | --- |
| Initial load, fresh entry | Return cache immediately; no network required. |
| Initial load, stale entry, online | Return stale data for continuity and refresh in background. |
| Initial load, stale entry, offline | Return stale data with offline/stale indicator. |
| Initial load, no entry | Fetch network; surface failure if unavailable. |
| Pull to refresh | Network-first; retain prior data if refresh fails. |
| Search/filter changed | Use the new canonical key and reset to page 1. |

### 7.4 Image cache

Use Coil's memory and disk cache for the `image` URL. Preserve the same URL as the stable cache key. Do not manually download images through the JSON client. The documented source is 300 x 300; request UI-sized decoding to avoid oversized bitmaps, but do not claim a higher-resolution source.

## 8. Performance and UX rules

- Load incrementally; never fetch every page before showing the first screen.
- Display cached list content while a refresh is in progress.
- Debounce free-text search by 300 ms, apply `distinctUntilChanged`, cancel the previous request, and reset to page 1.
- Do not request blank filters.
- Prefetch at most the next page as the user approaches the end of the current page.
- Avoid N+1 requests: use REST batch endpoints or one bounded GraphQL nested query.
- Reuse list data during navigation for an immediate visual transition, then load the canonical detail according to freshness policy.
- Do not load location residents or all episode characters for the character detail.
- Limit parallel enrichment calls to avoid bursts; two concurrent remote calls is the initial app policy.
- Surface an empty search as normal UI state, not a generic error.
- Keep prior content visible for recoverable refresh failures and expose a retry action.

Paging 3 is appropriate for REST if the app already uses it. A small custom pager is also acceptable because the page contract is simple; dependency choice must be justified. Retrofit/OkHttp plus one serializer is sufficient for REST. Apollo Kotlin is sufficient for GraphQL. A community Rick and Morty SDK is unnecessary.

## 9. Security and observability

- Permit only HTTPS and the configured `rickandmortyapi.com` host.
- Do not follow relation or pagination URLs to a different host.
- Send GraphQL user input only as variables.
- Do not log full response bodies, image bytes, or stack traces in release builds.
- Logs may include protocol, named operation/path template, page, normalized filter names, status family, cache source, duration, and request correlation ID.
- Avoid recording raw search text in analytics.
- Metrics should distinguish network, memory cache, disk cache, stale fallback, empty result, timeout, decoding failure, and cancellation.
- No API keys or secrets are required; none should be added to the repository.

Rate-limit headers were **not** observed on a normal GraphQL query during the 2026-09-29 verification (only edge-cache headers such as `x-cache` and `x-served-by` were present). No numeric quota is documented or assumed. Handle `429`, honor `Retry-After` when present, debounce searches, and keep request volume low.

## 10. Verification strategy

### 10.1 REST contract tests

- Decode first, middle, and last character pages.
- Verify `next == null` terminates pagination.
- Decode a full character, an empty `type`, `unknown` values, and an empty reference URL.
- Decode location and episode fixtures, including `air_date` mapping.
- Verify single-object and batch-array response shapes.
- Verify URL encoding and preservation of combined filters across pages.
- Map detail `404` to `NotFound` and filtered-list `404` to an empty result.
- Map malformed JSON, empty body, timeouts, offline, `429`, and `5xx`.

### 10.2 GraphQL contract tests

- Validate checked-in operations against a pinned copy of the official schema.
- Verify generated nullability matches the schema.
- Map GraphQL `ID` to canonical domain IDs.
- Verify page metadata and filter variables.
- Normalize empty results when nullable count/page fields are absent.
- Map `data.character == null` to `NotFound`.
- Test errors-only and partial-data-plus-errors envelopes.
- Verify queries stay within the service depth constraints.
- Run parity tests proving REST and GraphQL fixtures map to equivalent domain models.

### 10.3 Cache tests

- Fresh hit, expired hit, revalidation, and cache miss.
- Isolation between pages, filters, IDs, protocols, and GraphQL field selections.
- REST `404` responses are rewritten to `no-store` and are not served from OkHttp cache.
- GraphQL entities merge only by the configured type-and-ID policy; differently parameterized pages remain isolated.
- Offline stale fallback with `isStale = true`.
- Failed refresh retains previously displayed data.
- Errors and partial GraphQL responses are not cached.
- Concurrent identical requests are deduplicated.
- Image requests hit Coil memory/disk cache independently of response caching.

Use Ktor's `MockEngine` with the committed JSON fixtures for transport, mapping and cache-policy integration tests, so the same suites run in `commonTest` on both platforms (`DEC-030`, `TESTING.md` §4). `MockWebServer` is retained only where the OkHttp engine's own behaviour must be exercised and `MockEngine` cannot reach it — the REST `404` cache-header rewrite in §7.1 — and that check lives in the Android source set. Pure mapper tests need no HTTP layer at all. Inject clock, connectivity state and dispatchers for deterministic tests.

## 11. Requirements traceability

Every row cites the requirement identifier that now owns the obligation. The pre-audit table cited line numbers in a 27-line draft; those line references no longer exist.

| Requirement | Source | API decision in this document |
| --- | --- | --- |
| `REQ-FUNC-001` — Paginated character list | `assessment.md:4` | §4.3 pagination; incremental loading until `info.next == null`; `API-CHAR-001`…`003` |
| `REQ-FUNC-002` — Character detail | `assessment.md:4` | §4.4 single-character operation keyed by canonical ID; `API-CHAR-004` |
| `REQ-FUNC-003` — Name search | `assessment.md:21` | §4.4 `name` parameter, §6.3 policy, §8 debounce and cancellation |
| `REQ-FUNC-004` — Status filter | `assessment.md:21` | §4.4 canonical lowercase `status` values; `All` sends no parameter |
| `REQ-FUNC-005` — Image-first presentation | `assessment.md:9` | §7.4 single 300 × 300 source; no higher-resolution request |
| `REQ-FUNC-020` — Response caching | `assessment.md:19` | §7.1 app-level cache and freshness policy; `API-CACHE-###` |
| `REQ-FUNC-021` — Image caching | `assessment.md:17` | §7.4 image cache, separate from JSON responses |
| `REQ-FUNC-022` — Error handling | `assessment.md:18` | §6 failure taxonomy, §6.1 REST mapping, §6.2 GraphQL mapping, §6.3 retry policy; `API-ERR-###` |
| `REQ-FUNC-023` — Detail enrichment | `assessment.md:4`, `assessment.md:10` | §4.6 batch episode request; never one request per episode |
| `REQ-NFR-002` — Dependency restraint | `assessment.md:7` | §2 one protocol stack; no community API SDK |
| `REQ-NFR-003` — Performance budgets | `assessment.md:10` | §8 incremental loading, single-page prefetch, deduplication, bounded concurrency |
| `REQ-NFR-005` — Verification depth | `assessment.md:20` | §10.1–§10.3 contract, mapping, transport, parity and cache suites |
| `REQ-NFR-001` / `REQ-NFR-009` — Layering and module structure | `assessment.md:8,23` | §3 DTOs and generated types confined to `:core:data`; shared domain/repository contract |
| `REQ-SEC-001` — HTTPS and host restriction | `assessment.md:7` | §9 HTTPS-only, host allow-list, no foreign-host pagination or relation URLs |
| `REQ-SEC-005` — Log redaction | `assessment.md:7` | §9 permitted log fields; no response bodies, image bytes or raw search text |

## 13. Contract identifier index

Stable identifiers for the rules this document owns. Other documents must reference these ids instead of restating a rule.

| ID | Rule | Section |
| --- | --- | --- |
| `API-CHAR-001` | List characters through `GET /character` with server-controlled page size (20) and optional combined filters. | §4.3, §4.4 |
| `API-CHAR-002` | `info.next == null` is the authoritative end-of-pagination signal; page numbers are parsed and rebuilt against the configured base URL, never followed as returned hosts. | §4.3 |
| `API-CHAR-003` | Single character through `GET /character/{id}`; `404` maps to `NotFound(CharacterId)`. | §4.4 |
| `API-CHAR-004` | Filter parameters use canonical lowercase values (`alive`, `dead`, `unknown`; `female`, `male`, `genderless`, `unknown`); blank filters are omitted and a changed filter resets to page 1. | §4.4 |
| `API-CHAR-005` | A filtered list with no matches returns HTTP `404` and maps to an **empty result**, not an error; the same `404` must be rewritten to `Cache-Control: no-store` and never stored. | §4.4, §6.1, §7.1 |
| `API-CHAR-006` | Batch retrieval requires at least two IDs; one ID uses the single-character endpoint and empty input performs no request; chunks are bounded at 20 IDs. | §4.2, §4.4 |
| `API-LOC-001` | Location list filters are `page`, `name`, `type`, `dimension`; retrieval is optional for the MVP because character payloads already carry origin and last-known-location summaries. | §4.5 |
| `API-EPI-001` | Episode list filters are `page`, `name`, `episode`; `air_date` is a display string, not ISO-8601. | §4.6, §4.7 |
| `API-EPI-002` | Episode enrichment for a detail screen uses one batch request built from the character's episode URLs, never one request per episode. | §4.6 |
| `API-CACHE-001` | The shipped cache is application-level in `:core:data`: explicit key (path + page + normalized filters + protocol), injected clock, freshness 24 h / 7 d / 30 d, `isStale` surfaced on the result. | §7.1 |
| `API-CACHE-002` | Only successfully decoded, domain-valid responses are cached; errors, empty bodies and partial GraphQL responses are never stored. | §7.1, §7.2 |
| `API-CACHE-003` | The engine-level HTTP cache must not retain JSON API responses, because the server's 90-day `immutable` directive would defeat app-owned freshness. | §7.1 |
| `API-CACHE-004` | Images are cached by the platform image loader under the image URL as key, independently of JSON response caching. | §7.4 |
| `API-ERR-001`…`API-ERR-017` | One identifier per failure class in the taxonomy: connectivity/DNS/socket, connect timeout, read timeout, TLS, `400` other, `408`, `429`, `404` detail, `404` filtered list, `404` paging end, `5xx`, empty body, malformed JSON/schema, GraphQL errors-only envelope, GraphQL partial-data-plus-errors, GraphQL depth/validation rejection, cancellation. | §6, §6.1, §6.2 |
| `API-GQL-001` | The GraphQL contract uses `POST` with `operationName`, `query` and `variables`; HTTP success alone is not application success. | §5.1 |
| `API-GQL-002` | GraphQL `ID` values decode to canonical string identifiers; a null root field without errors maps to `NotFound` for a single-resource query. | §5.4, §6.2 |
| `API-GQL-003` | Named, static operations with one relationship level of nesting; no recursive relationship queries. | §5.6 |

## 14. Resolved implementation decisions

The choices that previously sat here as open questions are decided; their rationale lives in the ADRs and their status in [`DECISION_BOARD.md`](DECISION_BOARD.md). This section records only how each one lands on this document.

| Question | Resolution | Effect on this document |
| --- | --- | --- |
| Shipped protocol | REST (`DEC-011`, [`adr/0004-rest-client.md`](adr/0004-rest-client.md)); GraphQL stays documented as the alternative, not shipped | §2 stands; §5 is specification-only |
| HTTP client and serializer | Ktor 3.6.0 + kotlinx.serialization everywhere (`DEC-011`) | §6.3 retry policy is implemented as a Ktor plugin, not an OkHttp interceptor; §7.1 is written for the app-level cache |
| Paging | Shared custom pager in `:core:data` (`DEC-016`, [`adr/0009-pagination-strategy.md`](adr/0009-pagination-strategy.md)); Paging 3 rejected | §8 paging rules are implemented by that pager |
| Cache storage and budgets | Application-level cache with explicit keys and an injected clock (`DEC-018`, [`adr/0005-caching-strategy.md`](adr/0005-caching-strategy.md)); exact byte budget is a configuration value settled during implementation | §7.1 owns the policy; §7.4 images remain a separate concern |
| Enriched episode/location content | Episode enrichment is in scope (`REQ-FUNC-023`); location residents are never fetched | §4.6 batch rule and §8 "avoid N+1" stand |
| Image resolution | The single documented 300 × 300 source is accepted; no higher-resolution request is made (`CON-002`, `UI_SPEC.md` §5.1) | §7.4 stands |

Any change to the selected protocol, required fields, caching policy or error semantics updates this document and its contract tests together.

## 15. Change log

| Date | Change | Decision |
| --- | --- | --- |
| 2026-09-29 | Scope widened from "Android character review app" to the two-platform KMP client. §7.1 rewritten from an OkHttp disk cache to the app-level cache with explicit freshness, keying and the `404` `no-store` requirement. §9 rate-limit-header claim corrected against the live probe. §11 traceability re-pointed at stable requirement ids. §12 open decisions replaced by the resolved-decision table. §13 contract identifier index added. | DEC-011, DEC-012, DEC-018, DEC-052 |
