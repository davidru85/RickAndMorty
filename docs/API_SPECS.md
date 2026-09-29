# API_SPECS.md - REST and GraphQL Technical Specification

- **Status:** implementation contract
- **Last verified:** 2026-09-29
- **Primary source:** [official API documentation](https://rickandmortyapi.com/documentation)
- **GraphQL schema source:** [official `typeDefs.js`](https://github.com/afuh/rick-and-morty-api/blob/master/graphql/typeDefs.js)

## 1. Purpose and scope

This document defines the remote data contract for the Android character review app. It covers both public interfaces offered by The Rick and Morty API:

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

The repository returns data-source metadata separately from the model:

```kotlin
data class DataResult<T>(
    val value: T,
    val source: DataSource,
    val isStale: Boolean,
    val warnings: List<ApiWarning> = emptyList(),
)

enum class DataSource { NETWORK, MEMORY_CACHE, DISK_CACHE }
data class ApiWarning(val code: String, val detail: String? = null)
```

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

Use a bounded OkHttp disk cache (recommended initial budget: 20 MiB). Cache keys are the complete normalized URL, including resource path, page, and every filter value.

Live REST responses were observed with `ETag` and `Cache-Control: public, max-age=7776000, immutable`. These headers are not documented guarantees. App policy is:

- respect server freshness and validators when present;
- only promote successfully decoded `GET` responses into any application-level cache; OkHttp may independently retain the raw HTTP response;
- never cache application-generated errors or malformed bodies;
- allow an explicit pull-to-refresh to revalidate/bypass freshness;
- when offline, allow a successfully decoded stale response up to 30 days beyond freshness and mark it `isStale = true`;
- if no cache entry exists, return `Offline` rather than an empty list;
- do not globally force-cache server failures;
- deduplicate concurrent identical requests in the repository.

The server currently marks even some `404` responses cacheable for a long period. A network-response interceptor must replace cache headers on REST `404` responses with `Cache-Control: no-store` before OkHttp evaluates storage; not adding the response to a domain cache is insufficient. Cover this behavior with an integration test so a transient miss cannot hide later data.

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

The live GraphQL gateway exposes rate-limit headers, but no stable public quota is documented. Do not assume a numeric allowance. Handle `429`, honor `Retry-After` when present, debounce searches, and keep request volume low.

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

Use MockWebServer for transport/cache integration and pure mapper tests for DTO conversion. Inject clock, connectivity state, and dispatchers for deterministic tests.

## 11. Requirements traceability

| Requirement | Source | API decision |
| --- | --- | --- |
| List all characters | `assessment.md:4`, `REQUIREMENTS.md:8` | Paginated character list; incremental loading until `next == null`. |
| Character detail | `assessment.md:4`, `REQUIREMENTS.md:9` | Single character REST/GraphQL operation keyed by ID. |
| Search/filter | `assessment.md:21`, `REQUIREMENTS.md:13` | Server-side character filters, debounce, cancellation, page reset. |
| Error handling | `assessment.md:18`, `REQUIREMENTS.md:14` | Protocol-aware domain failures, retry boundaries, cached fallback. |
| Response caching | `assessment.md:19`, `REQUIREMENTS.md:15` | HTTP cache for REST; normalized/application cache for GraphQL. |
| Image caching | `assessment.md:17`, `REQUIREMENTS.md:24` | Coil memory/disk cache, separate from JSON responses. |
| Performance | `assessment.md:10` | Paging, minimal GraphQL selections, batch enrichment, deduplication. |
| Tests | `assessment.md:20` | Contract, mapping, transport, parity, and cache test suites. |
| Dependency restraint | `assessment.md:7` | Ship one protocol stack for the MVP; no community API SDK. |
| SOLID/clean code | `assessment.md:8,23`, `REQUIREMENTS.md:23` | DTO/generated types in data layer; shared domain/repository contract. |

## 12. Open implementation decisions

These choices belong to the later architecture/implementation phase and do not alter the external contract:

- select REST (recommended MVP) or GraphQL as the shipped adapter;
- choose Retrofit serializer or Apollo normalized-cache storage;
- decide whether Paging 3 is justified;
- confirm cache budgets against the target device profile;
- decide whether enriched episode/location content is in the first release;
- align `UI_SPEC.md` with the API's single 300 x 300 character image source.

Any change to the selected protocol, required fields, caching policy, or error semantics must update this document and its contract tests together.
