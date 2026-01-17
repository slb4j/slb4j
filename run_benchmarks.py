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
PARALLEL_BACKENDS = ["slb4j", "log4j", "logback", "jul"]
PARALLEL_FRONTENDS = ["slf4j", "log4j"]
PARALLEL_THREADS = [1, 2, 4, 8, 16, 64, 128]

VALID_HANDLERS = ["CONSOLE", "FILE"]
VALID_FORMATS = ["COMPACT", "DEFAULT", "DETAILED"]
VALID_MESSAGE_TYPES = ["CONSTANT", "ARGUMENTS", "LAMBDA"]

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
    is_parallel = args.parallel or args.all
    backends = args.backends if args.backends else (PARALLEL_BACKENDS if is_parallel else list(SEQUENTIAL_BACKENDS_MAP.keys()))
    for b in backends:
        if is_parallel:
            if b not in PARALLEL_BACKENDS:
                print(f"{BOLD_RED}Error: Invalid parallel backend {b}. Valid backends are: {PARALLEL_BACKENDS}{RESET}")
                return False
        if not args.parallel: # Check sequential backends if not exclusively parallel
            if b not in SEQUENTIAL_BACKENDS_MAP:
                print(f"{BOLD_RED}Error: Invalid backend {b}. Valid backends are: {list(SEQUENTIAL_BACKENDS_MAP.keys())}{RESET}")
                return False
    
    if args.handlers:
        for c in args.handlers:
            if c not in VALID_HANDLERS:
                print(f"{BOLD_RED}Error: Invalid handler {c}. Valid handlers are: {VALID_HANDLERS}{RESET}")
                return False
                
    if not args.parallel and args.formats:
        for f in args.formats:
            if f not in VALID_FORMATS:
                print(f"{BOLD_RED}Error: Invalid format {f}. Valid formats are: {VALID_FORMATS}{RESET}")
                return False
                
    if not args.parallel and args.message_types:
        for mt in args.message_types:
            if mt not in VALID_MESSAGE_TYPES:
                print(f"{BOLD_RED}Error: Invalid message type {mt}. Valid message types are: {VALID_MESSAGE_TYPES}{RESET}")
                return False

    if args.parallel and args.threads:
        for t in args.threads:
            if t not in PARALLEL_THREADS:
                print(f"{BOLD_RED}Error: Invalid thread count {t}. Valid thread counts are: {PARALLEL_THREADS}{RESET}")
                return False
                
    return True

def run_command(command):
    print(f"Running: {command}")
    process = subprocess.Popen(command, shell=True, text=True)
    process.wait()
    return process.returncode == 0

def get_estimated_runtime(args):
    total_time = 0
    if args.all or not args.parallel:
        total_time += calculate_runtime(args, parallel=False)
    if args.all or args.parallel:
        total_time += calculate_runtime(args, parallel=True)
    return total_time

def calculate_runtime(args, parallel):
    selected_backends = args.backends if args.backends else (PARALLEL_BACKENDS if parallel else list(SEQUENTIAL_BACKENDS_MAP.keys()))
    num_backends = len(selected_backends)
    
    warmup = args.warmup if args.warmup is not None else 2
    iterations = args.iterations if args.iterations is not None else 3
    time_per_iter = parse_time(args.time if args.time else "1s")
    
    gradle_startup_overhead = 15.0
    forks = 0 if args.mode == "smoketest" else 1
    if args.forks is not None:
        forks = args.forks
    jmh_fork_overhead = 1.0 if forks > 0 else 0.1

    if parallel:
        num_frontends = len(args.frontends) if args.frontends else len(PARALLEL_FRONTENDS)
        num_threads = len(args.threads) if args.threads else len(PARALLEL_THREADS)
        total_benchmarks_per_backend = num_frontends * num_threads
    else:
        num_frontends = len(args.frontends) if args.frontends else 4
        num_handlers = len(args.handlers) if args.handlers else 2
        num_formats = len(args.formats) if args.formats else 3
        msg_types = args.message_types if args.message_types else VALID_MESSAGE_TYPES
        num_msg_types = len(msg_types)
        total_benchmarks_per_backend = num_frontends * num_handlers * num_formats * num_msg_types

    time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
    return num_backends * (gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark)

def collect_results(args, timestamp, results_dir, force_parallel=None):
    all_results = []
    is_parallel = force_parallel if force_parallel is not None else args.parallel
    selected_backends = args.backends if args.backends else (PARALLEL_BACKENDS if is_parallel else list(SEQUENTIAL_BACKENDS_MAP.keys()))
    
    for backend in selected_backends:
        print(f"{LIGHT_CYAN}Testing backend: {backend}{RESET}")
        cmd = f"./gradlew :benchmark:runJmh -Pbackend={backend}"
        
        if is_parallel:
            benchmark_class = "ParallelLoggingBenchmark"
            includes = []
            target_frontends = args.frontends if args.frontends else PARALLEL_FRONTENDS
            target_threads = args.threads if args.threads else PARALLEL_THREADS
            for f in target_frontends:
                for t in target_threads:
                    includes.append(f"{benchmark_class}.{f}_{t}")
            cmd += f" -Pjmh.includes='{','.join(includes)}'"
        else:
            benchmark_class = SEQUENTIAL_BACKENDS_MAP[backend]
            if args.frontends:
                includes = [f"{benchmark_class}\\.{f}" for f in args.frontends]
                cmd += f" -Pjmh.includes='{','.join(includes)}'"
            else:
                cmd += f" -Pjmh.includes='{benchmark_class}'"

            params = []
            if args.formats:
                params.append(f"format={','.join(args.formats)}")
            if args.handlers:
                params.append(f"category={','.join(args.handlers)}")
            msg_types = args.message_types if args.message_types else VALID_MESSAGE_TYPES
            params.append(f"messageType={','.join(msg_types)}")
            if params:
                cmd += f" -Pjmh.parameters='{';'.join(params)}'"

        # Common JMH arguments
        if args.warmup is not None:
            cmd += f" -PwarmupIterations={args.warmup}"
        if args.iterations is not None:
            cmd += f" -Piterations={args.iterations}"
        if args.time:
            cmd += f" -PtimeOnIteration={args.time}"
        
        forks = 0 if args.mode == "smoketest" else 1
        if args.forks is not None:
            forks = args.forks
        cmd += f" -Pforks={forks}"

        if not is_parallel and args.output_to_file:
            cmd += " -PoutputToFile=true"

        if args.dry_run:
            print(f"{LIGHT_YELLOW}Dry run: Would execute command for backend {backend}:{RESET}")
            print(f"  Command: {cmd}")
            continue

        if run_command(cmd):
            src_json = "benchmark/jmh-results.json"
            suffix = "_parallel" if is_parallel else ""
            dest_json = os.path.join(results_dir, f"results_{backend}{suffix}_{timestamp}.json")
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
            print(f"{BOLD_RED}Error: Benchmark failed for {backend}{RESET}")

    return all_results

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

    report_path = os.path.join(results_dir, f"BENCHMARK_RESULTS_{timestamp}.md")
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
            all_msg_types = ["CONSTANT", "ARGUMENTS", "LAMBDA"]
            rows = []
            for (backend, frontend), data in pairs.items():
                row = {"backend": backend, "frontend": frontend, "scores": {}, "errors": {}}
                total_score = 0.0
                count = 0
                for mt in all_msg_types:
                    if mt in data:
                        s = data[mt]["score"]
                        e = data[mt]["error"]
                        row["scores"][mt] = f"{s:,.2f}"
                        row["errors"][mt] = f"{e:,.2f}" if e is not None else "N/A"
                        total_score += s
                        count += 1
                    else:
                        row["scores"][mt] = ""
                        row["errors"][mt] = ""
                avg_score = total_score / count if count > 0 else 0.0
                row["avg_score_val"] = avg_score
                row["avg_score"] = f"{avg_score:,.2f}" if count > 0 else ""
                row["avg_error"] = "N/A" if count > 0 else ""
                rows.append(row)
            
            rows.sort(key=lambda x: x["avg_score_val"], reverse=True)
            col_widths = {
                "backend": max(len("Backend"), max(len(r["backend"]) for r in rows) if rows else 0),
                "frontend": max(len("Frontend"), max(len(r["frontend"]) for r in rows) if rows else 0),
                "CONSTANT_score": max(len("Constant (ops/s)"), max(len(r["scores"]["CONSTANT"]) for r in rows) if rows else 0),
                "CONSTANT_error": max(len("Error"), max(len(r["errors"]["CONSTANT"]) for r in rows) if rows else 0),
                "ARGUMENTS_score": max(len("Argument (ops/s)"), max(len(r["scores"]["ARGUMENTS"]) for r in rows) if rows else 0),
                "ARGUMENTS_error": max(len("Error"), max(len(r["errors"]["ARGUMENTS"]) for r in rows) if rows else 0),
                "LAMBDA_score": max(len("Lambda (ops/s)"), max(len(r["scores"]["LAMBDA"]) for r in rows) if rows else 0),
                "LAMBDA_error": max(len("Error"), max(len(r["errors"]["LAMBDA"]) for r in rows) if rows else 0),
                "avg_score": max(len("Average (ops/s)"), max(len(r["avg_score"]) for r in rows) if rows else 0),
                "avg_error": max(len("Error"), max(len(r['avg_error']) for r in rows) if rows else 0),
            }

            header = (f"| {'Backend':<{col_widths['backend']}} | {'Frontend':<{col_widths['frontend']}} | "
                     f"{'Constant (ops/s)':>{col_widths['CONSTANT_score']}} | {'Error':>{col_widths['CONSTANT_error']}} | "
                     f"{'Argument (ops/s)':>{col_widths['ARGUMENTS_score']}} | {'Error':>{col_widths['ARGUMENTS_error']}} | "
                     f"{'Lambda (ops/s)':>{col_widths['LAMBDA_score']}} | {'Error':>{col_widths['LAMBDA_error']}} | "
                     f"{'Average (ops/s)':>{col_widths['avg_score']}} | {'Error':>{col_widths['avg_error']}} |")
            
            separator = (f"| {'-' * col_widths['backend']} | {'-' * col_widths['frontend']} | "
                        f"{'-' * (col_widths['CONSTANT_score'] - 1)}: | {'-' * (col_widths['CONSTANT_error'] - 1)}: | "
                        f"{'-' * (col_widths['ARGUMENTS_score'] - 1)}: | {'-' * (col_widths['ARGUMENTS_error'] - 1)}: | "
                        f"{'-' * (col_widths['LAMBDA_score'] - 1)}: | {'-' * (col_widths['LAMBDA_error'] - 1)}: | "
                        f"{'-' * (col_widths['avg_score'] - 1)}: | {'-' * (col_widths['avg_error'] - 1)}: |")
            
            f.write(header + "\n" + separator + "\n")
            for r in rows:
                line = (f"| {r['backend']:<{col_widths['backend']}} | {r['frontend']:<{col_widths['frontend']}} | "
                        f"{r['scores']['CONSTANT']:>{col_widths['CONSTANT_score']}} | {r['errors']['CONSTANT']:>{col_widths['CONSTANT_error']}} | "
                        f"{r['scores']['ARGUMENTS']:>{col_widths['ARGUMENTS_score']}} | {r['errors']['ARGUMENTS']:>{col_widths['ARGUMENTS_error']}} | "
                        f"{r['scores']['LAMBDA']:>{col_widths['LAMBDA_score']}} | {r['errors']['LAMBDA']:>{col_widths['LAMBDA_error']}} | "
                        f"{r['avg_score']:>{col_widths['avg_score']}} | {r['avg_error']:>{col_widths['avg_error']}} |")
                f.write(line + "\n")
            f.write("\n")

    print(f"Markdown report generated: {report_path}")

def generate_markdown_parallel(results, args, timestamp, results_dir, sys_info, cmd_line):
    if not results:
        print("No results to process.")
        return

    grouped = {}
    present_threads = set()
    for entry in results:
        backend = entry["backend_param"]
        benchmark_name = entry["benchmark"].split(".")[-1]
        frontend, thread_count = benchmark_name.split("_")
        thread_count = int(thread_count)
        present_threads.add(thread_count)
        if backend not in grouped: grouped[backend] = {}
        if frontend not in grouped[backend]: grouped[backend][frontend] = {}
        score = entry.get("primaryMetric", {}).get("score", 0)
        grouped[backend][frontend][thread_count] = score

    report_path = os.path.join(results_dir, f"PARALLEL_BENCHMARK_RESULTS_{timestamp}.md")
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
                    row += f" {score:,.2f} |"
                f.write(row + "\n")
                
    print(f"Markdown report generated: {report_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run SLB4J benchmarks.")
    parser.add_argument("--parallel", action="store_true", help="Run parallel benchmarks instead of sequential ones")
    parser.add_argument("--all", action="store_true", help="Run both sequential and parallel benchmarks")
    parser.add_argument("--backends", nargs="+", help="Backends to test")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test")
    parser.add_argument("--handlers", nargs="+", help="Handlers to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (COMPACT, DEFAULT, DETAILED)")
    parser.add_argument("--message-types", nargs="+", help="Message types to test (CONSTANT, ARGUMENTS, LAMBDA)")
    parser.add_argument("--threads", type=int, nargs="+", help="Thread counts to test (for parallel mode)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--forks", type=int, help="Number of forks")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole (sequential only)")
    parser.add_argument("--dry-run", action="store_true", help="Show the benchmarks that will run without actually executing them")
    parser.add_argument("--mode", choices=["smoketest", "quick", "full"], help="Benchmark mode")
    
    args = parser.parse_args()
    if not validate_args(args):
        sys.exit(1)

    if args.mode:
        if args.mode == "full":
            if args.warmup is None: args.warmup = 3
            if args.iterations is None: args.iterations = 5
            if args.time is None: args.time = "1s"
        elif args.mode == "smoketest":
            if args.warmup is None: args.warmup = 0
            if args.iterations is None: args.iterations = 1
            if args.time is None: args.time = "50ms"
        elif args.mode == "quick":
            if args.warmup is None: args.warmup = 2
            if args.iterations is None: args.iterations = 3
            if args.time is None: args.time = "1s"
            
        if args.backends is None:
            args.backends = PARALLEL_BACKENDS if args.parallel else list(SEQUENTIAL_BACKENDS_MAP.keys())
        if not args.parallel:
            if args.handlers is None: args.handlers = ["CONSOLE", "FILE"]
            if args.formats is None: args.formats = ["COMPACT", "DEFAULT", "DETAILED"]
            if args.message_types is None: args.message_types = ["CONSTANT", "ARGUMENTS", "LAMBDA"]
        else:
            if args.frontends is None: args.frontends = PARALLEL_FRONTENDS
            if args.threads is None: args.threads = PARALLEL_THREADS

    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    results_dir = "benchmark_results"
    if not os.path.exists(results_dir):
        os.makedirs(results_dir)

    print(f"{BOLD_GREEN}Estimated total runtime: {format_duration(get_estimated_runtime(args))}{RESET}")
    
    cmd_line = "python3 " + " ".join(sys.argv)
    sys_info = get_system_info()
    
    if args.all:
        print("--- Running Sequential Benchmarks ---")
        results_seq = collect_results(args, timestamp, results_dir, force_parallel=False)
        if not args.dry_run:
            generate_markdown_sequential(results_seq, args, timestamp, results_dir, sys_info, cmd_line)
        
        print("\n--- Running Parallel Benchmarks ---")
        results_par = collect_results(args, timestamp, results_dir, force_parallel=True)
        if not args.dry_run:
            generate_markdown_parallel(results_par, args, timestamp, results_dir, sys_info, cmd_line)
    else:
        results = collect_results(args, timestamp, results_dir)
        if not args.dry_run:
            if args.parallel:
                generate_markdown_parallel(results, args, timestamp, results_dir, sys_info, cmd_line)
            else:
                generate_markdown_sequential(results, args, timestamp, results_dir, sys_info, cmd_line)
