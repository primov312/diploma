# research/

Offline material for the diploma experiments. Nothing here runs inside the containers.

| Folder | Content |
| --- | --- |
| `fixtures/` | pointer to the demo personas and catalog used by the running app |
| `training/` | AI experiment: `generate_data.py` (seeded synthetic customers + latent-risk labels), `train.py` (logistic regression, numpy, writes the serving artifact `rocket-credit-analysis/app/model/model-v1.json`), `evaluate.py` (rules vs hybrid on held-out customers), `run_all.sh` |
| `results/` | committed evaluation outputs: `evaluation.md`, `evaluation.json`, `confusion_matrices.csv` |
| `benchmarks/` | multithreading experiment (Step 7): sequential vs parallel feature preparation, CSV timings |

```bash
python3 -m venv rocket-credit-analysis/.venv && rocket-credit-analysis/.venv/bin/pip install -r rocket-credit-analysis/requirements-dev.txt -r research/requirements.txt
research/training/run_all.sh          # ~10 s; deterministic for a given seed
```

The generated `training/data/` is git-ignored (reproducible from the seed); the model artifact and the
results are committed so the served model and the reported numbers always match.
