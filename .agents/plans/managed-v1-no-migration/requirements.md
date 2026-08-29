# Managed V1 no-migration requirements

Status: Founder approved

- **MVNM-001** - The source baseline is exactly `sh-rn/xlite-daemon`
  `dev-maven` commit `7b15ddecbea3d220a8249baf1d33507c45effc73`.
- **MVNM-002** - `--no-migrate-legacy-wallet` is a value-free option. A value
  following it is rejected as a secret-bearing positional argument.
- **MVNM-003** - With the option present, a correctly encrypted V1 wallet is
  decrypted through the existing legacy reader and returned without changing
  any `key.dat` byte and without creating a legacy-migration backup.
- **MVNM-004** - Without the option, the existing V1 migration, backup,
  validation and rollback path remains unchanged and enabled by default.
- **MVNM-005** - V2 wallet reads behave identically with or without the option.
- **MVNM-006** - Existing three- and four-argument `CoinInstance.init`
  call sites retain the migration-enabled default. Only the new explicit
  policy path may disable migration.
- **MVNM-007** - The policy is passed explicitly. It must not be selected by
  an environment variable, configuration value or mutable global switch.
- **MVNM-008** - No password, mnemonic, seed, wallet byte or path is logged or
  placed in arguments. The existing stdin-only `--password` contract remains.
- **MVNM-009** - The existing numeric IPv4 loopback RPC bindings and restricted
  signing/broadcast behaviour are unchanged.
- **MVNM-010** - Focused tests prove V1 byte identity and no backup, default
  migration, V2 compatibility, flag ordering, value rejection and help text.
  The complete Maven suite must also pass under Java 21.
- **MVNM-011** - This tranche makes no remote change and does not authorise a
  daemon, profile, wallet, RPC, trade or funded action.
