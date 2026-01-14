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

        # Handle JMH params: category, format, messageType
        params = []
        if args.formats:
            params.append(f"format={','.join(args.formats)}")
        if args.categories:
            params.append(f"category={','.join(args.categories)}")
        
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
    parser.add_argument("--categories", nargs="+", help="Categories to test (CONSOLE, FILE)")
    parser.add_argument("--formats", nargs="+", help="Formats to test (SIMPLE, MDC, MARKER, LOCATION, COLOR)")
    parser.add_argument("--message-types", nargs="+", help="Message types to test (CONSTANT, ARGUMENTS, LAMBDA)")
    parser.add_argument("--warmup", type=int, help="Number of warmup iterations")
    parser.add_argument("--iterations", type=int, help="Number of measurement iterations")
    parser.add_argument("--time", help="Time per iteration (e.g. 1s)")
    parser.add_argument("--output-to-file", action="store_true", help="Write logging output to a file instead of a blackhole")
    
    args = parser.parse_args()
    
    results = collect_results(args)
    generate_markdown(results)
