# Development

`clean build` and the JUnit tests work with this folder as a standalone project.
The PowerShell smoke/integration and resource-generation scripts document the
original environment: they require server instances, tools, or the old reference
outside the repository. They are not needed to compile the resources already
included. `docs/evidence/` contains local evidence excluded from Git; it is not
published with the source.

## Catalog and identity

`TelepadCatalog` uses the Overworld's `SavedDataStorage` and the identifier
`telepads:catalog`. The format is saved with `version: 1`, world entries, and
preferences keyed by player UUID. Indexes are rebuilt when reading: by UUID and
by namespaced dimension and coordinates. Duplicate names are valid; two entries
at the same location are rejected during reading.

Each placement creates a new identity, even if a telepad previously occupied
that location. The new entry replaces the previous one in both indexes.
Destruction keeps the entry as missing. Unloading a chunk does not mark it as
missing: `BlockEntity.preRemoveSideEffects` is used, which only receives block
removal. Previously forgotten identifiers do not hide the new entry.

Entries and preferences are immutable. Catalog operations immediately mark
`SavedData` as dirty; no mutable collections are exposed. Friends and forgotten
destinations belong to a player in a world, independently of the player entity
or its cloning on death.

## Initial naming

Placement immediately registers the actor with the name `Telepad`. The server
creates a 30-second naming context tied to the player, identity, and location.
Confirmation checks dimension, distance, actual block, identity, and user.
Confirming consumes the context; closing it preserves the default name.
Confirming sharing enables `publicAccess` through `TelepadCatalog.share`, after
those validations and without copying friends into `users`. Creative mode is
not required. Existing registrations and catalog format 1 are preserved.
Network requests carry only intent, token, a name limited to 16 characters,
and the sharing option. Friend UUIDs are resolved from server data.

## Release baseline preparation

Before changing production code, preserve the original published protocol-2
`26.3-7.1.0-dev` package, the pre-change `1.0` Alpha package and the available
protocol-1 package outside publishable sources. Do not rebuild a baseline from
candidate sources. The published package's SHA-256 is
`D2DAC004E188CCBDA8113F81991E9EC9F645F0F21877453B0374DC41ACC2DB58`.
The preserved pre-change `1.0` package is
`196FF5862052BE5C21306AE89171FDCD162FC9611AC329D5034BAF7B1B698892`;
the protocol-1 reference is
`002220BDDF97FCF45E63BC642960B60AE466C5AFAB89159695A18539A494E0B5`.

From this project in PowerShell 7, with the local Java 25 environment selected:

```powershell
.\scripts\preserve-release-baselines.ps1 `
  -PublishedJar ..\.tools\release-baselines\telepads-26.3-7.1.0-dev.jar `
  -AlphaJar ..\.tools\release-baselines\telepads-26.3-1.0.jar `
  -ProtocolOneJar ..\.tools\release-baselines\telepads-protocol-1.jar `
  -BaselineDirectory ..\.tools\release-baselines
.\gradlew.bat -PtelepadsFixture compileJava
.\scripts\release-baseline-fixtures.ps1 `
  -JavaHome ..\.tools\jdk\jdk-25.0.4.1+1 `
  -ForgeDirectory ..\.tools\test-server `
  -BaselineDirectory ..\.tools\release-baselines `
  -InstanceRoot ..\.tools\release-baseline-worlds-v3
```

Creation requires a new instance path and refuses to overwrite worlds. The
fixture helper is packaged as a separate local mod; the original Telepads JAR
is unchanged. Never use `telepadsFixture` when building a distribution.
Each world contains eight entries across all vanilla dimensions: owner/friend
registrations, a public pad only for Alpha, a forgotten missing pad, friends,
direct and mixed palettes, a partial override with its bounded wash receipt,
both upgrades and a configured destination with its saved server configuration.
`expected-state.json` records exact generated identities and persisted state;
unordered UUID sets are compared without depending on iteration order.

The helper creates the world with the original package, stops and saves, copies
it to `<version>-untouched-backup/`, then checks the original-package reload
against the manifest. `<version>-backup-hashes.json` verifies every backup file.
`-Mode verify` repeats original-package loading; `-Mode restore` creates a new
restored instance from the untouched world backup and matching original package,
then compares state. Both modes use the same path arguments above. Restore the
world's `serverconfig` along with the world. Never attempt an in-place downgrade
of a candidate-saved world or overwrite the backups.

The two original-package create/reload runs passed locally on 5 October 2026
(America/Santiago). Baselines and raw logs remain local under `.tools/` and
`docs/evidence/`; these results do not satisfy candidate-upgrade acceptance.
The baseline manifest also records all 222 files in `Telepad1.19.2` and
`Telepad26.3-code` so their unchanged hashes can be verified after implementation.

## Running the regression suites

With Java 25 selected as described in the README, from `Telepad26.3/`:

```powershell
.\gradlew.bat build
.\gradlew.bat -PtelepadsGameTests runGameTestServer
```

GameTests are included in the source set only with `-PtelepadsGameTests`.
Build the distribution JAR without that property.
The historical smoke test used the isolated instance `../.tools/test-server/`, bound to
localhost on port 25576. It preserves its test world and checks a block's name
and identity after shutting down and restarting the server.

## Travel and protocols

`ActivationSessions` keeps the wait and session by player/departure UUID. It reads
the current wait each tick, opens once, and expires the token after 1200 ticks.
Leaving, changing departure pad, canceling, dying, changing dimensions, and logging
out clear temporary state; they never delete persistent preferences.

The `telepads:main` channel, protocol 3, offers and accepts exactly generation 3
and defines explicit directions and codecs.
Requests run on the server's main thread. Names: 16 characters; friends: nine;
destination responses: 64 rows per page, without truncating the full catalog.
The client requests pages/dimensions by index and actions by UUID, without
providing coordinates, permissions, prices, or inventory.

Before travel, the token, physical departure pad and its identity, current rules,
destination access, and inclusion in the offered destinations are checked.
The session is consumed before attempting movement. The chunk is prepared and
the block/identity/redstone are revalidated. The search tests at most 441 positions
within a horizontal radius of three and a vertical radius of four: support,
standing space, absence of liquids, height, world border and the supported
vanilla hazards listed in `gameplay.md`. The body and the thin support contact
slice are inspected at the prospective fractional standing height; a telepad
can isolate feet from magma below while a lit campfire's surface is rejected.
`getChunk` does not install permanent chunk tickets belonging to the mod.

The MDK's `ServerPlayer.teleportTo` calls Forge's cancelable event, including for
travel within a dimension. Only success allows XP charging or portable-item
consumption. Available points are calculated from the level and progress bar;
`totalExperience` is not trusted. Levels take priority over points.

Configured destinations and portable items use the same arrival service, with
an explicit zero cost. Configured travel needs no receiver/transmitter; portable
travel requires an actual authorized block in the current dimension. The necklace
sorts by spatial distance; the bead chooses uniformly among valid candidates.
Dyes/drops use the registered `telepads:colors` component; upgrades are returned
separately.

Item colors are registered during `RegisterColorHandlersEvent.Block`, before
models load. Doing this during client setup could race with resource parsing.
Late page responses do not reopen a closed activation.

## Crafting colors

`telepads:colors` retains `frame` and `base` in `[-1,15]` and its two-ID constructor.
It adds optional 24-bit `frame_rgb` and `base_rgb`, and `craft_dyes`, an immutable
list that is empty or contains exactly eight IDs in `[0,15]`. Older data without
these fields is read as before. An RGB requires ID `-1` for that part and an
eight-dye receipt; if both RGBs exist, they are equal. The helper returns opaque
ARGB. The same complete instance passes through implicit components, placement,
drops, saving, and block-update packets.

The special `telepads:telepad_dye` recipe requires a 3×3 grid, a central telepad,
and eight slots with `DataComponents.DYE`. It copies one item from the center
and replaces only its colors. `DyedItemColor.applyDyes(null, dyes)` mixes one
sample per slot, without incorporating previous colors. The vanilla menu consumes
ingredients and handles shift-click; there are no crafting remainders and no
fixed-result recipe in the recipe book.

The first direct dye replaces the frame and the second replaces the rim; the
shared receipt disappears when no RGB remains. Washing returns that receipt
once, plus the current direct dye IDs, and clears colors before handing out items.
The history never accumulates. Upgrades remain separate.

The color stream, introduced in historical protocol 2 and retained in protocol 3,
carries two ID bytes, two optional-RGB flags with 32-bit
integers, and a receipt size restricted to 0 or 8 before reading its bytes.
Install candidate 1.0.1 on the server and every client together. Earlier
protocol-1/2 builds are excluded during configuration; save codecs and sharing
behavior are unchanged. The published `26.3-7.1.0-dev` and pre-change `1.0` are
upgrade baselines, not compatible peers. To roll back, restore the matching old
binary and complete untouched world backup, including server configuration.

## Historical artifact acceptance workflows

The commands and results in the following historical sections describe their
original source versions and local instances. Use the candidate helpers below
for 1.0.1; historical logs do not satisfy its gates.

```powershell
.\gradlew.bat -PtelepadsGameTests -PtelepadsClientSmoke runClient
.\scripts\integration-smoke.ps1
.\scripts\integration-smoke.ps1 -Restart
.\gradlew.bat clean build
```

Integration packages an instrumented variant at
`../.tools/integration-server/mods/telepads-acceptance.jar`. Two graphical Forge
processes load that JAR through `telepadsPackagedJar`, skipping compilation and
resource copying during launch. The installed server loads the same JAR from
`mods/`. The hooks in `src/gametest/` prepare data, press controls, and check
observable results; they are enabled only with opt-in properties. The test server
is bound to localhost:25577 and uses offline test players. The final `clean build`
excludes all those classes and data from the delivered JAR. Hashes of production
classes/resources are compared between both artifacts.

The script can be repeated on its own test world. `-Restart` verifies existing
data after restarting and requires the initial run to have completed first.
Acceptance logs are preserved in `docs/evidence/` before cleaning `build/`.

For historical `26.3-7.1.0-dev` colors, the scripts in the corresponding tagged
source used `scripts/color-acceptance.ps1 -Mode initial` to create
an isolated world in `../.tools/color-server/`, runs two clients, and reconnects
them. `-Mode restart` checks that world after restarting; it preserves fixtures
for default, uniform red, mixed red/blue, two legacy IDs, and a blend with a partial
override. Both clients verify components in previews, inventory, and blocks, and
save screenshots. `-Mode old-client` and `-Mode old-server` use the previous
protocol-1 instrumented JAR, preserved locally at
`../.tools/telepads-protocol-1-acceptance.jar`, and a separate handshake world.
These scripts require the local Forge installation used for the port's acceptance.
JAR launches disable `net.minecraftforge.gradle.merge-source-sets` to prevent
locally compiled classes from mixing with the selected version.
`scripts/verify-color-artifact.ps1` compares the production runtime with the tested
instrumented JAR, checks all four modes, and excludes all acceptance classes.
Evidence is saved in `docs/evidence/dye-colors/`.

## Candidate acceptance and package verification

Manual and graphical gates are assigned to the user for this continuation.
Use [the pending checklist](release-checklist.md); implementation or a short
instrumented run does not mark those gates complete. The candidate remains Alpha.

Candidate 1.0.1 remains Alpha until every gate in `docs/acceptance.md` passes.
Use PowerShell 7 and explicit local Java 25, Forge 66.0.9, original-package,
candidate and evidence paths. Copy the clean candidate outside `build/` before
running helpers that clean/rebuild. Keep one frozen binary throughout a run.
The paths below describe the isolated local setup; in another environment,
replace them with the corresponding prepared paths.

```powershell
$javaHome = '..\.tools\jdk\jdk-25.0.4.1+1'
$candidate = '..\.tools\release-candidates\1.0.1\telepads-26.3-1.0.1.jar'
$evidence = 'docs\evidence\release-readiness\1.0.1\regressions'
.\scripts\release-regressions.ps1 -JavaHome $javaHome `
  -CandidateJar $candidate -EvidenceDirectory $evidence
.\scripts\verify-release-artifact.ps1 `
  -CandidateJar $candidate -EvidenceDirectory $evidence
.\scripts\test-release-verifier.ps1 -CandidateJar $candidate `
  -EvidenceDirectory $evidence `
  -ScratchDirectory ..\.tools\release-verifier-negativecases-new
```

These quick regression/package commands passed for the recorded candidate.
The regression helper disables build-cache restoration and reruns tasks, writes
fresh JUnit/GameTest XMLs and an instrumented counterpart, and binds each to
the clean candidate SHA-256, base Git revision and current input fingerprint.
Cached reports or old console markers are insufficient. Serialize Gradle
launches in this workspace: packaged launches change source-set metadata.
ForgeGradle's merged output also needs fresh compile/resource tasks when
switching instrumentation; otherwise cached resources can remove classes.
Normal compilation still uses the standalone wrapper; these constraints concern
the acceptance workflow.

The verifier requires every current JUnit suite and every `telepads:*` test
instance, with no failures or skipped tests. It checks all 91 production runtime
entries, manifests, notices, source resources, seven item definitions, five
recipes, language values and absence of GameTests/fixture hooks. The negative
checks cover altered runtime, shipped hooks, failed/missing suites, a missing
hazard test, a failed run, stale sources and a different candidate.
`RELEASE_PACKAGE_PASS` establishes package/regression acceptance only. The
verifier explicitly retains Alpha; it cannot assert world upgrades, ordinary
installation, scale/endurance or corresponding-source gates it did not run.

The packaged compatibility helper is implemented but its local matching-client
run is blocked by client startup. The following is a reproduction command, not
a passing compatibility record:

```powershell
$env:JAVA_HOME = (Resolve-Path $javaHome).Path
.\gradlew.bat -PtelepadsFixture compileJava --no-build-cache --rerun-tasks
.\scripts\release-baseline-fixtures.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server `
  -BaselineDirectory ..\.tools\release-baselines `
  -InstanceRoot ..\.tools\release-peer-helper-new -BuildHelperOnly
.\scripts\release-compatibility.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server -CandidateJar $candidate `
  -BaselineDirectory ..\.tools\release-baselines `
  -HelperJar ..\.tools\release-peer-helper-new\telepads-fixture-helper.jar `
  -InstanceRoot ..\.tools\release-compatibility-new `
  -EvidenceDirectory docs\evidence\release-readiness\1.0.1\compatibility-new `
  -Mode all
```

`-Mode matching` runs the generation-3 pair only; `all` additionally requires
both directions against each preserved original protocol-1/2 package. No old
package is rebuilt or altered. Worlds bind only to localhost:25580. Each run
uses a new instance directory and an independent helper mod; timeouts are
FAILED, never PASS. A unique JVM run identifier, private Gradle daemon and
bounded termination limit cleanup to the processes launched for that run.
Three matching attempts timed out on 5 October 2026 while the client was loading;
the previous server installations and worlds were not changed, and all launched
game/server processes were stopped. This requires resolution before compatibility
or multiplayer acceptance can be completed.

An optional `-InstalledClientConfiguration <local-json>` launches the same clean
packages through an installed Forge bootstrap instead of ForgeGradle. Export the
runtime from a running ordinary Minecraft 26.3 / Forge 66.0.9 client using its
Java process ID (not the launcher process ID):

```powershell
.\scripts\export-installed-forge-runtime.ps1 -ClientProcessId <java-process-id> `
  -OutputFile ..\.tools\installed-forge-client-runtime-new.json
```

Pass that JSON to `release-compatibility.ps1` with a fresh instance/evidence root.
The exporter stores only the Java executable, classpath, native/library/assets
directories and asset index; it does not store account credentials or the user's
game directory. The acceptance launcher creates a new mods directory containing
only the selected Telepads package and external helper, uses a synthetic offline
test identity and preserves the running user's installation and worlds. Its
evidence identifies the launcher, configuration, runtime and library checksums.
Cleanup recognizes only this run's nonce-bearing Java/Javaw processes.
The installed-runtime candidate run also timed out before connection, including
after bringing its window to the foreground. A successful original 1.0 session
in the user's installation does not establish candidate compatibility. The
frame-limiter wait observed in diagnostics remains an unresolved startup blocker.

Raw JARs, XMLs, screenshots, logs, player data and instances stay under ignored
`docs/evidence/release-readiness/<candidate>/` or local `.tools/`, outside source
archives. Publish only a sanitized summary: candidate version and SHA-256,
immutable source revision/archive when available, tested versions, environment,
each gate's PASS/FAILED/NOT RUN status, unresolved blockers and the Alpha/Release
decision. Exclude personal paths, UUIDs, credentials and raw logs. See
`docs/acceptance.md` for the current record. A dirty source tree must be identified
as such; a base commit plus fingerprint is not a finalized corresponding-source
publication handoff.

### Candidate world upgrades

Build the separate fixture helper first, preserve it outside `build/`, and use
fresh instance/evidence paths. This loads copies of **untouched backups**, not
the original fixture instances. Both candidate worlds are saved and restarted;
rollback creates another copy with the matching original JAR. A separate new
world verifies empty catalogs/preferences. The helper compares catalog codecs,
physical blocks and configuration against the original manifests, including
private/future-user authorization and bounded wash receipts. Real-player
reconnection and actual washing remain separate manual checks.

```powershell
$run = Get-Date -Format 'yyyyMMdd-HHmmss'
.\gradlew.bat -PtelepadsFixture compileJava --no-build-cache --rerun-tasks
.\scripts\release-baseline-fixtures.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server `
  -BaselineDirectory ..\.tools\release-baselines `
  -InstanceRoot "..\.tools\release-helper-$run" -BuildHelperOnly
$helper = "..\.tools\release-helper-$run\telepads-fixture-helper.jar"
.\scripts\release-world-upgrades.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server -CandidateJar $candidate `
  -BaselineDirectory ..\.tools\release-baselines `
  -BaselineWorldDirectory ..\.tools\release-baseline-worlds-v3 `
  -HelperJar $helper -InstanceRoot "..\.tools\release-upgrades-$run" `
  -EvidenceDirectory "docs\evidence\release-readiness\1.0.1\upgrades-$run"
```

The seven individual outcomes, original/candidate hashes, source fingerprint,
Java/Forge/Minecraft environment and log hashes are in `world-upgrades.json`.
The server binds to localhost:25581; shutdown/termination affects only the
process created by the helper. Hashes and file counts of both untouched backups
are checked before and after loading. Never run parallel helpers on the same port.

### Four-client workload and metrics

`release-scale.ps1` prepares 1,000 public destinations evenly distributed among
the Overworld, Nether and End, plus twelve origin/private/hazard fixtures. Four
real Forge clients use the clean candidate with a separate local helper. A fixed
eight-step cycle exercises Nether and End arrivals with exact two-level cost and
token replay, unsafe missing destinations, an unoffered identity, necklace, bead,
an actually unloaded Overworld chunk, and a reconnect for each actor. Clients
walk real paginated `TravelView` responses and send the normal mod requests.
The server checks permissions, catalog equality, positions, XP, consumption and
necklace recovery; final restart verifies catalog and physical identities/state.

The helper opens valid activation sessions to control timing and suppresses
unscheduled automatic activation. It does not benchmark the waiting timer or
manual UI clicking. The fixture uses fixed costs and disables the live-dragon
travel restriction; GameTests and ordinary-installation checks cover those rules.
Bead choice and naturally timed chunk unloading/GC retain their runtime behavior.

```powershell
# Seeding and restart only; never asserts multiplayer/endurance acceptance.
.\scripts\release-scale.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server -CandidateJar $candidate `
  -HelperJar $helper -InstanceRoot "..\.tools\release-seed-$run" `
  -EvidenceDirectory "docs\evidence\release-readiness\1.0.1\seed-$run" `
  -PrepareOnly

# Four installed Forge clients in new isolated directories.
.\scripts\release-scale.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server -CandidateJar $candidate `
  -HelperJar $helper `
  -InstalledClientConfiguration ..\.tools\installed-forge-client-runtime-exported.json `
  -InstanceRoot "..\.tools\release-scale-$run" `
  -EvidenceDirectory "docs\evidence\release-readiness\1.0.1\scale-$run" `
  -Mode quick
```

Use different new paths and `-Mode endurance` after a successful short run.
`quick` defaults to 60 seconds warm-up and 180 measured seconds; `endurance`
requires 600 seconds warm-up and 14,400 continuous measured seconds. Outstanding
operations finish before saving. Four client heaps default to 1 GiB each and
the server to 2 GiB (`-ServerHeap`); CPU/core count, physical memory, OS, actual
Java versions, helper/runtime/library hashes and source/JAR identity are recorded.
Only the four processes launched by the automatic helper are terminated. With
`-ManualClients`, user-owned clients are neither configured nor terminated.

The JVM records wall-clock cumulative tick samples about every five seconds and
the last **naturally observed** GC event per collector. Post-GC heap uses the
event's heap-pool values from `getLastGcInfo`, not current allocated memory or
forced `System.gc()`. The evaluator interpolates cumulative ticks at each exact
five-minute boundary, requires >=19 TPS for every full window, and compares
first/last-hour medians for each collector with >=5 unique samples in each hour.
Every operation family must occur in each full hour. Ordinary four-client
population is required, allowing only one recorded scheduled reconnect at a
time; missing/gapped samples, insufficient duration/load/GC or missing restart
are INCOMPLETE. A correctness, identity, TPS or memory violation is FAILED.

`scale-identity.json` binds raw evidence hashes; `scale-workload.json` retains
samples, operations and login counts; `endurance-evaluation.json` contains all
five-minute windows, hourly workload counts, heap comparisons and blockers.
A short/seed-only run cannot qualify. The classifier retains Alpha even for an
endurance PASS because other release gates are independent. Do not infer PASS
from process exit status. A failed gate needs its regression and a new qualifying
run after correction.

```powershell
.\scripts\evaluate-release-endurance.ps1 -CandidateJar $candidate `
  -EvidenceDirectory <completed-run-evidence>
.\scripts\test-release-endurance-evaluator.ps1 -CandidateJar $candidate `
  -ScratchDirectory "..\.tools\release-endurance-evaluator-$run"
```

The fourteen evaluator tests use explicitly labeled synthetic telemetry. They
verify classification of early exits, small catalogs/client counts, absent GC,
slow TPS, growing heap, invariant failures, lost clients, sample gaps, missing
operations/restart, wrong candidates and altered evidence. Their synthetic PASS
is test coverage, never a real four-hour result. The default evaluator rejects
synthetic records.

### Reviewable candidate source and handoff

After package verification, prepare an **Alpha** handoff while manual gates are
pending. Select only files allowed by Git, preserving complete source, scripts,
tests, Gradle wrapper and notices. A temporary independent index creates a Git
snapshot commit and a retained `refs/telepads/candidates/...` reference. The
ordinary branch and staging index are left intact. The archive builds that exact
snapshot and compares every rebuilt JAR entry with the frozen candidate.

```powershell
.\scripts\prepare-release-handoff.ps1 -JavaHome $javaHome `
  -CandidateJar $candidate -RegressionEvidenceDirectory $evidence `
  -OutputDirectory "..\.tools\release-handoff-$run" -VerifySourceBuild
```

`handoff.json`, `HANDOFF.md`, the JAR, corresponding-source ZIP and
`SHA256SUMS.txt` identify the unpublished candidate and outstanding gates.
Raw evidence, worlds, installed-runtime JSON and rebuild output are excluded
from the source ZIP. `source-build-check/` and `source-build.log` in the local
handoff directory are verification scratch, not publication assets. Update the
sanitized acceptance record and regenerate the snapshot/archive after manual
results or further edits; a pending Alpha handoff does not complete the final
Release decision. No uploads or hosted status changes occur.

## Historical sharing acceptance

The English form displays `Share with server`, initially unchecked.
`ClientSmoke` checks text, state, and bounds, and saves an actual screenshot.
`IntegrationServer` checks publication through the naming packet in survival,
Bob's travel without friendship or registration, and visibility to a new
identity. Restarting preserves public access and previous private registrations.

```powershell
.\gradlew.bat test
.\gradlew.bat -PtelepadsGameTests runGameTestServer
.\gradlew.bat -PtelepadsGameTests -PtelepadsClientSmoke runClient
.\scripts\integration-smoke.ps1
.\scripts\integration-smoke.ps1 -Restart
.\gradlew.bat clean build
.\scripts\verify-sharing-artifact.ps1
```

Before cleaning, preserve `run/gameTestServer/report.xml`, the client log, its
`*name.png` screenshot, and integration logs in the local
`docs/evidence/server-sharing/` folder, using the names specified by the verifier.
Save both clients' logs after each run, because they are overwritten.
The verifier compares the clean JAR's runtime with the tested dedicated-server
JAR, rejects acceptance hooks, and verifies markers, languages, and the 1.19.2
reference. These results are kept separate from historical color acceptance.
