# Functional Alpha XLite intent-settlement remediation design

Status: T10 denial prerequisite implemented - named settlement design remains proposed

## Ownership and transport

Desktop main owns user confirmation, parent/child persistence, economic policy,
recovery ordering and the Core-facing compatibility gateway. Core receives only
gateway credentials. A future XLite managed-settlement listener uses a distinct
main-only credential delivered without command-line or environment secrets. The
ordinary per-coin RPC listener never dispatches a settlement operation.

Each BLOCK or LTC `CoinInstance` owns one settlement executor, but durable
deduplication belongs to a profile-scoped executor store shared across handler
connections and runtime restarts. A Netty handler is transport only. It cannot
own reservations, permissions or replay state.

The launch selector is proposed as `--managed-intent-settlement`. It is
value-free and valid only once alongside exactly
`--read-only-existing-profile`, `--no-migrate-legacy-wallet` and `--password`.
The credential/framing, endpoint allocation and private-store key delivery are
unresolved joint-review items. No code may select an unlimited or ambient
default.

## Closed v1 request envelope

Every operation uses a bounded JSON object with no duplicate/unknown fields,
noncanonical numbers or unbounded nesting. Integer amounts and times are
unsigned decimal strings. IDs/digests are exactly 64 lower-case hexadecimal
characters. Hex bytes are even-length lower-case hexadecimal. Base64, where
explicitly required for the compatibility proof, is canonical padded base64.

Settlement operations use this common context:

```text
schema                 = "blocknet.xlite.settlement.request.v1"
operation              = one exact named operation
profileId              = hex64 durable Desktop profile identity
runtimeGenerationId    = hex64 exact managed composition generation
parentIntentId         = hex64 durable Desktop parent identity
childStageId           = hex64 durable Desktop child identity
semanticOperationId    = hex64, recomputed by XLite
payloadDigest          = hex64, recomputed by XLite
role                    = "maker" | "taker"
stage                   = "service-fee" | "maker-deposit" |
                          "taker-deposit" | "maker-redeem" |
                          "taker-redeem" | "maker-refund" |
                          "taker-refund"
asset                   = "BLOCK" | "LTC"
chainIdentity           = exact ChainIdentityV1
policyDigest            = hex64 reviewed policy catalogue identity
issuedAtUnixMillis      = canonical unsigned decimal string
deadlineUnixMillis      = canonical unsigned decimal string
body                    = exact operation-specific object
```

Successful replies use schema `blocknet.xlite.settlement.result.v1` and contain
exactly `schema`, `operation`, `semanticOperationId`, `payloadDigest`, `status`,
`resultDigest`, `recordedAtUnixMillis` and the closed operation-specific `body`.
Denied replies use schema `blocknet.xlite.settlement.error.v1` and contain
exactly `schema`, `operation`, optional `semanticOperationId`, `code`,
`quarantined` and `recordedAtUnixMillis`. No free-form upstream message crosses
the boundary.

`checkOwnedAddress` uses a smaller connector context because the reviewed Core
probe can precede an order: schema, operation, profile, generation, semantic
operation, payload digest, asset, chain identity, policy digest, timestamps and
body. It has no parent or child and creates no reservation or spending authority.

`ChainIdentityV1` contains exactly `asset`, `network` (`mainnet`),
`genesisHash`, `networkMagicHex`, `p2pkhVersion`, `p2shVersions`,
`signedMessagePrefixHex`, `baseUnitsPerCoin`, `minimumConfirmations` and
`chainPolicyDigest`. The already accepted genesis hashes are BLOCK
`00000eb7919102da5a07dc90905651664e6ebf0811c28f06573b9a0fd84ab7b8`
and LTC
`12a765e31ffd4059bada1e25190f6e98c99d9714d334efa41a195a7e7e04bfe2`.
The remaining exact accepted values and source identities must be frozen by
Desktop `FA-IS-T02`; current Java constants are not runtime admission.

## Semantic identity

Both sides compute `semanticOperationId` as SHA-256 over the exact
domain-separated, length-prefixed UTF-8 identity tuple:

```text
"BLOCKNET-XLITE-SETTLEMENT-OP-V1";
operation; profileId; parentIntentId-or-empty;
childStageId-or-empty; role-or-empty; stage-or-empty; asset;
chainPolicyDigest; operationObjectId
```

Each component is encoded as its ASCII field name, a colon, canonical decimal
UTF-8 byte length, a colon and its exact bytes, followed by LF. `payloadDigest`
uses domain `BLOCKNET-XLITE-SETTLEMENT-PAYLOAD-V1` and the same framing over the
immutable effect fields. Transport-only `schema`, `runtimeGenerationId`,
`issuedAtUnixMillis`, `semanticOperationId` and `payloadDigest` are excluded so
an accepted generation handover cannot manufacture a new effect or conflict
with the same durable payload. XLite validates those excluded fields separately.
`operationObjectId` is the child stage for reserve/sign/broadcast, the exact
outpoint digest for proof, the address digest for the ownership probe and the
target operation ID for result lookup. The implementation uses fixed internal
field order and accepts no caller-supplied serializer or digest function. This
encoding remains proposed until T02/T03 freezes independent vectors.

## Exact operation bodies

`reserveWalletInputs` body:

```text
purpose                 = "service-fee" | "deposit"
walletAddress           = canonical mainnet address
baseAmountBlockUnits    = canonical integer in 1..1000000000
outgoingAmountUnits     = positive canonical integer
fixedTakerFeeBlockUnits = "1500000" for service-fee, otherwise "0"
maxStageNativeFeeUnits  = positive canonical integer
maxGraphNativeFeeUnits  = positive canonical integer
recoveryReserveUnits    = non-negative canonical integer
outpoints               = 1..10 unique PrevoutEvidenceV1 entries
```

Each `PrevoutEvidenceV1` contains exactly display-order `txid`, `vout`,
`valueBaseUnits`, `scriptPubKeyHex`, `ownedAddress`, `confirmationBlockHash`,
`confirmationHeight` and `observedConfirmations`. XLite obtains independent
current evidence and requires equality, one owned address, adequate depth and
no spent or reserved outpoint. The result contains `reservationId`,
`reservationDigest`, exact outpoint identities, status `reserved` and
`deduplicated: true|false`.

`proveReservedUtxoOwnership` body contains `reservationId`, one exact reserved
outpoint, canonical Core message bytes/digest and connector purpose
`xbridge-reserved-utxo-proof-v1`. The result is status `proof-created`, the
message digest, compact-proof base64 and proof digest. It creates no authority.
The operation remains unavailable until peer-side replay closure is accepted.

`checkOwnedAddress` body contains one exact address and purpose
`xbridge-owned-address-probe-v1`. Its result is status `ownership-checked` and
`owned: true|false`. It never returns proof or signature bytes.

`signSettlementStage` body contains exactly `reservationId`,
`unsignedTransactionHex`, `unsignedBytesDigest`, `unsignedTxid`, `unsignedWtxid`,
ordered `previousOutputs`, ordered `expectedOutputs`, `changeAddress`,
`inputSequences`, `lockTime`, `sighashPolicy` (`all` only),
`maxStageNativeFeeUnits` and `contractPolicyDigest`. Each expected output contains
exactly index, value base units and scriptPubKey hex. It is accepted only for
service-fee/deposit stages. The result body contains exactly final transaction
hex/digest, final txid/wtxid and signed input indexes with status `signed`.

`broadcastSettlementStage` body contains exactly `finalTransactionHex`,
`finalBytesDigest`, `finalTxid`, `finalWtxid`, producer
`xlite-wallet-input-signer-v1` or `admitted-core-protocol-signer-v1`, exact
`predecessorOperationId`, `contractPolicyDigest` and `recoveryBinding`. The
binding is either null or contains exactly `recoveryRecordId`, `fundingTxid`,
`refundTxid`, `recoveryPayloadDigest` and `persistedAtUnixMillis`. The result is
`broadcast-succeeded` only when the backend-returned identity equals the local
identity. A definitive pre-acceptance rejection is `broadcast-rejected`.
Anything ambiguous is `quarantined` and is never automatically dispatched again.

`getSettlementOperationResult` body contains the target semantic operation ID
and expected payload digest. It returns the durable result or `not-found`; it
never signs, broadcasts, reconstructs or retries.

## Durable result contract

Persisted statuses are exactly:

- `reserved`
- `proof-created`
- `ownership-checked`
- `signed`
- `broadcast-dispatch-recorded`
- `broadcast-succeeded`
- `broadcast-rejected`
- `quarantined`

An identical request returns the prior status/result. A different payload for
the same semantic identity returns `OPERATION_PAYLOAD_CONFLICT`. Dispatch is
durably recorded with exact byte identity before relay. A crash or lost reply
therefore returns `broadcast-dispatch-recorded` or `quarantined`, never a fresh
permission. Reconciliation may add evidence and move to a proved terminal state
but cannot dispatch. Reservation release/terminal erasure is deliberately not
defined here and remains a T03/T07 retention-contract blocker.

Closed public error codes are:

```text
INVALID_REQUEST, UNAUTHENTICATED, CAPABILITY_UNAVAILABLE,
STALE_GENERATION, DEADLINE_EXPIRED, CHAIN_IDENTITY_MISMATCH,
POLICY_IDENTITY_MISMATCH, INTENT_BINDING_MISMATCH,
OPERATION_PAYLOAD_CONFLICT, OPERATION_NOT_FOUND,
ASSET_OR_STAGE_DENIED, WALLET_OWNERSHIP_DENIED,
RESERVATION_CONFLICT, PREVIOUS_OUTPUT_MISMATCH,
TRANSACTION_POLICY_DENIED, SIGNING_DENIED,
BROADCAST_PREREQUISITE_MISSING, BROADCAST_IDENTITY_MISMATCH,
BACKEND_DEFINITIVE_REJECTION, AMBIGUOUS_OUTCOME_QUARANTINED,
PERSISTENCE_UNAVAILABLE
```

Errors expose no raw backend response or private transaction/wallet data.

## Proposed source dossier

No source change is admitted yet. After T02/T03 acceptance, the exact mutation
proposal is expected to amend `ConsoleMenu`, `CoinInstance`, RPC managed-mode
denials and lifecycle ownership; add a private settlement endpoint, closed
schemas, policy validator, wallet executor and encrypted durable operation
store; and add focused launch/schema/policy/dedup/crash/transport/bypass tests.

Before code, a reviewed amendment must freeze every exact file, class, method,
test and dependency. In particular it must decide private credential delivery,
listener allocation, store root/key ownership, durable publication, rollback
limits, reservation release, exact chain constants, fee caps, stage variants,
contract fields, proof replay and Core-protocol result verification. This draft
does not authorise filling those gaps by implementation choice.

## XL-IS-SEQ-001 denial-only implementation boundary

The independently accepted first source tranche is only the existing managed
read-only denial boundary. Each `CoinInstance` binds its effective read-only
policy once during initialisation; sensitive helpers consult that immutable
instance policy, not a mutable global mode. Existing-profile address derivation
uses a private same-thread initialisation authority that cannot be invoked by an
RPC/helper caller after startup.

The ordinary coin handler rejects sensitive method names before inspecting
their parameters. The inventory includes generic raw construction/sign/send,
address creation, key import/export, mnemonic export, arbitrary message signing,
transfer/send aliases and XRouter send. Direct transaction-signing, private-key
import/address-generation and raw-relay helpers enforce the same instance
policy. Unmanaged behaviour remains covered and unchanged.

This boundary has no success path in managed mode and defines no settlement
request. Its only output is a generic unavailable denial or an in-process
exception without private values. It therefore cannot substitute for T02/T03,
the six named operations or a runtime admission.
