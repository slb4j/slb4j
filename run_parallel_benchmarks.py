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

backends = ["slb4j", "log4j", "logback", "jul"]
frontends = ["slf4j", "log4j"]
threads = [1, 2, 4, 8, 16, 64, 128]

def parse_time(time_str):
    if not time_str:
        return 1.0
    if time_str.endswith("ms"):
        return float(time_str[:-2]) / 1000.0
    if time_str.endswith("s"):
        return float(time_str[:-1])
    return float(time_str)

def get_estimated_runtime(args):
    num_backends = len(args.backends) if args.backends else len(backends)
    num_frontends = len(args.frontends) if args.frontends else len(frontends)
    num_threads = len(args.threads) if args.threads else len(threads)
    
    warmup = args.warmup if args.warmup is not None else 2
    iterations = args.iterations if args.iterations is not None else 3
    time_per_iter = parse_time(args.time if args.time else "1s")
    
    # JMH overhead estimation
    gradle_startup_overhead = 5.0 # seconds per backend
    
    # Determine number of forks
    forks = 1
    if args.mode == "smoketest":
        forks = 0
    
    jmh_fork_overhead = 0.5 if forks > 0 else 0.05 # seconds per benchmark
    
    total_benchmarks_per_backend = num_frontends * num_threads
    time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
    
    total_time = num_backends * (gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark)
    
    return total_time

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

def validate_args(args):
    if args.backends:
        for b in args.backends:
            if b not in backends:
                print(f"Error: Invalid backend '{b}'. Valid backends are: {', '.join(backends)}")
                return False
    
    if args.frontends:
        for f in args.frontends:
            if f not in frontends:
                print(f"Error: Invalid frontend '{f}'. Valid frontends are: {', '.join(frontends)}")
                return False

    if args.threads:
        for t in args.threads:
            if t not in threads:
                print(f"Error: Invalid thread count '{t}'. Valid thread counts are: {', '.join(map(str, threads))}")
                return False
                
    return True

def run_command(command):
    print(f"Running: {command}")
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Command failed with return code {result.returncode}")
        print(f"STDOUT: {result.stdout}")
        print(f"STDERR: {result.stderr}")
    return result.returncode == 0

def collect_results(args):
    all_results = []
    results_dir = "benchmark_results"
    if os.path.exists(results_dir):
        # We don't want to delete the whole directory if it's shared
        # but for this script's purposes, it was parallel_benchmark_results_json
        # which was dedicated. benchmark_results might be shared with run_benchmarks.py.
        # run_benchmarks.py uses benchmark_results_json.
        pass 
    else:
        os.makedirs(results_dir)

    selected_backends = args.backends if args.backends else backends
    
    for backend in selected_backends:
        print(f"Testing backend: {backend}")
        
        cmd = f"./gradlew :benchmark:runJmh -Pbackend={backend}"
        
        benchmark_class = "ParallelLoggingBenchmark"
        
        includes = []
        target_frontends = args.frontends if args.frontends else frontends
        target_threads = args.threads if args.threads else threads
        
        for f in target_frontends:
            for t in target_threads:
                includes.append(f"{benchmark_class}.{f}_{t}")
        
        cmd += f" -Pjmh.includes='{','.join(includes)}'"

        # Handle iterations/warmup
        if args.warmup is not None:
            cmd += f" -PwarmupIterations={args.warmup}"
        if args.iterations is not None:
            cmd += f" -Piterations={args.iterations}"
        if args.time:
            cmd += f" -PtimeOnIteration={args.time}"
        
        forks = 0 if args.mode == "smoketest" else 1
        cmd += f" -Pforks={forks}"

        if args.dry_run:
            print(f"Dry run: Would execute command for backend '{backend}':")
            print(f"  Command: {cmd}")
            
            # Local estimation for this backend
            num_frontends = len(target_frontends)
            num_threads = len(target_threads)
            warmup = args.warmup if args.warmup is not None else 2
            iterations = args.iterations if args.iterations is not None else 3
            time_per_iter = parse_time(args.time if args.time else "1s")
            
            gradle_startup_overhead = 5.0
            
            # Determine number of forks
            forks = 1
            if args.mode == "smoketest":
                forks = 0
            
            jmh_fork_overhead = 0.5 if forks > 0 else 0.05 # seconds per benchmark
            
            total_benchmarks_per_backend = num_frontends * num_threads
            time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
            estimated_backend = gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark
            
            print(f"  Estimated runtime for this backend: {format_duration(estimated_backend)}")
            print()
            continue

        # Print estimation for this backend when starting
        num_frontends = len(target_frontends)
        num_threads = len(target_threads)
        warmup = args.warmup if args.warmup is not None else 2
        iterations = args.iterations if args.iterations is not None else 3
        time_per_iter = parse_time(args.time if args.time else "1s")
        
        gradle_startup_overhead = 5.0
        
        # Determine number of forks
        forks = 1
        if args.mode == "smoketest":
            forks = 0
            
        jmh_fork_overhead = 0.5 if forks > 0 else 0.05 # seconds per benchmark
        
        total_benchmarks_per_backend = num_frontends * num_threads
        time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
        estimated_backend = gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark
        
        print(f"Estimated runtime for backend '{backend}': {format_duration(estimated_backend)}")
        
        if run_command(cmd):
            src_json = "benchmark/jmh-results.json"
            dest_json = os.path.join(results_dir, f"results_{backend}_parallel.json")
            if os.path.exists(src_json):
                shutil.copy(src_json, dest_json)
                with open(dest_json, 'r') as f:
                    data = json.load(f)
                    for entry in data:
                        entry['backend_param'] = backend # Use backend_param to avoid confusion with JMH params if any
                    all_results.extend(data)
            else:
                print(f"Warning: Result file {src_json} not found for {backend}")
        else:
            print(f"Error: Benchmark failed for {backend}")

    return all_results

def generate_markdown(results):
    if not results:
        print("No results to process.")
        return

    # Group results by backend and frontend
    # structure: grouped[backend][frontend][threads] = score
    grouped = {}
    
    # We want to keep all threads that were actually present in the results
    present_threads = set()
    
    for entry in results:
        backend = entry['backend_param']
        benchmark_name = entry['benchmark'].split('.')[-1] # e.g., slf4j_1
        frontend, thread_count = benchmark_name.split('_')
        thread_count = int(thread_count)
        present_threads.add(thread_count)
        
        if backend not in grouped:
            grouped[backend] = {}
        if frontend not in grouped[backend]:
            grouped[backend][frontend] = {}
            
        primary_metric = entry.get('primaryMetric', {})
        score = primary_metric.get('score', 0)
        
        grouped[backend][frontend][thread_count] = score

    with open("PARALLEL_BENCHMARK_RESULTS.md", "w") as f:
        f.write("# Parallel Logging Benchmark Results\n\n")
        f.write("Throughput (ops/s) for different thread counts.\n\n")
        
        sorted_backends = sorted(grouped.keys())
        all_threads = sorted(list(present_threads))
        
        # Table Header
        header = "| Backend | Frontend | " + " | ".join([f"{t} Threads" for t in all_threads]) + " |"
        separator = "| :--- | :--- | " + " | ".join([":---:" for _ in all_threads]) + " |"
        f.write(header + "\n")
        f.write(separator + "\n")
        
        for backend in sorted_backends:
            sorted_frontends = sorted(grouped[backend].keys())
            for frontend in sorted_frontends:
                row = f"| {backend} | {frontend} |"
                for t in all_threads:
                    score = grouped[backend][frontend].get(t, 0)
                    row += f" {score:,.2f} |"
                f.write(row + "\n")
                
    print("Markdown report generated: PARALLEL_BENCHMARK_RESULTS.md")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run SLB4J parallel benchmarks.")
    parser.add_argument("--backends", nargs="+", help="Backends to test (slb4j, log4j, logback, jul)")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test (slf4j, log4j)")
    parser.add_argument("--handlers", nargs="+", help="Handlers to test (not currently used by this script but for compatibility with run_benchmarks.py)")
    parser.add_argument("--threads", type=int, nargs="+", help="Thread counts to test (1, 2, 4, 8, 16, 64, 128)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--dry-run", action="store_true", help="Show the benchmarks that will run without actually executing them")
    parser.add_argument("--mode", choices=["smoketest", "quick", "full"], help="Benchmark mode")
    
    args = parser.parse_args()

    if not validate_args(args):
        exit(1)

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
            if args.warmup is None: args.warmup = 1
            if args.iterations is None: args.iterations = 2
            if args.time is None: args.time = "500ms"
            
        if args.backends is None: args.backends = backends
        if args.frontends is None: args.frontends = frontends
        if args.threads is None: args.threads = threads

    estimated_total = get_estimated_runtime(args)
    print(f"Estimated total runtime: {format_duration(estimated_total)}")

    if args.dry_run:
        collect_results(args)
    else:
        results = collect_results(args)
        generate_markdown(results)
