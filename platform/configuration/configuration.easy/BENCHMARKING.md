# OPC UA parser benchmark

This benchmark measures one complete `DomParser.process` invocation in a fresh JVM. It records the total generation
time and a stage breakdown for loading and indexing, parsing, statistics collection and IVML generation.

## Comparison commits

Use these commits to isolate the indexed-lookup change merged through pull request 18:

- baseline: `9ca113e6ee1e664e7a1496dd0e68f01dade98601`
- optimized: `3468e4557335af9e72904d65c69099dc7284c558`

The optimized commit is the merge commit on `elizaveta-andreeva/platform` `main`; the baseline is its first parent.
The benchmark inputs are identical between them.

## Prepare Ubuntu worktrees

From a current clone of `elizaveta-andreeva/platform`:

```bash
git fetch origin --prune
git worktree add -b fix/opcua-benchmark-isolated-jvms \
    ../platform-benchmark-new origin/main
git worktree add --detach \
    ../platform-benchmark-base 9ca113e6ee1e664e7a1496dd0e68f01dade98601
```

Apply the benchmark patch as a commit to the new branch and as uncommitted test instrumentation to the detached
baseline worktree:

```bash
git -C ../platform-benchmark-new am /path/to/benchmark-isolated-runs.patch
git -C ../platform-benchmark-base apply /path/to/benchmark-isolated-runs.patch
```

The detached baseline worktree is used only to produce measurements. Push only
`fix/opcua-benchmark-isolated-jvms` for the eventual pull request.

## Run

Run the script from the optimized worktree, passing both repository roots:

```bash
cd ../platform-benchmark-new/platform/configuration/configuration.easy
chmod +x run-benchmark.sh summarize-benchmark.py
./run-benchmark.sh \
    ../../../../platform-benchmark-base \
    ../../.. \
    5
```

The script activates the module's `Cfg` Maven profile and compiles both versions before measuring. Without `-PCfg`,
this module's default profile skips compilation and tests. The script then randomizes the NodeSet order, alternates
which version runs first and starts a new Maven test JVM for every measurement with `-Xms2g -Xmx2g`.
If required models are missing, standard input is closed and the invocation fails rather than waiting for interactive
input. Each invocation also has a 30-minute timeout; set `BENCHMARK_TIMEOUT` to another GNU `timeout` duration when
needed.

Results are written to a timestamped `benchmark-results-*` directory:

- `raw/`: one CSV for each version, round and NodeSet
- `all-results.csv`: all raw rows with one header
- `summary.csv`: medians, percentage reduction, speedup and output-hash comparison
- `environment.txt`: commits, Java version, Maven version and number of rounds

Use at least five rounds. Increase to ten if timings have substantial variation. The output hash must match before a
performance comparison is accepted.
