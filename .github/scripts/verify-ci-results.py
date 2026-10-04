"""TEST-UNIT-062: the required android check succeeds only when every worker succeeds."""

import json
import os
import sys

REQUIRED_WORKERS = {
    "module-checks", "designsystem", "app-tests", "app-artifacts",
    "build-logic", "policies", "dependency-health", "contract-replay",
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate result key: {key}")
        result[key] = value
    return result


def main():
    try:
        require_ios = os.environ["CI_REQUIRE_IOS"]
        if require_ios not in {"true", "false"}:
            raise ValueError("CI_REQUIRE_IOS must be true or false")
        expected = REQUIRED_WORKERS | ({"ios"} if require_ios == "true" else set())
        results = json.loads(os.environ["CI_JOB_RESULTS"], object_pairs_hook=unique_object)
        if not isinstance(results, dict) or set(results) != expected:
            raise ValueError(f"Expected exactly these worker results: {', '.join(sorted(expected))}")
        failures = []
        for name in sorted(expected):
            result = results[name]
            state = result.get("result") if isinstance(result, dict) else None
            print(f"{name}: {state}")
            if state != "success":
                failures.append(name)
        if failures:
            raise ValueError(f"Required checks did not succeed: {', '.join(failures)}")
    except (KeyError, ValueError, TypeError) as error:
        print(f"CI gate failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
