"""TEST-UNIT-062: exercise the gate's process exit status with GitHub needs payloads."""

import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

SCRIPT = Path(__file__).with_name("verify-ci-results.py")
WORKERS = [
    "module-checks", "designsystem", "app-tests", "app-artifacts",
    "build-logic", "policies", "dependency-health", "contract-replay",
]


class ResultGateTest(unittest.TestCase):
    def run_gate(self, results, require_ios="false"):
        env = dict(os.environ, CI_JOB_RESULTS=results, CI_REQUIRE_IOS=require_ios)
        return subprocess.run([sys.executable, str(SCRIPT)], env=env, capture_output=True, text=True)

    def passing_results(self):
        return {name: {"result": "success", "outputs": {}} for name in WORKERS}

    def test_all_workers_must_succeed(self):
        result = self.run_gate(json.dumps(self.passing_results()))
        self.assertEqual(0, result.returncode, result.stderr)

    def test_each_non_success_worker_blocks_the_gate(self):
        for worker in WORKERS:
            for state in ["failure", "cancelled", "skipped", None, ""]:
                with self.subTest(worker=worker, state=state):
                    results = self.passing_results()
                    results[worker]["result"] = state
                    result = self.run_gate(json.dumps(results))
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(worker, result.stderr)

    def test_a_missing_worker_blocks_the_gate(self):
        for worker in WORKERS:
            with self.subTest(worker=worker):
                results = self.passing_results()
                del results[worker]
                self.assertNotEqual(0, self.run_gate(json.dumps(results)).returncode)

    def test_invalid_empty_and_duplicate_results_block_the_gate(self):
        for payload in ["", "{", "{}", "[]", "null", '{"module-checks": {}, "module-checks": {}}']:
            with self.subTest(payload=payload):
                self.assertNotEqual(0, self.run_gate(payload).returncode)

    def test_unknown_or_malformed_workers_block_the_gate(self):
        results = self.passing_results()
        results["unrelated"] = {"result": "success"}
        self.assertNotEqual(0, self.run_gate(json.dumps(results)).returncode)
        results = self.passing_results()
        results["policies"] = "success"
        self.assertNotEqual(0, self.run_gate(json.dumps(results)).returncode)

    def test_the_restored_ios_stage_requires_its_success(self):
        results = self.passing_results()
        self.assertNotEqual(0, self.run_gate(json.dumps(results), "true").returncode)
        results["ios"] = {"result": "success"}
        self.assertEqual(0, self.run_gate(json.dumps(results), "true").returncode)
        results["ios"]["result"] = "skipped"
        self.assertNotEqual(0, self.run_gate(json.dumps(results), "true").returncode)

    def test_an_unknown_ios_stage_blocks_the_gate(self):
        self.assertNotEqual(0, self.run_gate(json.dumps(self.passing_results()), "").returncode)


if __name__ == "__main__":
    unittest.main()
