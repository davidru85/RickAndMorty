package io.github.davidru85.multiverse.core.domain.model

/**
 * The list filter of `IC-010`: an input of `IC-007`, so part of the domain seam.
 *
 * [query] keeps the raw user text; a blank query means "send no `name` parameter", and trimming and
 * encoding are the mapper's job. [StatusFilter.All] means "send no `status` parameter". Equal
 * filters are one request identity, and different filters never share one (`REQ-REL-001`).
 */
public data class CharacterFilter(
    public val query: String = "",
    public val status: StatusFilter = StatusFilter.All,
)

/** The four status options the list offers, `All` first (`AC-REQ-FUNC-004-2`). */
public enum class StatusFilter { All, Alive, Dead, Unknown }
