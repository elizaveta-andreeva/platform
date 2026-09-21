#!/usr/bin/env python3

import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path


STAGES = (
    "TotalMs",
    "LoadAndIndexMs",
    "ParseMs",
    "CollectorMs",
    "GenerateMs",
)


def median(rows, column):
    return statistics.median(int(row[column]) for row in rows)


def format_number(value):
    return f"{value:.1f}"


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: summarize-benchmark.py INPUT.csv OUTPUT.csv")

    input_file = Path(sys.argv[1])
    output_file = Path(sys.argv[2])
    with input_file.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))

    grouped = defaultdict(lambda: defaultdict(list))
    for row in rows:
        grouped[row["NodeSet"]][row["Version"]].append(row)

    output_columns = ["NodeSet", "BaselineRuns", "OptimizedRuns"]
    for stage in STAGES:
        stage_name = stage.removesuffix("Ms")
        output_columns.extend(
            (
                f"Baseline{stage_name}MedianMs",
                f"Optimized{stage_name}MedianMs",
            )
        )
        if stage == "TotalMs":
            output_columns.extend(("TotalReductionPercent", "TotalSpeedup"))
    output_columns.append("OutputMatches")

    output_file.parent.mkdir(parents=True, exist_ok=True)
    with output_file.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=output_columns)
        writer.writeheader()
        for nodeset in sorted(grouped):
            versions = grouped[nodeset]
            baseline = versions.get("baseline", [])
            optimized = versions.get("optimized", [])
            if not baseline or not optimized:
                raise SystemExit(f"Missing baseline or optimized results for {nodeset}")

            summary = {
                "NodeSet": nodeset,
                "BaselineRuns": len(baseline),
                "OptimizedRuns": len(optimized),
            }
            for stage in STAGES:
                stage_name = stage.removesuffix("Ms")
                baseline_median = median(baseline, stage)
                optimized_median = median(optimized, stage)
                summary[f"Baseline{stage_name}MedianMs"] = format_number(baseline_median)
                summary[f"Optimized{stage_name}MedianMs"] = format_number(optimized_median)
                if stage == "TotalMs":
                    reduction = 100.0 * (baseline_median - optimized_median) / baseline_median
                    speedup = baseline_median / optimized_median
                    summary["TotalReductionPercent"] = format_number(reduction)
                    summary["TotalSpeedup"] = f"{speedup:.3f}"

            baseline_hashes = {row["OutputSha256"] for row in baseline}
            optimized_hashes = {row["OutputSha256"] for row in optimized}
            summary["OutputMatches"] = (
                "yes"
                if len(baseline_hashes) == 1
                and baseline_hashes == optimized_hashes
                and "" not in baseline_hashes
                else "no"
            )
            writer.writerow(summary)

    print(f"Wrote summary to {output_file}")


if __name__ == "__main__":
    main()
