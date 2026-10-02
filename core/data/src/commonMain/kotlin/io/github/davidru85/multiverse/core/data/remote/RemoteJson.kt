package io.github.davidru85.multiverse.core.data.remote

import kotlinx.serialization.json.Json

/**
 * The one decoder for remote JSON, with the settings `SECURITY.md` §12.2 fixes: an extra field is
 * ignored so an additive server change does not break the client, while a missing required field,
 * lenient syntax, coerced values and special floating-point values all fail decoding. No
 * polymorphic deserialization is registered, so untrusted input never selects a type.
 */
internal val RemoteJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
        isLenient = false
        allowSpecialFloatingPointValues = false
        explicitNulls = true
    }
