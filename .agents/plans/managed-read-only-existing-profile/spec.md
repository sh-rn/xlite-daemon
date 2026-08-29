# Managed read-only existing-profile startup

## Outcome

Add one explicitly opt-in local mode that starts XLite from an already existing
profile without creating, repairing, migrating or rewriting any profile file.

## Baseline

`codex/managed-v1-no-migration` at `126f1bd8aa746a4f83c6c28a4952bf3f967626c8`.

## Scope

- Add the value-free `--read-only-existing-profile` flag.
- Require it to be used only with `--no-migrate-legacy-wallet --password`.
- Validate the existing wallet plus master, BLOCK and LTC configuration before
  reading stdin or starting a server.
- Permit structurally valid V1 and V2 wallet files without rewriting either.
- Prevent configuration, wallet, address-count and log-file writes in this mode.
- Reject configuration reload and new-address RPC operations in this mode.

## User journey

The managed launcher supplies exactly the three approved flags. XLite checks
the existing profile before the password prompt. A valid profile can be read
and started; an incomplete or malformed profile fails without changing it.

## Non-goals

- No wallet import, wallet creation, migration, password change or backup.
- No live daemon or real-profile test.
- No remote action, release artefact, signing or packaging change.
