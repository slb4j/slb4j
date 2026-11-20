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
    def test_initializes_report_only_when_requested(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            args = SimpleNamespace(dryrun=False, sample_output=True)

            result = run_benchmarks.initialize_sample_output(args, directory)

            self.assertEqual(os.path.abspath(report), result)
            self.assertEqual("# Benchmark Sample Outputs\n\n", report.read_text(encoding="utf-8"))

            self.assertIsNone(run_benchmarks.initialize_sample_output(
                SimpleNamespace(dryrun=False, sample_output=False), directory))

    def test_dry_run_does_not_create_a_sample_report(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"

            result = run_benchmarks.initialize_sample_output(
                SimpleNamespace(dryrun=True, sample_output=True), directory)

            self.assertIsNone(result)
            self.assertFalse(report.exists())

    def test_appends_new_benchmark_output(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            output = Path(directory) / "benchmark.out"
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            output.write_text("old output\nactual benchmark output\n", encoding="utf-8")
            report.write_text("# Benchmark Sample Outputs\n\n", encoding="utf-8")

            run_benchmarks.append_sample_output(output, report, "jul", 2, len("old output\n"))

            self.assertTrue(output.exists())
            self.assertEqual(
                "# Benchmark Sample Outputs\n\n## jul (run 3)\n\n````text\n"
                "actual benchmark output\n````\n\n",
                report.read_text(encoding="utf-8"))

    def test_rejects_sample_output_with_more_than_one_line(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            output = Path(directory) / "benchmark.out"
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            output.write_text("first\nsecond\n", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "Expected one line"):
                run_benchmarks.append_sample_output(output, report, "jul", 0)

    def test_sample_output_uses_a_separate_single_shot_fork(self):
        args = SimpleNamespace(
            backends=["jul"], message_types=["CONSTANT"], frontends=["jul"], formats=["DEFAULT"],
            handlers=["CONSOLE"], output_to_file=False, sample_output=True, warmup=2, iterations=7,
            time="10ms", forks=1, profile=None, dryrun=True)

        with patch("builtins.print") as output:
            run_benchmarks.collect_results(args, "timestamp", "results", sample_output_path="samples.md")

        commands = [call.args[0] for call in output.call_args_list if call.args and "Command:" in call.args[0]]
        self.assertEqual(2, len(commands))

        benchmark_command, sample_command = commands
        self.assertIn("outputToFile=false", benchmark_command)
        self.assertIn("-Pjmh.warmupIterations=2", benchmark_command)
        self.assertIn("-Pjmh.iterations=7", benchmark_command)
        self.assertNotIn("jmh.benchmarkMode", benchmark_command)

        self.assertIn("outputToFile=true", sample_command)
        self.assertIn("-Pjmh.benchmarkMode=SingleShotTime", sample_command)
        self.assertIn("-Pjmh.warmupIterations=0", sample_command)
        self.assertIn("-Pjmh.iterations=1", sample_command)
        self.assertIn("-Pjmh.forks=1", sample_command)
        self.assertNotIn("-Pjmh.timeOnIteration", sample_command)

    def test_sample_output_rejects_parallel_only_runs(self):
        args = SimpleNamespace(
            serial=False, parallel=True, sample_output=True, backends=None, handlers=None,
            formats=None, message_types=None)

        self.assertFalse(run_benchmarks.validate_args(args))

    def test_compiles_all_benchmark_modules_in_one_gradle_invocation(self):
        with patch.object(run_benchmarks, "run_command", return_value=True) as command:
            self.assertTrue(run_benchmarks.compile_benchmarks(SimpleNamespace(dryrun=False)))

        command.assert_called_once()
        build_command = command.call_args.args[0]
        self.assertTrue(build_command.startswith("./gradlew --offline --quiet "))
        for backend in run_benchmarks.SEQUENTIAL_BACKENDS_MAP:
            self.assertIn(f":benchmark:benchmark-{backend}:jmhJar", build_command)

    def test_dry_run_does_not_compile_benchmarks(self):
        with patch.object(run_benchmarks, "run_command") as command:
            self.assertTrue(run_benchmarks.compile_benchmarks(SimpleNamespace(dryrun=True)))
        command.assert_not_called()


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
