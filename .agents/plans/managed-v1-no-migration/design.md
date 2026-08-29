# Managed V1 no-migration design

Status: Founder approved

## Decision

Retain `KeyHandler.getBaseSeed(char[])` as the compatibility and default API.
It delegates to a new overload whose second argument explicitly controls only
legacy migration:

```java
getBaseSeed(passphrase, true)  // existing default
getBaseSeed(passphrase, false) // decrypt V1 without rewriting it
```

The V1 branch already decrypts the seed before it calls
`migrateToNewFormat(...)`. The false policy returns that decrypted seed at
that point. It does not introduce another cipher, parser, backup or wallet
format. V2 and missing-wallet behaviour do not branch on this policy.

`CoinInstance` receives a five-argument `init` overload carrying the policy.
Its existing overloads delegate with migration enabled so existing callers
cannot change behaviour accidentally. `ConsoleMenu` derives one final policy
from the sanitised no-value option and passes it to both the synchronous BLOCK
initialisation and concurrent asset initialisation.

The option is not a secret source. The existing argument sanitiser already
rejects positional values after stdin-only options. It is extended to classify
`--no-migrate-legacy-wallet` as value-free, so a following non-option value is
rejected rather than silently ignored.

## Safety boundary

The flag preserves the legacy encrypted bytes and therefore preserves their
weaker at-rest KDF/cipher until a separately authorised migration occurs. This
is an explicit local-testing compatibility trade-off, not a new default. The
normal unflagged path still upgrades V1 to V2 with its current backup,
validation and rollback controls.

No mutable global policy is used. No password or wallet material crosses the
console option, and no RPC or configuration behaviour changes. Unified
Desktop must separately pin and review the exact resulting JAR before it may
pass the option.

## Verification

`KeyHandlerTest` snapshots the complete legacy file, reads with migration
disabled, confirms the mnemonic, byte identity and absence of a migration
backup, and separately proves V2 compatibility. Existing migration tests
continue to prove the default path.

`POR171BoundaryTest` checks both orderings with `--password`, rejects a value
after the new option, and confirms the help text. Focused tests run first,
followed by the complete Maven suite and a clean-diff inspection for
OpenRewrite changes.
