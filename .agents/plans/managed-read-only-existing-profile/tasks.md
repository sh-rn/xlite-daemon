# Tasks

- [x] T1 Record the constrained read-only existing-profile contract.
- [x] T2 Select the policy before configuration, logging or master-server construction.
  Evidence: `App.main` selects the flag policy before log rotation or file logging;
  master RPC construction is lazy.
- [x] T3 Make configuration and wallet preflight read-only and fail closed.
  Evidence: `ConfigHelper`, `KeyHandler` and `ConsoleMenu` validate an existing
  V1/V2 wallet plus enabled, credentialed master/BLOCK/LTC configurations with
  unique usable ports before stdin is read.
- [x] T4 Prevent coin initialisation, RPC and background paths from mutating profile state.
  Evidence: read-only guards cover configuration writes, address discovery and
  repair, config reload, new-address RPC, startup file logging and rotation;
  `ConsoleMenu` does not construct the background timer in this mode.
- [x] T5 Add focused policy and byte-identical profile tests.
  Evidence: `ReadOnlyExistingProfileTest` covers V1/V2 temporary-profile trees,
  malformed input before stdin, disabled/missing credentials, duplicate ports,
  default coin initialisation and no-write address-count paths.
- [x] T6 Run focused and full Java 21 tests, review the diff and build the deterministic JAR twice.
  Evidence: Temurin 21.0.12.1 and Maven 3.9.11; focused 34/34 and full 84/84
  passed. Independent static review reported no P0-P3. Two consecutive local
  builds with `project.build.outputTimestamp=1787990474` produced
  `target/xlite-daemon-0.5.15.jar`, 13,519,396 bytes,
  SHA-256 `0033490e272d2991881a8e8baf3430eb05ca8ca22bd12d4df94569ccb6fe8af6`.
- [x] T7 Refresh required BLOCK/LTC heights once through the existing public
  height client before read-only master RPC readiness, fail closed on missing
  heights, retain the no-background-timer boundary, and rebuild the exact JAR.
  Evidence: Java 21 focused tests passed 17/17 and the full suite passed 85/85.
  Two consecutive deterministic packages produced 13,519,552-byte JARs with
  SHA-256 `d798f0da8c757a8534cdb7bec035c61dc6fb2ce4e1ff3a2f1ad1f88b8aab5060`.
