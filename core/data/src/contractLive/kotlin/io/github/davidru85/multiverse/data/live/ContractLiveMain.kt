package io.github.davidru85.multiverse.data.live

import java.io.File

/** Entry point the scheduled workflow runs: `./gradlew :core:data:contractLiveProbe`. */
public fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/observations")
    val records = ObservationProbes.run(output)
    records.forEach { record ->
        println("TEST-CONTRACT-006 ${record.probe}: status=${record.status} totals=${record.totals}")
    }
    println("observations written to ${output.absolutePath}")
    check(records.any { it.status in 200..299 || it.status == 404 }) {
        "no probe reached the service; the run is not evidence (TESTING.md §11.3)"
    }
}
