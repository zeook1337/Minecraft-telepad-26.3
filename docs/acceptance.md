# Acceptance record

## Candidate 1.0.1 — 5 October 2026 — Alpha, manual acceptance pending

The candidate is unpublished and is not eligible for Release. Public version
numbering remains independent of Minecraft. All earlier acceptance below is
historical and does not replace this candidate's required gates.

- Clean JAR: `build/libs/telepads-26.3-1.0.1.jar`, also preserved locally outside
  build output. SHA-256:
  `BBC71248A9393F7E5BC958676C969777F35712DB2DFC18AC732130CA700A3E57`.
- Source: uncommitted working tree based on
  `08abaaabec6b3bab790144bc81928d85ea9a035d`. Regression/build input fingerprint:
  `345B28E66FEC85BAFB2AA8AAFE3F7766E344467BF3ADCB7FD015BB7842913028`.
  `prepare-release-handoff.ps1` creates an immutable snapshot commit and rebuilds
  its corresponding-source archive; the local `handoff.json` identifies that
  snapshot and both checksums. Final Release acceptance is pending.
- Environment: Windows 11 x64; Java Temurin 25.0.4.1+1; Minecraft Java 26.3;
  Forge 66.0.9; Gradle 9.7.1; ForgeGradle 7.0.17; protocol 3.
- Raw evidence: local `docs/evidence/release-readiness/1.0.1/`. No personal paths,
  player identifiers or raw logs are included in this summary.

| Gate | Candidate outcome |
| --- | --- |
| Original packages and baseline worlds | PASS preparation: original published protocol-2, pre-change 1.0 and protocol-1 binaries checksum/version checked; two original-package worlds created, reloaded and restored with untouched backups and manifests |
| Hazard regression | PASS: all ten supported hazards, immunity/equipment/crouch, fractional contacts, ordinary surfaces/unlit campfires, fallback/bound and exhausted search |
| End-to-end arrival | PASS: active, confirmed missing, configured, bead and necklace rejection preserves position/XP/inventory; fallback charges/consumes once; failed necklace returns nothing |
| Fresh automated suites | PASS: 16 JUnit and all 12 required GameTests, zero failures/skips; original-code comparison fails exactly the three new arrival tests and passes the original nine |
| Clean package equivalence | PASS: 91 production entries byte-identical to the freshly built instrumented counterpart; versions, resources, recipes/items, notices and language checked; no hooks shipped |
| Verifier negative cases | PASS: eight altered/missing/failed/stale package/evidence cases rejected |
| Generation-3 real client and six old/new rejection pairings | BLOCKED: three ForgeGradle attempts and a subsequent isolated launch using a functioning ordinary Forge installation timed out during client loading; bringing the test window to the foreground did not resolve the latter. An earlier ordinary-runtime launch failed due to a missing launcher argument, which was corrected. No passing connection or incompatible-pair result is asserted |
| Candidate upgrades of both baseline worlds | PASS automated state checks: both untouched-backup copies load with the candidate, match original catalogs/blocks/configuration and permissions, save and match after restart; separate original-JAR/backup rollback copies pass; a new candidate world has empty catalog/preferences. Real-client reconnect and gameplay checks remain manual |
| Four clients, 1,000 destinations, four continuous hours, restart | PASS preparation only: 1,000 destinations in all three dimensions plus twelve control fixtures seed and survive restart. Four-client workload helper compiles. Fourteen explicitly synthetic evaluator cases classify PASS/FAILED/INCOMPLETE correctly. Real four-client short run and four-hour workload are NOT RUN; no measured TPS/heap/endurance PASS is asserted |
| Ordinary clean-JAR single-player and dedicated smoke | NOT RUN: regression instrumentation and attempted packaged connections do not satisfy this gate |
| Final revision/source archive, release decision and handoff | Alpha review bundle preparation is available, with immutable snapshot, source rebuild, JAR and source checksums. Final Release decision/handoff remains pending the user's manual gates; no stable publication is prepared |

All spawned acceptance game/server processes were terminated; unrelated Java
processes and the original server instances were retained. All 222 reference
files in `Telepad1.19.2` and `Telepad26.3-code` retained their hashes.
The user has reserved manual/graphical checks; follow `release-checklist.md`.
The candidate helpers use new localhost instances and preserve untouched backups.
Fresh build/test inputs include test sources and acceptance scripts as well as
production sources; stale regression evidence is rejected. Automated world,
seed/restart, package and evaluator results are separate from real-client gates.
Retake the blocked and remaining checks using functioning real-client instances.
The user's ordinary Forge 66.0.9 client successfully loaded an existing world
with pre-change Telepads 1.0; this is baseline evidence, not candidate acceptance.
The isolated candidate client loaded Forge/Telepads and resource atlases, but
render-thread diagnostics showed it waiting in Minecraft's frame limiter before
the automation advanced or attempted a connection. The cause remains unresolved;
neither the ordinary installation nor its existing world was modified.
Any production/resource/metadata change requires package verification and the
affected acceptance checks for the new binary. See `development.md` for commands.

## Historical acceptance

Initial acceptance: 2 October 2026 (America/Santiago).
Acceptance for `26.3-7.1.0-dev` is at the end, under “Color crafting acceptance”.
The subsequent server-sharing implementation is recorded under
“Server sharing acceptance”, with its own reports and artifact.

The following sections summarize the port's historical acceptance. Raw evidence in
`docs/evidence/`, worlds, and local instances are excluded from Git because they
may contain machine paths and player data. References below describe local files,
not downloads available in the public repository. The build and JUnit tests can
be repeated with the project wrapper; historical integration scripts require
setting up the external instances specified here.

## Initial environment and artifact — 26.3-7.0.0-dev

Minecraft Java 26.3, Forge/MDK 26.3-66.0.9, Temurin 25.0.4.1+1 Windows x64,
Gradle 9.7.1, and ForgeGradle 7.0.17. Official MDK and JDK checksums were verified
before extraction. `gradlew.bat --version` confirms Java 25 for both Launcher
and Daemon. This selection does not change the machine's global Java installation.

Artifact: `build/libs/telepads-26.3-7.0.0-dev.jar`. This is a new adaptation without
automatic import from 1.19.2. It contains the GPL v3 license, attribution, MDK
license, and metadata restricted to Minecraft 26.3 / Forge 66.

## Tests and results

| Check | Result and coverage |
| --- | --- |
| `gradlew.bat clean build` with Java 25 | Clean build, JAR without test hooks, and ten passing JUnit tests |
| `-PtelepadsGameTests runGameTestServer` | Six required `telepads:*` GameTests, all passing |
| Optional graphical client | Naming, nine friends, period key, states, scrollable list, pages, and empty list |
| Two TCP clients with the packaged JAR | Friends, form/sharing, separate access, activation, travel, replay/fabrication, Nether, unloaded chunk, missing pad, and portable items |
| Server restart with both clients | Catalog, friends, shared users, missing state, identity, colors, and upgrades preserved |
| Server with the clean JAR | Dedicated startup, block saved/restored, and successful shutdown, without dependencies on client classes |
| 1.19.2 reference | 100 file SHA-256 hashes compared, zero differences |

JUnit covers the versioned catalog codec, indexes/duplicates, permissions, friends,
individual forgetting, world isolation, placement/token, a 60-tick wait,
changes/cancellation/expiry, XP balance and level priority, and parsing of negative
and inclusive ranges. The report is in `evidence/junit/`.

GameTests exercise real server APIs with embedded player connections: the
0.2-block platform and data, names/contexts, discovery and sharing,
fabricated/replayed requests and revoked access, logout and death events,
Forge cancellation, exact level and point costs, insufficient balance,
blocked/unsupported/out-of-border arrivals, missing pads and nonexistent
dimensions, a transmitter only at departure, redstone, public-access tools,
dyes and recovery, unique drops and components on replacement, beads/necklaces
with invalid candidates and a full inventory, the four recipes, the actual anvil
menu with totals of 2–8 and complete consumption, disabled conversion/nine pearls,
a living dragon, fixed destinations and ranges in the Nether, and a return to
normal mode after removing configuration.
Canonical local report: `evidence/gametest-report.xml`.

## Session with graphical clients and the packaged JAR

The server installed at `../.tools/integration-server/` uses localhost:25577.
Two independent Forge processes, `TelepadAlice` and `TelepadBob`, load the packaged
`telepads-acceptance.jar`, with the same production classes and resources as the
delivered artifact. Optional hooks exist only in that acceptance variant.
The hash comparison with the clean JAR is in `evidence/artifact-check.json`.
80 production code and resource files were compared, with no differences.
The clean JAR from that session is 218080 bytes and its SHA-256 is
`C0615427963393CA71E2CDADBBD6355CEAFBEA4E2040360468948313FBC039B1`.
To repeat the checks of the preserved package, reports, and reference, run
`./scripts/verify-artifact.ps1` from `Telepad26.3/`.

Both clients send the mod's normal packets and use its actual controls.
Alice adds Bob and confirms the form with sharing enabled. Bob keeps an empty
list and does not receive `AliceSecret`. Each initial trip charges exactly two
levels; a fabricated request and a replay do not add charges. Alice travels to
the Nether, returns to a destination whose chunk was confirmed unloaded before
the trip, and confirms the location of a missing platform. The necklace takes
the player to the nearest valid destination; the bead uses an authorized candidate
in the current dimension. Both consume one item; the necklace returns 1–2 string,
and both preserve XP.

Observed markers: `INTEGRATION_UNLOADED_PASS`, `INTEGRATION_SHARED_PASS`,
`INTEGRATION_TRAVEL_PASS`, `INTEGRATION_DIMENSION_PASS`, `INTEGRATION_FAR_PASS`,
`INTEGRATION_NECKLACE_PASS`, `INTEGRATION_BEAD_PASS`, `INTEGRATION_SERVER_PASS`, and,
after restarting, `INTEGRATION_RESTART_PASS`.
Logs: `evidence/integration-console.log` and `evidence/integration-restart-console.log`.

Screenshots were inspected at GUI scales 2 and 3, with 960x540 and 1280x720 windows.
The dyed platform shows upgrades, and all seven items load in inventory and in
hand. No Telepads parsing/model errors remain. Alice has the mod's particles
disabled; Bob keeps them enabled. All trips pass for both clients.
Selected screenshots: `evidence/platform.png`, `evidence/name.png`,
`evidence/friends.png`, `evidence/travel.png`, `evidence/scroll.png`, `evidence/empty.png`.

## Preserved tools and instances

- Portable JDK: `../.tools/jdk/jdk-25.0.4.1+1/`.
- Verified downloads: `../.tools/downloads/`.
- API sources consulted: `../.tools/forge-sources/`.
- Installed smoke-test server: `../.tools/test-server/`, localhost:25576.
- Multiplayer acceptance server: `../.tools/integration-server/`.
- Development client/instances: `run/client/`, `run/TelepadAlice/`, `run/TelepadBob/`.
- Dependencies: the user's normal Gradle cache; official assets in the local
  Minecraft installation's `assets/` directory.

The environment shows OSHI warnings about Windows performance counters and
Log4j warnings when inspecting Linux/BSD Netty native classes. Runs reach the
game, complete the checks, save, and exit successfully. Neither the Windows
registry nor Forge libraries were modified to silence these warnings.

The first GameTest attempt ran only Minecraft's built-in test; it is excluded
from the evidence. Explicit registration and selection were corrected.
A stale anvil result and late item-tint registration were also corrected,
and the affected checks were repeated.

No features from the four specifications remain pending. Validation is limited
to the listed versions and new worlds; it does not include migration from 1.19.2
or a guarantee of compatibility with other mods.
## Color crafting acceptance — 2026-10-03

`26.3-7.1.0-dev` / color protocol 2: 14 passing JUnit tests and 9 passing GameTests.
Actual crafting menus verify all sixteen dyes, Minecraft's blend, preserved
components and exact consumption through ordinary taking and shift-click.
Placement, update packets, saves, drops, partial/full overrides, washing,
inventory overflow and upgrade recovery pass.

Two real Forge clients verify default, uniform, mixed, legacy and partial palettes
in crafting previews, inventory and placed blocks, including reconnection and
dedicated-server restart. Local screenshots confirm that the turquoise motif
retains its color. Both old-client/new-server and new-client/old-server connections
reject `telepads:main` during configuration, before a player enters the world.

The clean production JAR contains five recipes and the custom dye serializer,
seven item definitions, all assets and notices, and no acceptance classes.
All 87 runtime files match the tested acceptance build. Evidence for this release
is local in `docs/evidence/dye-colors/`; reproduce it with `color-acceptance.ps1`
and `verify-color-artifact.ps1` as described in `development.md`.

The original port acceptance above is historical. The current publication logo
was subsequently replaced with a cropped in-game screenshot; that asset and
attribution change means the repackaged JAR has a new hash. Publication checks
and the current artifact hash are recorded in `PUBLISHING.md`.

## Server sharing acceptance — 2026-10-03

Local implementation of `share-telepads-with-server`, using the existing
`26.3-7.1.0-dev` development artifact name. Install this updated build on both
ends; this entry does not describe a new published release.

- `gradlew.bat test` and the final `clean build`: 16 JUnit tests, no failures.
  Publication grants access without adding friends to users, requires a
  registered actor, marks saved data dirty, survives codec reload, and preserves
  earlier private sharing and individual registration.
- `-PtelepadsGameTests runGameTestServer`: all 9 required tests pass. Naming
  checks survival, empty friends, unchecked sharing, canceled/replayed/invented
  sessions, invalid names, distance, dimension, expiry, actor access, missing
  blocks and replaced identities. Rejected requests never publish the pad.
- Real client at 1280×720 / GUI scale 3: `Share with server` is exact, initially
  unchecked and fits the form. Visually inspected `name.png`; the existing
  friends, scrolling, destination-state and empty-screen checks also pass.
- Two real TCP Forge clients and the packaged dedicated server: Alice confirms
  the naming form in survival after clearing her friends; Bob travels to
  `SharedHome` without friendship or individual registration. Public access and
  owner-only users are asserted, and an unrelated fresh UUID sees the destination.
  Private `AliceSecret` remains hidden. All eight existing integration stages pass.
- Dedicated-server restart with both clients: public access, original users,
  independent empty friend lists and future-identity visibility persist.
  A private fixture representing earlier friend sharing keeps both registered
  users and remains private. Physical identity, colors, toggler and missing state
  also survive.
- `verify-sharing-artifact.ps1`: all 87 production runtime files match the
  dedicated acceptance JAR; no test classes or test instances are shipped.
  Seven item definitions, five recipes, language JSONs and notices are verified.
  All 100 original reference files in `Telepad1.19.2` retain their SHA-256 hashes.

Local evidence: `docs/evidence/server-sharing/` contains JUnit XMLs, GameTest
report/log, naming screenshot/client log, dedicated initial/restart logs, both
client logs, build log and `artifact-check.json`. It is excluded from publication.
Reproduction uses the existing local setup described in `development.md`.

Production JAR: 330792 bytes; SHA-256
`E1BD5144A34C287742E91B334E924C3A6806354CC3299D8A0857E5CDC6976912`.

