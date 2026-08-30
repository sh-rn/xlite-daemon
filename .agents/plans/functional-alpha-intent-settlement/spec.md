# Functional Alpha XLite intent-settlement remediation proposal

Status: T10 denial prerequisite independently accepted - settlement and runtime remain held

## Outcome

Define the XLite half of the jointly reviewed Functional Alpha settlement
contract without enabling signing, broadcasting or funded use. A future
reviewed implementation would give Desktop main six named, authenticated and
intent-bound operations for BLOCK and LTC while keeping ordinary Core-compatible
raw signing, broadcasting and key methods unavailable.

This proposal is the XLite mutation dossier required by Desktop task
`FA-IS-T11`. It does not complete that task, Desktop `FA-IS-T02` or
`FA-IS-T03`, recovery `FA-RCV-T07`, any native admission gate, or any parent
`FA-SIGN-001` to `FA-SIGN-016` requirement.

## Exact proposal subject

- Authorised repository: `https://github.com/sh-rn/xlite-daemon.git`
- Read-only upstream: `https://github.com/tryiou/xlite-daemon.git`
- Base commit: `6f790ad9fb892861db2350ddcb2c559e34b11af0`
- Base tree: `9997761b3d02812a557055cba8d0993f3d23f12f`
- Proposal branch: `codex/functional-alpha-intent-settlement`
- Java/Maven baseline: Java 21 and Maven 3.8.6 or later

The independently accepted denial-only source head is
`5f8a15759b027b1fd4205bfb067130b7040d6bea`, tree
`71b5f33fc8931918bcc40f002b4ff6726a13316e`. The exact T04 future-source
dossier is
[`docs/protocols/functional-alpha-intent-settlement-mutation-manifest.md`](../../../docs/protocols/functional-alpha-intent-settlement-mutation-manifest.md).
It remains a review draft and authorises no source.

The repository standing authority permits this proposal and later normal
delivery work in `sh-rn/xlite-daemon`. It does not make an unreviewed interface,
source change, JAR or runtime admissible. No change may be made to either
read-only upstream.

## Baseline facts and corrected gaps

The current fork deliberately returns method-not-found for
`signrawtransaction` and `sendrawtransaction`. That remains correct. Current
managed read-only startup also forbids new addresses but ordinary RPC paths
still contain key import, key export and message signing that need a complete
managed-mode bypass inventory and fail-closed remediation.

The discarded earlier draft was not compatible with the active Desktop plan:

- Core calls `signrawtransaction` with exactly `[rawtx, [], null]`, not one
  argument.
- Core uses `sendrawtransaction` for the wallet-funded deposit and for the
  redeem/refund contract-spend paths. A BLOCK-only broadcast design cannot
  complete a BLOCK/LTC swap.
- A successful compatibility message proof cannot itself create spending
  authority. Desktop main must first durably accept the parent and child.
- Handler-local or process-only replay state cannot survive connection changes,
  restarts or a lost response.
- Consuming a broadcast permission before relay without durable result lookup
  can strand an unknown successful transaction.
- The product ceiling is `10 BLOCK`, while principal, fixed `0.015 BLOCK`
  taker fee, BLOCK native fees, LTC native fees and recovery reserves remain
  separate bounded ledger entries.

## Proposed private operations

Only these exact v1 names are proposed:

1. `reserveWalletInputs`
2. `proveReservedUtxoOwnership`
3. `checkOwnedAddress`
4. `signSettlementStage`
5. `broadcastSettlementStage`
6. `getSettlementOperationResult`

They are private Desktop-to-XLite operations, not ordinary coin RPC methods.
Desktop main remains the sole durable parent/child intent authority. XLite
must independently verify wallet ownership, reservations, chain and policy
identity, stage, exact bytes and durable semantic-operation identity. Main's
claim that a request is authorised is never sufficient by itself.

## Scope of a future reviewed implementation

- Both BLOCK and LTC mainnets, with exact source-derived identities frozen
  before code.
- One parent and child stage supplied only by authenticated Desktop main.
- One owned-address funding set per trade need, one to ten exact inputs, no
  cross-intent or cross-address reuse, and disjoint BLOCK trade and taker-fee
  inputs where applicable.
- Wallet input signing only for the service-fee and deposit stages. Core's
  separately admitted per-swap protocol signer remains the only exception for
  contract redeem/refund inputs.
- Stage-bound broadcast of exact final bytes after Desktop and XLite validation,
  refund-before-funding durability where required, durable dispatch recording,
  returned transaction-identity equality and unknown-outcome quarantine.
- Persistent XLite semantic-operation deduplication and result lookup that
  survives restarts and lost replies without blind re-dispatch.
- Distinct private XLite transport credentials that Core never receives.
- Complete managed-mode denial of generic signing, broadcasting, transfers,
  key import/export, arbitrary message signing and alternate local routes.

## Out of scope

- Any source implementation other than independently authorised T10 denial
  hardening.
- Live runtimes, wallet profiles, real keys, funds, orders or transactions.
- Generic raw transaction, transfer, key or message-signing APIs.
- Additional assets, testnet, public Beta, existing-profile adoption or a
  general sweep/exit operation.
- Core source mutation, Desktop recovery implementation, signing, packaging,
  notarisation, release, push, merge, tag or remote publication in this tranche.

## Sequenced denial prerequisite XL-IS-SEQ-001

On 30 August 2026, independent coordinator `/root/delivery_coordinator`
reviewed exact proposal commit
`9bfc85d6e7aee1dbf99943ed113575a6805a3ddd` and returned PASS as a coherent
prerequisite contract. The coordinator authorised only `XL-IS-T10` to proceed
before Desktop `FA-IS-T02`/`T03` and recovery `FA-RCV-T07` because this tranche
removes existing generic authority and creates no new capability.

The tranche may add managed read-only denials at existing RPC and in-process
helper boundaries, explicit alias tests and positive unmanaged-compatibility
tests. It may not add the proposed selector, endpoint, credential, schema
parser, store, reservation, proof, signer, broadcaster, chain constant or live
wiring. T10 is checked only because the complete inventory and Java 21 focused
and relevant full tests passed; its evidence is recorded in `tasks.md`.
T02/T03/T05-T09 and all runtime/funds gates remain held.

The T04 review also found that IS-SEQ-004's statement that the proposal body
shapes are adopted is not enough to implement a closed parser where the
proposal gives only narrative, unnamed nested or status-specific fields. Those
exact schema gaps must be resolved by an additive joint contract before parser
source can be authorised.

## Dependency hold

Named settlement implementation remains held until the Desktop connector/schema review
`FA-IS-T02`, joint recovery/executor contract `FA-IS-T03` and
`FA-RCV-T07`, proof-replay decision, exact native mutation review and all
required source/runtime admission gates are closed. A committed proposal is
planning evidence only and must not be described as settlement-ready.
