package io.github.davidru85.multiverse.app.di

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger

/**
 * The **release** variant's logger (`TASK-044`, `DESIGN.md` §5, `DEC-039`).
 *
 * A release build logs at `ERROR` only over the Logcat sink, and [debugModules] registers nothing:
 * the diagnostic recorder has no definition at all, so no release code path can reach the panel.
 *
 * The debug variant does not edit this file. Its Application subclass
 * (`io.github.davidru85.multiverse.app.debug.DebugMultiverseApplication`) is selected by the debug
 * manifest and overrides the two seams below, so the threshold and the panel are properties of the
 * compiled variant rather than of a runtime flag (`OBSERVABILITY.md` §5 rule 1).
 */
public open class ShellLogging {
    /** The logger the graph binds. */
    open fun logger(): ValidatingAppLogger = ValidatingAppLogger.forRelease(LogcatSink)

    /** Extra modules only a non-release variant registers; none in a release build. */
    open fun extraModules(logger: ValidatingAppLogger): List<org.koin.core.module.Module> = emptyList()
}
