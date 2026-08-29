# Design

`ConfigHelper` owns a process-wide, default-off read-only policy. It is set by
`App.main` before log setup, endpoint initialisation or any `ConfigHelper`
construction. The eager master-server field is replaced by lazy construction
after this policy is selected.

In read-only mode `ConfigHelper` only opens an existing, complete JSON file.
It never creates a data directory, settings directory or file, and
`writeConfig` fails closed. It retains validity metadata so optional coin
profiles can be skipped rather than repaired. The startup preflight requires a
structurally valid existing `key.dat` plus usable master, BLOCK and LTC
configuration. It is deliberately performed before the stdin password prompt.

`KeyHandler` gains a structural existing-wallet check. It accepts the legacy
two-line V1 form and the four-line V2 form, but neither creates nor migrates a
wallet. Its existing migration policy remains unchanged outside this mode.

`CoinInstance` passes the policy into initialisation, disables discovery and
all configuration-mutating branches, and treats absent or invalid optional
coins as unavailable. Both master and coin RPC handlers reject operations that
would reload configuration or derive a persisted next address. The background
timer does not create a log-rotation scheduler in read-only mode. `App` skips
file logging and initial log rotation entirely.

This is a narrowly scoped zero-fund lifecycle containment mode. It does not
authorise write-enabled wallet behaviour or production packaging.
