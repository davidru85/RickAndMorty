package io.github.davidru85.multiverse.buildlogic.hygiene

/**
 * Streams text past the credential registry without an arbitrary whole-object ceiling
 * (`TEST-UNIT-026`, `AC-REQ-SEC-002-1`; DEC-062, TASK-016 §6.5).
 *
 * A blob or a working-set file is read in bounded chunks and handed here as it arrives, so
 * no object is skipped for its size and no unbounded byte array is allocated. Two guarantees
 * keep the scan lossless across chunk boundaries:
 *
 * - **the tail of an unterminated line is carried**, so a credential split at an arbitrary
 *   chunk boundary is still one line when it is scanned;
 * - **the last complete line is kept as context**, so a rule whose value may follow its key
 *   across a newline cannot be hidden by a boundary either.
 *
 * Findings are deduplicated by the absolute character offset of their match, so a match that
 * appears in two overlapping windows is reported once, and line numbers come from the running
 * newline count rather than from any single window.
 *
 * A single line longer than [MAX_LINE_CHARACTERS] is refused with a fail-closed error rather
 * than scanned with unbounded memory; the caller names the object safely.
 */
internal class ContentScanner(
    private val onFinding: (ruleId: String, line: Int) -> Unit,
) {

    /** The last complete line kept as context for the next window. */
    private var context = ""
    private var contextStart = 0L
    private var newlinesBeforeContext = 0L

    /** The tail of the line still being assembled; always starts at a line boundary. */
    private var pending = StringBuilder()

    private val reported = mutableSetOf<Pair<String, Long>>()

    /** Feeds one decoded chunk. Chunks may split anywhere, including inside a credential. */
    fun accept(chunk: String) {
        pending.append(chunk)
        process(flushing = false)
    }

    /** Call once, after the last chunk, to scan whatever remains. */
    fun finish() {
        process(flushing = true)
    }

    private fun process(flushing: Boolean) {
        if (pending.isEmpty() && context.isEmpty()) return
        val text = context + pending
        val lastNewline = text.lastIndexOf('\n')
        val completeLength = when {
            flushing -> text.length
            lastNewline < 0 -> 0
            else -> lastNewline + 1
        }
        if (completeLength > 0) {
            scanWindow(text.substring(0, completeLength), contextStart)
        }
        if (flushing) {
            contextStart += text.length
            newlinesBeforeContext += text.count { it == '\n' }
            context = ""
            pending = StringBuilder()
            return
        }
        // Keep the last complete line as context and carry the unfinished tail unchanged.
        val previousLineBreak = text.lastIndexOf('\n', completeLength - 2)
        val contextFrom = if (previousLineBreak < 0) 0 else previousLineBreak + 1
        context = text.substring(contextFrom, completeLength)
        contextStart += contextFrom
        newlinesBeforeContext += text.substring(0, contextFrom).count { it == '\n' }
        pending = StringBuilder(text.substring(completeLength))
        if (pending.length > MAX_LINE_CHARACTERS) {
            throw GitRepository.GitFailure(
                "a line exceeds ${MAX_LINE_CHARACTERS / (1024 * 1024)} MiB; the scan fails closed " +
                    "rather than skipping the object (HYG-06)",
            )
        }
    }

    private fun scanWindow(window: String, windowStart: Long) {
        HygieneRules.scanContent(window) { ruleId, matchOffset ->
            if (reported.add(ruleId to windowStart + matchOffset)) {
                onFinding(ruleId, (newlinesBeforeContext + window.countNewlines(matchOffset) + 1).toInt())
            }
        }
    }

    private fun String.countNewlines(upToExclusive: Int): Long {
        var count = 0L
        val limit = upToExclusive.coerceAtMost(length)
        for (index in 0 until limit) if (this[index] == '\n') count++
        return count
    }

    private companion object {
        const val MAX_LINE_CHARACTERS = 64 * 1024 * 1024
    }
}
