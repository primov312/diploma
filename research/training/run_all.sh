#!/usr/bin/env sh
# Reproduce the AI experiment end to end: synthetic data -> model artifact -> evaluation.
# Needs the analysis virtualenv (rocket-credit-analysis/.venv) with numpy installed.
set -eu
cd "$(dirname "$0")"
PY=../../rocket-credit-analysis/.venv/bin/python
[ -x "$PY" ] || PY=python3
$PY generate_data.py --customers "${CUSTOMERS:-4000}" --seed "${SEED:-42}"
$PY train.py
$PY evaluate.py
echo "results: ../results/evaluation.md"
