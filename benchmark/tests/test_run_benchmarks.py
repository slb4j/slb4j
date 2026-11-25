import datetime
import importlib.util
import io
import subprocess
import sys
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
    def test_formats_samples_like_the_reference(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = io.StringIO()
            samples = {
                ("COMPACT", "CONSTANT", True, "jul"): {"jul": ["message", "Exception", "\tat caller"]},
                ("DEFAULT", "CONSTANT", False, "slf4j"): {"jul": ["later"]},
                ("COMPACT", "MESSAGE_SUPPLIER", False, "jul"): {"jul": ["supplier"]},
                ("COMPACT", "ARGUMENTS", False, "jul"): {"jul": ["arguments"]},
                ("COMPACT", "CONSTANT", False, "jcl"): {"jul": ["jcl message"]},
                ("COMPACT", "CONSTANT", False, "jul"): {"jul": ["second"], "slb4j": ["first"]},
            }
            run_benchmarks.write_sample_output(report, samples)
            self.assertEqual(
                "## Example Output\n\n<details>\n<summary>Show console output by format, frontend, and message type</summary>\n\n"
                "### Format: COMPACT, Frontend JUL, CONSTANT\n\n````text\n"
                "SLB4J:      first\nJUL:        second\n````\n\n"
                "### Format: COMPACT, Frontend JCL, CONSTANT\n\n````text\nJUL:        jcl message\n````\n\n"
                "### Format: COMPACT, Frontend JUL, ARGUMENTS\n\n````text\nJUL:        arguments\n````\n\n"
                "### Format: COMPACT, Frontend JUL, CONSTANT (with exception)\n\n````text\n"
                "JUL:        message\n            Exception\n            \tat caller\n````\n\n"
                "### Format: DEFAULT, Frontend SLF4J, CONSTANT\n\n````text\nJUL:        later\n````\n\n</details>\n\n",
                report.getvalue())

    def test_collects_matrix_in_one_gradle_command_per_backend(self):
        args = run_benchmarks.parse_args([
            "--run", "SAMPLE_OUTPUT", "--backends", "jul", "--frontends", "slf4j", "jcl",
            "--formats", "DEFAULT", "COMPACT", "--message-types", "ARGUMENTS",
        ])
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            report = io.StringIO()

            def capture(command):
                params = command.split("sampleDirectory=", 1)[1].split("'", 1)[0]
                for fmt in ("DEFAULT", "COMPACT"):
                    for frontend in ("slf4j", "jcl"):
                        for message_type in run_benchmarks.VALID_MESSAGE_TYPES:
                            for with_exception in (False, True):
                                suffix = "-EXCEPTION" if with_exception else ""
                                text = f"{message_type} message\n"
                                if with_exception:
                                    text += "Exception\n\tat caller\n\n"
                                (Path(params) / f"{fmt}-{frontend}-{message_type}{suffix}.out").write_text(
                                    text, encoding="utf-8")
                return True

            with patch.object(run_benchmarks, "run_command", side_effect=capture) as command:
                samples = run_benchmarks.collect_sample_output(args, directory)

            command.assert_called_once()
            self.assertIn("sampleFrontend=jcl,slf4j", command.call_args.args[0])
            self.assertIn(";sampleMessageType=CONSTANT,ARGUMENTS;", command.call_args.args[0])
            self.assertIn("sampleException=false,true", command.call_args.args[0])
            run_benchmarks.write_sample_output(report, samples)
            text = report.getvalue()
            self.assertEqual(16, text.count("### Format:"))
            self.assertIn("Frontend JCL, ARGUMENTS\n\n````text\nJUL:        ARGUMENTS message\n", text)
            self.assertIn(
                "Frontend SLF4J, CONSTANT\n\n````text\nJUL:        CONSTANT message\n", text)
            self.assertEqual(8, text.count("            \tat caller\n````\n"))

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
            run_benchmarks.collect_sample_output(args, "results")

        commands = [call.args[0] for call in output.call_args_list if call.args and "Command:" in call.args[0]]
        self.assertEqual(2, len(commands))

        benchmark_command, sample_command = commands
        self.assertIn("outputToFile=false", benchmark_command)
        self.assertNotIn("withException=", benchmark_command)
        self.assertNotIn("sampleException=", benchmark_command)
        self.assertIn("-Pjmh.warmupIterations=2", benchmark_command)
        self.assertIn("-Pjmh.iterations=7", benchmark_command)
        self.assertNotIn("jmh.benchmarkMode", benchmark_command)

        self.assertIn("outputToFile=true", sample_command)
        self.assertNotIn("withException=", sample_command)
        self.assertIn("sampleException=false,true", sample_command)
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
            run_benchmarks.collect_sample_output(args, "results")

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

    def test_rejects_removed_message_types(self):
        for message_type in ("MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"):
            with self.subTest(message_type=message_type):
                args = run_benchmarks.parse_args(["--run", "SERIAL", "PARALLEL", "--message-types", message_type])
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

class UnifiedReportTest(unittest.TestCase):
    def test_formats_bars_in_eighths(self):
        for score, blocks in enumerate(("▏", "▎", "▍", "▌", "▋", "▊", "▉", "█"), start=1):
            with self.subTest(score=score):
                self.assertEqual(f'<span style="color: #0072b2">{blocks}</span>',
                                 run_benchmarks.format_table_bar(score, 128, "jul"))
        self.assertEqual('<span style="color: #0072b2">█▏</span>',
                         run_benchmarks.format_table_bar(8.6, 128, "jul"))
        self.assertEqual('<span style="color: #0072b2">▏</span>',
                         run_benchmarks.format_table_bar(0.01, 128, "jul"))
        self.assertEqual("", run_benchmarks.format_table_bar(0, 0, "jul"))

    def test_dry_run_always_schedules_samples_and_passes_formats_to_non_jul_backends(self):
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            completed = subprocess.run([
                sys.executable, str(SCRIPT_PATH), "--dryrun", "--run", "PARALLEL", "--mode", "smoketest",
                "--backends", "logback", "--frontends", "slf4j", "--formats", "COMPACT", "DETAILED",
            ], cwd=directory, text=True, capture_output=True, check=True)
            self.assertEqual(1, completed.stdout.count(r"LgbckBenchmark\.sampleOutput$"))
            self.assertIn(r"LgbckParallelBenchmark\.(slf4j)_.*", completed.stdout)
            self.assertIn(";format=COMPACT,DETAILED;messageType=CONSTANT,ARGUMENTS'", completed.stdout)
            self.assertFalse(list(Path(directory).rglob("*.md")))

    def test_throughput_runners_group_only_compatible_workloads(self):
        args = run_benchmarks.parse_args(["--dryrun", "--run", "SERIAL", "PARALLEL", "--backends", "jul",
                                         "--frontends", "jul", "jcl", "log4j"])
        for collect, benchmark, suffix in ((run_benchmarks.collect_results, "JulBenchmark", "$"),
                                           (run_benchmarks.collect_parallel_results, "JulParallelBenchmark", "_.*")):
            with self.subTest(benchmark=benchmark), patch("builtins.print") as output:
                collect(args, "timestamp", "results")

                commands = [call.args[0] for call in output.call_args_list if "-Pjmh.includes=" in call.args[0]]
                self.assertEqual(2, len(commands))
                for frontends, message_type in (("jul|jcl|log4j", "CONSTANT"), ("jul|log4j", "ARGUMENTS")):
                    matching = [cmd for cmd in commands if f";messageType={message_type}'" in cmd]
                    self.assertEqual(1, len(matching))
                    self.assertIn(rf"{benchmark}\.({frontends}){suffix}", matching[0])
                    self.assertNotIn("Exception=", matching[0])

    def test_combines_all_sections_and_keeps_measurement_dimensions_separate(self):
        def result(method, handler, fmt, score, error=None, backend="logback", **params):
            return {"backend_param": backend, "benchmark": f"benchmark.{method}", "jdkVersion": "27.0.1",
                    "params": {"category": handler, "format": fmt, **params},
                    "primaryMetric": {"score": score, "scoreError": error}}

        serial = [
            result("jul", "CONSOLE", "COMPACT", 100, 1.5, backend="jul", messageType="CONSTANT"),
            result("jul", "CONSOLE", "COMPACT", 80, messageType="ARGUMENTS"),
            result("jul", "CONSOLE", "COMPACT", 9000, backend="slb4j", messageType="MESSAGE_SUPPLIER"),
            result("jul", "CONSOLE", "DEFAULT", 42, messageType="ARGUMENTS"),
            result("slf4j", "CONSOLE", "DETAILED", 56.25, None, messageType="CONSTANT"),
            result("slf4j", "FILE", "COMPACT", 10, "NaN", messageType="CONSTANT"),
            result("slf4j", "FILE", "COMPACT", 5, messageType="ARGUMENTS"),
            result("jul", "CONSOLE", "COMPACT", 125, 42, backend="jul", messageType="CONSTANT", withException="true"),
        ]
        parallel = [
            result("jul_128", "CONSOLE", "COMPACT", 175, 2.5, backend="jul", messageType="ARGUMENTS"),
            result("jul_2", "CONSOLE", "COMPACT", 0, backend="jul", messageType="CONSTANT"),
            result("jul_2", "CONSOLE", "COMPACT", 9000, backend="jul", messageType="MESSAGE_SUPPLIER"),
            result("jul_2", "CONSOLE", "COMPACT", 65, messageType="ARGUMENTS"),
            result("slf4j_1", "CONSOLE", "COMPACT", 50, backend="jul"),
            result("log4j_1", "CONSOLE", "COMPACT", 9000, backend="jul"),
            result("slf4j_2", "FILE", "DETAILED", 1200, 60, messageType="ARGUMENTS"),
            result("slf4j_2", "FILE", "DETAILED", 9000, messageType="LAMBDA_PARAMETER"),
            result("slf4j_4", "FILE", "DETAILED", 600, messageType="CONSTANT"),
        ]
        samples = {
            ("COMPACT", "CONSTANT", False, "jul"): {"logback": ["actual example output"]},
            ("COMPACT", "CONSTANT", True, "jul"): {"logback": ["example", "Sample exception stack trace"]},
        }
        args = run_benchmarks.parse_args(["--run", "SERIAL", "PARALLEL", "--frontends", "jul", "slf4j"])

        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            run_benchmarks.generate_markdown_report(serial[::-1], parallel[::-1], samples, args,
                                                    datetime.datetime(2026, 11, 23, 16, 32, 20),
                                                    directory, "test system", "test command")
            output = Path(directory)
            self.assertEqual(["BENCHMARK_RESULTS.md"], sorted(path.name for path in output.glob("*.md")))
            self.assertFalse(list(output.glob("*.svg")))
            report = (output / "BENCHMARK_RESULTS.md").read_text(encoding="utf-8")
            self.assertEqual(4, report.count("| Frontend | Pattern | Backend |"))
            self.assertNotIn(".svg", report)
            self.assertIn("Date: 2026-11-23 16:32:20", report)
            self.assertIn("Benchmark JVM: 27.0.1", report)
            self.assertIn("test command", report)
            self.assertIn("## Example Output", report)
            examples, throughput = report.split("## Singlethreaded\n\n")
            self.assertIn("actual example output", examples)
            self.assertIn("CONSTANT (with exception)", examples)
            self.assertIn("Sample exception stack trace", examples)
            self.assertNotIn("with exception", throughput)
            self.assertNotIn("125", throughput)

            singlethreaded, multithreaded = throughput.split("## Multithreaded\n\n")
            def table_rows(section):
                return [[cell.strip() for cell in line[1:-1].split("|")]
                        for line in section.splitlines() if line.startswith("|")]

            def handler_tables(section):
                start, console_section = section.split("### CONSOLE\n\n")
                self.assertFalse(start.strip())
                console_section, file_section = console_section.split("### FILE\n\n")
                return table_rows(console_section), table_rows(file_section)

            (serial_header, serial_alignment, *serial_rows), serial_file = handler_tables(singlethreaded)
            (parallel_header, parallel_alignment, *parallel_rows), parallel_file = handler_tables(multithreaded)
            labels = ["Frontend", "Pattern", "Backend"]
            columns = ["CONSTANT", "ARGUMENTS"]
            self.assertEqual(labels + columns, serial_header)
            self.assertEqual(labels + ["Threads"] + columns, parallel_header)
            self.assertEqual([serial_header, serial_alignment], serial_file[:2])
            self.assertEqual([parallel_header, parallel_alignment], parallel_file[:2])
            self.assertEqual(["---"] * 3 + ["---:"] * 2, serial_alignment)
            self.assertEqual(["---"] * 3 + ["---:"] * 3, parallel_alignment)
            self.assertEqual([
                ["JUL", "COMPACT", "JUL"], ["", "", "LOGBACK"], ["", "DEFAULT", "LOGBACK"],
                ["SLF4J", "DETAILED", "LOGBACK"],
            ], [row[:3] for row in serial_rows])
            self.assertEqual([
                ["JUL", "COMPACT", "JUL", "2"], ["", "", "", "128"],
                ["", "", "LOGBACK", "2"], ["SLF4J", "COMPACT", "JUL", "1"],
            ], [row[:4] for row in parallel_rows])
            serial_file_row, = serial_file[2:]
            self.assertEqual(["SLF4J", "COMPACT", "LOGBACK"], serial_file_row[:3])
            self.assertEqual([["SLF4J", "DETAILED", "LOGBACK", "2"], ["", "", "", "4"]],
                             [row[:4] for row in parallel_file[2:]])

            self.assertRegex(serial_rows[0][3], r">████████████████</span>.*100&nbsp;±&nbsp;1</span>$")
            self.assertRegex(serial_rows[1][4], r">████████████▊</span>.*80")
            self.assertRegex(serial_rows[3][3], r">█████████</span>.*56")
            self.assertRegex(serial_file_row[3], r">████████████████</span>.*10</span>$")
            self.assertRegex(serial_file_row[4], r">████████</span>.*5</span>$")
            self.assertIn("color: #0072b2", serial_rows[0][3])
            self.assertIn("color: #d55e00", serial_rows[1][4])
            self.assertIn("color: #d55e00", serial_rows[3][3])
            self.assertEqual("—", serial_rows[0][4])
            self.assertEqual("—", parallel_rows[0][5])
            self.assertRegex(parallel_rows[0][4], r"0</span>$")
            self.assertRegex(parallel_rows[1][5], r">████████████████</span>.*175")
            self.assertRegex(parallel_rows[2][5], r">██████</span>.*65")
            self.assertRegex(parallel_rows[3][5], r">████▋</span>.*50")
            self.assertRegex(parallel_file[2][-1], r">████████████████</span>.*1,200.*±.*60")
            self.assertRegex(parallel_file[3][4], r">████████</span>.*600")
            self.assertNotIn("NaN", report)
            self.assertNotIn("<br>", throughput)

    def test_sample_only_report_does_not_create_empty_tables_or_charts(self):
        args = run_benchmarks.parse_args(["--run", "SAMPLE_OUTPUT"])
        samples = {("DEFAULT", "CONSTANT", True, "slf4j"): {"jul": ["example", "with exception"]}}
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            run_benchmarks.generate_markdown_report([], [], samples, args, datetime.datetime.now(),
                                                    directory, "test system", "test command")
            report, = Path(directory).iterdir()
            self.assertEqual("BENCHMARK_RESULTS.md", report.name)
            text = report.read_text(encoding="utf-8")
            self.assertIn("## Example Output", text)
            self.assertIn("with exception", text)
            self.assertEqual(2, text.count("Not requested for this run."))
            self.assertNotIn("| Frontend |", text)


if __name__ == "__main__":
    unittest.main()
