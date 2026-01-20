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
import os
import subprocess
import json
import shutil
import argparse
import datetime
import sys
import platform
import math

# Constants
SEQUENTIAL_BACKENDS_MAP = {
    "slb4j": "Slb4jBenchmark",
    "log4j": "Log4jBenchmark",
    "logback": "LogbackBenchmark",
    "jul": "JulBenchmark"
}

PARALLEL_BACKENDS_MAP = {
    "slb4j": "Slb4jParallelBenchmark",
    "log4j": "Log4jParallelBenchmark",
    "logback": "LogbackParallelBenchmark",
    "jul": "JulParallelBenchmark"
}

VALID_HANDLERS = ["CONSOLE", "FILE"]
VALID_FORMATS = ["COMPACT", "DEFAULT", "DETAILED"]
VALID_MESSAGE_TYPES = ["CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]

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
    # If neither serial nor parallel is specified, we run both
    run_serial = args.serial or not args.parallel
    run_parallel = args.parallel or not args.serial

    # Validate backends for serial if needed
    if run_serial:
        backends_map = SEQUENTIAL_BACKENDS_MAP
        backends = args.backends if args.backends else list(backends_map.keys())
        for b in backends:
            if b not in backends_map:
                print(f"{BOLD_RED}Error: Invalid serial backend {b}. Valid backends are: {list(backends_map.keys())}{RESET}")
                return False

    # Validate backends for parallel if needed
    if run_parallel:
        backends_map = PARALLEL_BACKENDS_MAP
        backends = args.backends if args.backends else list(backends_map.keys())
        for b in backends:
            if b not in backends_map:
                print(f"{BOLD_RED}Error: Invalid parallel backend {b}. Valid backends are: {list(backends_map.keys())}{RESET}")
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

def get_estimated_runtime(args):
    return calculate_runtime(args)

def calculate_runtime(args):
    run_serial = args.serial or not args.parallel
    run_parallel = args.parallel or not args.serial
    
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
        
        num_frontends = len(args.frontends) if args.frontends else 4
        num_handlers = len(args.handlers) if args.handlers else 2
        num_formats = len(args.formats) if args.formats else 3
        msg_types = args.message_types if args.message_types else VALID_MESSAGE_TYPES
        num_msg_types = len(msg_types)
        total_benchmarks_per_backend = num_frontends * num_handlers * num_formats * num_msg_types
        
        total_runtime += num_backends * (gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark)

    if run_parallel:
        selected_backends = args.backends if args.backends else list(PARALLEL_BACKENDS_MAP.keys())
        num_backends = len(selected_backends)
        
        num_frontends = 2 # slf4j, log4j
        num_threads = 7 # 1, 2, 4, 8, 16, 64, 128
        num_handlers = len(args.handlers) if args.handlers else 2
        total_benchmarks_per_backend = num_frontends * num_threads * num_handlers
        
        total_runtime += num_backends * (gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark)

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
    success = True
    
    for backend in selected_backends:
        print(f"{LIGHT_CYAN}Testing parallel backend: {backend}{RESET}")
        
        benchmark_class = PARALLEL_BACKENDS_MAP[backend]
        effective_frontends = args.frontends if args.frontends else ["slf4j", "log4j"]
        
        include_pattern = f"{benchmark_class}\\.({'|'.join(effective_frontends)})_.*"
        
        cmd = f"./gradlew --quiet :benchmark:benchmark-{backend}:jmh"
        cmd += f" -Pjmh.includes='{include_pattern}'"
        
        params = []
        if args.handlers:
            params.append(f"category={','.join(args.handlers)}")
        
        if params:
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
            if profiler_subdir:
                prof_dir = os.path.join(results_dir, profiler_subdir)
                if not os.path.exists(prof_dir):
                    os.makedirs(prof_dir)
                cmd += f" -Pjmh.profilerOutput='{prof_dir}'"
        
        # Determine the results file location. 
        # The JMH plugin for Gradle by default puts results in build/results/jmh/results.json
        # We'll use this default and move it afterwards.
        # Note: We can't easily change the output file from property if not supported in build.gradle.kts
        results_file_src = os.path.join("benchmark", f"benchmark-{backend}", "build", "results", "jmh", "results.json")
        results_file_dst = os.path.join(results_dir, f"parallel_results_{backend}.json")
        
        if args.dry_run:
            print(f"{LIGHT_YELLOW}Dry run: {cmd}{RESET}")
        else:
            # Remove old result file if exists
            if os.path.exists(results_file_src):
                os.remove(results_file_src)

            if run_command(cmd, os.path.join(results_dir, f"parallel_benchmark_{backend}.log")):
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
                success = False
        
        if not args.dry_run and args.profile:
            move_profiler_outputs(backend, results_dir, profiler_subdir)
                
    return all_results

def collect_results(args, timestamp, results_dir, profiler_subdir=None):
    all_results = []
    selected_backends = args.backends if args.backends else list(SEQUENTIAL_BACKENDS_MAP.keys())
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
            effective_frontends = args.frontends if args.frontends else ["slf4j", "log4j", "jul", "jcl"]
            
            # Filter out jcl for non-CONSTANT message types
            if run["exclude"] and ".*jcl.*" in run["exclude"]:
                effective_frontends = [f for f in effective_frontends if f != "jcl"]
            
            if not effective_frontends:
                if args.dry_run:
                    print(f"{LIGHT_YELLOW}Dry run (Run {i+1}/{len(runs)}): Skipping backend {backend} for messageTypes {run['msg_types']} (no frontends to test){RESET}")
                else:
                    print(f"  Skipping Run {i+1}/{len(runs)} for backend {backend} (no frontends to test)")
                continue

            if not args.dry_run and len(runs) > 1:
                print(f"  Run {i+1}/{len(runs)}: messageType={run['msg_types']}, exclude={run['exclude']}")
            
            cmd = f"./gradlew --quiet :benchmark:benchmark-{backend}:jmh"
            
            includes = [f"{benchmark_class}\\.{f}" for f in effective_frontends]
            include_pattern = f"{benchmark_class}\\.({'|'.join(effective_frontends)})"
            
            # JMH arguments via project properties
            cmd += f" -Pjmh.includes='{include_pattern}'"
            if run["exclude"]:
                cmd += f" -Pjmh.excludes='{run['exclude']}'"

            params = []
            if args.output_to_file:
                params.append("outputToFile=true")
            else:
                params.append("outputToFile=false")

            if args.formats:
                params.append(f"format={','.join(args.formats)}")
            if args.handlers:
                params.append(f"category={','.join(args.handlers)}")
            
            params.append(f"messageType={','.join(run['msg_types'])}")
            if params:
                cmd += f" -Pjmh.parameters='{';'.join(params)}'"

            if args.warmup is not None:
                cmd += f" -Pjmh.warmupIterations={args.warmup}"
            if args.iterations is not None:
                cmd += f" -Pjmh.iterations={args.iterations}"
            if args.time:
                cmd += f" -Pjmh.timeOnIteration={args.time} -Pjmh.warmupTime={args.time}"
            
            forks = 1
            if args.forks is not None:
                forks = args.forks
            cmd += f" -Pjmh.forks={forks}"

            if args.profile:
                cmd += f" -Pjmh.profilers='{','.join(args.profile)}'"
                if profiler_subdir:
                    prof_dir = os.path.join(results_dir, profiler_subdir)
                    if not os.path.exists(prof_dir):
                        os.makedirs(prof_dir)
                    cmd += f" -Pjmh.profilerOutput='{prof_dir}'"

            jvm_args = ["-Djmh.ignoreLock=true"]
            cmd += f" -Pjmh.jvmArgs='{' '.join(jvm_args)}'"

            if args.dry_run:
                print(f"{LIGHT_YELLOW}Dry run (Run {i+1}/{len(runs)}): Would execute command for backend {backend}:{RESET}")
                print(f"  Command: {cmd}")
                continue

            output_file = None
            if args.profile:
                suffix = f"_{i}" if len(runs) > 1 else ""
                output_file = os.path.join(results_dir, f"profile_{backend}{suffix}.txt")

            if run_command(cmd, output_file):
                src_json = f"benchmark/benchmark-{backend}/build/results/jmh/results.json"
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
            elif not args.dry_run:
                print(f"{BOLD_RED}Error: Benchmark failed for {backend} (run {i}){RESET}")
                success = False
        
        if not args.dry_run:
            # Check for profiler output files in the root directory and move them
            if args.profile:
                move_profiler_outputs(backend, results_dir, profiler_subdir)

    return all_results

def generate_markdown_parallel(results, args, timestamp, results_dir, sys_info, cmd_line):
    if not results:
        print(f"{BOLD_RED}No results to generate parallel report.{RESET}")
        return

    grouped = {}
    present_threads = set()
    for entry in results:
        backend = entry["backend_param"]
        benchmark_full_name = entry["benchmark"]
        # benchmark_full_name is something like "org.slb4j.benchmark.Slb4jParallelBenchmark.slf4j_1"
        benchmark_name = benchmark_full_name.split(".")[-1]
        try:
            frontend, thread_count = benchmark_name.split("_")
            thread_count = int(thread_count)
            present_threads.add(thread_count)
            if backend not in grouped: grouped[backend] = {}
            if frontend not in grouped[backend]: grouped[backend][frontend] = {}
            score = entry.get("primaryMetric", {}).get("score", 0)
            grouped[backend][frontend][thread_count] = score
        except ValueError:
            print(f"{BOLD_RED}Warning: Could not parse benchmark name: {benchmark_name}{RESET}")
            continue

    # Calculate min/max scores for each frontend and thread count
    frontend_thread_scores = {} # frontend -> thread_count -> list of scores
    for backend, frontends in grouped.items():
        for frontend, threads in frontends.items():
            if frontend not in frontend_thread_scores:
                frontend_thread_scores[frontend] = {}
            for t, score in threads.items():
                if t not in frontend_thread_scores[frontend]:
                    frontend_thread_scores[frontend][t] = []
                frontend_thread_scores[frontend][t].append(score)
    
    min_max_parallel = {} # frontend -> thread_count -> (min, max)
    for frontend, threads in frontend_thread_scores.items():
        min_max_parallel[frontend] = {}
        for t, scores in threads.items():
            if scores:
                min_max_parallel[frontend][t] = (min(scores), max(scores))
            else:
                min_max_parallel[frontend][t] = (None, None)

    report_path = os.path.join(results_dir, "PARALLEL_BENCHMARK_RESULTS.md")
    with open(report_path, "w") as f:
        f.write("# Parallel Logging Benchmark Results\n\n")
        f.write(f"Date: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("## Command Line\n```bash\n" + cmd_line + "\n```\n\n")
        f.write("## System Information\n```\n" + sys_info + "\n```\n\n")
        f.write("Throughput (ops/s) for different thread counts.\n\n")
        
        sorted_backends = sorted(grouped.keys())
        all_threads = sorted(list(present_threads))
        header = "| Backend | Frontend | " + " | ".join([f"{t} Threads" for t in all_threads]) + " |"
        separator = "| :--- | :--- | " + " | ".join([":---:" for _ in all_threads]) + " |"
        f.write(header + "\n" + separator + "\n")
        for backend in sorted_backends:
            sorted_frontends = sorted(grouped[backend].keys())
            for frontend in sorted_frontends:
                row = f"| {backend} | {frontend} |"
                for t in all_threads:
                    score = grouped[backend][frontend].get(t, 0)
                    score_str = f"{score:,.2f}"
                    if score > 0 and t in min_max_parallel.get(frontend, {}):
                        min_val, max_val = min_max_parallel[frontend][t]
                        if min_val is not None and max_val is not None and min_val != max_val:
                            if score == max_val:
                                score_str = f"**{score_str}**"
                            elif score == min_val:
                                score_str = f"*{score_str}*"
                    row += f" {score_str} |"
                f.write(row + "\n")
                
    print(f"Markdown report generated: {report_path}")

def generate_markdown_sequential(results, args, timestamp, results_dir, sys_info, cmd_line):
    if not results:
        print("No results to process.")
        return

    grouped = {}
    for entry in results:
        params = entry.get("params", {})
        category = params.get("category", "N/A")
        fmt = params.get("format", "N/A")
        key = (category, fmt)
        if key not in grouped:
            grouped[key] = {}
        
        backend = entry["backend_param"]
        frontend = entry["benchmark"].split(".")[-1]
        pair_key = (backend, frontend)
        if pair_key not in grouped[key]:
            grouped[key][pair_key] = {}
        
        msg_type = params.get("messageType", "N/A")
        primary_metric = entry.get("primaryMetric", {})
        score = primary_metric.get("score", 0)
        error = primary_metric.get("scoreError", 0)
        
        try: score = float(score)
        except: score = 0.0
        try:
            error = float(error)
            if math.isnan(error): error = None
        except: error = None
            
        grouped[key][pair_key][msg_type] = {"score": score, "error": error}

    report_path = os.path.join(results_dir, "BENCHMARK_RESULTS.md")
    with open(report_path, "w") as f:
        f.write("# Logging Benchmark Results\n\n")
        f.write(f"Date: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("## Command Line\n```bash\n" + cmd_line + "\n```\n\n")
        f.write("## System Information\n```\n" + sys_info + "\n```\n\n")
        
        sorted_keys = sorted(grouped.keys())
        for key in sorted_keys:
            category, fmt = key
            f.write(f"### Category: {category}, Format: {fmt}\n\n")
            pairs = grouped[key]
            all_msg_types = ["CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]

            # Calculate min/max scores for each frontend and each column
            frontend_scores = {} # frontend -> mt -> list of scores
            for (backend, frontend), data in pairs.items():
                if frontend not in frontend_scores:
                    frontend_scores[frontend] = {mt: [] for mt in all_msg_types + ["AVERAGE"]}
                
                total_score = 0.0
                count = 0
                for mt in all_msg_types:
                    if mt in data:
                        s = data[mt]["score"]
                        frontend_scores[frontend][mt].append(s)
                        total_score += s
                        count += 1
                if count > 0:
                    frontend_scores[frontend]["AVERAGE"].append(total_score / count)

            min_max = {} # frontend -> mt -> (min, max)
            for frontend, mts in frontend_scores.items():
                min_max[frontend] = {}
                for mt, scores in mts.items():
                    if scores:
                        min_max[frontend][mt] = (min(scores), max(scores))
                    else:
                        min_max[frontend][mt] = (None, None)

            rows = []
            for (backend, frontend), data in pairs.items():
                row = {"backend": backend, "frontend": frontend, "scores": {}, "errors": {}}
                total_score = 0.0
                count = 0
                for mt in all_msg_types:
                    is_excluded = (frontend == "jcl" and mt in ["ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]) or \
                                  (frontend == "jul" and mt == "LAMBDA_PARAMETER")
                    if is_excluded:
                        row["scores"][mt] = "N/A"
                        row["errors"][mt] = "N/A"
                    elif mt in data:
                        s = data[mt]["score"]
                        e = data[mt]["error"]
                        
                        s_str = f"{s:,.2f}"
                        if min_max[frontend][mt][0] is not None and min_max[frontend][mt][1] is not None and min_max[frontend][mt][0] != min_max[frontend][mt][1]:
                            if s == min_max[frontend][mt][1]:
                                s_str = f"**{s_str}**"
                            elif s == min_max[frontend][mt][0]:
                                s_str = f"*{s_str}*"
                        
                        row["scores"][mt] = s_str
                        row["errors"][mt] = f"{e:,.2f}" if e is not None else "N/A"
                        total_score += s
                        count += 1
                    else:
                        row["scores"][mt] = ""
                        row["errors"][mt] = ""
                
                avg_score = total_score / count if count > 0 else 0.0
                row["avg_score_val"] = avg_score
                
                avg_score_str = f"{avg_score:,.2f}" if count > 0 else ""
                if count > 0 and min_max[frontend]["AVERAGE"][0] is not None and min_max[frontend]["AVERAGE"][1] is not None and min_max[frontend]["AVERAGE"][0] != min_max[frontend]["AVERAGE"][1]:
                    if avg_score == min_max[frontend]["AVERAGE"][1]:
                        avg_score_str = f"**{avg_score_str}**"
                    elif avg_score == min_max[frontend]["AVERAGE"][0]:
                        avg_score_str = f"*{avg_score_str}*"
                
                row["avg_score"] = avg_score_str
                row["avg_error"] = "N/A" if count > 0 else ""
                rows.append(row)
        
            rows.sort(key=lambda x: (x["backend"], x["frontend"]))
            col_widths = {
                "backend": max(len("Backend"), max(len(r["backend"]) for r in rows) if rows else 0),
                "frontend": max(len("Frontend"), max(len(r["frontend"]) for r in rows) if rows else 0),
                "CONSTANT_score": max(len("Constant (ops/s)"), max(len(r["scores"]["CONSTANT"]) for r in rows) if rows else 0),
                "CONSTANT_error": max(len("Error"), max(len(r["errors"]["CONSTANT"]) for r in rows) if rows else 0),
                "ARGUMENTS_score": max(len("Argument (ops/s)"), max(len(r["scores"]["ARGUMENTS"]) for r in rows) if rows else 0),
                "ARGUMENTS_error": max(len("Error"), max(len(r["errors"]["ARGUMENTS"]) for r in rows) if rows else 0),
                "MESSAGE_SUPPLIER_score": max(len("Msg Supp (ops/s)"), max(len(r["scores"]["MESSAGE_SUPPLIER"]) for r in rows) if rows else 0),
                "MESSAGE_SUPPLIER_error": max(len("Error"), max(len(r["errors"]["MESSAGE_SUPPLIER"]) for r in rows) if rows else 0),
                "LAMBDA_PARAMETER_score": max(len("Lambda Param (ops/s)"), max(len(r["scores"]["LAMBDA_PARAMETER"]) for r in rows) if rows else 0),
                "LAMBDA_PARAMETER_error": max(len("Error"), max(len(r["errors"]["LAMBDA_PARAMETER"]) for r in rows) if rows else 0),
                "avg_score": max(len("Average (ops/s)"), max(len(r["avg_score"]) for r in rows) if rows else 0),
                "avg_error": max(len("Error"), max(len(r['avg_error']) for r in rows) if rows else 0),
            }

            header = (f"| {'Backend':<{col_widths['backend']}} | {'Frontend':<{col_widths['frontend']}} | "
                     f"{'Constant (ops/s)':>{col_widths['CONSTANT_score']}} | {'Error':>{col_widths['CONSTANT_error']}} | "
                     f"{'Argument (ops/s)':>{col_widths['ARGUMENTS_score']}} | {'Error':>{col_widths['ARGUMENTS_error']}} | "
                     f"{'Msg Supp (ops/s)':>{col_widths['MESSAGE_SUPPLIER_score']}} | {'Error':>{col_widths['MESSAGE_SUPPLIER_error']}} | "
                     f"{'Lambda Param (ops/s)':>{col_widths['LAMBDA_PARAMETER_score']}} | {'Error':>{col_widths['LAMBDA_PARAMETER_error']}} | "
                     f"{'Average (ops/s)':>{col_widths['avg_score']}} | {'Error':>{col_widths['avg_error']}} |")
        
            separator = (f"| {'-' * col_widths['backend']} | {'-' * col_widths['frontend']} | "
                        f"{'-' * (col_widths['CONSTANT_score'] - 1)}: | {'-' * (col_widths['CONSTANT_error'] - 1)}: | "
                        f"{'-' * (col_widths['ARGUMENTS_score'] - 1)}: | {'-' * (col_widths['ARGUMENTS_error'] - 1)}: | "
                        f"{'-' * (col_widths['MESSAGE_SUPPLIER_score'] - 1)}: | {'-' * (col_widths['MESSAGE_SUPPLIER_error'] - 1)}: | "
                        f"{'-' * (col_widths['LAMBDA_PARAMETER_score'] - 1)}: | {'-' * (col_widths['LAMBDA_PARAMETER_error'] - 1)}: | "
                        f"{'-' * (col_widths['avg_score'] - 1)}: | {'-' * (col_widths['avg_error'] - 1)}: |")
        
            f.write(header + "\n" + separator + "\n")
            for r in rows:
                line = (f"| {r['backend']:<{col_widths['backend']}} | {r['frontend']:<{col_widths['frontend']}} | "
                        f"{r['scores']['CONSTANT']:>{col_widths['CONSTANT_score']}} | {r['errors']['CONSTANT']:>{col_widths['CONSTANT_error']}} | "
                        f"{r['scores']['ARGUMENTS']:>{col_widths['ARGUMENTS_score']}} | {r['errors']['ARGUMENTS']:>{col_widths['ARGUMENTS_error']}} | "
                        f"{r['scores']['MESSAGE_SUPPLIER']:>{col_widths['MESSAGE_SUPPLIER_score']}} | {r['errors']['MESSAGE_SUPPLIER']:>{col_widths['MESSAGE_SUPPLIER_error']}} | "
                        f"{r['scores']['LAMBDA_PARAMETER']:>{col_widths['LAMBDA_PARAMETER_score']}} | {r['errors']['LAMBDA_PARAMETER']:>{col_widths['LAMBDA_PARAMETER_error']}} | "
                        f"{r['avg_score']:>{col_widths['avg_score']}} | {r['avg_error']:>{col_widths['avg_error']}} |")
                f.write(line + "\n")
            f.write("\n")

    print(f"Markdown report generated: {report_path}")

def generate_markdown_parallel(results, args, timestamp, results_dir, sys_info, cmd_line):
    if not results:
        print("No results to process.")
        return

    # grouped[category][backend][frontend][thread_count] = score
    grouped = {}
    present_threads = set()
    for entry in results:
        category = entry.get("params", {}).get("category", "DEFAULT")
        backend = entry["backend_param"]
        benchmark_name = entry["benchmark"].split(".")[-1]
        frontend, thread_count = benchmark_name.split("_")
        thread_count = int(thread_count)
        present_threads.add(thread_count)
        
        if category not in grouped: grouped[category] = {}
        if backend not in grouped[category]: grouped[category][backend] = {}
        if frontend not in grouped[category][backend]: grouped[category][backend][frontend] = {}
        
        score = entry.get("primaryMetric", {}).get("score", 0)
        grouped[category][backend][frontend][thread_count] = score

    # Calculate min/max scores for each category, frontend and thread count
    min_max_parallel = {} # category -> frontend -> thread_count -> (min, max)
    for category, backends in grouped.items():
        min_max_parallel[category] = {}
        frontend_thread_scores = {} # frontend -> thread_count -> list of scores
        for backend, frontends in backends.items():
            for frontend, threads in frontends.items():
                if frontend not in frontend_thread_scores:
                    frontend_thread_scores[frontend] = {}
                for t, score in threads.items():
                    if t not in frontend_thread_scores[frontend]:
                        frontend_thread_scores[frontend][t] = []
                    frontend_thread_scores[frontend][t].append(score)
        
        for frontend, threads in frontend_thread_scores.items():
            min_max_parallel[category][frontend] = {}
            for t, scores in threads.items():
                if scores:
                    min_max_parallel[category][frontend][t] = (min(scores), max(scores))
                else:
                    min_max_parallel[category][frontend][t] = (None, None)

    report_path = os.path.join(results_dir, "PARALLEL_BENCHMARK_RESULTS.md")
    with open(report_path, "w") as f:
        f.write("# Parallel Logging Benchmark Results\n\n")
        f.write(f"Date: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("## Command Line\n```bash\n" + cmd_line + "\n```\n\n")
        f.write("## System Information\n```\n" + sys_info + "\n```\n\n")
        f.write("Throughput (ops/s) for different thread counts.\n\n")
        
        all_threads = sorted(list(present_threads))
        sorted_categories = sorted(grouped.keys())

        for category in sorted_categories:
            f.write(f"### Handler: {category}\n\n")
            
            header = "| Backend | Frontend | " + " | ".join([f"{t} Threads" for t in all_threads]) + " |"
            separator = "| :--- | :--- | " + " | ".join([":---:" for _ in all_threads]) + " |"
            f.write(header + "\n" + separator + "\n")
            
            sorted_backends = sorted(grouped[category].keys())
            for backend in sorted_backends:
                sorted_frontends = sorted(grouped[category][backend].keys())
                for frontend in sorted_frontends:
                    row = f"| {backend} | {frontend} |"
                    for t in all_threads:
                        score = grouped[category][backend][frontend].get(t, 0)
                        score_str = f"{score:,.2f}"
                        if score > 0 and t in min_max_parallel[category].get(frontend, {}):
                            min_val, max_val = min_max_parallel[category][frontend][t]
                            if min_val is not None and max_val is not None and min_val != max_val:
                                if score == max_val:
                                    score_str = f"**{score_str}**"
                                elif score == min_val:
                                    score_str = f"*{score_str}*"
                        row += f" {score_str} |"
                    f.write(row + "\n")
                
            f.write("\n")
                
    print(f"Markdown report generated: {report_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run SLB4J benchmarks.")
    parser.add_argument("--backends", nargs="+", help="Backends to test")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test")
    parser.add_argument("--handlers", nargs="+", help="Handlers to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (COMPACT, DEFAULT, DETAILED)")
    parser.add_argument("--message-types", nargs="+", help="Message types to test (CONSTANT, ARGUMENTS, LAMBDA)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--forks", type=int, help="Number of forks")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole")
    parser.add_argument("--dry-run", action="store_true", help="Show the benchmarks that will run without actually executing them")
    parser.add_argument("--mode", choices=["smoketest", "quick", "full"], help="Benchmark mode")
    parser.add_argument("--profile", nargs="+", help="JMH profilers to use (gc, stack, cl, comp, jfr, pauses safepoints)")
    parser.add_argument("--serial", action="store_true", help="Run single-threaded (serial) benchmarks")
    parser.add_argument("--parallel", action="store_true", help="Run parallel benchmarks")
    
    args = parser.parse_args()
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
            run_serial = args.serial or not args.parallel
            run_parallel = args.parallel or not args.serial
            backends = set()
            if run_serial:
                backends.update(SEQUENTIAL_BACKENDS_MAP.keys())
            if run_parallel:
                backends.update(PARALLEL_BACKENDS_MAP.keys())
            args.backends = sorted(list(backends))
        if args.handlers is None: args.handlers = ["CONSOLE", "FILE"]
        if args.formats is None: args.formats = ["COMPACT", "DEFAULT", "DETAILED"]
        if args.message_types is None: args.message_types = ["CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"]

    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    results_dir = os.path.join("benchmark_results", timestamp)
    if not os.path.exists(results_dir):
        os.makedirs(results_dir)

    print(f"{BOLD_GREEN}Estimated total runtime: {format_duration(get_estimated_runtime(args))}{RESET}")
    
    cmd_line = "python3 " + " ".join(sys.argv)
    sys_info = get_system_info()
    
    run_serial = args.serial or not args.parallel
    run_parallel = args.parallel or not args.serial

    if run_serial:
        profiler_subdir = "profile-serial" if args.profile else None
        results = collect_results(args, timestamp, results_dir, profiler_subdir=profiler_subdir)
        if not args.dry_run:
            generate_markdown_sequential(results, args, timestamp, results_dir, sys_info, cmd_line)
    
    if run_parallel:
        profiler_subdir = "profile-parallel" if args.profile else None
        results = collect_parallel_results(args, timestamp, results_dir, profiler_subdir=profiler_subdir)
        if not args.dry_run:
            generate_markdown_parallel(results, args, timestamp, results_dir, sys_info, cmd_line)
