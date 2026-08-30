# Functional Alpha XLite source-only mutation manifest

Status: proposed XL-IS-T04 review subject - no named settlement source is authorised

## Exact subjects and authority

This is the pre-code dossier required by `XL-IS-T04`. It is documentation, not
an implementation or runtime admission.

- Authorised repository: `https://github.com/sh-rn/xlite-daemon.git`
- Read-only upstream: `https://github.com/tryiou/xlite-daemon.git`
- Branch: `codex/functional-alpha-intent-settlement`
- Source baseline head: `5f8a15759b027b1fd4205bfb067130b7040d6bea`
- Source baseline tree: `71b5f33fc8931918bcc40f002b4ff6726a13316e`
- Accepted denial sequence: `9bfc85d6e7aee1dbf99943ed113575a6805a3ddd`
  to `0963adc5cf96d03f0473f2c0714fa10dde682482` to the source baseline
- Canonical Desktop review head: `2c9551b20c0406493c2dddec9cb934ba9eae55a4`
- Canonical Desktop tree: `74383f4c81ba14ce4f0295d357776f1daafea0e6`
- Canonical Desktop contract: `docs/protocols/functional-alpha-intent-settlement-contract.md`,
  section `IS-SEQ-004 source-only executor interface proposal`
- Immutable interface proposal: `9bfc85d6e7aee1dbf99943ed113575a6805a3ddd`,
  tree `4514d3e26bc9246c1396278a583a91cd472ba25f`

The commit containing this dossier cannot name its own digest. Independent
review must record that exact commit and tree. Any source-baseline, dossier or
Desktop-contract change invalidates the review. Source work must then amend
this manifest and obtain a new independent review before code.

The only expected remote action for this dossier is a normal push of its
independently reviewed local commit to the authorised `sh-rn/xlite-daemon`
branch. No pull-request merge, tag, release or artefact publication is included.
Any later source commit receives a separate exact-head review before its own
remote action.

## Admitted source-only boundary

After this dossier and the joint interface pass independent review, a later
explicit source authorisation may add only:

1. package-private closed v1 contract values and immutable data records;
2. a package-private strict canonical JSON parser/encoder;
3. package-private request, result and error envelope decoders selected by the
   six exact operation enum values;
4. pure semantic, payload and handover digest functions; and
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
method is package-private or private. Records defensively copy byte arrays and
lists on construction and access. No type implements a generic command,
callback, provider, signer, broadcaster or RPC interface.

### `SettlementContractV1`

This class has a private constructor and contains only these nested enums:

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
- `Asset`: `BLOCK`, `LTC`.

Each enum has only `wireValue()` and its compiler-generated `values()` and
`valueOf(String)` methods. `wireValue()` never accepts caller input.

The class contains only these immutable nested records, their canonical
constructors and compiler-generated accessors, equality, hash and string
methods:

```text
ChainIdentity
RequestEnvelope
ResultEnvelope
ErrorEnvelope
ReserveWalletInputsRequest
ProveReservedUtxoOwnershipRequest
CheckOwnedAddressRequest
SignSettlementStageRequest
BroadcastSettlementStageRequest
GetSettlementOperationResultRequest
PrevoutEvidence
ExpectedOutput
RecoveryBinding
ReservationResult
OwnershipProofResult
OwnedAddressResult
SignedStageResult
BroadcastStageResult
IdentityInputs
PayloadInputs
GenerationHandover
LookupKey
OperationAppend
StoreLimits
```

`RequestEnvelope`, `ResultEnvelope` and `ErrorEnvelope` contain only the exact
top-level fields in IS-SEQ-004. The named request and result records reserve
explicit closed types and prohibit a map or generic body, but their unresolved
nested/status-specific constructors and accessors cannot be written until the
schema gaps below are frozen. `GetSettlementOperationResultRequest` contains
exactly `targetSemanticOperationId` and `expectedPayloadDigest`; it has no
result record because a found lookup returns the selected stored result
envelope byte-for-byte. `GenerationHandover` contains exactly `profileId`,
`priorGenerationId`, `nextGenerationId`, `desktopJournalHeadDigest` and
`handoverDigest`. `LookupKey` contains exactly `profileId`,
`semanticOperationId` and `payloadDigest`.

### `SettlementCanonicalJsonV1`

This class has a private constructor and exactly these methods:

```text
static JsonElement parse(byte[] utf8)
static byte[] encode(JsonElement value)
private static JsonElement readValue(JsonReader reader, int depth, Counter counter)
private static void writeValue(JsonElement value, StringBuilder output, int depth,
                               Counter counter)
private static void writeString(String value, StringBuilder output)
private static void requireAscii(String name, String value)
private static void requireWithinLimits(int byteCount, int depth, int members,
                                        int arrayEntries)
```

`Counter` is a private nested class with exactly `addMember()` and
`addArrayEntry()` methods. Parsing uses strict UTF-8 and rejects BOM, duplicate
members, numbers, unknown non-ASCII input, invalid escapes, trailing bytes and
noncanonical input, then byte-compares canonical re-encoding with the request.
Canonical integer values are JSON strings, not JSON numbers. Boolean and null
remain JSON primitives. Resource ceilings for this inert parser are exactly 262,144 input
bytes, depth 16, 128 members per object, 64 array entries and 200,000 UTF-8
bytes per string. These are parser resource bounds only, not transaction, fee,
financial or runtime admission.

### `SettlementEnvelopeCodecV1`

This class has a private constructor and exactly these entry methods:

```text
static RequestEnvelope decodeRequest(byte[] canonicalUtf8)
static ResultEnvelope decodeResult(byte[] canonicalUtf8)
static ErrorEnvelope decodeError(byte[] canonicalUtf8)
```

It has exactly six private request selectors:

```text
private static ReserveWalletInputsRequest decodeReserveWalletInputs(JsonObject body)
private static ProveReservedUtxoOwnershipRequest decodeProveReservedUtxoOwnership(JsonObject body)
private static CheckOwnedAddressRequest decodeCheckOwnedAddress(JsonObject body)
private static SignSettlementStageRequest decodeSignSettlementStage(JsonObject body)
private static BroadcastSettlementStageRequest decodeBroadcastSettlementStage(JsonObject body)
private static GetSettlementOperationResultRequest decodeGetSettlementOperationResult(
        JsonObject envelope, JsonObject body)
```

It has exactly five private result selectors:

```text
private static ReservationResult decodeReservationResult(JsonObject body)
private static OwnershipProofResult decodeOwnershipProofResult(JsonObject body)
private static OwnedAddressResult decodeOwnedAddressResult(JsonObject body)
private static SignedStageResult decodeSignedStageResult(JsonObject body)
private static BroadcastStageResult decodeBroadcastStageResult(
        Status status, JsonObject body)
```

The only remaining private helpers are `decodeChainIdentity`,
`decodePrevoutEvidence`, `decodeExpectedOutput`, `decodeRecoveryBinding`,
`requireExactKeys`, `requireObject`, `requireArray`, `requireString`,
`requireBoolean`, `requireNullOrObject`, `requireHex64`, `requireEvenHex`,
`requireCanonicalUnsignedDecimal`, `requireDeadlineOrder` and
`requireEnvelopeIdentityEquality`. There is no generic selector, reflection,
class-name dispatch, arbitrary method string, map-to-method adapter or
fallback. An unknown schema, operation, status, error, field or type denies.

The exact selection table is:

| Wire operation | Request record | Result record | Stored status |
| --- | --- | --- | --- |
| `reserveWalletInputs` | `ReserveWalletInputsRequest` | `ReservationResult` | `reserved` or `quarantined` |
| `proveReservedUtxoOwnership` | `ProveReservedUtxoOwnershipRequest` | `OwnershipProofResult` | `proof-created` or `quarantined` |
| `checkOwnedAddress` | `CheckOwnedAddressRequest` | `OwnedAddressResult` | `ownership-checked` or `quarantined` |
| `signSettlementStage` | `SignSettlementStageRequest` | `SignedStageResult` | `signed` or `quarantined` |
| `broadcastSettlementStage` | `BroadcastSettlementStageRequest` | `BroadcastStageResult` | one of the four broadcast statuses |
| `getSettlementOperationResult` | `GetSettlementOperationResultRequest` | none | no append or transition |

The three schema strings are exactly
`blocknet.xlite.settlement.request.v1`,
`blocknet.xlite.settlement.result.v1` and
`blocknet.xlite.settlement.error.v1`. No ordinary coin RPC name is accepted.

### `SettlementIdentityV1`

This class has a private constructor and exactly these methods:

```text
static String semanticOperationId(IdentityInputs input)
static String payloadDigest(PayloadInputs input)
static String handoverDigest(GenerationHandover input)
static String canonicalDigest(JsonElement input)
private static byte[] frame(String name, String value)
private static String sha256Hex(List<byte[]> parts)
private static void requireLowerHex64(String name, String value)
```

It uses JDK `MessageDigest` only. The domain strings, field order, exclusions
and top-level `policyDigest` versus `chainPolicyDigest` distinction are exactly
IS-SEQ-004. It accepts no caller-supplied digest, serializer or field order.

### `InertSettlementOperationStoreV1`

This class implements `AutoCloseable` and has exactly these package-private
methods:

```text
static InertSettlementOperationStoreV1 createFreshTemporaryProfile(
        Path root, byte[] syntheticAuthenticationKey, StoreLimits limits,
        String profileId, String initialGenerationId)
static InertSettlementOperationStoreV1 reopenTemporaryProfile(
        Path root, byte[] syntheticAuthenticationKey, StoreLimits limits)
AppendOutcome append(OperationAppend append)
LookupOutcome lookup(LookupKey key)
HandoverOutcome acceptHandover(GenerationHandover handover)
StoreSnapshot snapshot()
void close()
```

The private methods are exactly `requireFreshTemporaryRoot`, `openChannel`,
`readAndAuthenticateWholeLog`, `decodeFrame`, `validateAppendTransition`,
`findLatest`, `appendFrame`, `appendStartupQuarantines`, `enforceCapacity`,
`computeFrameMac`, `force`, `copyBytes` and `zeroSyntheticKey`.
`AppendOutcome`, `LookupOutcome`, `HandoverOutcome` and `StoreSnapshot` are
package-private nested records or enums with data-only accessors.

The inert format is one file named `xlite-settlement-inert-test-v1.log` beneath
an empty caller-supplied JUnit temporary directory. It is deliberately not a
production format. Each frame is a 4-byte big-endian length, canonical record
bytes and HMAC-SHA-256 over the previous frame MAC, length and record bytes.
The only key is a copied synthetic test key supplied by the test. The key is
never read from `KeyHandler`, `ConfigHelper`, an environment variable, a
command line, Desktop or a wallet profile. The exact inert limits are 1,024
frames and 16 MiB. Capacity holds without eviction.

The complete log authenticates before any lookup or append. A partial,
malformed, conflicting or unauthenticated frame returns persistence failure;
there is no truncation, repair or earlier-record fallback. Identical replay
returns the latest valid result envelope bytes without append, timestamp or
file-time mutation. Same semantic ID with another payload conflicts without
append. Reopen appends a quarantine for every retained
`broadcast-dispatch-recorded` item before the factory returns. There is no
dispatch or reconciliation method. One valid generation handover is appended
from the exact current generation; reuse, conflict and stale generations deny.

This inert class is not a production store, encryption design, rollback
detector, recovery journal, credential owner or wallet authority. Its format
must not be wired or migrated into a runtime without a new T03/T07 design and
manifest.

## Exact schema gaps that still hold source

IS-SEQ-004 says that the immutable XLite proposal's operation-specific request
and result body field sets are adopted as closed parser shapes. That statement
is insufficient and contradictory for implementation where the proposal gives
only narrative descriptions rather than exact wire keys and scalar types for
all nested and status-specific values. In particular, the following are not
source-ready:

- `ChainIdentityV1` scalar and array wire types for `p2pkhVersion`,
  `p2shVersions`, `baseUnitsPerCoin` and `minimumConfirmations`;
- `PrevoutEvidenceV1` scalar wire types for `vout`, `valueBaseUnits`,
  `confirmationHeight` and `observedConfirmations`;
- the reservation result's exact outpoint-array field name, each identity
  entry's keys/types and the exact placement/type of `deduplicated`;
- proof request exact keys/types for the reserved outpoint and canonical Core
  message bytes/digest, plus exact proof result keys/types;
- sign request exact `previousOutputs` entry keys/types,
  `inputSequences` item type, expected-output and signed-index wire types, plus
  exact signed-result keys/types;
- exact status-specific broadcast result bodies for dispatch-recorded,
  succeeded, rejected and quarantined, including returned identity and bounded
  rejection/quarantine evidence;
- the exact result-versus-error rule and body for non-broadcast quarantine;
- the canonical `resultDigest` preimage, algorithm and independent vector;
- error-envelope `semanticOperationId` presence rules for every error and
  processing phase; and
- JSON number-versus-canonical-decimal-string rules for every nested scalar.

The records and decoder methods above are the complete proposed code surface,
but no parser or record may be implemented until an independently reviewed
contract amendment freezes those missing names and types. Implementers may not
invent them, use a generic map/body, accept unknown fields or omit one of the
six selectors. This makes the present T04 dossier fail closed rather than
silently manufacturing protocol authority.

The existing six operation names, three schema IDs, eight statuses, 21 errors
and three identity vectors remain frozen and unchanged. They are necessary but
insufficient to instantiate closed operation bodies or results. Desktop
`FA-IS-T32`, recovery `FA-RCV-T18`, this exact T04 dossier review, the additive
schema/vector review and an explicit `FA-IS-T33` source authorisation are all
still required before code.

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

## Exact tests and build gates

`SettlementCanonicalJsonV1Test` covers strict UTF-8, canonical byte equality,
duplicate/unknown fields, every scalar type, trailing input and each resource
limit plus one. `SettlementEnvelopeCodecV1Test` covers all six selectors,
three schemas, eight statuses, 21 errors, exact/extra/missing/wrong fields,
closed asset/role/stage values, lookup envelope/body equality and no fallback.

`SettlementIdentityV1Test` independently reproduces semantic vector
`718fd3390ec6b05e15306b9fb331db604aba5457e5b95f3c8da4c4248fc92016`,
payload vector
`f7c2b4911611397cddaa42f3f56cd25a6eeeafc39efc5b1c1fcf30cf13b2cbd9`
and handover vector
`b5895ff95be0245f82ab2cfb9883022453b141c2e5f2f83ef10a0491a7a96b8b`.
It covers every field/order/exclusion change and the two distinct policy
digests.

`InertSettlementOperationStoreV1Test` uses only fresh `@TempDir` roots and
synthetic keys. It covers every allowed and denied transition, byte-for-byte
replay with no append/time mutation, payload conflict, not-found hold, complete
chain authentication, corruption/truncation at every frame boundary, no
fallback, startup quarantine before reopen returns, no redispatch API,
generation handover/reuse/stale/conflict, writer exclusion and both capacity
limits plus one. No test reads an existing wallet profile.

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
dossier commit and resolves or explicitly preserves every schema gap above.
Even a T04 pass authorises no source by itself. A separate exact authorisation
must name either the parser/identity subtranche or the inert-store subtranche.

T05 and T06 remain incomplete. T02, T03, T07-T09, production persistence,
credential/listener work, wallet/chain/proof/sign/broadcast calls, transaction
bytes, runtime integration, JAR admission, recovery, zero-fund testing, funds,
orders, swaps, signing, packaging, tagging and release all remain held.
