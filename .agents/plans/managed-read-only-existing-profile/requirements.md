# Requirements

- R1: `--read-only-existing-profile` is value-free and only valid together
  with `--no-migrate-legacy-wallet` and `--password`.
- R2: The process selects read-only policy before any configuration object or
  master RPC server can be constructed.
- R3: A missing or malformed wallet, master configuration, BLOCK configuration
  or LTC configuration fails before a password is read or an RPC server starts.
- R4: Structurally valid V1 and V2 wallets remain byte-identical.
- R5: Read-only startup creates no directory, configuration, wallet, backup or
  application log file, and performs no configuration write.
- R6: Optional coin configurations that are absent, malformed or not enabled
  are skipped without creating replacement files.
- R7: `reloadconfig` and `getnewaddress` fail closed in read-only mode.
- R8: Focused tests prove argument policy, profile validation, V1/V2 acceptance
  and a whole temporary-profile byte snapshot before and after read-only reads.
