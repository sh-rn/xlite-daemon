# Functional Alpha XLite intent-settlement remediation tasks

Status: T10 denial prerequisite implemented - independent follow-up review pending

`XL-IS-SEQ-001` authorises only T10 ahead of T02/T03/T07. Independent review
subject: `9bfc85d6e7aee1dbf99943ed113575a6805a3ddd`; reviewer:
`/root/delivery_coordinator`; verdict: PASS for denial-only implementation.
Every other task remains held.

- [ ] **XL-IS-T01** - Jointly review these four files against Desktop
      `FA-SIGN-001` to `FA-SIGN-016`, `FA-IS-T02`/`T03`/`T11`, recovery
      `FA-RCV-T07` and the accepted connector dossier. Resolve every material
      mismatch and record exact reviewed bytes, reviewer and limits.
- [ ] **XL-IS-T02** - Freeze the exact six operation schemas, semantic/payload
      preimages and independent vectors, stage graph, BLOCK/LTC identities,
      transaction/script/sighash forms, confirmation policy, fee caps, proof
      replay decision, deadlines and all byte/count/concurrency limits.
- [ ] **XL-IS-T03** - Jointly freeze Desktop/recovery and XLite durable
      consumed/result semantics, generation handover, writer exclusion,
      encryption/key/nonce ownership, crash publication, rollback limits,
      capacity, reservation release and terminal retention.
- [ ] **XL-IS-T04** - Produce the exact pre-code mutation manifest: repository,
      base/head/tree, branch, every existing/new file, class/method, dependency,
      test, build command, expected remote action and complete bypass inventory.
      Obtain exact independent review before source work.
- [ ] **XL-IS-T05** - Implement only the reviewed value-free launch selector,
      private authenticated transport and closed schema parsing. Keep the
      capability unavailable without admitted policy/store/executor components.
- [ ] **XL-IS-T06** - Implement and test the durable semantic-operation store,
      exact-payload conflict handling, result lookup, generation handover,
      crash cuts, corruption/rollback/capacity holds and no blind redispatch.
- [ ] **XL-IS-T07** - Implement and test BLOCK/LTC reservation, independent
      chain/ownership/confirmation validation, one-address funding, disjoint
      fee/trade sets and both named ownership operations.
- [ ] **XL-IS-T08** - Implement and test wallet-input-only stage signing,
      final-byte revalidation, signature-only deltas, stage/graph fee bounds and
      refusal of every unreserved, cross-intent, wrong-chain or contract input.
- [ ] **XL-IS-T09** - Implement and test stage-bound broadcast, refund-before-
      funding prerequisite, durable pre-dispatch record, exact returned identity,
      definitive rejection and unknown-outcome quarantine/result lookup.
- [x] **XL-IS-T10** - Prove ordinary `signrawtransaction`,
      `sendrawtransaction`, transfers, address creation, key import/export,
      arbitrary message signing and every alternate helper/endpoint remain
      unavailable in managed mode. Re-run the complete RPC/helper inventory.
      This is the only implementation task sequenced before T02/T03/T07 by
      `XL-IS-SEQ-001`; it must preserve positive unmanaged compatibility and
      creates no private settlement operation.
      Evidence: `ManagedReadOnlyAuthorityBoundaryTest` exercises the real
      Netty handler through `EmbeddedChannel` and proves byte-identical
      method-not-found denial before missing, null, object, string, numeric or
      hostile-array parameters can be read or coerced. It also covers aliases,
      zero profile/config/key/packet/relay effects, immutable per-instance
      policy, startup derivation and positive unmanaged address/import/export/
      proof/raw-create/input-signing compatibility. Java 21 focused
      compatibility tests passed 36/36 and the complete suite passed 89/89
      with zero failures, errors or skips.
- [ ] **XL-IS-T11** - Run Java 21 focused/full/hostile/crash tests, inspect all
      OpenRewrite drift, produce two byte-identical shaded JARs and close exact
      dependency/licence/provenance/SBOM/source identities.
- [ ] **XL-IS-T12** - Obtain independent exact-head fund-safety review and
      publish only sanitised source/build evidence. Hand the immutable candidate
      digest to Desktop admission. Keep runtime, zero-fund, funded, packaging,
      notarisation and release gates open for their owners.

T10 creates no settlement success path and advances no other checkbox. No JAR,
wallet profile, runtime, funds or remote repository state is part of this
denial-only tranche.
