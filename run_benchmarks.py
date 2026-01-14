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
        
        cmd = f"./gradlew :benchmark:jmh -Pbackend={backend}"
        
        # Build JMH includes/excludes based on frontend
        # Frontends are methods in the benchmark classes
        if args.frontends:
            includes = []
            for frontend in args.frontends:
                includes.append(f"^{benchmark_class}\\.{frontend}$")
            cmd += f" -Pjmh.includes='({'|'.join(includes)})'"
        else:
            cmd += f" -Pjmh.includes='{benchmark_class}'"

        # Handle JMH params: category and format
        params = []
        if args.formats:
            params.append(f"format={','.join(args.formats)}")
        if args.categories:
            params.append(f"category={','.join(args.categories)}")
            
        if params:
            cmd += f" -Pjmh.parameters='{';'.join(params)}'"

        # Handle iterations/warmup
        if args.warmup is not None:
            cmd += f" -PwarmupIterations={args.warmup}"
        if args.iterations is not None:
            cmd += f" -Piterations={args.iterations}"
        if args.time:
            cmd += f" -PtimeOnIteration={args.time}"
        if args.output_to_file:
            cmd += " -PoutputToFile=true"

        if run_command(cmd):
            src_json = "benchmark/build/results/jmh/results.json"
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
            grouped[key] = []
        grouped[key].append(entry)

    with open("BENCHMARK_RESULTS.md", "w") as f:
        f.write("# Logging Benchmark Results\n\n")
        
        # Sort keys: CONSOLE first, then FILE; then by format
        sorted_keys = sorted(grouped.keys())

        for key in sorted_keys:
            category, fmt = key
            f.write(f"## Category: {category}, Format: {fmt}\n\n")
            
            entries = grouped[key]
            # Sort by score descending
            entries.sort(key=lambda x: float(x.get('primaryMetric', {}).get('score', 0)), reverse=True)
            
            # Prepare rows data and calculate column widths
            rows = []
            col_widths = {
                'backend': len('Backend'),
                'frontend': len('Frontend'),
                'score': len('Score (ops/s)'),
                'error': len('Error')
            }
            
            for entry in entries:
                backend = entry['backend']
                frontend = entry['benchmark'].split('.')[-1]
                
                primary_metric = entry.get('primaryMetric', {})
                score = primary_metric.get('score', 0)
                error = primary_metric.get('scoreError', 0)
                
                try:
                    score = float(score)
                    score_str = f"{score:,.2f}"
                except (ValueError, TypeError):
                    score_str = "N/A"
                    
                try:
                    error = float(error)
                    import math
                    if math.isnan(error):
                        error_str = "N/A"
                    else:
                        error_str = f"{error:,.2f}"
                except (ValueError, TypeError):
                    error_str = "N/A"
                
                row = {
                    'backend': backend,
                    'frontend': frontend,
                    'score': score_str,
                    'error': error_str
                }
                rows.append(row)
                
                # Update max widths
                for k in col_widths:
                    col_widths[k] = max(col_widths[k], len(row[k]))

            # Write header
            header = f"| {'Backend':<{col_widths['backend']}} | {'Frontend':<{col_widths['frontend']}} | {'Score (ops/s)':>{col_widths['score']}} | {'Error':>{col_widths['error']}} |"
            separator = f"| {'-' * col_widths['backend']} | {'-' * col_widths['frontend']} | {'-' * col_widths['score']}: | {'-' * col_widths['error']}: |"
            f.write(header + "\n")
            f.write(separator + "\n")
            
            # Write rows
            for row in rows:
                line = f"| {row['backend']:<{col_widths['backend']}} | {row['frontend']:<{col_widths['frontend']}} | {row['score']:>{col_widths['score']}} | {row['error']:>{col_widths['error']}} |"
                f.write(line + "\n")
                
            f.write("\n")

    print("Markdown report generated: BENCHMARK_RESULTS.md")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run SLB4J benchmarks.")
    parser.add_argument("--backends", nargs="+", help="Backends to test (slb4j, log4j, logback, jul)")
    parser.add_argument("--frontends", nargs="+", help="Frontends to test (slf4j, log4j, jul, jcl)")
    parser.add_argument("--categories", nargs="+", help="Categories to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (SIMPLE, MDC, MARKER, LOCATION, COLOR)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole")
    
    args = parser.parse_args()
    
    results = collect_results(args)
    generate_markdown(results)
