# Managed V1 no-migration specification

Status: Founder approved

Approved in Codex task: `01a03510-1e11-7bc1-8436-af678af92082`

Approved: 29 August 2026

## Outcome

Add one explicit, value-free `--no-migrate-legacy-wallet` option for the
Unified Desktop controlled local runtime. When it accompanies normal
`--password` startup, an existing legacy V1 wallet may be decrypted for that
process without renaming, backing up or rewriting `key.dat`.

The option exists only to preserve the current wallet bytes during bounded
local Alpha testing. It does not weaken the default path: without the option,
the existing automatic V1-to-V2 migration and rollback behaviour remains
unchanged. V2 wallets continue to use the current PBKDF2-SHA-256 and AES-CBC
path in both modes.

## Exact baseline

- Repository: `https://github.com/sh-rn/xlite-daemon.git`
- Base branch: `dev-maven`
- Base commit: `7b15ddecbea3d220a8249baf1d33507c45effc73`
- Base tree: `d3d4555ae02f74f72e952dd4bbff55f01e1e9628`
- Working branch: `codex/managed-v1-no-migration`

No tryiou or public-upstream repository is mutable in this tranche.

## Scope

- Add the value-free console option.
- Carry one immutable migration policy through console and coin
  initialisation.
- Reuse the existing V1 decryption implementation.
- Skip only `migrateToNewFormat(...)` when the option is present.
- Preserve existing overloads and their migration-enabled defaults.
- Add focused V1, V2, argument and help tests.
- Run the focused and complete Maven test suites with Java 21.

## Non-goals

- No password, mnemonic or key in arguments, environment, logs or output.
- No RPC, authentication, listener, port or configuration change.
- No wallet creation, import, password change or migration command.
- No Desktop change, daemon launch, profile access or funded action here.
- No push, pull request, merge, tag or release.
