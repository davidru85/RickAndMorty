package io.github.davidru85.multiverse.core.data.remote

/**
 * Whether [this] — or a cause in its chain — is a TLS handshake or certificate failure.
 *
 * Only a platform can tell: the JVM reports `javax.net.ssl.SSLException`, and Apple reports
 * `NSURLErrorDomain` security codes inside the Darwin engine's exception; no multiplatform library
 * the build already declares exposes either (`GUIDELINES.md` §3.5). The answer decides retryability:
 * a TLS failure is `ApiFailure.Unknown` and is never retried (`API_SPECS.md` §6.1, `API-ERR-003`).
 */
internal expect fun Throwable.isTlsFailure(): Boolean
