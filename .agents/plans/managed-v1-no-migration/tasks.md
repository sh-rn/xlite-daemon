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
- [ ] **MVNM-T07** - Commit locally and record the exact commit, tree and
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
