package io.github.davidru85.multiverse.app.debug

import io.github.davidru85.multiverse.app.MultiverseApplication
import io.github.davidru85.multiverse.app.di.ShellLogging
import io.github.davidru85.multiverse.app.di.LogcatSink
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.diagnostics.DiagnosticsRecorder
import io.github.davidru85.multiverse.core.domain.logging.LogRecord
import io.github.davidru85.multiverse.core.domain.logging.LogSink
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The debug variant's Application (`TASK-044`, `DEC-039`, `OBSERVABILITY.md` §5): the debug manifest
 * selects this class, so a debug build gets the DEBUG threshold, the recorder fan-out and the
 * recorder binding the panel reads — and a release build has none of them, because neither this
 * class nor the manifest entry is compiled into it (`R11`, `TEST-UNIT-033`).
 */
public class DebugMultiverseApplication : MultiverseApplication() {
    private val recorder = DiagnosticsRecorder()

    override fun logging(): ShellLogging =
        object : ShellLogging() {
            override fun logger(): ValidatingAppLogger =
                ValidatingAppLogger.forDebug(
                    LogSink { record: LogRecord ->
                        LogcatSink.write(record)
                        recorder.write(record)
                    },
                )

            override fun extraModules(logger: ValidatingAppLogger): List<Module> = listOf(module { single { recorder } })
        }
}
