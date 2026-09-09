# Contributing

1. Open or reference an issue describing the invariant or behavior being changed.
2. Work on a feature branch and keep commits focused.
3. Run `gradle clean test --warning-mode=fail` on JDK 21 or newer.
4. Do not weaken compiler warnings, convergence tests, or security checks to make CI pass.
5. Add deterministic tests for CRDT or reconciliation changes, especially alternate delivery orders and duplicates.
6. Keep claims aligned with measured and tested behavior.

Pull requests should preserve Apache-2.0 compatibility and must not include credentials, private production data, or generated secrets.
