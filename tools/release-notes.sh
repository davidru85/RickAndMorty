#!/usr/bin/env bash
# Generate release notes from Conventional Commits (`DEC-041`, `DEC-042`, `TASK-066`).
#
# The repository keeps no hand-written changelog: the commit prefixes carry the meaning, and these
# notes are derived for a tag range. The TDD phase prefixes (`DEC-053`) are grouped with the release
# types, so a reader sees what shipped and how it was built without a second document.
#
# Usage: tools/release-notes.sh <from-tag-or-revision> <to-revision>
#        tools/release-notes.sh --all            (the initial release: every commit)
#
# Exit: 0 notes written, 2 bad usage.
set -eu

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPOSITORY_ROOT"

if [ "${1:-}" = "--all" ]; then
    RANGE=""
elif [ $# -eq 2 ]; then
    RANGE="$1..$2"
else
    echo "usage: tools/release-notes.sh <from> <to> | --all" >&2
    exit 2
fi

# The pipeline is one `awk` pass over the commit subjects. The mapping from a Conventional Commit
# type to a section and the section order both live in the awk program, so the shell adds no
# delimiter of its own — the first drafts passed a `\t` through three quoting layers and produced
# nothing, which is a defect no reader would see in the output.
# shellcheck disable=SC2086
git log $RANGE --format='%s' --no-merges | awk '
    {
        subject = $0
        # `<type>[(<scope>)]: <subject>` — the type is the token before the first colon.
        colon = index(subject, ": ")
        if (colon == 0) next
        prefix = substr(subject, 1, colon - 1)
        body = substr(subject, colon + 2)
        type = prefix
        open = index(prefix, "(")
        if (open > 0) type = substr(prefix, 1, open - 1)

        section = ""
        if (type == "feat") section = "Features"
        else if (type == "fix") section = "Fixes"
        else if (type == "perf") section = "Performance"
        else if (type == "refactor") section = "Refactors"
        else if (type == "build" || type == "ci") section = "Build and CI"
        else if (type == "chore") section = "Maintenance"
        else if (type == "docs") section = "Documentation"
        else if (type == "test") section = "Tests"
        # A type outside the Conventional Commit set is not a release note, and a merge subject is
        # filtered by `--no-merges` above.
        if (section == "") next

        entries[section] = entries[section] body "\n"
    }
    END {
        # The sections print in a fixed order, so the notes do not depend on commit order. A section
        # with no commits is omitted rather than printed empty.
        split("Features|Fixes|Performance|Refactors|Build and CI|Maintenance|Documentation|Tests", order, "|")
        for (i = 1; i <= 8; i++) {
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
