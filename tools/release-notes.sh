#!/usr/bin/env bash
# Generate release notes from Conventional Commits (`DEC-041`, `DEC-042`, `TASK-066`).
#
# The repository keeps no hand-written changelog: the commit prefixes carry the meaning, and the
# note is derived for a range in the shape `CONTRIBUTING.md` §3.6 specifies — a Breaking changes
# section leads, then Features (`feat`), Fixes (`fix`) and Performance (`perf`); every other type
# (`refactor`, `test`, `docs`, `build`, `ci`, `chore`, `revert`) is Maintenance, and a range that holds
# only those lists none of them. `TEST-UNIT-064` holds the shape.
#
# Usage: tools/release-notes.sh                   (the range since the previous `v*` tag)
#        tools/release-notes.sh <from> <to>       (an explicit range)
#        tools/release-notes.sh --all             (every commit)
#
# Exit: 0 notes written, 2 bad usage.
set -eu

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPOSITORY_ROOT"

if [ $# -eq 0 ]; then
    # The previous release is the newest `v*` tag reachable from HEAD's parent, so the note is the
    # same before and after the release commit itself is tagged. The first release has none and
    # covers the whole history.
    PREVIOUS="$(git describe --tags --abbrev=0 --match 'v[0-9]*' HEAD^ 2>/dev/null || true)"
    if [ -n "$PREVIOUS" ]; then RANGE="$PREVIOUS..HEAD"; else RANGE=""; fi
elif [ $# -eq 1 ] && [ "$1" = "--all" ]; then
    RANGE=""
elif [ $# -eq 2 ]; then
    RANGE="$1..$2"
else
    echo "usage: tools/release-notes.sh [<from> <to> | --all]" >&2
    exit 2
fi

# The pipeline is one `awk` pass over the commits, each a record of `subject <US> body <RS>`. The
# mapping from a type to a section and the section order both live in the awk program, so the shell
# adds no delimiter of its own — the first drafts passed a `\t` through three quoting layers and
# produced nothing, which is a defect no reader would see in the output.
# shellcheck disable=SC2086
git log $RANGE --format='%s%x1f%b%x1e' --no-merges | awk '
    BEGIN { RS = "\036"; FS = "\037" }
    {
        subject = $1
        sub(/^\n+/, "", subject)
        if (subject == "") next
        # `<type>[(<scope>)][!]: <subject>` — the type is the token before the first colon.
        colon = index(subject, ": ")
        if (colon == 0) next
        prefix = substr(subject, 1, colon - 1)
        text = substr(subject, colon + 2)
        breaking = (substr(prefix, length(prefix), 1) == "!")
        if (breaking) prefix = substr(prefix, 1, length(prefix) - 1)
        type = prefix
        open = index(prefix, "(")
        if (open > 0) type = substr(prefix, 1, open - 1)

        # A `BREAKING CHANGE:` footer names what breaks and the replacement (`CONTRIBUTING.md` §3.4).
        footer = ""
        count = split($2, lines, "\n")
        for (line = 1; line <= count; line++) {
            if (lines[line] ~ /^BREAKING[ -]CHANGE: /) {
                footer = lines[line]
                sub(/^BREAKING[ -]CHANGE: /, "", footer)
                breaking = 1
            }
        }

        if (type == "feat") section = "Features"
        else if (type == "fix") section = "Fixes"
        else if (type == "perf") section = "Performance"
        else if (type ~ /^(refactor|test|docs|build|ci|chore|revert)$/) section = "Maintenance"
        # A type outside the Conventional Commit set is not a release note, and a merge subject is
        # filtered by `--no-merges` above.
        else next

        if (breaking) {
            entries["Breaking changes"] = entries["Breaking changes"] text (footer == "" ? "" : " — " footer) "\n"
        } else if (section == "Maintenance") {
            # A maintenance entry keeps its type, so a revert still reads as one.
            entries[section] = entries[section] subject "\n"
            maintenance++
        } else {
            entries[section] = entries[section] text "\n"
            released++
        }
        if (breaking) released++
    }
    END {
        if (released == 0) {
            if (maintenance > 0) {
                print "No feature, fix, performance or breaking change: the range holds " maintenance " maintenance commit(s)."
            }
            exit
        }
        # The sections print in a fixed order, so the note does not depend on commit order. A
        # section with no commits is omitted rather than printed empty.
        split("Breaking changes|Features|Fixes|Performance|Maintenance", order, "|")
        for (i = 1; i <= 5; i++) {
            if (!(order[i] in entries)) continue
            print "## " order[i]
            count = split(entries[order[i]], lines, "\n")
            for (line = 1; line <= count; line++) {
                if (lines[line] != "") print "- " lines[line]
            }
            print ""
        }
    }
'
