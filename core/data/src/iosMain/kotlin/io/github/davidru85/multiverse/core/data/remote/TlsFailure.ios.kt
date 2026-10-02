package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorAppTransportSecurityRequiresSecureConnection
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorSecureConnectionFailed

/**
 * The `NSURLErrorDomain` security codes: `NSURLErrorSecureConnectionFailed` (-1200) through
 * `NSURLErrorClientCertificateRequired` (-1206), plus App Transport Security refusing a cleartext
 * connection (-1022).
 */
internal actual fun Throwable.isTlsFailure(): Boolean =
    generateSequence(this) { it.cause }.any { failure ->
        failure is DarwinHttpRequestException &&
            failure.origin.domain == NSURLErrorDomain &&
            (
                failure.origin.code in NSURLErrorClientCertificateRequired..NSURLErrorSecureConnectionFailed ||
                    failure.origin.code == NSURLErrorAppTransportSecurityRequiresSecureConnection
            )
    }
