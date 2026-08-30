# Functional Alpha XLite intent-settlement remediation requirements

Status: T10 denial requirement fulfilled - named settlement requirements remain open

- **XL-IS-001** - Desktop main is the sole creator and durable authority for
  parent intents and child stages. XLite accepts requests only from its distinct
  authenticated internal transport and independently revalidates every wallet,
  reservation, chain, policy, stage and transaction fact.
- **XL-IS-002** - The capability is off by default. A future value-free managed
  selector is valid only with the exact read-only-existing-profile,
  no-migration and stdin-password launch. Invalid, duplicate, valued or partial
  flag combinations fail before wallet or listener initialisation.
- **XL-IS-003** - The only private v1 operation names are
  `reserveWalletInputs`, `proveReservedUtxoOwnership`, `checkOwnedAddress`,
  `signSettlementStage`, `broadcastSettlementStage` and
  `getSettlementOperationResult`. No generic method selector exists.
- **XL-IS-004** - BLOCK and LTC are the only assets. Every request binds the
  exact mainnet/genesis, network magic, address/script versions, signed-message
  prefix where applicable, policy digest and required confirmation depth. All
  components must independently agree before a key or network operation.
- **XL-IS-005** - The closed transaction-stage set is `service-fee`,
  `maker-deposit`, `taker-deposit`, `maker-redeem`, `taker-redeem`,
  `maker-refund` and `taker-refund`. `service-fee` is BLOCK/taker only.
  XLite wallet-input signing is permitted only for `service-fee`,
  `maker-deposit` and `taker-deposit`; contract stages require the separately
  admitted graph-bound Core protocol signer.
- **XL-IS-006** - Every effect request binds immutable profile, runtime
  generation, parent, child, role, stage, asset, chain-policy and deadline
  identities. Opaque parent/child identifiers cannot manufacture authority.
  Stale generations, missing fields, unknown values and unbound identities deny.
- **XL-IS-007** - Both sides recompute a domain-separated semantic operation ID
  and request payload digest from the closed schema. The same semantic operation
  and payload returns the durable prior result. A different payload conflicts.
  Transport generation is validated separately and cannot change either durable
  identity. Changing a request ID, connection or process generation never
  permits replay; generation handover requires the jointly reviewed durable
  handover record.
- **XL-IS-008** - Deadlines are canonical unsigned decimal Unix-epoch
  milliseconds. XLite enforces its own clock and a reviewed maximum horizon.
  Expiry cannot resurrect initial authority or remove already committed bounded
  recovery authority. The joint contract must close clock rollback and sleep
  behaviour before implementation.
- **XL-IS-009** - `reserveWalletInputs` accepts only one to ten unique,
  presently unspent, sufficiently confirmed wallet outputs from one owned
  address. Exact outpoint, value, script, confirmation block/height and
  independent chain evidence must agree. Inputs cannot overlap another parent;
  BLOCK service-fee and trade sets are disjoint.
- **XL-IS-010** - The parent base amount is greater than zero and no more than
  `10 BLOCK`. Exact outgoing BLOCK or LTC principal and every stage/graph fee
  cap are separate integer base-unit fields. The fixed `0.015 BLOCK` taker fee,
  both native-chain fees and recovery reserve are never merged or inferred from
  a relay-fee scalar. Exact caps remain a `FA-IS-T02` prerequisite.
- **XL-IS-011** - `proveReservedUtxoOwnership` reconstructs or byte-compares the
  exact reviewed `txid:vout:amount:address` proof, verifies wallet ownership and
  returns a private compact proof only for the bound reservation and connector
  purpose. Funding remains denied until peer-side replay prevention is proved.
- **XL-IS-012** - `checkOwnedAddress` is a connector-purpose predicate bound to
  profile, generation, chain and exact address. It returns only owned/not-owned
  and never a signature. It does not require or create a settlement parent.
- **XL-IS-013** - `signSettlementStage` independently parses exact unsigned
  bytes, resolves every previous output, verifies reservation, outputs,
  contracts, change, values, fees, sequences, locktime, sighash and stage, then
  signs only wallet-owned inputs. It reparses final bytes, verifies the permitted
  signature-only delta and returns locally derived final identity.
- **XL-IS-014** - A refund-dependent funding stage cannot become broadcastable
  until Desktop supplies the jointly reviewed durable recovery binding to the
  exact final funding identity. XLite stores only the minimum private binding
  needed for independent enforcement and result recovery.
- **XL-IS-015** - `broadcastSettlementStage` accepts only retained final bytes
  for one eligible child. XLite-produced bytes must match its durable signing
  result. Core-produced contract bytes must match the admitted graph signer and
  stage policy. XLite records dispatch before relay and compares the returned
  identity with its local txid/wtxid derivation.
- **XL-IS-016** - A timeout, lost reply, identity mismatch, conflicting spend,
  persistence failure or unknown backend outcome consumes the effect and enters
  durable quarantine. There is no automatic resend or reconstruction.
  `getSettlementOperationResult` is read-only and returns the durable state plus
  bounded reconciliation evidence without dispatching.
- **XL-IS-017** - Durable operation state survives clean/unclean restart and
  generation handover. Same-operation deduplication, request/result persistence,
  writer exclusion, crash cuts, corruption handling, encryption, rollback
  limits, capacity and terminal retention require joint recovery review before
  source implementation.
- **XL-IS-018** - In managed mode ordinary `signrawtransaction`,
  `sendrawtransaction`, transfers, address generation, key import/export and
  arbitrary `signmessage` remain unavailable regardless of parameter shape.
  The source/helper/endpoint inventory must prove no alternate route.
- **XL-IS-019** - Credentials, keys, proofs, addresses, outpoints, transaction
  bytes/IDs, raw backend errors and durable payloads are excluded from normal
  logs and RPC errors. All bodies, headers, fields, arrays, bytes, queues,
  concurrency and deadlines have reviewed finite limits.
- **XL-IS-020** - Java 21 focused/full tests, hostile boundary and crash tests,
  complete bypass inventory, post-Rewrite diff inspection, two byte-identical
  builds, licence/provenance closure and independent exact-head review must pass
  before any JAR can be proposed for Desktop admission. None authorises funds.
