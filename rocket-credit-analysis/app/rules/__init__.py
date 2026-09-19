"""Pure scoring functions. No I/O, no globals: every function receives the
feature section and the policy and returns a result, which keeps each module
independently testable and safe to call from several threads."""
