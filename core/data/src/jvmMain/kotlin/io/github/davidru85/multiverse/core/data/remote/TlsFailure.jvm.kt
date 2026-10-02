package io.github.davidru85.multiverse.core.data.remote

import javax.net.ssl.SSLException

internal actual fun Throwable.isTlsFailure(): Boolean = generateSequence(this) { it.cause }.any { it is SSLException }
