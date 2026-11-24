#!/usr/bin/env python3
#
# Copyright 2026 Axel Howind - axh@dua3.com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
import re
import os
import subprocess
import json
import shutil
import argparse
import datetime
import sys
import platform
import math
import shlex
import tempfile
from itertools import product
from html import escape

# Constants
SEQUENTIAL_BACKENDS_MAP = {
    "slb4j":      "Slb4jBenchmark",
    "log4j":      "Log4jBenchmark",
    "logback":    "LgbckBenchmark",
    "rainbowgum": "RbGumBenchmark",
    "jul":        "JulBenchmark"
}

PARALLEL_BACKENDS_MAP = {
    "slb4j":      "Slb4jParallelBenchmark",
    "log4j":      "Log4jParallelBenchmark",
    "logback":    "LgbckParallelBenchmark",
    "rainbowgum": "RbGumParallelBenchmark",
    "jul":        "JulParallelBenchmark"
}

VALID_HANDLERS = ["CONSOLE", "FILE"]
VALID_FORMATS = ["COMPACT", "DEFAULT", "DETAILED"]
SAMPLE_FRONTENDS = ["jul", "jcl", "log4j", "slf4j"]
VALID_MESSAGE_TYPES = ["CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]
VALID_RUN_TYPES = ["SERIAL", "PARALLEL", "SAMPLE_OUTPUT"]
PARALLEL_THREADS = (1, 2, 4, 8, 16, 64, 128)
REPORT_FRONTENDS = ("jul", "log4j", "slf4j", "jcl")
MESSAGE_COLUMNS = {
    "CONSTANT": "CONSTANT",
    "MESSAGE_SUPPLIER": "MESSAGE SUPPLIER",
    "ARGUMENTS": "ARGUMENTS",
    "LAMBDA_PARAMETER": "LAMBDA_PARAMETER",
}
BACKEND_COLORS = {
    "jul": "#0072b2",
    "log4j": "#008659",
    "logback": "#d55e00",
    "rainbowgum": "#9c518a",
    "slb4j": "#a6761d",
}
LEGACY_PARALLEL_MESSAGE_TYPES = {"jul": "ARGUMENTS", "log4j": "MESSAGE_SUPPLIER", "slf4j": "ARGUMENTS", "jcl": "CONSTANT"}
TABLE_BAR_WIDTH = 16

# ANSI colors
BLACK = "\033[0;30m"
DARK_GRAY = "\033[90m"
RED = "\033[0;31m"
BOLD_RED = "\033[1;31m"
LIGHT_RED = "\033[91m"
GREEN = "\033[0;32m"
BOLD_GREEN = "\033[1;32m"
LIGHT_GREEN = "\033[92m"
YELLOW = "\033[0;33m"
BOLD_YELLOW = "\033[1;33m"
LIGHT_YELLOW = "\033[93m"
BLUE = "\033[0;34m"
BOLD_BLUE = "\033[1;34m"
LIGHT_BLUE = "\033[94m"
MAGENTA = "\033[0;35m"
BOLD_MAGENTA = "\033[1;35m"
LIGHT_MAGENTA = "\033[95m"
CYAN = "\033[0;36m"
BOLD_CYAN = "\033[1;36m"
LIGHT_CYAN = "\033[96m"
WHITE = "\033[0;37m"
BOLD_WHITE = "\033[1;37m"

# Special Styles
BOLD = "\033[1m"
UNDERLINE = "\033[4m"
REVERSE = "\033[7m"
RESET = "\033[0m"

def parse_time(time_str):
    if not time_str:
        return 1.0
    if time_str.endswith("ms"):
        return float(time_str[:-2]) / 1000.0
    if time_str.endswith("s"):
        return float(time_str[:-1])
    return float(time_str)

def format_duration(seconds):
    if seconds < 1.0:
        return f"{seconds*1000:.0f}ms"
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    seconds = int(seconds % 60)
    if hours > 0:
        return f"{hours}h {minutes}m {seconds}s"
    elif minutes > 0:
        return f"{minutes}m {seconds}s"
    else:
        return f"{seconds}s"

def get_system_info():
    info = []
    info.append(f"OS: {platform.system()} {platform.release()} ({platform.machine()})")
    try:
        cpu_info = subprocess.check_output("sysctl -n machdep.cpu.brand_string", shell=True).decode().strip()
        info.append(f"CPU: {cpu_info}")
    except:
        info.append(f"CPU: {platform.processor()}")
    
    try:
        java_version = subprocess.check_output("java -version 2>&1 | head -n 1", shell=True).decode().strip()
        info.append(f"Java: {java_version}")
    except:
        info.append("Java: Unknown")
    
    info.append(f"Python: {platform.python_version()}")
    return "\n".join(info)

def validate_args(args):
    # Sample output uses the sequential benchmark implementations.
    for b in args.backends or SEQUENTIAL_BACKENDS_MAP:
        if b not in SEQUENTIAL_BACKENDS_MAP:
            print(f"{BOLD_RED}Error: Invalid sequential backend {b}. Valid backends are: {list(SEQUENTIAL_BACKENDS_MAP)}{RESET}")
            return False

    # Validate backends for parallel if needed
    if "PARALLEL" in args.run:
        backends_map = PARALLEL_BACKENDS_MAP
        backends = args.backends if args.backends else list(backends_map.keys())
        for b in backends:
            if b not in backends_map:
                print(f"{BOLD_RED}Error: Invalid parallel backend {b}. Valid backends are: {list(backends_map.keys())}{RESET}")
                return False
    
    if args.frontends and any(f not in SAMPLE_FRONTENDS for f in args.frontends):
        print(f"{BOLD_RED}Error: Sample frontends must be one of: {SAMPLE_FRONTENDS}{RESET}")
        return False

    if args.handlers:
        for c in args.handlers:
            if c not in VALID_HANDLERS:
                print(f"{BOLD_RED}Error: Invalid handler {c}. Valid handlers are: {VALID_HANDLERS}{RESET}")
                return False
                
    if args.formats:
        for f in args.formats:
            if f not in VALID_FORMATS:
                print(f"{BOLD_RED}Error: Invalid format {f}. Valid formats are: {VALID_FORMATS}{RESET}")
                return False
                
    if args.message_types:
        for mt in args.message_types:
            if mt not in VALID_MESSAGE_TYPES:
                print(f"{BOLD_RED}Error: Invalid message type {mt}. Valid message types are: {VALID_MESSAGE_TYPES}{RESET}")
                return False

    return True

def run_command(command, output_file=None):
    print(f"Running: {command}")
    if output_file:
        with open(output_file, "w") as f:
            process = subprocess.Popen(command, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            for line in process.stdout:
                sys.stdout.write(line)
                f.write(line)
            process.wait()
    else:
        process = subprocess.Popen(command, shell=True, text=True)
        process.wait()
    return process.returncode == 0

def benchmark_task(backend):
    return f":benchmark:benchmark-{backend}:jmh"

def benchmark_run_command(backend):
    task = benchmark_task(backend)
    return f"./gradlew --offline --quiet {task} -x {task}Jar -x :jar -x :benchmark:jar"

def compile_benchmarks(args):
    tasks = " ".join(f"{benchmark_task(backend)}Jar" for backend in SEQUENTIAL_BACKENDS_MAP)
    command = f"./gradlew --offline --quiet {tasks}"
    if args.dryrun:
        print(f"{LIGHT_YELLOW}Dry run (compile): {command}{RESET}")
        return True
    return run_command(command)

def read_sample_output(output_path, with_exception):
    with open(output_path, encoding="utf-8") as output:
        text = output.read()
    if len(text) == 0:
        return ["N/A"]

    lines = (text.rstrip("\n") if with_exception else text).splitlines()
    if not with_exception and (len(lines) != 1 or not lines[0]):
        raise RuntimeError(f"Expected one line of sample output in {output_path}, got {len(lines)}")
    if with_exception and (len(lines) < 2 or not lines[0]):
        raise RuntimeError(f"Expected sample output with a stack trace in {output_path}")
    return lines

def write_sample_output(report, samples):
    label_width = max(map(len, SEQUENTIAL_BACKENDS_MAP)) + 2
    report.write("## Example Output\n\n")
    report.write("<details>\n<summary>Show console output by format, frontend, and message type</summary>\n\n")
    for fmt in VALID_FORMATS:
        for with_exception in (False, True):
            for message_type in VALID_MESSAGE_TYPES:
                title = message_type + (" (with exception)" if with_exception else "")
                for frontend in SAMPLE_FRONTENDS:
                    section = samples.get((fmt, message_type, with_exception, frontend))
                    if not section:
                        continue
                    report.write(f"### Format: {fmt}, Frontend {frontend.upper()}, {title}\n\n````text\n")
                    for backend in SEQUENTIAL_BACKENDS_MAP:
                        if backend in section:
                            first, *rest = section[backend]
                            label = backend.upper() + ":"
                            report.write(f"{label:<{label_width}}{first}\n")
                            for line in rest:
                                report.write(" " * label_width + line + "\n")
                    report.write("````\n\n")
    report.write("</details>\n\n")

def collect_backend_samples(backend, formats, frontends, directory, dryrun, samples):
    include = f"{SEQUENTIAL_BACKENDS_MAP[backend]}\\.sampleOutput$"
    params = [
        "outputToFile=true", "category=CONSOLE", "messageType=CONSTANT",
        f"format={','.join(formats)}", f"sampleFrontend={','.join(frontends)}",
        f"sampleMessageType={','.join(VALID_MESSAGE_TYPES)}", "sampleException=false,true",
        f"sampleDirectory={directory}",
    ]
    command = benchmark_run_command(backend) + f" -Pjmh.includes='{include}'"
    command += " -Pjmh.parameters=" + shlex.quote(";".join(params))
    command += " -Pjmh.benchmarkMode=SingleShotTime"
    command += " -Pjmh.warmupIterations=0 -Pjmh.iterations=1 -Pjmh.forks=1"
    command += " -Pjmh.jvmArgs='-Djmh.ignoreLock=true'"
    if dryrun:
        print(f"  Command: {command}")
        return
    if not run_command(command):
        raise RuntimeError(f"Sample output collection failed for backend: {backend}")
    for fmt in formats:
        for frontend in frontends:
            for message_type in VALID_MESSAGE_TYPES:
                for with_exception in (False, True):
                    suffix = "-EXCEPTION" if with_exception else ""
                    path = os.path.join(directory, f"{fmt}-{frontend}-{message_type}{suffix}.out")
                    lines = read_sample_output(path, with_exception)
                    samples.setdefault((fmt, message_type, with_exception, frontend), {})[backend] = lines

def collect_sample_output(args, results_dir):
    formats = [fmt for fmt in VALID_FORMATS if not args.formats or fmt in args.formats]
    frontends = [frontend for frontend in SAMPLE_FRONTENDS if not args.frontends or frontend in args.frontends]
    samples = {}
    for backend in SEQUENTIAL_BACKENDS_MAP:
        if args.backends and backend not in args.backends:
            continue
        if args.dryrun:
            collect_backend_samples(backend, formats, frontends, "<sample-directory>", True, samples)
        else:
            with tempfile.TemporaryDirectory(dir=os.path.abspath(results_dir)) as directory:
                collect_backend_samples(backend, formats, frontends, directory, False, samples)
    return samples

def create_results_directory(timestamp):
    os.makedirs("benchmark_results", exist_ok=True)
    suffix = 0
    while True:
        name = timestamp if suffix == 0 else f"{timestamp}_{suffix}"
        results_dir = os.path.join("benchmark_results", name)
        try:
            os.mkdir(results_dir)
            return results_dir
        except FileExistsError:
            suffix += 1

def get_estimated_runtime(args):
    return calculate_runtime(args)

def parallel_workload_groups(args):
    groups = {}
    for message in (args.message_types or VALID_MESSAGE_TYPES):
        frontends = tuple(frontend for frontend in (args.frontends or SAMPLE_FRONTENDS)
                          if (frontend != "jcl" or message == "CONSTANT")
                          and (frontend != "jul" or message != "LAMBDA_PARAMETER"))
        if frontends:
            groups.setdefault(frontends, []).append(message)
    return groups

def calculate_runtime(args):
    run_types = set(args.run)
    run_serial = "SERIAL" in run_types
    run_parallel = "PARALLEL" in run_types
    
    total_runtime = 0.0
    
    warmup = args.warmup if args.warmup is not None else 2
    iterations = args.iterations if args.iterations is not None else 3
    time_per_iter = parse_time(args.time if args.time else "1s")
    
    gradle_startup_overhead = 15.0
    forks = 1
    if args.forks is not None:
        forks = args.forks
    jmh_fork_overhead = 0.1 if forks > 0 else 0
    
    time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead

    if run_serial:
        selected_backends = args.backends if args.backends else list(SEQUENTIAL_BACKENDS_MAP.keys())
        num_backends = len(selected_backends)
        
        num_handlers = len(args.handlers or VALID_HANDLERS)
        num_formats = len(args.formats or VALID_FORMATS)
        workloads = sum(1 for frontend in (args.frontends or SAMPLE_FRONTENDS)
                        for message in (args.message_types or VALID_MESSAGE_TYPES)
                        if (frontend != "jcl" or message == "CONSTANT")
                        and (frontend != "jul" or message != "LAMBDA_PARAMETER"))
        total_benchmarks_per_backend = workloads * num_handlers * num_formats
        
        total_runtime += num_backends * (gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark)

    if run_parallel:
        selected_backends = args.backends if args.backends else list(PARALLEL_BACKENDS_MAP.keys())
        groups = parallel_workload_groups(args)
        workloads = sum(len(frontends) * len(messages) for frontends, messages in groups.items())
        total_benchmarks_per_backend = (workloads * len(PARALLEL_THREADS)
                                        * len(args.handlers or VALID_HANDLERS) * len(args.formats or VALID_FORMATS))
        total_runtime += len(selected_backends) * (len(groups) * gradle_startup_overhead
                                                  + total_benchmarks_per_backend * time_per_benchmark)

    selected_backends = args.backends or SEQUENTIAL_BACKENDS_MAP
    num_samples = len(VALID_MESSAGE_TYPES) * 2 * len(args.formats or VALID_FORMATS) * len(args.frontends or SAMPLE_FRONTENDS)
    total_runtime += len(selected_backends) * (gradle_startup_overhead + num_samples * 0.1)

    return total_runtime

def move_profiler_outputs(backend, results_dir, profiler_subdir):
    # Determine destination directory
    dest_dir = os.path.join(results_dir, profiler_subdir) if profiler_subdir else results_dir
    if not os.path.exists(dest_dir):
        os.makedirs(dest_dir)

    # JMH profilers often create files in the current directory or backend sub-module directory
    # Common extensions for JMH profilers: .txt, .csv, .bin, .html, .svg, .jfr
    
    # Check root directory
    for f in os.listdir("."):
        if os.path.isfile(f) and f.endswith((".csv", ".bin", ".html", ".svg", ".txt", ".prof", ".jfr")):
            # Also avoid moving run_benchmarks.py if it somehow matches (unlikely)
            if f == "run_benchmarks.py" or f == "requirements.txt":
                continue

            dest_f = os.path.join(dest_dir, f"{backend}_{f}")
            shutil.move(f, dest_f)
            print(f"Moved profiler output {f} to {dest_f}")

    # Check backend directory for JFR or other profiler directories
    backend_dir = f"benchmark/benchmark-{backend}"
    if os.path.exists(backend_dir):
        for f in os.listdir(backend_dir):
            full_path = os.path.join(backend_dir, f)
            # JMH JFR profiler often creates directories named after the benchmark
            if os.path.isdir(full_path) and "Benchmark" in f:
                # Look for .jfr files inside this directory
                for sub_f in os.listdir(full_path):
                    if sub_f.endswith(".jfr"):
                        sub_full_path = os.path.join(full_path, sub_f)
                        # Rename to something more useful: backend_benchmarkname_profile.jfr
                        dest_name = f"{backend}_{f}_{sub_f}"
                        dest_f = os.path.join(dest_dir, dest_name)
                        shutil.move(sub_full_path, dest_f)
                        print(f"Moved profiler output {sub_f} from {f} to {dest_f}")
                # Try to remove the now possibly empty directory
                try:
                    shutil.rmtree(full_path)
                except:
                    pass
            elif os.path.isfile(full_path) and f.endswith((".csv", ".bin", ".html", ".svg", ".txt", ".prof", ".jfr")):
                dest_f = os.path.join(dest_dir, f"{backend}_{f}")
                shutil.move(full_path, dest_f)
                print(f"Moved profiler output {f} from {backend_dir} to {dest_f}")

def collect_parallel_results(args, timestamp, results_dir, profiler_subdir=None):
    all_results = []
    selected_backends = args.backends if args.backends else list(PARALLEL_BACKENDS_MAP.keys())
    groups = parallel_workload_groups(args)
    
    for backend, (frontends, message_types) in product(selected_backends, groups.items()):
        print(f"{LIGHT_CYAN}Testing parallel backend: {backend}, messageType={','.join(message_types)}{RESET}")
        suffix = f"_{message_types[0].lower()}" if len(groups) > 1 else ""
        run_profiler_subdir = os.path.join(profiler_subdir, backend + suffix) if profiler_subdir and suffix else profiler_subdir
        
        benchmark_class = PARALLEL_BACKENDS_MAP[backend]
        include_pattern = f"{benchmark_class}\\.({'|'.join(frontends)})_.*"
        
        cmd = benchmark_run_command(backend)
        cmd += f" -Pjmh.includes='{include_pattern}'"
        
        params = []
        if args.handlers:
            params.append(f"category={','.join(args.handlers)}")
        params.append(f"format={','.join(args.formats or VALID_FORMATS)}")
        params.append(f"messageType={','.join(message_types)}")
        
        cmd += f" -Pjmh.parameters='{';'.join(params)}'"
        
        if args.warmup is not None: cmd += f" -Pjmh.warmupIterations={args.warmup}"
        if args.iterations is not None: cmd += f" -Pjmh.iterations={args.iterations}"
        if args.time is not None: cmd += f" -Pjmh.timeOnIteration={args.time} -Pjmh.warmupTime={args.time}"
        if args.forks is not None:
            cmd += f" -Pjmh.forks={args.forks}"
        else:
            cmd += " -Pjmh.forks=1"
        if args.profile: 
            cmd += f" -Pjmh.profilers='{','.join(args.profile)}'"
            if run_profiler_subdir:
                prof_dir = os.path.join(results_dir, run_profiler_subdir)
                if not os.path.exists(prof_dir):
                    os.makedirs(prof_dir)
                cmd += f" -Pjmh.profilerOutput='{prof_dir}'"
        
        # Determine the results file location. 
        # The JMH plugin for Gradle by default puts results in build/results/jmh/results.json
        # We'll use this default and move it afterwards.
        # Note: We can't easily change the output file from property if not supported in build.gradle.kts
        results_file_src = os.path.join("benchmark", f"benchmark-{backend}", "build", "results", "jmh", "results.json")
        results_file_dst = os.path.join(results_dir, f"parallel_results_{backend}{suffix}.json")
        
        if args.dryrun:
            print(f"{LIGHT_YELLOW}Dry run: {cmd}{RESET}")
        else:
            # Remove old result file if exists
            if os.path.exists(results_file_src):
                os.remove(results_file_src)

            if run_command(cmd, os.path.join(results_dir, f"parallel_benchmark_{backend}{suffix}.log")):
                if os.path.exists(results_file_src):
                    shutil.copy2(results_file_src, results_file_dst)
                    with open(results_file_dst, 'r') as f:
                        backend_results = json.load(f)
                        for r in backend_results:
                            r["backend_param"] = backend
                        all_results.extend(backend_results)
                else:
                    print(f"{YELLOW}Warning: No results found for backend {backend} at {results_file_src}{RESET}")
            else:
                print(f"{BOLD_RED}Parallel benchmark failed for backend: {backend}{RESET}")
        
        if not args.dryrun and args.profile:
            move_profiler_outputs(backend, results_dir, run_profiler_subdir)
                
    return all_results

def collect_results(args, timestamp, results_dir, profiler_subdir=None):
    all_results = []
    selected_backends = args.backends if args.backends else list(SEQUENTIAL_BACKENDS_MAP.keys())
    run_types = set(args.run)
    run_serial = "SERIAL" in run_types
    success = True
    
    for backend in selected_backends:
        print(f"{LIGHT_CYAN}Testing backend: {backend}{RESET}")
        
        msg_types = args.message_types if args.message_types else VALID_MESSAGE_TYPES
        
        # We need to handle JCL and JUL specially.
        # JCL: doesn't support ARGUMENTS, MESSAGE_SUPPLIER, and LAMBDA_PARAMETER.
        # JUL: doesn't support LAMBDA_PARAMETER.
        runs = []
        if "CONSTANT" in msg_types:
            runs.append({
                "msg_types": ["CONSTANT"],
                "exclude": None
            })
        
        other_types = [mt for mt in msg_types if mt != "CONSTANT"]
        if other_types:
            if "LAMBDA_PARAMETER" in other_types:
                # Run other types except LAMBDA_PARAMETER for JCL (already excluded below)
                # and run LAMBDA_PARAMETER only for those that support it.
                non_lambda_others = [mt for mt in other_types if mt != "LAMBDA_PARAMETER"]
                if non_lambda_others:
                    runs.append({
                        "msg_types": non_lambda_others,
                        "exclude": ".*jcl.*" if not args.frontends or "jcl" in args.frontends else None
                    })
                
                # LAMBDA_PARAMETER is not supported by JCL and JUL
                runs.append({
                    "msg_types": ["LAMBDA_PARAMETER"],
                    "exclude": ".*(jcl|jul).*" if not args.frontends or any(f in args.frontends for f in ["jcl", "jul"]) else None
                })
            else:
                runs.append({
                    "msg_types": other_types,
                    "exclude": ".*jcl.*" if not args.frontends or "jcl" in args.frontends else None
                })

        for i, run in enumerate(runs):
            benchmark_class = SEQUENTIAL_BACKENDS_MAP[backend]
            effective_frontends = args.frontends if args.frontends else ["jul", "jcl", "slf4j", "log4j"]
            
            # Filter out incompatible frontends for the current message types
            if run["exclude"]:
                effective_frontends = [f for f in effective_frontends if not re.search(run["exclude"], f)]
            
            if not effective_frontends:
                if args.dryrun:
                    print(f"{LIGHT_YELLOW}Dry run (Run {i+1}/{len(runs)}): Skipping backend {backend} for messageTypes {run['msg_types']} (no frontends to test){RESET}")
                else:
                    print(f"  Skipping Run {i+1}/{len(runs)} for backend {backend} (no frontends to test)")
                continue

            if not args.dryrun and len(runs) > 1:
                print(f"  Run {i+1}/{len(runs)}: messageType={run['msg_types']}, exclude={run['exclude']}")
            
            jvm_args = ["-Djmh.ignoreLock=true"]
            cmd = None
            if run_serial:
                cmd_base = benchmark_run_command(backend)
                include_pattern = f"{benchmark_class}\\.({'|'.join(effective_frontends)})$"
                cmd_base += f" -Pjmh.includes='{include_pattern}'"
                if run["exclude"]:
                    cmd_base += f" -Pjmh.excludes='{run['exclude']}'"

                params = [f"outputToFile={'true' if args.output_to_file else 'false'}"]
                if args.formats:
                    params.append(f"format={','.join(args.formats)}")
                if args.handlers:
                    params.append(f"category={','.join(args.handlers)}")
                params.append(f"messageType={','.join(run['msg_types'])}")
                cmd = cmd_base + f" -Pjmh.parameters='{';'.join(params)}'"

                if args.warmup is not None:
                    cmd += f" -Pjmh.warmupIterations={args.warmup}"
                if args.iterations is not None:
                    cmd += f" -Pjmh.iterations={args.iterations}"
                if args.time:
                    cmd += f" -Pjmh.timeOnIteration={args.time} -Pjmh.warmupTime={args.time}"

                forks = args.forks if args.forks is not None else 1
                cmd += f" -Pjmh.forks={forks}"

                if args.profile:
                    cmd += f" -Pjmh.profilers='{','.join(args.profile)}'"
                    if profiler_subdir:
                        prof_dir = os.path.join(results_dir, profiler_subdir)
                        if not os.path.exists(prof_dir):
                            os.makedirs(prof_dir)
                        cmd += f" -Pjmh.profilerOutput='{prof_dir}'"
                cmd += f" -Pjmh.jvmArgs='{' '.join(jvm_args)}'"

            if args.dryrun:
                print(f"{LIGHT_YELLOW}Dry run (Run {i+1}/{len(runs)}): Would execute command for backend {backend}:{RESET}")
                if cmd:
                    print(f"  Command: {cmd}")
                continue

            output_file = None
            if args.profile:
                suffix = f"_{i}" if len(runs) > 1 else ""
                output_file = os.path.join(results_dir, f"profile_{backend}{suffix}.txt")

            if cmd:
                src_json = f"benchmark/benchmark-{backend}/build/results/jmh/results.json"
                if os.path.exists(src_json):
                    os.remove(src_json)
                if run_command(cmd, output_file):
                    dest_json = os.path.join(results_dir, f"results_{backend}_{i}.json")
                    if os.path.exists(src_json):
                        shutil.copy(src_json, dest_json)
                        with open(dest_json, "r") as f:
                            data = json.load(f)
                            for entry in data:
                                entry["backend_param"] = backend
                            all_results.extend(data)
                    else:
                        print(f"Warning: Result file {src_json} not found for {backend}")
                else:
                    print(f"{BOLD_RED}Error: Benchmark failed for {backend} (run {i}){RESET}")
                    success = False

        if not args.dryrun and run_serial:
            # Check for profiler output files in the root directory and move them
            if args.profile:
                move_profiler_outputs(backend, results_dir, profiler_subdir)

    return all_results

def measurement_value(value):
    try:
        value = float(value)
    except (TypeError, ValueError):
        return None
    return value if math.isfinite(value) and value >= 0 else None


def collect_report_measurements(serial_results, parallel_results):
    serial, parallel = {}, {}
    for is_parallel, results, measurements in ((False, serial_results, serial), (True, parallel_results, parallel)):
        for entry in results:
            params = entry["params"]
            if str(params.get("withException", "false")).lower() == "true":
                continue
            frontend = entry["benchmark"].rsplit(".", 1)[-1]
            threads = None
            if is_parallel:
                frontend, threads = frontend.rsplit("_", 1)
                threads = int(threads)
                message_type = params.get("messageType", LEGACY_PARALLEL_MESSAGE_TYPES[frontend])
            else:
                message_type = params["messageType"]
            metric = entry["primaryMetric"]
            score = measurement_value(metric.get("score"))
            if score is not None:
                row = (frontend, params["category"], params.get("format", "DEFAULT"), entry["backend_param"], threads)
                measurements.setdefault(row, {})[message_type] = (score, measurement_value(metric.get("scoreError")))
    return serial, parallel


def format_measurement(measurement, *, whole=False):
    score, error = measurement
    if whole:
        return f"{int(score):,}" if error is None else f"{int(score):,} ± {int(error):,}"
    return f"{score:,.2f}" if error is None else f"{score:,.2f} ± {error:,.2f}"


def format_table_bar(score, maximum, backend, *, pad=False):
    eighths = max(1, round(TABLE_BAR_WIDTH * 8 * (score / maximum))) if score else 0
    full, remainder = divmod(eighths, 8)
    bar = "█" * full + ("▏▎▍▌▋▊▉"[remainder - 1] if remainder else "")
    padding = "&nbsp;" * (TABLE_BAR_WIDTH - len(bar)) if pad else ""
    return (f'<span style="color: {BACKEND_COLORS[backend]}">{bar}</span>' if bar else "") + padding


def write_report_chart(path, title, rows, measurements, maxima):
    handler_rows = {handler: [row for row in rows if row[1] == handler] for handler in VALID_HANDLERS if handler in maxima}
    label_width, panel_width = 330, 200
    row_height = 19
    width = label_width + len(MESSAGE_COLUMNS) * panel_width + 20
    height = 75 + sum(57 + len(keys) * row_height for keys in handler_rows.values())
    with open(path, "w", encoding="utf-8") as svg:
        svg.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" '
                  f'width="{width}" height="{height}" role="img">\n')
        svg.write(f'<title>{escape(title)}</title><rect width="100%" height="100%" fill="#fff"/>\n')
        svg.write('<g font-family="sans-serif" font-size="13" fill="#24292f">\n')
        svg.write(f'<text x="16" y="25" font-size="19">{escape(title)}</text>\n')
        for i, backend in enumerate(sorted({row[3] for row in rows})):
            x = 16 + i * 125
            color = BACKEND_COLORS[backend]
            svg.write(f'<rect x="{x}" y="41" width="11" height="11" fill="{color}"/>'
                      f'<text x="{x + 16}" y="51">{backend.upper()}</text>\n')
        y = 72
        for handler, keys in handler_rows.items():
            maximum = maxima[handler]
            svg.write(f'<text x="16" y="{y}" font-weight="bold">{escape(handler)}</text>\n')
            for i, label in enumerate(MESSAGE_COLUMNS.values()):
                x = label_width + i * panel_width
                svg.write(f'<text x="{x}" y="{y}" font-weight="bold">{label}</text>'
                          f'<text x="{x}" y="{y + 18}">Scale: 0–{maximum:,.2f} ops/s</text>\n')
            y += 32
            for i, row in enumerate(keys):
                frontend, _, fmt, backend, threads = row
                labels = (frontend.upper(), fmt, backend.upper())
                if threads is not None:
                    labels += (f"{threads} threads",)
                if i % 2 == 0:
                    svg.write(f'<rect x="12" y="{y - 2}" width="{width - 24}" height="{row_height}" fill="#f6f8fa"/>\n')
                svg.write(f'<text x="16" y="{y + row_height // 2 + 3}">'
                          f'{escape(" · ".join(labels))}</text>\n')
                for column, (message_type, label) in enumerate(MESSAGE_COLUMNS.items()):
                    measurement = measurements[row].get(message_type)
                    if measurement is None:
                        continue
                    score = measurement[0]
                    x = label_width + column * panel_width
                    bar_width = score / maximum * (panel_width - 22) if maximum else 0
                    color = BACKEND_COLORS[backend]
                    tooltip = (" / ".join((frontend.upper(), handler, *labels[1:], label))
                               + f": {format_measurement(measurement)} ops/s")
                    svg.write(f'<rect class="measurement" x="{x}" y="{y + 1}" width="{bar_width:.4f}" '
                              f'height="11" fill="{color}"><title>{escape(tooltip)}</title></rect>\n')
                y += row_height
            y += 25
        svg.write('</g>\n</svg>\n')


def write_result_section(report, heading, filename, measurements, run_type, args, results_dir):
    report.write(f"## {heading}\n\n")
    if not measurements:
        reason = "No usable throughput measurements were recorded." if run_type in args.run else "Not requested for this run."
        report.write(reason + "\n\n")
        return

    rows = sorted(measurements, key=lambda row: (REPORT_FRONTENDS.index(row[0]), VALID_HANDLERS.index(row[1]),
                                                 VALID_FORMATS.index(row[2]), row[3], row[4]))
    maxima = {}
    measurement_width = 0
    for row, values in measurements.items():
        handler = row[1]
        maxima[handler] = max(maxima.get(handler, 0), *(score for score, _ in values.values()))
        measurement_width = max(measurement_width, *(len(format_measurement(value, whole=True)) for value in values.values()))

    def write_row(cells):
        report.write("| " + " | ".join(cells) + " |\n")

    has_threads = run_type == "PARALLEL"
    write_row(["Frontend", "Handler", "Pattern", "Backend"] + (["Threads"] if has_threads else [])
              + list(MESSAGE_COLUMNS.values()))
    write_row(["---"] * 4 + ["---:"] * (len(MESSAGE_COLUMNS) + has_threads))
    previous = ()
    for row in rows:
        _, handler, _, backend, threads = row
        cells = [value.upper() if row[:i + 1] != previous[:i + 1] else "" for i, value in enumerate(row[:4])]
        previous = row
        if has_threads:
            cells.append(str(threads))
        for message_type in MESSAGE_COLUMNS:
            measurement = measurements[row].get(message_type)
            if measurement is None:
                cells.append("—")
                continue
            bar = format_table_bar(measurement[0], maxima[handler], backend, pad=True)
            score = format_measurement(measurement, whole=True).rjust(measurement_width).replace(" ", "&nbsp;")
            cells.append(f'<span style="font-family: monospace">{bar}&nbsp;{score}</span>')
        write_row(cells)

    title = f"{heading}: throughput by message type"
    write_report_chart(os.path.join(results_dir, filename), title, rows, measurements, maxima)
    report.write(f"\n![{title}]({filename})\n\n")


def generate_markdown_report(serial_results, parallel_results, samples, args, started_at, results_dir, sys_info, cmd_line):
    serial, parallel = collect_report_measurements(serial_results, parallel_results)
    benchmark_jvms = {" ".join(entry[key] for key in ("jdkVersion", "vmName", "vmVersion") if entry.get(key))
                      for results in (serial_results, parallel_results) for entry in results if entry.get("jdkVersion")}
    report_path = os.path.join(results_dir, "BENCHMARK_RESULTS.md")
    with open(report_path, "w", encoding="utf-8") as report:
        report.write("# Logging Benchmark Results\n\n")
        report.write(f"Date: {started_at:%Y-%m-%d %H:%M:%S}\n\n")
        report.write("**Command Line**\n\n```bash\n" + cmd_line + "\n```\n\n")
        report.write("**System Information**\n\n```text\n" + sys_info + "\n")
        for jvm in sorted(benchmark_jvms):
            report.write(f"Benchmark JVM: {jvm}\n")
        report.write("```\n\n")
        write_sample_output(report, samples)
        if serial or parallel:
            report.write("Scores are in **ops/s** (higher is better), with JMH’s **± error** when available. "
                         "A dash means no measurement. Bar colors identify backends. Table bars are scaled to the "
                         "highest score for the same handler in each section and shown in eighth-block steps.\n\n")
        write_result_section(report, "Singlethreaded", "benchmark-chart-singlethreaded.svg",
                             serial, "SERIAL", args, results_dir)
        write_result_section(report, "Multithreaded", "benchmark-chart-multithreaded.svg",
                             parallel, "PARALLEL", args, results_dir)
    print(f"Markdown report generated: {report_path}")


def create_argument_parser():
    parser = argparse.ArgumentParser(description="Run SLB4J benchmarks.")
    parser.add_argument("--run", nargs="+", choices=VALID_RUN_TYPES, required=True,
                        help="Run SERIAL and/or PARALLEL, or SAMPLE_OUTPUT for examples only; all reports include examples")
    parser.add_argument("--backends", nargs="+", help="Backends to test")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test")
    parser.add_argument("--handlers", nargs="+", help="Handlers to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (COMPACT, DEFAULT, DETAILED)")
    parser.add_argument("--message-types", nargs="+", help=f"Message types to test ({', '.join(VALID_MESSAGE_TYPES)})")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--forks", type=int, help="Number of forks")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole")
    parser.add_argument("--dryrun", action="store_true", help="Show the benchmarks that will run without actually executing them")
    parser.add_argument("--mode", choices=["smoketest", "quick", "full"], help="Benchmark mode")
    parser.add_argument("--profile", nargs="+", help="JMH profilers to use (gc, stack, cl, comp, jfr, pauses safepoints)")
    return parser

def parse_args(argv=None):
    return create_argument_parser().parse_args(argv)

if __name__ == "__main__":
    args = parse_args()
    if not validate_args(args):
        sys.exit(1)

    if args.mode:
        if args.mode == "full":
            if args.warmup is None: args.warmup = 5
            if args.iterations is None: args.iterations = 5
            if args.time is None: args.time = "5s"
        elif args.mode == "smoketest":
            if args.warmup is None: args.warmup = 1
            if args.iterations is None: args.iterations = 1
            if args.time is None: args.time = "10ms"
        elif args.mode == "quick":
            if args.warmup is None: args.warmup = 3
            if args.iterations is None: args.iterations = 3
            if args.time is None: args.time = "1s"
            
        if args.backends is None:
            run_types = set(args.run)
            backends = set()
            if "SERIAL" in run_types or "SAMPLE_OUTPUT" in run_types:
                backends.update(SEQUENTIAL_BACKENDS_MAP.keys())
            if "PARALLEL" in run_types:
                backends.update(PARALLEL_BACKENDS_MAP.keys())
            args.backends = sorted(backends)
        if args.handlers is None: args.handlers = ["CONSOLE", "FILE"]
        if args.formats is None: args.formats = ["COMPACT", "DEFAULT", "DETAILED"]
        if args.message_types is None: args.message_types = ["CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]

    started_at = datetime.datetime.now()
    timestamp = started_at.strftime("%Y%m%d_%H%M%S")
    results_dir = create_results_directory(timestamp)

    print(f"{BOLD_GREEN}Estimated total runtime: {format_duration(get_estimated_runtime(args))}{RESET}")

    if not compile_benchmarks(args):
        print(f"{BOLD_RED}Error: Benchmark compilation failed{RESET}")
        sys.exit(1)

    try:
        samples = collect_sample_output(args, results_dir)
    except (OSError, RuntimeError) as error:
        print(f"{BOLD_RED}Error: {error}{RESET}", file=sys.stderr)
        sys.exit(1)

    serial_results, parallel_results = [], []
    if "SERIAL" in args.run:
        profiler_subdir = "profile-serial" if args.profile else None
        serial_results = collect_results(args, timestamp, results_dir, profiler_subdir=profiler_subdir)

    if "PARALLEL" in args.run:
        profiler_subdir = "profile-parallel" if args.profile else None
        parallel_results = collect_parallel_results(args, timestamp, results_dir, profiler_subdir=profiler_subdir)

    if not args.dryrun:
        generate_markdown_report(serial_results, parallel_results, samples, args, started_at, results_dir,
                                 get_system_info(), "python3 " + shlex.join(sys.argv))
