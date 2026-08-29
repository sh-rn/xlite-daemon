# Managed V1 no-migration tasks

Status: Founder approved

- [x] **MVNM-T01** - Record founder approval for this exact plan, baseline,
      nine-file scope, local-only branch and absence of remote actions.
- [x] **MVNM-T02** - Implement the explicit KeyHandler migration policy while
      retaining the migration-enabled default.
- [x] **MVNM-T03** - Carry the policy through CoinInstance and ConsoleMenu and
      document the value-free option.
- [x] **MVNM-T04** - Add focused V1 byte-identity, no-backup, V2, ordering,
      value-rejection and help tests.
- [x] **MVNM-T05** - Run focused and complete Java 21 Maven tests and inspect
      OpenRewrite drift.
- [x] **MVNM-T06** - Obtain independent exact-diff review with no open P0-P2
      or material P3 finding.
- [x] **MVNM-T07** - Commit locally and record the exact commit, tree and
      deterministic JAR identity. No push, PR, merge, tag or release.

## Verification evidence

- Java: Eclipse Temurin `21.0.12.1+1-LTS`; Maven `3.9.11`.
- Focused suite: `KeyHandlerTest,POR171BoundaryTest` - 23 tests passed.
- Complete suite: 73 tests passed with no failures or skips.
- OpenRewrite `process-classes` completed successfully. Its unrelated
  formatting drift was removed; the final diff remains the approved nine-file
  plan, implementation and test scope.
- Independent exact-diff review: PASS with no P0-P3 finding; reviewed tracked
  diff SHA-256 `3e1ad4b2cc39207f9d4ef9cd9e321bcaece8fdc2756199e5135efc45578751fa`.
- Source commit: `f1d85ca0efe31d40909239d1c48987c2f4db1ceb`; tree:
  `caffa48a2a6c34895e6a21928d91e4660169c214`; source archive SHA-256:
  `a70a3b73aa8e1f2fdee2bbaff5c5b5e23b37818e564aeb44d46d9740f9f71322`.
- Deterministic shaded JAR: 13,515,578 bytes; SHA-256
  `451b5b3079781cbaf60840d2c79b565e9058d993f3f728813b6ce2aaf3974856`.
  Two builds using the source-commit epoch produced the same digest.
- Independent source/JAR provenance review: PASS with no P0-P3 finding. The
  previous Desktop runtime receipt remains invalid for this new JAR and must
  be replaced during its separate admission and packaging gate.
