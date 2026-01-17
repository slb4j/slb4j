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

backends_map = {
    "slb4j": "Slb4jBenchmark",
    "log4j": "Log4jBenchmark",
    "logback": "LogbackBenchmark",
    "jul": "JulBenchmark"
}

VALID_CATEGORIES = ["CONSOLE", "FILE"]
VALID_FORMATS = ["SIMPLE", "MDC", "MARKER", "LOCATION", "COLOR"]
VALID_MESSAGE_TYPES = ["CONSTANT", "ARGUMENTS", "LAMBDA"]

def parse_time(time_str):
    if not time_str:
        return 1.0
    if time_str.endswith("ms"):
        return float(time_str[:-2]) / 1000.0
    if time_str.endswith("s"):
        return float(time_str[:-1])
    return float(time_str)

def get_estimated_runtime(args):
    # backends_map is defined in global scope
    num_backends = len(args.backends) if args.backends else len(backends_map)
    
    # Frontends: 4 if not specified (slf4j, log4j, jul, jcl)
    num_frontends = len(args.frontends) if args.frontends else 4
    
    num_handlers = len(args.handlers) if args.handlers else 2 # CONSOLE, FILE
    num_formats = len(args.formats) if args.formats else 5 # SIMPLE, MDC, MARKER, LOCATION, COLOR
    
    msg_types = args.message_types if args.message_types else ["CONSTANT", "ARGUMENTS", "LAMBDA"]
    num_msg_types = len(msg_types)
    
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
    
    total_benchmarks_per_backend = num_frontends * num_handlers * num_formats * num_msg_types
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
            if b not in backends_map:
                print(f"Error: Invalid backend '{b}'. Valid backends are: {', '.join(backends_map.keys())}")
                return False
    
    if args.handlers:
        for c in args.handlers:
            if c not in VALID_CATEGORIES:
                print(f"Error: Invalid handler '{c}'. Valid handlers are: {', '.join(VALID_CATEGORIES)}")
                return False
                
    if args.formats:
        for f in args.formats:
            if f not in VALID_FORMATS:
                print(f"Error: Invalid format '{f}'. Valid formats are: {', '.join(VALID_FORMATS)}")
                return False
                
    if args.message_types:
        for mt in args.message_types:
            if mt not in VALID_MESSAGE_TYPES:
                print(f"Error: Invalid message type '{mt}'. Valid message types are: {', '.join(VALID_MESSAGE_TYPES)}")
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
    results_dir = "benchmark_results_json"
    if os.path.exists(results_dir):
        shutil.rmtree(results_dir)
    os.makedirs(results_dir)

    selected_backends = args.backends if args.backends else list(backends_map.keys())
    
    for backend in selected_backends:
        if backend not in backends_map:
            print(f"Unknown backend: {backend}")
            continue
            
        benchmark_class = backends_map[backend]
        print(f"Testing backend: {backend}")
        
        cmd = f"./gradlew :benchmark:runJmh -Pbackend={backend}"
        
        # Build JMH includes/excludes based on frontend
        # Frontends are methods in the benchmark classes
        if args.frontends:
            includes = []
            for frontend in args.frontends:
                includes.append(f"{benchmark_class}\\.{frontend}")
            cmd += f" -Pjmh.includes='{','.join(includes)}'"
        else:
            cmd += f" -Pjmh.includes='{benchmark_class}'"

        # Handle JMH params: category, format, messageType
        params = []
        if args.formats:
            params.append(f"format={','.join(args.formats)}")
        if args.handlers:
            params.append(f"category={','.join(args.handlers)}")
        
        msg_types = args.message_types if args.message_types else ["CONSTANT", "ARGUMENTS", "LAMBDA"]
        params.append(f"messageType={','.join(msg_types)}")
            
        if params:
            cmd += f" -Pjmh.parameters='{';'.join(params)}'"

        # Handle iterations/warmup
        if args.warmup is not None:
            cmd += f" -PwarmupIterations={args.warmup}"
        if args.iterations is not None:
            cmd += f" -Piterations={args.iterations}"
        if args.time:
            cmd += f" -PtimeOnIteration={args.time}"
        
        forks = 0 if args.mode == "smoketest" else 1
        cmd += f" -Pforks={forks}"
        
        if args.output_to_file:
            cmd += " -PoutputToFile=true"

        if args.dry_run:
            print(f"Dry run: Would execute command for backend '{backend}':")
            print(f"  Command: {cmd}")
            print(f"  Frontends: {', '.join(args.frontends) if args.frontends else 'ALL'}")
            print(f"  Handlers: {', '.join(args.handlers) if args.handlers else 'ALL (CONSOLE, FILE)'}")
            print(f"  Formats: {', '.join(args.formats) if args.formats else 'ALL (SIMPLE, MDC, MARKER, LOCATION, COLOR)'}")
            print(f"  Message Types: {', '.join(msg_types)}")
            print(f"  Warmup Iterations: {args.warmup if args.warmup is not None else 'Default (2)'}")
            print(f"  Measurement Iterations: {args.iterations if args.iterations is not None else 'Default (3)'}")
            print(f"  Time per Iteration: {args.time if args.time else 'Default (1s)'}")
            
            # Local estimation for this backend
            num_frontends = len(args.frontends) if args.frontends else 4
            num_handlers = len(args.handlers) if args.handlers else 2
            num_formats = len(args.formats) if args.formats else 5
            num_msg_types = len(msg_types)
            warmup = args.warmup if args.warmup is not None else 2
            iterations = args.iterations if args.iterations is not None else 3
            time_per_iter = parse_time(args.time if args.time else "1s")
            
            gradle_startup_overhead = 5.0
            
            # Determine number of forks
            forks = 1
            if args.mode == "smoketest":
                forks = 0
            
            jmh_fork_overhead = 0.5 if forks > 0 else 0.05 # seconds per benchmark
            
            total_benchmarks_per_backend = num_frontends * num_handlers * num_formats * num_msg_types
            time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
            estimated_backend = gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark
            
            print(f"  Estimated runtime for this backend: {format_duration(estimated_backend)}")
            print()
            continue

        # Print estimation for this backend when starting
        num_frontends = len(args.frontends) if args.frontends else 4
        num_handlers = len(args.handlers) if args.handlers else 2
        num_formats = len(args.formats) if args.formats else 5
        num_msg_types = len(msg_types)
        warmup = args.warmup if args.warmup is not None else 2
        iterations = args.iterations if args.iterations is not None else 3
        time_per_iter = parse_time(args.time if args.time else "1s")
        
        gradle_startup_overhead = 5.0
        
        # Determine number of forks
        forks = 1
        if args.mode == "smoketest":
            forks = 0
            
        jmh_fork_overhead = 0.5 if forks > 0 else 0.05 # seconds per benchmark
        
        total_benchmarks_per_backend = num_frontends * num_handlers * num_formats * num_msg_types
        time_per_benchmark = (warmup + iterations) * time_per_iter + jmh_fork_overhead
        estimated_backend = gradle_startup_overhead + total_benchmarks_per_backend * time_per_benchmark
        
        print(f"Estimated runtime for backend '{backend}': {format_duration(estimated_backend)}")
        
        if run_command(cmd):
            src_json = "benchmark/jmh-results.json"
            dest_json = os.path.join(results_dir, f"results_{backend}.json")
            if os.path.exists(src_json):
                shutil.copy(src_json, dest_json)
                with open(dest_json, 'r') as f:
                    data = json.load(f)
                    for entry in data:
                        entry['backend'] = backend
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

    # Group by category and format
    grouped = {}
    for entry in results:
        params = entry.get('params', {})
        category = params.get('category', 'N/A')
        fmt = params.get('format', 'N/A')
        key = (category, fmt)
        if key not in grouped:
            grouped[key] = {}
        
        backend = entry['backend']
        frontend = entry['benchmark'].split('.')[-1]
        pair_key = (backend, frontend)
        if pair_key not in grouped[key]:
            grouped[key][pair_key] = {}
        
        msg_type = params.get('messageType', 'N/A')
        primary_metric = entry.get('primaryMetric', {})
        score = primary_metric.get('score', 0)
        error = primary_metric.get('scoreError', 0)
        
        try:
            score = float(score)
        except (ValueError, TypeError):
            score = 0.0
            
        try:
            error = float(error)
            import math
            if math.isnan(error):
                error = None
        except (ValueError, TypeError):
            error = None
            
        grouped[key][pair_key][msg_type] = {'score': score, 'error': error}

    with open("BENCHMARK_RESULTS.md", "w") as f:
        f.write("# Logging Benchmark Results\n\n")
        
        # Sort keys: category, format
        sorted_keys = sorted(grouped.keys())

        for key in sorted_keys:
            category, fmt = key
            f.write(f"## Category: {category}, Format: {fmt}\n\n")
            
            pairs = grouped[key]
            
            # Message types we expect
            all_msg_types = ["CONSTANT", "ARGUMENTS", "LAMBDA"]
            
            rows = []
            for (backend, frontend), data in pairs.items():
                row = {
                    'backend': backend,
                    'frontend': frontend,
                    'scores': {},
                    'errors': {}
                }
                
                total_score = 0.0
                count = 0
                for mt in all_msg_types:
                    if mt in data:
                        s = data[mt]['score']
                        e = data[mt]['error']
                        row['scores'][mt] = f"{s:,.2f}"
                        row['errors'][mt] = f"{e:,.2f}" if e is not None else "N/A"
                        total_score += s
                        count += 1
                    else:
                        row['scores'][mt] = ""
                        row['errors'][mt] = ""
                
                avg_score = total_score / count if count > 0 else 0.0
                row['avg_score_val'] = avg_score
                row['avg_score'] = f"{avg_score:,.2f}" if count > 0 else ""
                # Error for average is not trivial to calculate correctly, 
                # but for simplicity we can show N/A or maybe user doesn't strictly need it.
                # In the user example, the Error column for average is shown but empty or N/A.
                row['avg_error'] = "N/A" if count > 0 else ""
                
                rows.append(row)
            
            # Sort by avg_score_val descending
            rows.sort(key=lambda x: x['avg_score_val'], reverse=True)
            
            # Calculate column widths
            col_widths = {
                'backend': max(len('Backend'), max(len(r['backend']) for r in rows) if rows else 0),
                'frontend': max(len('Frontend'), max(len(r['frontend']) for r in rows) if rows else 0),
                'CONSTANT_score': max(len('Constant (ops/s)'), max(len(r['scores']['CONSTANT']) for r in rows) if rows else 0),
                'CONSTANT_error': max(len('Error'), max(len(r['errors']['CONSTANT']) for r in rows) if rows else 0),
                'ARGUMENTS_score': max(len('Argument (ops/s)'), max(len(r['scores']['ARGUMENTS']) for r in rows) if rows else 0),
                'ARGUMENTS_error': max(len('Error'), max(len(r['errors']['ARGUMENTS']) for r in rows) if rows else 0),
                'LAMBDA_score': max(len('Lambda (ops/s)'), max(len(r['scores']['LAMBDA']) for r in rows) if rows else 0),
                'LAMBDA_error': max(len('Error'), max(len(r['errors']['LAMBDA']) for r in rows) if rows else 0),
                'avg_score': max(len('Average (ops/s)'), max(len(r['avg_score']) for r in rows) if rows else 0),
                'avg_error': max(len('Error'), max(len(r['avg_error']) for r in rows) if rows else 0),
            }

            # Header
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
            
            f.write(header + "\n")
            f.write(separator + "\n")
            
            for r in rows:
                line = (f"| {r['backend']:<{col_widths['backend']}} | {r['frontend']:<{col_widths['frontend']}} | "
                        f"{r['scores']['CONSTANT']:>{col_widths['CONSTANT_score']}} | {r['errors']['CONSTANT']:>{col_widths['CONSTANT_error']}} | "
                        f"{r['scores']['ARGUMENTS']:>{col_widths['ARGUMENTS_score']}} | {r['errors']['ARGUMENTS']:>{col_widths['ARGUMENTS_error']}} | "
                        f"{r['scores']['LAMBDA']:>{col_widths['LAMBDA_score']}} | {r['errors']['LAMBDA']:>{col_widths['LAMBDA_error']}} | "
                        f"{r['avg_score']:>{col_widths['avg_score']}} | {r['avg_error']:>{col_widths['avg_error']}} |")
                f.write(line + "\n")
                
            f.write("\n")

    print("Markdown report generated: BENCHMARK_RESULTS.md")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run SLB4J benchmarks.")
    parser.add_argument("--backends", nargs="+", help="Backends to test (slb4j, log4j, logback, jul)")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test (slf4j, log4j, jul, jcl)")
    parser.add_argument("--handlers", nargs="+", help="Categories/Handlers to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (SIMPLE, MDC, MARKER, LOCATION, COLOR)")
    parser.add_argument("--message-types", nargs="+", help="Message types to test (CONSTANT, ARGUMENTS, LAMBDA)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole")
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
            if args.warmup is None: args.warmup = 2
            if args.iterations is None: args.iterations = 3
            if args.time is None: args.time = "1s"
            
        if args.backends is None: args.backends = list(backends_map.keys())
        if args.handlers is None: args.handlers = ["CONSOLE", "FILE"]
        if args.formats is None: args.formats = ["SIMPLE", "MDC", "MARKER", "LOCATION", "COLOR"]
        if args.message_types is None: args.message_types = ["CONSTANT", "ARGUMENTS", "LAMBDA"]
    
    estimated_total = get_estimated_runtime(args)
    print(f"Estimated total runtime: {format_duration(estimated_total)}")

    if args.dry_run:
        collect_results(args)
    else:
        results = collect_results(args)
        generate_markdown(results)
