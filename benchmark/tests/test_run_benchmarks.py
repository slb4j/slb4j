import importlib.util
import os
import tempfile
import unittest
import xml.etree.ElementTree as ET
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
                ("COMPACT", "CONSTANT", True, "jul"): {"jul": ["message", "Exception", "\tat caller"]},
                ("DEFAULT", "CONSTANT", False, "slf4j"): {"jul": ["later"]},
                ("COMPACT", "MESSAGE_SUPPLIER", False, "jul"): {"jul": ["supplier"]},
                ("COMPACT", "ARGUMENTS", False, "jul"): {"jul": ["arguments"]},
                ("COMPACT", "CONSTANT", False, "jcl"): {"jul": ["jcl message"]},
                ("COMPACT", "CONSTANT", False, "jul"): {"jul": ["second"], "slb4j": ["first"]},
            }
            run_benchmarks.write_sample_output(report, samples)
            self.assertEqual(
                "# Benchmark Sample Outputs\n\n"
                "## Format: COMPACT, Frontend JUL, CONSTANT\n\n````text\n"
                "SLB4J:      first\nJUL:        second\n````\n\n"
                "## Format: COMPACT, Frontend JCL, CONSTANT\n\n````text\nJUL:        jcl message\n````\n\n"
                "## Format: COMPACT, Frontend JUL, ARGUMENTS\n\n````text\nJUL:        arguments\n````\n\n"
                "## Format: COMPACT, Frontend JUL, MESSAGE_SUPPLIER\n\n````text\nJUL:        supplier\n````\n\n"
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
                run_benchmarks.collect_sample_output(args, directory, report)

            command.assert_called_once()
            self.assertIn("sampleFrontend=jcl,slf4j", command.call_args.args[0])
            self.assertIn(
                "sampleMessageType=CONSTANT,ARGUMENTS,MESSAGE_SUPPLIER,LAMBDA_PARAMETER",
                command.call_args.args[0])
            self.assertIn("sampleException=false,true", command.call_args.args[0])
            text = report.read_text(encoding="utf-8")
            self.assertEqual(32, text.count("## Format:"))
            self.assertIn("Frontend JCL, ARGUMENTS\n\n````text\nJUL:        ARGUMENTS message\n", text)
            self.assertIn(
                "Frontend SLF4J, LAMBDA_PARAMETER\n\n````text\nJUL:        LAMBDA_PARAMETER message\n", text)
            self.assertEqual(16, text.count("            \tat caller\n````\n"))

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

    def test_sequential_report_contains_side_by_side_message_type_bars_per_frontend(self):
        results = []
        for frontend, backend, message_type, score in (
                ("jcl", "jul", "CONSTANT", 100.0),
                ("jcl", "slb4j", "CONSTANT", 300.0),
                ("log4j", "logback", "MESSAGE_SUPPLIER", 250.0),
                ("slf4j", "jul", "CONSTANT", 200.0),
                ("slf4j", "jul", "ARGUMENTS", 150.0),
                ("slf4j", "jul", "MESSAGE_SUPPLIER", 175.0),
                ("slf4j", "jul", "LAMBDA_PARAMETER", 125.0),
                ("slf4j", "slb4j", "CONSTANT", 400.0),
                ("slf4j", "slb4j", "ARGUMENTS", 350.0),
                ("slf4j", "slb4j", "LAMBDA_PARAMETER", 325.0)):
            results.append({
                "backend_param": backend,
                "benchmark": f"benchmark.{frontend}",
                "params": {"category": "CONSOLE", "format": "DEFAULT", "messageType": message_type},
                "primaryMetric": {"score": score, "scoreError": 1.0},
            })

        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            run_benchmarks.generate_markdown_sequential(
                results, SimpleNamespace(), "timestamp", directory, "system", "command")
            report = (Path(directory) / "BENCHMARK_RESULTS.md").read_text(encoding="utf-8")
            self.assertEqual(3, report.count("](benchmark-chart-"))
            ns = {"": "http://www.w3.org/2000/svg"}
            for index, frontend, details in (
                    (1, "jcl", ", Message Type Constant"),
                    (2, "log4j", ", Backend logback, Message Type Message Supplier"),
                    (3, "slf4j", "")):
                filename = f"benchmark-chart-1-{index}.svg"
                title = f"Frontend {frontend}, Target CONSOLE{details}, Throughput (higher is better)"
                self.assertIn(f"#### Frontend: {frontend}\n\n![{title}]({filename})", report)
                chart = ET.parse(Path(directory) / filename).getroot()
                self.assertEqual(title, chart.findtext("title", namespaces=ns))
                self.assertEqual(title, chart.findtext("g/text", namespaces=ns))

            for index, expected_groups in (
                    (1, [[100.0], [300.0]]),
                    (3, [[200.0, 150.0, 175.0, 125.0], [400.0, 350.0, None, 325.0]])):
                chart = ET.parse(Path(directory) / f"benchmark-chart-1-{index}.svg").getroot()
                legend = chart.findall(".//g[@class='legend-item']", ns)
                labels = [item.findtext("text", namespaces=ns) for item in legend]
                self.assertEqual(["Constant", "Argument", "Message Supplier", "Lambda Parameter"]
                                 [:len(expected_groups[0])], labels)
                colors = [item.find("rect", ns).get("fill") for item in legend]
                self.assertEqual(len(colors), len(set(colors)))

                groups = chart.findall(".//g[@class='backend']", ns)
                self.assertEqual(["jul", "slb4j"], [group.findtext("text", namespaces=ns) for group in groups])
                previous_right = 0
                scale = None
                for group, scores in zip(groups, expected_groups):
                    bars = group.findall("rect", ns)
                    expected = [(score, color) for score, color in zip(scores, colors) if score is not None]
                    self.assertEqual(len(expected), len(bars))
                    for bar, (score, color) in zip(bars, expected):
                        x, width, height = (float(bar.get(attr)) for attr in ("x", "width", "height"))
                        self.assertGreater(x, previous_right)
                        self.assertGreater(width, 0)
                        self.assertGreater(height, 0)
                        self.assertEqual(color, bar.get("fill"))
                        if scale is None:
                            scale = height / score
                        self.assertAlmostEqual(height / score, scale)
                        previous_right = x + width

    def test_parallel_report_keeps_targets_formats_and_frontends_separate(self):
        results = []
        for category, fmt, backend, frontend, threads, score in (
                ("FILE", None, "rainbowgum", "jcl", 1, 900),
                ("CONSOLE", "COMPACT", "jul", "jcl", 1, 800),
                ("CONSOLE", None, "rainbowgum", "slf4j", 1, 700),
                ("CONSOLE", None, "rainbowgum", "jcl", 16, 300),
                ("CONSOLE", "DEFAULT", "jul", "jcl", 1, 100),
                ("CONSOLE", "DEFAULT", "jul", "jcl", 16, 200),
                ("CONSOLE", "DEFAULT", "jul", "jcl", 2, 150)):
            params = {"category": category}
            if fmt is not None:
                params["format"] = fmt
            results.append({"backend_param": backend, "benchmark": f"benchmark.{frontend}_{threads}",
                            "params": params, "primaryMetric": {"score": score}})

        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            run_benchmarks.generate_markdown_parallel(
                results, SimpleNamespace(), "timestamp", directory, "system", "command")
            report = (Path(directory) / "PARALLEL_BENCHMARK_RESULTS.md").read_text(encoding="utf-8")
            console, file = report.split("### Handler: FILE")
            compact, default = console.split("#### Format: DEFAULT")
            self.assertEqual(1, compact.count("](parallel-benchmark-chart-"))
            self.assertEqual(2, default.count("](parallel-benchmark-chart-"))
            self.assertEqual(1, file.count("](parallel-benchmark-chart-"))
            self.assertIn("| rainbowgum | jcl | — | — | **300.00** |", default)

            ns = {"": "http://www.w3.org/2000/svg"}
            for filename, details in (
                    ("1-1", "Frontend jcl, Target CONSOLE, Backend jul, Message Type Preformatted"),
                    ("2-1", "Frontend jcl, Target CONSOLE, Message Type Preformatted"),
                    ("2-2", "Frontend slf4j, Target CONSOLE, Backend rainbowgum, Message Type Argument"),
                    ("3-1", "Frontend jcl, Target FILE, Backend rainbowgum, Message Type Preformatted")):
                filename = f"parallel-benchmark-chart-{filename}.svg"
                title = f"{details}, Throughput (higher is better)"
                self.assertIn(f"![{title}]({filename})", report)
                chart = ET.parse(Path(directory) / filename).getroot()
                self.assertEqual(title, chart.findtext("title", namespaces=ns))
                self.assertEqual(title, chart.findtext("g/text", namespaces=ns))

            chart = ET.parse(Path(directory) / "parallel-benchmark-chart-2-1.svg").getroot()
            legend = chart.findall(".//g[@class='legend-item']", ns)
            self.assertEqual(["1 Thread", "2 Threads", "16 Threads"],
                             [item.findtext("text", namespaces=ns) for item in legend])
            colors = [item.find("rect", ns).get("fill") for item in legend]
            self.assertEqual(3, len(set(colors)))
            jul, rainbowgum = chart.findall(".//g[@class='backend']", ns)
            self.assertEqual(["jul", "rainbowgum"],
                             [group.findtext("text", namespaces=ns) for group in (jul, rainbowgum)])
            bars = jul.findall("rect", ns)
            self.assertEqual(colors, [bar.get("fill") for bar in bars])
            for left, right in zip(bars, bars[1:]):
                self.assertLess(float(left.get("x")) + float(left.get("width")), float(right.get("x")))
            bar, = rainbowgum.findall("rect", ns)
            self.assertEqual(colors[2], bar.get("fill"))
            self.assertIn("16 Threads: 300.00 ops/s", bar.findtext("title", namespaces=ns))


if __name__ == "__main__":
    unittest.main()
