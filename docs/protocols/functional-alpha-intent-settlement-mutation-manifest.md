# Functional Alpha XLite source-only mutation manifest

Status: proposed XL-IS-T04 successor review subject - no named settlement source is authorised

## Exact subjects and authority

This is the pre-code dossier required by `XL-IS-T04`. It is documentation, not
an implementation or runtime admission.

- Authorised repository: `https://github.com/sh-rn/xlite-daemon.git`
- Read-only upstream: `https://github.com/tryiou/xlite-daemon.git`
- Branch: `codex/functional-alpha-intent-settlement`
- Dossier predecessor head: `a3be3230842fcfcb3d90dda8bf7192e9e4374119`
- Dossier predecessor tree: `1aea08c4ff07cfc9b771888806ee8f9099cd428f`
- Unchanged source baseline head: `5f8a15759b027b1fd4205bfb067130b7040d6bea`
- Unchanged source baseline tree: `71b5f33fc8931918bcc40f002b4ff6726a13316e`
- Accepted denial sequence: `9bfc85d6e7aee1dbf99943ed113575a6805a3ddd`
  to `0963adc5cf96d03f0473f2c0714fa10dde682482` to the source baseline
- Canonical corrected Desktop head: `023838583a735deb5b81151338f7a79c8d37aa54`
- Canonical corrected Desktop tree: `d39afdc05057f5facec42869534c324dc349228f`
- Canonical corrected Desktop parent: `3c7c381ce6ed3a8047ce2c8d15f7b686f6f45cfb`
- Final Desktop `FA-IS-T36` acceptance record head/tree:
  `12a915e458427f94c1a223695a21c70043d0cc57` /
  `2bebc2c138a7c6364a25aa29781050ba610eafd6`
- Normative IS-SEQ-006 interface commit/tree:
  `3d7c630af300c5d4234fd3e3ac29e1e2f4c64b3b` /
  `227dbb4fbe8eb27c21a210d71b25b8225e3657ea`
- Canonical Desktop contract: `docs/protocols/functional-alpha-intent-settlement-contract.md`,
  sections `IS-SEQ-004 source-only executor interface proposal` and
  `IS-SEQ-005 closed nested schemas and result identity` and
  `IS-SEQ-006 request-envelope and parser closure`
- Immutable IS-SEQ-005 commit/tree:
  `eefc250aec9674e2233028a14f343ec5b045752c` /
  `8c5d62a88c04cc2e0d5ffa2d9abab7a12284d2ce`
- Immutable IS-SEQ-005 review input: 10,503 bytes, 311 lines, SHA-256
  `aad37f34023b0d912953f3b9c77d5c14aab85836e0ec94e16664e096a2ffbb88`
- IS-SEQ-006 review status: final independent `FA-IS-T36` PASS on the exact
  normative, administrative and corrective sequence above; no source authority
  follows
- Immutable interface proposal: `9bfc85d6e7aee1dbf99943ed113575a6805a3ddd`,
  tree `4514d3e26bc9246c1396278a583a91cd472ba25f`

The successor commit containing this dossier cannot name its own digest.
Independent review must record its exact parent, commit and tree. Any source-
baseline, dossier or Desktop-contract change invalidates the review. Source
work must then amend this manifest and obtain a new independent review before
code.

Before independent review there is no remote action. After PASS, the only
expected remote action for this dossier is a normal push of its exact reviewed
commit to `origin/codex/functional-alpha-intent-settlement` in the authorised
`sh-rn/xlite-daemon` fork. No pull-request creation or merge, tag, release or
artefact publication is included. Any later source commit receives a separate
exact-head review before its own separately named remote action.

## Admitted source-only boundary

After this dossier and the joint interface pass independent review, a later
explicit source authorisation may add only:

1. package-private closed v1 contract values and immutable data records;
2. a package-private strict canonical JSON parser/encoder;
3. package-private request, result and error envelope decoders selected by the
   six exact operation enum values;
4. pure semantic, payload, result and handover digest functions; and
5. a package-private inert append-only state machine used only with synthetic
   keys and fresh JUnit temporary profiles.

There is no listener, launch selector, credential, production store key,
wallet, chain, proof, signature, transaction, relay, Desktop, Core, XBridge,
runtime or funds connection. Passing a parser or store test cannot make the
capability available.

## Exact future file manifest

No existing source, test, resource, dependency or build file may change in the
source-only slice. The later reviewed implementation may add exactly these ten
files:

```text
src/main/java/io/cloudchains/app/net/settlement/SettlementContractV1.java
src/main/java/io/cloudchains/app/net/settlement/SettlementCanonicalJsonV1.java
src/main/java/io/cloudchains/app/net/settlement/SettlementEnvelopeCodecV1.java
src/main/java/io/cloudchains/app/net/settlement/SettlementIdentityV1.java
src/main/java/io/cloudchains/app/net/settlement/InertSettlementOperationStoreV1.java
src/test/java/io/cloudchains/app/net/settlement/SettlementCanonicalJsonV1Test.java
src/test/java/io/cloudchains/app/net/settlement/SettlementEnvelopeCodecV1Test.java
src/test/java/io/cloudchains/app/net/settlement/SettlementIdentityV1Test.java
src/test/java/io/cloudchains/app/net/settlement/InertSettlementOperationStoreV1Test.java
src/test/java/io/cloudchains/app/net/settlement/SettlementSourceBoundaryTest.java
```

`pom.xml`, the Maven wrapper and the lock-equivalent dependency graph remain
byte-identical. No service registration, resource entry, reflection metadata,
module export or native-image configuration is added.

## Exact classes and members

Every new top-level class is `final` and package-private. Every constructor and
explicitly authored non-override entry/helper method is package-private or
private. `InertSettlementOperationStoreV1.close()` is the sole public authored
method because `AutoCloseable` requires that override; its enclosing class is
still package-private. Nested records/enums have only their required compiler-
generated public accessors, equality/hash/string methods and `values()`/
`valueOf()` inside the package-private enclosing class. None creates a public
top-level API. The package-private constructors, records and entry methods named
below, plus that one close override, are the exact callable source surface.
Private helper names, signatures and decomposition are reviewed implementation
detail, not callable API and not authority to add another package-private route.
Records defensively copy byte arrays and lists on construction and access. No
type implements a generic command, callback, provider, signer, broadcaster or
RPC interface.

### `SettlementContractV1`

This class has a private constructor and contains only these nested wire enums:

- `Operation`: `RESERVE_WALLET_INPUTS`, `PROVE_RESERVED_UTXO_OWNERSHIP`,
  `CHECK_OWNED_ADDRESS`, `SIGN_SETTLEMENT_STAGE`,
  `BROADCAST_SETTLEMENT_STAGE`, `GET_SETTLEMENT_OPERATION_RESULT`;
- `Schema`: `REQUEST`, `RESULT`, `ERROR`;
- `Status`: `RESERVED`, `PROOF_CREATED`, `OWNERSHIP_CHECKED`, `SIGNED`,
  `BROADCAST_DISPATCH_RECORDED`, `BROADCAST_SUCCEEDED`,
  `BROADCAST_REJECTED`, `QUARANTINED`;
- `ErrorCode`: the exact 21 values in the joint contract;
- `Role`: `MAKER`, `TAKER`;
- `Stage`: `SERVICE_FEE`, `MAKER_DEPOSIT`, `TAKER_DEPOSIT`,
  `MAKER_REDEEM`, `TAKER_REDEEM`, `MAKER_REFUND`, `TAKER_REFUND`; and
- `Asset`: `BLOCK`, `LTC`;
- `ReservationPurpose`: `SERVICE_FEE`, `DEPOSIT`;
- `ProofPurpose`: `XBRIDGE_RESERVED_UTXO_PROOF_V1`;
- `AddressProbePurpose`: `XBRIDGE_OWNED_ADDRESS_PROBE_V1`;
- `SighashPolicy`: `ALL`;
- `Producer`: `XLITE_WALLET_INPUT_SIGNER_V1`,
  `ADMITTED_CORE_PROTOCOL_SIGNER_V1`;
- `BroadcastRejectionCode`: `BACKEND_DEFINITIVE_REJECTION`; and
- `QuarantineCode`: `BROADCAST_IDENTITY_MISMATCH`,
  `AMBIGUOUS_OUTCOME_QUARANTINED`.

Each enum has only `wireValue()` and its compiler-generated `values()` and
`valueOf(String)` methods. `wireValue()` never accepts caller input.

The class contains four package-private sealed body sums, one sealed request-
envelope sum and one sealed expected-error-identity sum:

```text
RequestBody permits ContextualRequestBody, CheckOwnedAddressRequest,
    GetSettlementOperationResultRequest

ContextualRequestBody extends RequestBody permits ReserveWalletInputsRequest,
    ProveReservedUtxoOwnershipRequest, SignSettlementStageRequest,
    BroadcastSettlementStageRequest

ResultBody permits ReservationResult, OwnershipProofResult,
    OwnedAddressResult, SignedStageResult, NonBroadcastQuarantinedResult,
    BroadcastStageResult

BroadcastStageResult extends ResultBody permits
    BroadcastDispatchRecordedResult, BroadcastSucceededResult,
    BroadcastRejectedResult, BroadcastQuarantinedResult

RequestEnvelope permits ContextualRequestEnvelope,
    OwnedAddressRequestEnvelope, LookupRequestEnvelope

ExpectedErrorIdentity permits NoSemanticIdentity, ValidatedRequestIdentity
```

There is no generic body, map-backed body or unknown-body implementation. The
class contains only the following immutable nested records, their validating
canonical constructors and compiler-generated data methods, except that
`StoreLimits` is the raw value carrier described below. Every canonical
unsigned integer remains its validated decimal `String`; no JSON number or
lossy Java numeric conversion enters an envelope or digest:

```text
ChainIdentity(Asset asset, String network, String genesisHash,
    String networkMagicHex, String p2pkhVersion, List<String> p2shVersions,
    String signedMessagePrefixHex, String baseUnitsPerCoin,
    String minimumConfirmations, String chainPolicyDigest)

PrevoutEvidence(String txid, String vout, String valueBaseUnits,
    String scriptPubKeyHex, String ownedAddress,
    String confirmationBlockHash, String confirmationHeight,
    String observedConfirmations)

OutpointIdentity(String txid, String vout)
ExpectedOutput(String index, String valueBaseUnits, String scriptPubKeyHex)
RecoveryBinding(String recoveryRecordId, String fundingTxid, String refundTxid,
    String recoveryPayloadDigest, String persistedAtUnixMillis)

ContextualRequestEnvelope(Schema schema, Operation operation, String profileId,
    String runtimeGenerationId, String parentIntentId, String childStageId,
    String semanticOperationId, String payloadDigest, String role, String stage,
    Asset asset, ChainIdentity chainIdentity, String policyDigest,
    String issuedAtUnixMillis, String deadlineUnixMillis,
    ContextualRequestBody body)

OwnedAddressRequestEnvelope(Schema schema, Operation operation,
    String profileId, String runtimeGenerationId, String semanticOperationId,
    String payloadDigest, Asset asset, ChainIdentity chainIdentity,
    String policyDigest, String issuedAtUnixMillis,
    String deadlineUnixMillis, CheckOwnedAddressRequest body)

LookupRequestEnvelope(Schema schema, Operation operation, String profileId,
    String runtimeGenerationId, String parentIntentId, String childStageId,
    String semanticOperationId, String payloadDigest, String role, String stage,
    Asset asset, ChainIdentity chainIdentity, String policyDigest,
    String issuedAtUnixMillis, String deadlineUnixMillis,
    GetSettlementOperationResultRequest body)

NoSemanticIdentity(Operation operation)
ValidatedRequestIdentity(RequestEnvelope request)

ResultEnvelope(Schema schema, Operation operation, String semanticOperationId,
    String payloadDigest, Status status, String resultDigest,
    String recordedAtUnixMillis, ResultBody body)

ErrorEnvelope(Schema schema, Operation operation, String semanticOperationId,
    ErrorCode code, boolean quarantined, String recordedAtUnixMillis)
```

Exactly two record components are nullable.
`ErrorEnvelope.semanticOperationId` has an always-present wire member and is
Java `null` exactly for `INVALID_REQUEST` and `UNAUTHENTICATED`; it is a
validated expected lower-case `hex64` for every other emittable code. The five
effect operations recompute that expected value; lookup uses its validated
target semantic ID without recomputing a lookup identity.
`BroadcastSettlementStageRequest.recoveryBinding` has an always-present wire
member and is Java `null` exactly when that member is JSON null. No other
component is nullable. The three backend-outcome enum values are reserved and
rejected as v1 error envelopes.

The request-envelope sum preserves exact wire presence with no common nullable
context:

- `ContextualRequestEnvelope` is the exact 16-key envelope only for
  reservation, proof, signing and broadcast. All four context strings are
  present and non-empty closed values.
- `OwnedAddressRequestEnvelope` is the exact 12-key ownership-probe envelope.
  It has no parent, child, role or stage component because those four keys are
  absent on the wire. Its semantic and payload preimage builders inject four
  exact empty UTF-8 values under the IS-SEQ-004 field names.
- `LookupRequestEnvelope` is the exact 16-key lookup envelope. Its four context
  strings must be coherently all empty or all non-empty at stateless decode;
  any mixed form denies. Only store lookup can enforce that all-empty selects
  an ownership probe and all-non-empty deeply equals another selected target.

The wire schema member is always the request schema. Each record constructor
requires its one admitted operation/body pairing. No variant accepts or emits
unknown, optional, JSON-null or alternate context members.

`NoSemanticIdentity` is used only while validating an `INVALID_REQUEST` or
`UNAUTHENTICATED` error for its already selected operation.
`ValidatedRequestIdentity` contains one typed `RequestEnvelope`, but its
constructor provenance is not trusted. `decodeError` re-runs the exact variant,
operation/body, scalar, cardinality, cross-field and identity validation used
by `decodeRequest` before reading an expected semantic ID: each effect is
recomputed, while lookup rechecks envelope/body target equality without
deriving a lookup ID. For either expected-identity variant, the error envelope
operation must equal the context operation. Neither variant accepts an
arbitrary caller-supplied digest.

The exact request body records are:

```text
ReserveWalletInputsRequest(ReservationPurpose purpose, String walletAddress,
    String baseAmountBlockUnits, String outgoingAmountUnits,
    String fixedTakerFeeBlockUnits, String maxStageNativeFeeUnits,
    String maxGraphNativeFeeUnits, String recoveryReserveUnits,
    List<PrevoutEvidence> outpoints)

ProveReservedUtxoOwnershipRequest(String reservationId,
    OutpointIdentity outpoint, String messageHex, String messageDigest,
    ProofPurpose purpose)

CheckOwnedAddressRequest(String address, AddressProbePurpose purpose)

SignSettlementStageRequest(String reservationId,
    String unsignedTransactionHex, String unsignedBytesDigest,
    String unsignedTxid, String unsignedWtxid,
    List<PrevoutEvidence> previousOutputs,
    List<ExpectedOutput> expectedOutputs, String changeAddress,
    List<String> inputSequences, String lockTime, SighashPolicy sighashPolicy,
    String maxStageNativeFeeUnits, String contractPolicyDigest)

BroadcastSettlementStageRequest(String finalTransactionHex,
    String finalBytesDigest, String finalTxid, String finalWtxid,
    Producer producer, String predecessorOperationId,
    String contractPolicyDigest, RecoveryBinding recoveryBinding)

GetSettlementOperationResultRequest(String targetSemanticOperationId,
    String expectedPayloadDigest)
```

`BroadcastSettlementStageRequest.recoveryBinding` is Java `null` exactly when
the wire member is JSON null. The wire member is always present.

The exact success and quarantine result records are:

```text
ReservationResult(String reservationId, String reservationDigest,
    List<OutpointIdentity> outpoints, boolean deduplicated)
OwnershipProofResult(String messageDigest, String compactProofBase64,
    String proofDigest)
OwnedAddressResult(boolean owned)
SignedStageResult(String finalTransactionHex, String finalBytesDigest,
    String finalTxid, String finalWtxid, List<String> signedInputIndexes)
NonBroadcastQuarantinedResult(QuarantineCode quarantineCode,
    String evidenceDigest)

BroadcastDispatchRecordedResult(String finalBytesDigest, String finalTxid,
    String finalWtxid, String dispatchRecordDigest)
BroadcastSucceededResult(String finalBytesDigest, String finalTxid,
    String finalWtxid, String returnedTxid)
BroadcastRejectedResult(String finalBytesDigest, String finalTxid,
    String finalWtxid, BroadcastRejectionCode rejectionCode,
    String evidenceDigest)
BroadcastQuarantinedResult(String finalBytesDigest, String finalTxid,
    String finalWtxid, QuarantineCode quarantineCode, String evidenceDigest)
```

`ReservationResult.deduplicated` is always literal `false`, including replay.
`NonBroadcastQuarantinedResult` accepts only
`AMBIGUOUS_OUTCOME_QUARANTINED` and only for reservation, proof, ownership
probe or signing. `BroadcastRejectedResult` accepts only
`BACKEND_DEFINITIVE_REJECTION`. `BroadcastQuarantinedResult` accepts only
`BROADCAST_IDENTITY_MISMATCH` or `AMBIGUOUS_OUTCOME_QUARANTINED`.

The exact pure-identity and inert-store input records are:

```text
IdentityInputs(Operation operation, String profileId, String parentIntentId,
    String childStageId, String role, String stage, Asset asset,
    String chainPolicyDigest, String policyDigest, String operationObjectId)

PayloadInputs(Operation operation, String profileId, String parentIntentId,
    String childStageId, String role, String stage, Asset asset,
    String chainIdentityDigest, String chainPolicyDigest, String policyDigest,
    String deadlineUnixMillis, String operationObjectId, String bodyDigest)

ResultDigestInputs(Operation operation, String semanticOperationId,
    String payloadDigest, Status status, String recordedAtUnixMillis,
    ResultBody body)

GenerationHandover(String profileId, String priorGenerationId,
    String nextGenerationId, String desktopJournalHeadDigest,
    String handoverDigest)

TargetContext(String profileId, String parentIntentId, String childStageId,
    String role, String stage, Asset asset, ChainIdentity chainIdentity,
    String policyDigest)

OperationLookup(String runtimeGenerationId, TargetContext targetContext,
    String semanticOperationId, String payloadDigest)

OperationAppend(String runtimeGenerationId, TargetContext targetContext,
    Operation operation, String semanticOperationId, String payloadDigest,
    Status status, String resultDigest, byte[] canonicalResultEnvelope)

StoreLimits(int maxFrames, long maxBytes)
```

Every string identifier or digest in these records is lower-case `hex64` where
the contract requires it. `StoreLimits` is a package-private immutable raw
`int`/`long` carrier whose canonical constructor performs no range validation
and throws nothing. Both store factories are the sole validators of its values.
Other records defensively copy byte arrays and lists on construction and access.
`GetSettlementOperationResultRequest` has no result record because a found
lookup returns the selected stored result envelope byte-for-byte.

`TargetContext` is the exact durable selected-target context required by
corrected IS-SEQ-006. For an ownership-probe operation its parent, child, role
and stage strings are all empty; for another effect they are all the exact
non-empty closed target values. It retains profile, asset, full chain identity
and policy digest. `OperationAppend` authenticates that context with every
result frame. `OperationLookup` contains the currently admitted generation,
the lookup's full target context and its two body target IDs. Store lookup
validates all of them against the selected chain. It never compares current
generation with a historical result frame and returns the stored result bytes
unchanged after a valid handover.

An append or lookup whose supplied generation differs from the store's current
generation returns its `STALE_GENERATION` held variant from the factory-
authenticated in-memory generation without file access. For a matching
generation, the store first authenticates the complete current file and only
then selects a chain and tests profile, target context, semantic ID, payload,
replay or transition. A target profile differing from the store profile, or a
successor context differing from its existing operation chain, then returns
`TARGET_CONTEXT_MISMATCH`. Neither denial poisons or mutates the store.

### `SettlementCanonicalJsonV1`

This class has a private constructor and exactly these package-private methods:

```text
static JsonElement parse(byte[] utf8)
static byte[] encode(JsonElement value)
```

Private recursive reader, writer, string and counter helpers are implementation
detail and cannot be called outside this class. Parsing uses strict UTF-8 and
rejects BOM, duplicate members, numbers, unknown non-ASCII input, invalid
escapes, trailing bytes and noncanonical input, then byte-compares canonical
re-encoding with the request.
Canonical integer values are JSON strings, not JSON numbers. Boolean and null
remain JSON primitives. Resource ceilings for this inert parser are exactly
262,144 input bytes, depth 16, 128 members in each individual object, 64 entries
in each individual array, 512 members across the whole document, 128 array
entries across the whole document and 200,000 UTF-8 bytes per string.
Private cumulative counters enforce only the two whole-document totals; the
recursive reader/writer separately resets and checks the per-container counts.
A valid maximum sign request therefore does not consume
one global 64-entry budget across `previousOutputs`, `expectedOutputs`,
`inputSequences` and nested `p2shVersions`. The top-level object is depth one;
entering an object or array increments depth, while a scalar does not. Counts
use decoded UTF-8 bytes, direct key/value pairs and direct array elements.
At every structural maximum the admitted sign shape is exactly 311 cumulative
object members and 92 cumulative array entries: 16 envelope, 10 chain, 13 body,
80 previous-output and 192 expected-output members; and 8+10+64+10 entries.
These are parser resource bounds only, not transaction, fee, financial or
runtime admission.

### `SettlementEnvelopeCodecV1`

This class has a private constructor and exactly these package-private entry
methods:

```text
static RequestEnvelope decodeRequest(byte[] canonicalUtf8)
static ResultEnvelope decodeResult(byte[] canonicalUtf8)
static ErrorEnvelope decodeError(byte[] canonicalUtf8,
        ExpectedErrorIdentity expectedIdentity)
static byte[] encodeResult(ResultEnvelope envelope)
static byte[] encodeResultWithoutDigest(ResultDigestInputs input)
```

Its private implementation must exhaustively cover the six request-selection
behaviours. The displayed private names are review labels, not frozen method
signatures: reserve wallet inputs, prove reserved UTXO ownership, check owned
address, sign settlement stage, broadcast settlement stage and result lookup.

It must also cover these private status-aware result-selection behaviours.
Again, the labels are not callable API or frozen helper signatures: common
status/body selection, the four non-broadcast success bodies, status-aware
non-broadcast quarantine and the four distinct broadcast result bodies.

Private nested decoding covers chain identity, previous-output evidence,
outpoint identity, expected output and recovery binding. Private result
encoding covers the sealed result-body variants and outpoint identity. Helper
names and decomposition are implementation detail. Every switch remains
exhaustive over the sealed Java variants and never accepts a map or arbitrary
type.

The following are required validation behaviours, not frozen private method
signatures:

```text
requireExactKeys, requireObject, requireArray, requireString, requireBoolean,
requireNullOrObject, requireLiteral, requireHex8, requireHex64, requireEvenHex,
requireCanonicalBase64, requireAddress, requireU8, requireU32, requireU64,
requirePositiveU32, requirePositiveU64, requireArraySize,
requireUniqueOutpoints, requireAscendingUniqueDecimals, requireDeadlineOrder,
requireEnvelopeIdentityEquality, requireRequestCrossFields,
requireResultCrossFields, requireErrorSemantics
```

`requireU8`, `requireU32` and `requireU64` accept only canonical unsigned
decimal JSON strings within their exact unsigned range. The positive variants
also reject zero. `requireAddress` accepts only visible ASCII of 1..128 bytes
with no whitespace. It does not claim chain-canonical validation.
`requireCanonicalBase64` accepts only padded RFC 4648 base64 whose decode then
re-encode is byte-identical. There is no generic selector, reflection,
class-name dispatch, arbitrary method string, map-to-method adapter or
fallback. An unknown schema, operation, status, error, field or type denies.

The exact selection table is:

| Wire operation                 | Request record                        | Result record                                             | Stored status                        |
| ------------------------------ | ------------------------------------- | --------------------------------------------------------- | ------------------------------------ |
| `reserveWalletInputs`          | `ReserveWalletInputsRequest`          | `ReservationResult` or `NonBroadcastQuarantinedResult`    | `reserved` or `quarantined`          |
| `proveReservedUtxoOwnership`   | `ProveReservedUtxoOwnershipRequest`   | `OwnershipProofResult` or `NonBroadcastQuarantinedResult` | `proof-created` or `quarantined`     |
| `checkOwnedAddress`            | `CheckOwnedAddressRequest`            | `OwnedAddressResult` or `NonBroadcastQuarantinedResult`   | `ownership-checked` or `quarantined` |
| `signSettlementStage`          | `SignSettlementStageRequest`          | `SignedStageResult` or `NonBroadcastQuarantinedResult`    | `signed` or `quarantined`            |
| `broadcastSettlementStage`     | `BroadcastSettlementStageRequest`     | `BroadcastStageResult`                                    | one of the four broadcast statuses   |
| `getSettlementOperationResult` | `GetSettlementOperationResultRequest` | none                                                      | no append or transition              |

The three schema strings are exactly
`blocknet.xlite.settlement.request.v1`,
`blocknet.xlite.settlement.result.v1` and
`blocknet.xlite.settlement.error.v1`. No ordinary coin RPC name is accepted.

The result decoder admits only these operation/status/body combinations:

- reservation with `reserved`/`ReservationResult` or
  `quarantined`/`NonBroadcastQuarantinedResult`;
- proof with `proof-created`/`OwnershipProofResult` or the same non-broadcast
  quarantine type;
- ownership probe with `ownership-checked`/`OwnedAddressResult` or the same
  non-broadcast quarantine type;
- signing with `signed`/`SignedStageResult` or the same non-broadcast
  quarantine type; and
- broadcast with exactly one of the four broadcast body variants matched to
  its four statuses.

`getSettlementOperationResult` is rejected as a stored result operation. A
found lookup returns the selected target result envelope instead.

The error decoder requires exactly the six error-envelope members, including
an always-present `semanticOperationId`. It accepts JSON null for that member
only with `INVALID_REQUEST` or `UNAUTHENTICATED` plus
`NoSemanticIdentity`, requires `ValidatedRequestIdentity` and its validated
expected `hex64` for the other 16 emittable codes, and rejects
`BACKEND_DEFINITIVE_REJECTION`, `BROADCAST_IDENTITY_MISMATCH` and
`AMBIGUOUS_OUTCOME_QUARANTINED` as error envelopes. The last three strings are
legal only in their closed durable result-body fields.

Parser validation enforces exactly these parser-level cross-field
and cardinality rules, with no unstated policy choice:

- `requireDeadlineOrder` validates `issuedAtUnixMillis` and
  `deadlineUnixMillis` as `u64` and requires issued time not later than the
  deadline. It does not choose the still-held wall-clock, rollback, sleep or
  maximum-horizon policy.
- A contextual effect request requires lower-case `hex64` profile, generation,
  parent, child, semantic, payload and policy IDs, a closed role and stage, and
  an exact asset. The ownership probe requires the same non-context IDs and its
  exact 12-key shape. Lookup requires its exact contextual or present-empty
  target form. Result semantic ID, payload digest and result digest are
  `hex64`; result/error recorded times are `u64`.
- For the five effect operations, `requireEnvelopeIdentityEquality` recomputes
  `chainIdentityDigest`, `bodyDigest`, `semanticOperationId` and
  `payloadDigest` from the accepted closed values and requires byte-identical
  lower-case digest fields. Result lookup never recomputes either durable ID.
  It requires envelope semantic ID equal to
  `body.targetSemanticOperationId`, envelope payload digest equal to
  `body.expectedPayloadDigest` and coherent all-empty or all-non-empty context.
  The stateless codec has no store, callback, selected record or current-
  generation dependency. Current generation and deep selected `TargetContext`
  equality belong only to `InertSettlementOperationStoreV1.lookup`.
- `ChainIdentity.p2shVersions` has 1..8 `u8` values, unique and ascending by
  numeric value. Its `asset` equals the top-level asset and `network` is the
  literal `mainnet`.
- A reservation has 1..10 outpoints unique by `{txid,vout}`,
  `baseAmountBlockUnits` no greater than `1000000000`, and
  `fixedTakerFeeBlockUnits` exactly `1500000` for `service-fee` and `0` for
  `deposit`.
- A `reserved` result has 1..10 unique outpoint identities and literal false
  `deduplicated`. The decoder preserves their order. Equality to the request
  order is a later request/result executor check and is not invented by this
  isolated result decoder.
- A sign request has 1..10 `previousOutputs`, 1..64 `expectedOutputs` unique by
  index and ascending numerically, and 1..10 `inputSequences` whose count
  equals `previousOutputs`. A signed result has 1..10
  `signedInputIndexes`, unique and ascending numerically.
- A broadcast-succeeded result requires `returnedTxid` equal to its own
  `finalTxid`. Every broadcast body has exactly the common final digest, txid
  and wtxid plus its one closed status-specific member set. A non-broadcast
  quarantine has only quarantine code and evidence digest.
- `requireResultCrossFields` enforces the exact operation/status/body sum,
  rejects lookup as a stored result, and calls
  `SettlementIdentityV1.resultDigest` over the typed result with only
  `resultDigest` omitted. `decodeResult` rejects a mismatch before returning.
  `encodeResult` also recomputes and compares before encoding; it never trusts
  or silently replaces a supplied digest. The codec is stateless and performs
  no cross-append comparison; operation/identity/common-final-field
  preservation belongs only to `validateAppendTransition` in the inert store.
- `requireErrorSemantics` requires JSON null plus `NoSemanticIdentity` for
  `INVALID_REQUEST`/`UNAUTHENTICATED`; for each other emittable error it
  requires `ValidatedRequestIdentity` and equality between the wire member and
  the fully revalidated typed request's expected semantic ID. It always
  requires error operation equal to context operation. For an effect that ID
  is independently recomputed again; for lookup it is the revalidated
  `body.targetSemanticOperationId` and is never recomputed under lookup. It
  enforces `quarantined: true` only for
  `OPERATION_NOT_FOUND` and `PERSISTENCE_UNAVAILABLE`, false for all other
  emittable errors, permits `OPERATION_NOT_FOUND` only with operation
  `getSettlementOperationResult`, and rejects the three reserved backend-
  outcome codes.

No parser-level equality is inferred merely from similar field names. In
particular, message, unsigned/final transaction, policy, recovery, request-to-
result and chain-canonical address relationships not explicitly frozen above
remain later proof, signer, broadcaster or policy-validator work and have no
source path in this tranche.

### `SettlementIdentityV1`

This class has a private constructor and exactly these package-private methods:

```text
static String semanticOperationId(IdentityInputs input)
static String payloadDigest(PayloadInputs input)
static String resultDigest(ResultDigestInputs input)
static String handoverDigest(GenerationHandover input)
```

It uses JDK `MessageDigest` only. The semantic, payload and handover domain
strings, field order, exclusions and top-level `policyDigest` versus
`chainPolicyDigest` distinction are exactly IS-SEQ-004. `resultDigest` uses
exactly the IS-SEQ-005 domain and calls only the typed
`encodeResultWithoutDigest` path. It accepts no caller-supplied digest,
serializer, omission set or field order.
Private canonical-value, framing, SHA-256 and lower-hex validation helpers are
implementation detail. `canonicalDigest(JsonElement)` is private if used and
cannot appear in the package-private surface; there is no generic callable
digest entry point.

The exact result algorithm is SHA-256 over ASCII
`BLOCKNET-XLITE-SETTLEMENT-RESULT-V1`, LF, then
`F("resultEnvelope", C(result envelope with exactly resultDigest omitted))`.
The fixed canonical value is this one 614-byte UTF-8 line:

```text
{"body":{"deduplicated":false,"outpoints":[{"txid":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","vout":"0"}],"reservationDigest":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","reservationId":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},"operation":"reserveWalletInputs","payloadDigest":"f7c2b4911611397cddaa42f3f56cd25a6eeeafc39efc5b1c1fcf30cf13b2cbd9","recordedAtUnixMillis":"1780000000123","schema":"blocknet.xlite.settlement.result.v1","semanticOperationId":"718fd3390ec6b05e15306b9fb331db604aba5457e5b95f3c8da4c4248fc92016","status":"reserved"}
```

The exact preimage is 670 bytes: the domain line, then
`resultEnvelope:614:`, that exact canonical line and one final LF. Its expected
digest is
`97230fe9d7520b9d95a8a068ad507694b89b7fdfec61cc096aba8611c8bcc2b0`.
`decodeResult` and `encodeResult` independently reproduce all three values.

### `InertSettlementOperationStoreV1`

This class implements `AutoCloseable` and has exactly these entry methods; only
the required `close()` override is public and all others are package-private:

```text
static InertSettlementOperationStoreV1 createFreshTemporaryProfile(
        Path root, byte[] syntheticAuthenticationKey, StoreLimits limits,
        String profileId, String initialGenerationId) throws StoreOpenException
static InertSettlementOperationStoreV1 reopenTemporaryProfile(
        Path root, byte[] syntheticAuthenticationKey, StoreLimits limits,
        List<OperationAppend> preparedStartupQuarantines) throws StoreOpenException
AppendOutcome append(OperationAppend append)
LookupOutcome lookup(OperationLookup lookup)
HandoverOutcome acceptHandover(GenerationHandover handover)
SnapshotOutcome snapshot()
public void close() throws StoreCloseException
```

Private root/channel, whole-log authentication, frame encode/decode, transition,
lookup, append, startup-quarantine, capacity, HMAC, force, copy, poison and key-
zeroing helpers are implementation detail. They may not add a package-private
entry point, callback or alternate persistence path. The reviewed source diff
must still demonstrate each named behaviour below.
Its exact package-private nested outcome sums are:

```text
StoreHold = PAYLOAD_CONFLICT | TRANSITION_DENIED | NOT_FOUND |
    TARGET_CONTEXT_MISMATCH | STALE_GENERATION |
    STALE_OR_CONFLICTING_HANDOVER | CAPACITY_UNAVAILABLE |
    PERSISTENCE_UNAVAILABLE

AppendOutcome permits Appended, IdenticalReplay, AppendHeld
Appended(byte[] canonicalResultEnvelope)
IdenticalReplay(byte[] canonicalResultEnvelope)
AppendHeld(StoreHold reason)

LookupOutcome permits Found, LookupHeld
Found(byte[] canonicalResultEnvelope)
LookupHeld(StoreHold reason)

HandoverOutcome permits HandoverAccepted, HandoverHeld
HandoverAccepted(String nextGenerationId)
HandoverHeld(StoreHold reason)

SnapshotOutcome permits SnapshotAvailable, SnapshotHeld
SnapshotAvailable(StoreSnapshot snapshot)
SnapshotHeld(StoreHold reason)

StoreSnapshot(String profileId, String currentGenerationId,
    int authenticatedFrameCount, long authenticatedByteCount,
    int retainedOperationCount, String latestFrameMacHex)

FactoryFailure = INVALID_ROOT | INVALID_KEY | INVALID_IDENTITY | INVALID_LIMITS |
    LOCK_UNAVAILABLE | PERSISTENCE_UNAVAILABLE | AUTHENTICATION_FAILED |
    PREPARED_QUARANTINE_MISMATCH | CAPACITY_UNAVAILABLE

StoreOpenException extends Exception with FactoryFailure reason()
StoreCloseException extends Exception
```

Each held outcome validates that its reason belongs to that operation: append
permits payload conflict, transition, target-context mismatch, stale generation,
capacity or persistence; lookup permits not found, payload conflict, target-
context mismatch, stale generation or persistence; handover permits stale or
conflicting handover, capacity or persistence; snapshot permits persistence
only. Byte arrays are copied on construction and access.

`StoreSnapshot.authenticatedFrameCount` includes the mandatory initialisation
frame and every authenticated handover and operation frame.
`authenticatedByteCount` is the physical sum of `4 + recordLength + 32` for all
of those frames. `retainedOperationCount` counts unique
`{semanticOperationId,payloadDigest}` operation chains, never result frames or
handover/initialisation frames. `latestFrameMacHex` is the latest authenticated
MAC, including the initialisation MAC when no later frame exists.

Both factories fail only through `StoreOpenException`, whose public message is
the constant `Settlement store unavailable.` and which exposes no path, key,
frame bytes or underlying exception. `reason()` is package-private test
evidence. Invalid arguments do not fall through to `IllegalArgumentException`
or an implementation-selected exception.
`StoreCloseException` has the same constant message and exposes no cause or
private value. A second `close()` after cleanup returns normally.

Its exact private authenticated-record sum is:

```text
FrameRecord permits ProfileInitialisationFrameRecord, OperationFrameRecord,
    GenerationHandoverFrameRecord

ProfileInitialisationFrameRecord(String schema, String frameType,
    String profileId, String initialGenerationId)

OperationFrameRecord(String schema, String frameType,
    String runtimeGenerationId, TargetContext targetContext, Operation operation,
    String semanticOperationId, String payloadDigest, Status status,
    String resultDigest, String resultEnvelopeBase64)

GenerationHandoverFrameRecord(String schema, String frameType,
    String profileId, String priorGenerationId, String nextGenerationId,
    String desktopJournalHeadDigest, String handoverDigest)
```

Factory and file-lifecycle rules are exact:

- The root is a caller-supplied existing JUnit temporary directory resolved
  without following a symlink. Fresh creation requires it to be empty and the
  exact log file absent. Reopen requires the exact log file to be a regular,
  non-symlink file and rejects any sibling entry.
- The synthetic authentication key is exactly 32 bytes and is copied before
  use. Profile/generation IDs, limits and the prepared list's scalar/envelope
  syntax are validated before file creation or mutation. A malformed profile
  or initial generation ID is `INVALID_IDENTITY`; malformed limits, key or root
  use their distinct factory reason.
- `createFreshTemporaryProfile` opens the log with `CREATE_NEW`, read and write,
  acquires a non-blocking exclusive `FileLock`, writes the mandatory profile-
  initialisation frame and calls `FileChannel.force(true)` before returning.
- `reopenTemporaryProfile` opens read/write without create or truncate and
  acquires the non-blocking exclusive lock before reading or authenticating one
  byte. A failed or overlapping lock is `LOCK_UNAVAILABLE`; there is no
  unlocked inspection or read-only fallback.
- The same exclusive lock and channel live for the entire usable store instance
  until normal close or immediate poison cleanup.
  Every normal operation append and handover calls `force(true)` before a
  success outcome. `close()` calls `force(true)`, releases the lock, closes the
  channel and zeroes the copied key exactly once.
- Each factory alone maps raw limits outside `1..1024` frames or
  `1..16777216` bytes to checked `StoreOpenException(INVALID_LIMITS)`. A value
  inside both absolute ranges but too small for the fresh initialisation frame,
  the authenticated current file or the complete prepared startup batch is
  `StoreOpenException(CAPACITY_UNAVAILABLE)`. Fresh capacity denial happens
  before file creation; reopen never evicts to fit a valid undersized limit.
- Every factory failure releases any acquired lock, closes any opened channel
  and zeroes the copied key. A partial newly created file or partial failed
  write is never deleted, truncated or repaired; later authentication holds.
  No factory failure returns a partially usable instance.

Normal-instance failure semantics are also exact. Before an operation or
handover write, the store authenticates the whole current file, validates the
candidate and preflights its complete physical size. A write or `force(true)`
failure marks the instance permanently poisoned, performs no truncate, retry or
repair, then immediately makes best-effort lock release/channel close and
zeroes the copied key before returning the corresponding held outcome with
`PERSISTENCE_UNAVAILABLE`. Cleanup failure does not restore usability. After
poison, `append`, `lookup` and `acceptHandover` return their persistence-held
variants without file access; `snapshot` returns
`SnapshotHeld(PERSISTENCE_UNAVAILABLE)`. No later method can return cached
success bytes. The first `close()` after poison reports the poison through
constant `StoreCloseException` but performs no deferred file operation because
cleanup already ran; the second close is a no-op. Normal `close()` makes one
`force(true)` attempt, always releases the lock, closes the channel and zeroes
the key in finally-style cleanup, and throws the same constant exception if
force or close failed. Cleanup completion, not I/O success, marks the instance
closed.

After a successful normal close, `append`, `lookup` and `acceptHandover` return
their `PERSISTENCE_UNAVAILABLE` held variants and `snapshot` returns
`SnapshotHeld(PERSISTENCE_UNAVAILABLE)`, all without file access or cached
success. Every later close is a no-op. The same post-cleanup method holds apply
after the first poison-reporting close.

The inert format is one file named `xlite-settlement-inert-test-v1.log` beneath
an empty caller-supplied JUnit temporary directory. It is deliberately not a
production format. It has no header or trailer. Each frame is exactly:

```text
recordLength: 4-byte unsigned big-endian length, 1..262144
recordBytes: recordLength bytes of canonical UTF-8 JSON
frameMac: 32 bytes
```

The first frame's `previousFrameMac` is exactly 32 zero bytes. For every frame
the exact HMAC preimage is:

```text
ASCII("BLOCKNET-XLITE-SETTLEMENT-INERT-FRAME-V1\n") ||
previousFrameMac || recordLength || recordBytes
```

`frameMac` is HMAC-SHA-256 with the copied, exactly 32-byte synthetic test key.
`recordLength` in the preimage is the same four bytes written to the file. The
next frame uses the preceding stored and verified `frameMac`. There is no
alternate HMAC domain, salt, nonce, key identifier or unauthenticated metadata.
The key is never read from `KeyHandler`, `ConfigHelper`, an environment
variable, a command line, Desktop or a wallet profile.

The decoded frame record is the closed Java sum `FrameRecord`. A profile-
initialisation record's canonical JSON contains exactly:

```text
schema: "blocknet.xlite.settlement.inert-frame.v1"
frameType: "profile-initialisation"
profileId: hex64
initialGenerationId: hex64
```

It must be the first and only profile-initialisation frame. Fresh creation
writes and forces it before returning; reopen derives profile and current
generation from the authenticated chain rather than caller state. An operation
record's canonical JSON contains exactly:

```text
schema: "blocknet.xlite.settlement.inert-frame.v1"
frameType: "operation-append"
runtimeGenerationId: hex64
targetContext: exact TargetContextV1 object
operation: one of the five effect operations, never result lookup
semanticOperationId: hex64
payloadDigest: hex64
status: the status admitted for operation
resultDigest: hex64
resultEnvelopeBase64: canonical padded base64
```

`TargetContextV1` contains exactly:

```text
profileId: hex64
parentIntentId: empty string for ownership probe, otherwise hex64
childStageId: empty string for ownership probe, otherwise hex64
role: empty string for ownership probe, otherwise Role
stage: empty string for ownership probe, otherwise Stage
asset: Asset
chainIdentity: exact ChainIdentityV1 object
policyDigest: hex64
```

A generation-handover record's canonical JSON contains exactly:

```text
schema: "blocknet.xlite.settlement.inert-frame.v1"
frameType: "generation-handover"
profileId: hex64
priorGenerationId: hex64
nextGenerationId: hex64
desktopJournalHeadDigest: hex64
handoverDigest: hex64
```

There are no other frame types or keys. An operation frame is accepted only
when the decoded result envelope is canonical and its operation, semantic ID,
payload digest, status and result digest equal the duplicated frame members;
its result digest is independently recomputed. Its profile equals the store
profile and its generation equals the exact current generation. A handover
frame must match the current profile/generation and recomputed handover digest.

The exact inert maxima are 1,024 frames, 16,777,216 physical file bytes and
262,144 record bytes. A caller may choose smaller positive `StoreLimits` only
for boundary tests. Capacity holds before write and never evicts or rewrites.

The exact operation append transition table is:

| Operation       | Existing latest status        | Candidate status                                             | Outcome                                        |
| --------------- | ----------------------------- | ------------------------------------------------------------ | ---------------------------------------------- |
| reservation     | no record                     | `reserved` or `quarantined`                                  | append                                         |
| reservation     | `quarantined`                 | `reserved`                                                   | append only as caller-prepared evidence result |
| proof           | no record                     | `proof-created` or `quarantined`                             | append                                         |
| proof           | `quarantined`                 | `proof-created`                                              | append only as caller-prepared evidence result |
| ownership probe | no record                     | `ownership-checked` or `quarantined`                         | append                                         |
| ownership probe | `quarantined`                 | `ownership-checked`                                          | append only as caller-prepared evidence result |
| signing         | no record                     | `signed` or `quarantined`                                    | append                                         |
| signing         | `quarantined`                 | `signed`                                                     | append only as caller-prepared evidence result |
| broadcast       | no record                     | `broadcast-dispatch-recorded`                                | append                                         |
| broadcast       | `broadcast-dispatch-recorded` | `broadcast-succeeded`, `broadcast-rejected` or `quarantined` | append                                         |
| broadcast       | `quarantined`                 | `broadcast-succeeded` or `broadcast-rejected`                | append only as caller-prepared evidence result |

For every row, byte-identical replay of the latest canonical result returns
`IdenticalReplay` with those exact bytes and performs no append, timestamp or
file-time mutation. Same semantic ID with another payload returns
`PAYLOAD_CONFLICT`. Every unlisted transition, including a second non-identical
append to a success/rejected terminal status, a broadcast terminal-to-anything
append, quarantine-to-quarantine, direct no-record broadcast success/rejection/
quarantine or non-broadcast status crossing, returns `TRANSITION_DENIED`.

Every successor preserves the entire `TargetContext`, operation, semantic ID
and payload digest. Its frame uses the store's current generation, which can
differ from the predecessor after an accepted handover. Broadcast successors
also preserve the
exact common `finalBytesDigest`, `finalTxid` and `finalWtxid` from dispatch. The
inert store validates only the closed append and transition evidence supplied
to it; it has no callback, proof reader, signer, relay or authority to
manufacture an evidence successor.

After poisoned/closed holds and the stale-generation shortcut above, every
matching-generation lookup or append authenticates the complete current log
before target-chain selection, context/payload/replay/transition decisions or
write. A partial, malformed, conflicting or unauthenticated frame returns
persistence failure;
there is no truncation, repair or earlier-record fallback. Identical replay
returns the latest valid result envelope bytes without append, timestamp or
file-time mutation. Same semantic ID with another payload conflicts without
append. Lookup requires the current `runtimeGenerationId` and deep equality of
the supplied `TargetContext` with the selected operation chain. A context
mismatch returns `TARGET_CONTEXT_MISMATCH`; a different payload for an existing
semantic ID returns `PAYLOAD_CONFLICT`; absence returns `NOT_FOUND`. A valid
lookup returns the chain's latest stored result envelope bytes exactly, even
when that result frame used an older generation. It appends nothing.

`preparedStartupQuarantines` is copied before file access and must
contain exactly one `OperationAppend` for every operation chain whose latest
current status is `broadcast-dispatch-recorded`, not for every historical
dispatch frame, in ascending lexicographic order by
`semanticOperationId` then `payloadDigest`, and no other item. Each prepared
append must use the current profile/generation, the same broadcast identity and
the same full `TargetContext` and three final-identity values, status
`quarantined`, quarantine code
`AMBIGUOUS_OUTCOME_QUARANTINED`, a caller-supplied `hex64` evidence digest,
caller-supplied canonical `recordedAtUnixMillis`, and a valid recomputed result
digest. Missing, extra, reordered or mismatched prepared input makes reopen
fail before any append. After whole-log authentication and complete validation,
reopen canonicalises every operation frame in memory, validates each record
length, and preflights the whole batch's frame count and exact physical byte
sum (`4 + recordLength + 32` per frame) against both caller and absolute limits
before its first append. A later item cannot cause partial mutation through
capacity failure. It then appends every prepared quarantine through the same
frame/HMAC path and calls `force(true)` before returning. An I/O failure can
leave only unauthenticated or fully authenticated prefix evidence; the factory
closes and future reopen authenticates the whole file without repair or
fallback. Empty input is required when no dispatch is retained. There is no
callback, clock, digest invention, dispatch or reconciliation method. One
valid generation handover is appended from the exact current generation;
reuse, conflict and stale generations deny.

This inert class is not a production store, encryption design, rollback
detector, recovery journal, credential owner or wallet authority. Its format
must not be wired or migrated into a runtime without a new T03/T07 design and
manifest.

## IS-SEQ-005/006 closure and remaining source gate

Desktop commit `eefc250aec9674e2233028a14f343ec5b045752c` closes the ten
schema gaps recorded by the predecessor dossier. It freezes:

1. every `ChainIdentityV1` scalar/array type;
2. every `PrevoutEvidenceV1` scalar type;
3. reservation outpoint identities and literal-false deduplication;
4. proof request/result keys and types;
5. sign previous-output, sequence, expected-output, signed-index and result
   keys/types;
6. four distinct broadcast status bodies;
7. non-broadcast quarantine as a durable result body rather than an error;
8. the `resultDigest` preimage, algorithm and 614/670-byte vector;
9. always-present nullable error semantic ID and reserved error-code rules; and
10. canonical decimal-string grammar with bounded `u8`, `u32`, `u64`, Address
    and base64 values.

Those ten items are no longer described as unresolved. The six operation
names, three schema IDs, eight statuses, 21 error values and the prior three
identity vectors remain unchanged.

IS-SEQ-006 commit `3d7c630af300c5d4234fd3e3ac29e1e2f4c64b3b`, tree
`227dbb4fbe8eb27c21a210d71b25b8225e3657ea`, closes the later top-level
presence and parser-budget contradiction. Corrective head
`023838583a735deb5b81151338f7a79c8d37aa54` fixes the three review wording
defects without changing a wire shape, limit, identity, policy or vector, and
final acceptance record `12a915e458427f94c1a223695a21c70043d0cc57`
completes only `FA-IS-T36`.
The ownership probe now has exactly 12 wire keys and injects four digest-only
empty values. Contextual effects and lookup have their exact 16-key variants,
with present-empty lookup context only for an ownership-probe target. The
parser totals, 311/92 maximum sign shape, effect-versus-lookup identity rules,
result/error rules and 64-case digest-negative matrix are frozen above.

Desktop `FA-IS-T32`, recovery `FA-RCV-T18`, Desktop `FA-IS-T34` and corrected
`FA-IS-T36` are independently accepted. This exact XLite successor dossier
must still receive independent exact-commit review, after which joint
`FA-IS-T33` and `FA-IS-T35` must explicitly authorise an exact source-only
subtranche before any of the ten proposed files may be added. Implementers may
not infer a wire member, use a generic map/body, accept unknown fields or omit
a selector. No source authority arises from the schema closures or this draft.

## Credential and transport boundary

The source-only slice has no credential and no transport. It must not change or
reference:

```text
src/main/java/io/cloudchains/app/console/ArgMenu.java
src/main/java/io/cloudchains/app/console/ConsoleMenu.java
src/main/java/io/cloudchains/app/net/api/JSONRPCController.java
src/main/java/io/cloudchains/app/net/api/JSONRPCServer.java
src/main/java/io/cloudchains/app/net/api/http/master/HTTPServerHandler.java
src/main/java/io/cloudchains/app/net/api/http/server/HTTPServerHandler.java
src/main/java/io/cloudchains/app/net/api/http/server/HTTPServerInitializer.java
```

There is no `--managed-intent-settlement` flag, socket, port, Netty handler,
HTTP route, master route, ordinary coin RPC alias, Basic credential, token,
stdin secret, environment secret, service registration or runtime capability.
There is no production caller of a decoder entry method; the source-only slice
permits calls only from its tests in the same Java package.

Production credential delivery, endpoint allocation, listener lifecycle,
framing, peer authentication, replay protection and store-key publication
remain unresolved joint-review items and are outside this manifest.

## Complete alternate-route and bypass inventory

The baseline pins these existing boundary blobs, all of which remain unchanged:

```text
02d9a6d59967629a0b37e90d24bf2da42be2566b  pom.xml
da34c551b1e00533f7e623e598fefb3001154120  ArgMenu.java
9325c2e091d253a2097b18abff68382fd93343da  ConsoleMenu.java
8f9ff78abe690ae5b8709c145277bdcb2b8568ce  KeyHandler.java
61e8aa9c0fbc8ba8981f9b0c21858d02bcd873da  CoinInstance.java
5782007c41933d7912dfa7ccde054abde4a07cc3  JSONRPCController.java
d6a9906e8178e59622c5cd35cc4c72773dbad5f8  JSONRPCServer.java
80c15132b123509340c173dfc80dc1e1f84ffd03  master/HTTPServerHandler.java
06b1b0ac6458f747e2c13bb6e9c86667d5892802  server/HTTPServerHandler.java
d4f95fd1a8c0db3ad0009cde99428e0707969c93  HTTPServerInitializer.java
5e07252b74a1f3129e62af6798f3f5d8774b308a  HTTPClient.java
777673a76015b27a83694e1bf9b422334b9e35a8  ConfigHelper.java
50780e4f397d3e7c0b2bc2580d8078e1b53bcb83  WalletHelper.java
6e54e2db223ab5417cdfce2b2deaddaee8665305  ManagedReadOnlyAuthorityBoundaryTest.java
```

The ordinary coin handler continues to deny, case-insensitively and before
`params` access, all generic raw construction, funding, signing, broadcasting,
transfer/send/move, address creation, key import/export, mnemonic export,
arbitrary message signing and `xrSendTransaction` names in its exact sensitive
set. The shared response remains `-32601`, `Method not found.`.

Direct boundaries remain `CoinInstance.generateAddress`,
`CoinInstance.importPrivateKey`, both `CoinInstance.sendXrMessage` overloads,
`CoinInstance.reloadConfig`, `CoinInstance.runAddressDiscovery`,
`WalletHelper.generateAddress`, `WalletHelper.generateFromPrivateKey`, both
`WalletHelper.createRawTransactionWithAllUTXOs` overloads and
`WalletHelper.createTransactionSimple`. Public mnemonic exporters and
`HTTPClient.sendRawTransaction` remain absent. Startup-only private HD
derivation remains the sole managed address-derivation exception.

`WalletHelper.addTransactionToWallet` and `WalletHelper.setAsSpent` are
existing in-memory bookkeeping helpers. They receive no new caller and cannot
sign, return a key, construct bytes or relay. The new package cannot import or
call them. `HTTPClient` retains read-only chain/history methods but no raw relay
method. Read-only XRouter commands remain unchanged; effectful
`xrSendTransaction` remains denied.

`SettlementSourceBoundaryTest` must scan compiled visibility, imports and all
production call sites. It must prove that the five new classes are
package-private, only the six exact operation wire values exist, no existing
source imports the new package, and the new package imports none of Netty,
bitcoinj, `CoinInstance`, `WalletHelper`, `KeyHandler`, `ConfigHelper`,
`HTTPClient`, XRouter, console or RPC classes. It also reruns the accepted T10
boundary suite. Any route or blob change invalidates T04.
It also reflects over every new class and proves that the package-private
constructors/methods equal this manifest exactly, while private helpers remain
uncallable implementation detail. It permits only compiler-generated nested
record/enum public data methods behind package-private enclosing classes and
the single required public `AutoCloseable.close()` override. It rejects any
public/protected top-level type or other explicitly authored public/protected
authority.

## Exact tests and build gates

`SettlementCanonicalJsonV1Test` covers strict UTF-8, canonical byte equality,
duplicate/unknown fields, every scalar type, trailing input and each resource
limit plus one, including separate per-object/per-array and cumulative counters.
`SettlementEnvelopeCodecV1Test` covers all six selectors, three schemas, eight
statuses, 21 errors, exact/extra/missing/wrong fields, the sealed Java sums,
all four broadcast bodies, status-aware non-broadcast quarantine, both nullable
component rules, reserved-error rejection, bounded decimal/address/base64
validators, every cross-field/cardinality rule, lookup envelope/body equality,
typed error-context revalidation, effect/lookup expected-ID derivation, every
error/context operation mismatch, result-digest recomputation on decode and
encode, and no fallback. The
suite admits the exact 16-key contextual, 12-key ownership-probe and 16-key
lookup envelopes, permits coherent all-empty/all-non-empty lookup context and
denies every missing, extra, JSON-null or mixed form. A dependency/import spy
proves codec and error revalidation have no store, callback or current-
generation access.

`SettlementIdentityV1Test` independently reproduces semantic vector
`718fd3390ec6b05e15306b9fb331db604aba5457e5b95f3c8da4c4248fc92016`,
payload vector
`f7c2b4911611397cddaa42f3f56cd25a6eeeafc39efc5b1c1fcf30cf13b2cbd9`
and handover vector
`b5895ff95be0245f82ab2cfb9883022453b141c2e5f2f83ef10a0491a7a96b8b`.
It also independently reproduces the exact 614-byte canonical result, 670-byte
framed preimage and result digest
`97230fe9d7520b9d95a8a068ad507694b89b7fdfec61cc096aba8611c8bcc2b0`.
It covers every field/order/exclusion change, the two distinct policy digests
and the deterministic 64-case result-digest mismatch matrix: for each zero-
based hex position 0..63, replace only that nibble with its next lower-case
hexadecimal value modulo 16 and require decode/encode rejection.

`InertSettlementOperationStoreV1Test` uses only fresh `@TempDir` roots and
synthetic keys. It covers every allowed and denied transition, byte-for-byte
replay with no append/time mutation, payload conflict, not-found hold, complete
chain authentication, corruption/truncation at every frame boundary, no
fallback, startup quarantine before reopen returns, no redispatch API,
generation handover/reuse/stale/conflict, writer exclusion and both capacity
limits plus one. It also covers the three exact frame-record variants, first and
chained HMAC preimages, length/MAC/trailing-byte faults, frame-field/result-
envelope equality, authenticated `TargetContext`, every target-context mismatch,
wrong-profile append/lookup denial, stale-generation append/lookup denial,
current-generation lookup after handover returning byte-identical older result
bytes, ownership-target empty context versus other-target deep context equality,
result-digest equality and missing/extra/reordered/mismatched prepared
startup quarantines selected from latest status only. It proves mandatory
initialisation, lock-before-
authentication, second-writer denial for the full instance lifetime,
`force(true)` before success, whole-batch capacity preflight, undersized-limit
factory denial, distinct invalid root/key/identity/limit factory reasons,
constant factory failures, cleanup on every failure path and
synthetic-key zeroing. Fault injection at operation/handover write and force
proves permanent poison, every later held outcome, held snapshot, close cleanup,
constant close exception and idempotent second close. Read/HMAC spies prove
poisoned and stale-generation calls do no file access, while every matching-
generation call authenticates the complete log before target-context, payload,
replay or transition outcome. A focused normal-close case proves every later
operation/snapshot is persistence-held without file access or cached result and
every later close is a no-op. No test reads an existing wallet profile.

The limit cases construct raw `StoreLimits` at zero, negative, maximum-plus-one
and valid-but-undersized values without constructor failure. Both factories,
not the record, return `INVALID_LIMITS` for the first three classes and
`CAPACITY_UNAVAILABLE` for the last, with no unchecked exception or mutation.

`SettlementSourceBoundaryTest` covers the import/caller/visibility/bypass rules
above. The accepted managed and unmanaged compatibility tests remain mandatory.

The exact Java 21 and Maven 3.9.11 commands are:

```text
./mvnw -q -Drewrite.skip=true -DskipTests compile
./mvnw -q -Drewrite.skip=true \
  -Dtest=SettlementCanonicalJsonV1Test,SettlementEnvelopeCodecV1Test,SettlementIdentityV1Test,InertSettlementOperationStoreV1Test,SettlementSourceBoundaryTest,ManagedReadOnlyAuthorityBoundaryTest,POR171BoundaryTest,POR172CompatibilityTest,POR172XBridgeCompatibilityTest,ReadOnlyExistingProfileTest test
./mvnw -q -Drewrite.skip=true test
git diff --check
```

`JAVA_HOME` must identify the admitted Java 21 runner. Tests requiring local
loopback use an environment that permits local bind. OpenRewrite remains
disabled for evidence runs; any later Rewrite run is a separate inspected
delta. The focused and full reports must have zero failures, errors and skips.

## Hold and completion rule

`XL-IS-T04` stays unchecked until an independent reviewer accepts the exact
successor dossier commit against corrected IS-SEQ-006. Even a T04 pass
authorises no source by itself. Separate joint `FA-IS-T33`/`FA-IS-T35`
authorisation must name either the parser/identity subtranche or the inert-store
subtranche on the exact accepted head.

T05 and T06 remain incomplete. T02, T03, T07-T09, production persistence,
credential/listener work, wallet/chain/proof/sign/broadcast calls, transaction
bytes, runtime integration, JAR admission, recovery, zero-fund testing, funds,
orders, swaps, signing, packaging, tagging and release all remain held.
