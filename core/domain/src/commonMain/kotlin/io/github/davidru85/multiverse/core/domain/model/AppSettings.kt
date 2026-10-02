package io.github.davidru85.multiverse.core.domain.model

/**
 * The user's settings (`IC-021`). The defaults are the fresh-install values: Sounds off and REST
 * (`AC-REQ-FUNC-033-2`, `AC-REQ-FUNC-034-1`). Nothing personal may be added (`REQ-SEC-003`).
 */
public data class AppSettings(
    public val soundsEnabled: Boolean = false,
    public val remoteProtocol: RemoteProtocol = RemoteProtocol.Rest,
)

/** The remote protocol the repository uses for every screen (`DEC-056`). */
public enum class RemoteProtocol { Rest, GraphQl }
