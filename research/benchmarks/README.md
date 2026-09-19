# Multithreading experiment — sequential vs parallel feature preparation

Measured 2026-09-19 on a laptop (macOS, Docker Desktop VM hosting PostgreSQL 16), warm JVM, one client,
customer `avery@demo.rocket.local` (25 seeded purchases), partner `markethub`. Each row: 20 warm-up runs, then
`n` timed runs of the complete preparation (profile + history + finance providers, each in its own short read
transaction). "equal" = every parallel result was identical to the sequential baseline. Raw data:
[`results.csv`](results.csv), [`results-pools.csv`](results-pools.csv). Reproduce with [`run.sh`](run.sh).

## Ordinary operation (no simulated delay)

| mode | pool | n | median ms | p95 ms | mean ms | errors | equal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| SEQUENTIAL | – | 200 | 2.070 | 3.690 | 2.264 | 0 | true |
| PARALLEL | 3 | 200 | 0.722 | 1.141 | 0.793 | 0 | true |

Even without artificial delay the three provider calls are dominated by network round trips to the database
container, so running them concurrently overlaps that waiting: median 2.07 → 0.72 ms (≈ 2.9×). The absolute
numbers are small; on a request that also calls the analysis service (~5–10 ms) and writes the decision, this
saving is a minor share of the total.

## Controlled simulated I/O delay (clearly an experiment, not production behaviour)

A decorator adds `Thread.sleep(delay)` before **each** provider call — a stand-in for a slow remote data source.

| delay per provider | mode | pool | median ms | p95 ms | speed-up (median) |
| ---: | --- | ---: | ---: | ---: | ---: |
| 10 ms | SEQUENTIAL | – | 45.474 | 52.381 | – |
| 10 ms | PARALLEL | 3 | 15.121 | 17.469 | 3.0× |
| 30 ms | SEQUENTIAL | – | 112.772 | 119.677 | – |
| 30 ms | PARALLEL | 3 | 36.846 | 39.970 | 3.1× |

Sequential time ≈ 3 × (delay + query); parallel time ≈ max of the three ≈ 1 × (delay + query). The sleeps
overshoot their nominal value on macOS (10 ms → ~14 ms each), which is why 3 × 10 ms shows as 45 ms.

## Pool size (`results-pools.csv`, n = 100)

| delay | SEQUENTIAL | PARALLEL pool 1 | pool 2 | pool 3 |
| ---: | ---: | ---: | ---: | ---: |
| 0 ms | 2.402 | 2.116 | 0.912 | 0.694 |
| 30 ms | 112.802 | 111.418 | 71.681 | 36.994 |

A pool of one thread behaves like the sequential version plus hand-off overhead (no speed-up, as expected);
two threads give ≈ 1.6×; three threads — one per independent task — give the full ≈ 3×. More threads than
tasks would add nothing.

## What this shows and what it does not

- **Correctness**: 1 300 parallel runs, zero errors, features identical to the sequential result every time,
  also under 40 simultaneous preparations for two different customers (`FeatureModeEqualityTest`).
- **Speed-up** appears because the tasks are independent and I/O-bound. For purely CPU-bound work of this
  size the thread hand-off (~0.1–0.3 ms here) could outweigh the gain; the pool-1 row shows that overhead.
- **Not measured**: throughput under many concurrent users (the shared pool is bounded at 3 threads with a
  queue of 50 — under load requests would queue and the per-request advantage would shrink), the effect of
  a remote database, or GC behaviour. Single machine, single run per configuration, no confidence intervals.
- The saved application records which mode prepared its features (`preparation_mode`), so a demo can show
  both modes producing the same decision for the same input.
