# API_SPECS.md - API Technical Specification

## 1. Endpoint Mapping
- **List Characters:** `GET /character`
- **Single Character:** `GET /character/{id}`

## 2. Data Transfer Objects (DTOs)
- `CharacterDTO`: Mapping of API fields to internal models.
- `PaginationDTO`: Handling of result pages.

## 3. Error Mapping
- 404: Resource not found.
- 500: Server error.
- Network Timeout: Connectivity issues.

## 4. Caching Strategy
- Response caching implementation details (e.g., OkHttp cache).
