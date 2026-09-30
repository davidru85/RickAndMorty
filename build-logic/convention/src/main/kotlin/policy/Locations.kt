package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * Root-relative locations for violation messages (`TEST-UNIT-013`, `TEST-UNIT-014`,
 * `TEST-UNIT-051`; DEC-061).
 *
 * Twelve scanned build scripts are all named `build.gradle.kts`, so a bare file name
 * does not say which module is at fault. Every location is therefore the root-relative,
 * `/`-separated path, with `:<line>` where a line exists.
 */
internal fun File.location(root: File): String = relativeTo(root).invariantSeparatorsPath
