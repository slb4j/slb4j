import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = PROJECT_ROOT / "run_benchmarks.py"
SPEC = importlib.util.spec_from_file_location("run_benchmarks", SCRIPT_PATH)
run_benchmarks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(run_benchmarks)


class SampleOutputRunnerTest(unittest.TestCase):
    def setUp(self):
        self.previous_value = os.environ.get(run_benchmarks.SAMPLE_OUTPUT_ENV)

    def tearDown(self):
        if self.previous_value is None:
            os.environ.pop(run_benchmarks.SAMPLE_OUTPUT_ENV, None)
        else:
            os.environ[run_benchmarks.SAMPLE_OUTPUT_ENV] = self.previous_value

    def test_initializes_one_absolute_report_path_for_the_invocation(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            args = SimpleNamespace(dryrun=False)

            result = run_benchmarks.initialize_sample_output(args, directory)

            self.assertEqual(os.path.abspath(report), result)
            self.assertEqual(result, os.environ[run_benchmarks.SAMPLE_OUTPUT_ENV])
            text = report.read_text(encoding="utf-8")
            headings = [line for line in text.splitlines() if line.startswith("## ")]
            self.assertEqual(24, len(headings))
            for format_name in ("COMPACT", "DEFAULT", "DETAILED"):
                for frontend in ("JUL", "JCL", "LOG4J", "SLF4J"):
                    for suffix in ("", " (with exception)"):
                        self.assertIn(f"## Format: {format_name}, Frontend {frontend}{suffix}", headings)

    def test_dry_run_does_not_create_a_sample_report_or_change_environment(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            os.environ[run_benchmarks.SAMPLE_OUTPUT_ENV] = "existing-path"

            result = run_benchmarks.initialize_sample_output(SimpleNamespace(dryrun=True), directory)

            self.assertIsNone(result)
            self.assertFalse(report.exists())
            self.assertEqual("existing-path", os.environ[run_benchmarks.SAMPLE_OUTPUT_ENV])

    def test_collection_covers_all_backends_and_formats_even_for_filtered_runs(self):
        args = SimpleNamespace(dryrun=False, backends=["jul"], formats=["DEFAULT"], parallel=True)
        with patch.object(run_benchmarks, "run_command", return_value=True) as command:
            self.assertTrue(run_benchmarks.collect_sample_output(args))
        self.assertEqual(len(run_benchmarks.SEQUENTIAL_BACKENDS_MAP), command.call_count)
        for backend, benchmark in run_benchmarks.SEQUENTIAL_BACKENDS_MAP.items():
            commands = [call.args[0] for call in command.call_args_list if f"benchmark-{backend}:jmh" in call.args[0]]
            self.assertEqual(1, len(commands))
            self.assertIn(benchmark, commands[0])
            self.assertIn("format=COMPACT,DEFAULT,DETAILED", commands[0])
            self.assertIn("category=CONSOLE", commands[0])

    def test_sample_collection_failure_is_reported(self):
        with patch.object(run_benchmarks, "run_command", return_value=False):
            self.assertFalse(run_benchmarks.collect_sample_output(SimpleNamespace(dryrun=False)))

    def test_validation_requires_exactly_one_sample_for_every_combination(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            markers = [
                f"<!-- sample-output:{fmt}:{frontend}:{kind}:{backend.upper()} -->"
                for fmt in run_benchmarks.VALID_FORMATS
                for frontend in run_benchmarks.SAMPLE_FRONTENDS
                for kind in ("plain", "exception")
                for backend in run_benchmarks.SEQUENTIAL_BACKENDS_MAP
            ]
            report.write_text("\n".join(markers), encoding="utf-8")
            self.assertTrue(run_benchmarks.validate_sample_output(report))
            report.write_text("\n".join(markers[:-1]), encoding="utf-8")
            self.assertFalse(run_benchmarks.validate_sample_output(report))
            report.write_text("\n".join(markers + markers[:1]), encoding="utf-8")
            self.assertFalse(run_benchmarks.validate_sample_output(report))

    def test_results_directories_do_not_collide(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            previous = os.getcwd()
            try:
                os.chdir(directory)
                first = run_benchmarks.create_results_directory("20261118_000000")
                second = run_benchmarks.create_results_directory("20261118_000000")
                self.assertNotEqual(first, second)
                self.assertTrue(Path(first).is_dir())
                self.assertTrue(Path(second).is_dir())
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
