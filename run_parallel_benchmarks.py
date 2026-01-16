import os
import subprocess
import json
import shutil
import argparse

backends = ["slb4j", "log4j", "logback", "jul"]
frontends = ["slf4j", "log4j"]
threads = [1, 2, 4, 8, 16, 64, 128]

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
    results_dir = "parallel_benchmark_results_json"
    if os.path.exists(results_dir):
        shutil.rmtree(results_dir)
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

        if args.dry_run:
            print(f"Dry run: Would execute command for backend '{backend}':")
            print(f"  Command: {cmd}")
            print()
            continue

        if run_command(cmd):
            src_json = "benchmark/jmh-results.json"
            dest_json = os.path.join(results_dir, f"results_{backend}.json")
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
    parser.add_argument("--threads", type=int, nargs="+", help="Thread counts to test (1, 2, 4, 8, 16, 64, 128)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--dry-run", action="store_true", help="Show the benchmarks that will run without actually executing them")
    parser.add_argument("--smoketest", action="store_true", help="Run with 0 warmup, 1 iteration, 50ms time")
    parser.add_argument("--quick", action="store_true", help="Run with 1 warmup, 2 iterations, 500ms time")
    
    args = parser.parse_args()

    if not validate_args(args):
        exit(1)

    if args.smoketest:
        if args.warmup is None: args.warmup = 0
        if args.iterations is None: args.iterations = 1
        if args.time is None: args.time = "50ms"
    elif args.quick:
        if args.warmup is None: args.warmup = 1
        if args.iterations is None: args.iterations = 2
        if args.time is None: args.time = "500ms"
            
    if args.dry_run:
        collect_results(args)
    else:
        results = collect_results(args)
        generate_markdown(results)
