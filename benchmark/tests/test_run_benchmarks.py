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
            args = SimpleNamespace(dryrun=False, run=["SAMPLE_OUTPUT"])

            result = run_benchmarks.initialize_sample_output(args, directory)

            self.assertEqual(os.path.abspath(report), result)
            self.assertEqual("# Benchmark Sample Outputs\n\n", report.read_text(encoding="utf-8"))

            self.assertIsNone(run_benchmarks.initialize_sample_output(
                SimpleNamespace(dryrun=False, run=["SERIAL"]), directory))

    def test_dry_run_does_not_create_a_sample_report(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"

            result = run_benchmarks.initialize_sample_output(
                SimpleNamespace(dryrun=True, run=["SAMPLE_OUTPUT"]), directory)

            self.assertIsNone(result)
            self.assertFalse(report.exists())

    def test_formats_samples_like_the_reference(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"
            samples = {
                ("COMPACT", "CONSTANT_EXCEPTION", "jul"): {"jul": ["message", "Exception", "\tat caller"]},
                ("DEFAULT", "CONSTANT", "slf4j"): {"jul": ["later"]},
                ("COMPACT", "LAMBDA", "jul"): {"jul": ["lambda"]},
                ("COMPACT", "ARGUMENTS", "jul"): {"jul": ["arguments"]},
                ("COMPACT", "CONSTANT", "jcl"): {"jul": ["jcl message"]},
                ("COMPACT", "CONSTANT", "jul"): {"jul": ["second"], "slb4j": ["first"]},
            }
            run_benchmarks.write_sample_output(report, samples)
            self.assertEqual(
                "# Benchmark Sample Outputs\n\n"
                "## Format: COMPACT, Frontend JUL, CONSTANT\n\n````text\n"
                "SLB4J:      first\nJUL:        second\n````\n\n"
                "## Format: COMPACT, Frontend JCL, CONSTANT\n\n````text\nJUL:        jcl message\n````\n\n"
                "## Format: COMPACT, Frontend JUL, ARGUMENTS\n\n````text\nJUL:        arguments\n````\n\n"
                "## Format: COMPACT, Frontend JUL, LAMBDA\n\n````text\nJUL:        lambda\n````\n\n"
                "## Format: COMPACT, Frontend JUL, CONSTANT (with exception)\n\n````text\n"
                "JUL:        message\n            Exception\n            \tat caller\n````\n\n"
                "## Format: DEFAULT, Frontend SLF4J, CONSTANT\n\n````text\nJUL:        later\n````\n\n",
                report.read_text(encoding="utf-8"))

    def test_collects_matrix_in_one_gradle_command_per_backend(self):
        args = run_benchmarks.parse_args([
            "--run", "SAMPLE_OUTPUT", "--backends", "jul", "--frontends", "slf4j", "jcl",
            "--formats", "DEFAULT", "COMPACT", "--message-types", "LAMBDA_PARAMETER",
        ])
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = Path(directory) / "SAMPLE_OUTPUT.md"

            def capture(command):
                params = command.split("sampleDirectory=", 1)[1].split("'", 1)[0]
                for fmt in ("DEFAULT", "COMPACT"):
                    for frontend in ("slf4j", "jcl"):
                        for variant in ("CONSTANT", "ARGUMENTS", "LAMBDA", "CONSTANT_EXCEPTION"):
                            text = f"{variant} message\n"
                            if variant == "CONSTANT_EXCEPTION":
                                text += "Exception\n\tat caller\n\n"
                            (Path(params) / f"{fmt}-{frontend}-{variant}.out").write_text(text, encoding="utf-8")
                return True

            with patch.object(run_benchmarks, "run_command", side_effect=capture) as command:
                run_benchmarks.collect_sample_output(args, directory, report)

            command.assert_called_once()
            self.assertIn("sampleFrontend=jcl,slf4j", command.call_args.args[0])
            self.assertIn("sampleVariant=CONSTANT,ARGUMENTS,LAMBDA,CONSTANT_EXCEPTION", command.call_args.args[0])
            text = report.read_text(encoding="utf-8")
            self.assertEqual(16, text.count("## Format:"))
            self.assertIn("Frontend JCL, ARGUMENTS\n\n````text\nJUL:        ARGUMENTS message\n", text)
            self.assertIn("Frontend SLF4J, LAMBDA\n\n````text\nJUL:        LAMBDA message\n", text)
            self.assertEqual(4, text.count("            \tat caller\n````\n"))

    def test_reads_single_line_benchmark_output(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            output = Path(directory) / "benchmark.out"
            output.write_text("actual benchmark output\n", encoding="utf-8")
            self.assertEqual(["actual benchmark output"], run_benchmarks.read_sample_output(output, False))

    def test_rejects_sample_output_with_more_than_one_line(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            output = Path(directory) / "benchmark.out"
            output.write_text("first\nsecond\n", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "Expected one line"):
                run_benchmarks.read_sample_output(output, False)

    def test_serial_and_sample_output_use_a_separate_single_shot_fork(self):
        args = SimpleNamespace(
            backends=["jul"], message_types=["CONSTANT"], frontends=["jul"], formats=["DEFAULT"],
            handlers=["CONSOLE"], output_to_file=False, run=["SERIAL", "SAMPLE_OUTPUT"], warmup=2, iterations=7,
            time="10ms", forks=1, profile=None, dryrun=True)

        with patch("builtins.print") as output:
            run_benchmarks.collect_results(args, "timestamp", "results")
            run_benchmarks.collect_sample_output(args, "results", None)

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

    def test_sample_only_runs_only_the_sample_fork(self):
        args = SimpleNamespace(
            backends=["jul"], message_types=["CONSTANT"], frontends=["jul"], formats=["DEFAULT"],
            handlers=["CONSOLE"], output_to_file=False, run=["SAMPLE_OUTPUT"], warmup=2, iterations=7,
            time="10ms", forks=1, profile=None, dryrun=True)

        with patch("builtins.print") as output:
            run_benchmarks.collect_sample_output(args, "results", None)

        commands = [call.args[0] for call in output.call_args_list if call.args and "Command:" in call.args[0]]
        self.assertEqual(1, len(commands))
        self.assertIn("outputToFile=true", commands[0])
        self.assertIn("-Pjmh.benchmarkMode=SingleShotTime", commands[0])
        self.assertIn("-Pjmh.iterations=1", commands[0])
        self.assertNotIn("outputToFile=false", commands[0])

    def test_parallel_and_sample_output_are_valid_together(self):
        args = SimpleNamespace(
            run=["PARALLEL", "SAMPLE_OUTPUT"], backends=["jul"], handlers=None,
            frontends=None, formats=None, message_types=None)

        self.assertTrue(run_benchmarks.validate_args(args))

    def test_run_option_is_required_and_accepts_multiple_run_types(self):
        with self.assertRaises(SystemExit):
            run_benchmarks.parse_args([])

        args = run_benchmarks.parse_args(["--run", "SERIAL", "SAMPLE_OUTPUT"])
        self.assertEqual(["SERIAL", "SAMPLE_OUTPUT"], args.run)

        with self.assertRaises(SystemExit):
            run_benchmarks.parse_args(["--run", "UNKNOWN"])

        with self.assertRaises(SystemExit):
            run_benchmarks.parse_args(["--serial"])

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
