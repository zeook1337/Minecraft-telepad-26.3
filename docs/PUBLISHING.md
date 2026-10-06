# Publishing on GitHub and CurseForge

## Current candidate — unpublished 1.0.1 Alpha

The current source builds `telepads-26.3-1.0.1.jar` for protocol 3. Stable-release
acceptance is paused; see `acceptance.md` for the exact package and outstanding
gates. Do not label it Release or present a stable download as already available.
The 1.0 and 7.x records below describe previous packages. Hosted uploads/status
changes and a final publication handoff remain pending.

A local Alpha handoff can be prepared with `scripts/prepare-release-handoff.ps1`.
It includes an immutable corresponding-source snapshot, a rebuild-verified source
ZIP, the exact JAR and checksums. See `development.md` and `release-checklist.md`.
This is a review bundle; Release eligibility and hosted delivery remain pending.

## Initial public version — 4 October 2026

Public version numbering starts at **1.0**, independently of Minecraft 26.3.
The current file is `build/libs/telepads-26.3-1.0.jar` and its internal version
is `1.0`. It was rebuilt from source; this is not just a filename change.
The 7.x-dev references, hashes, and sizes below are historical records.
The previous development tag does not represent this new binary: preserve
the exact 1.0 source when publishing it. Version numbering alone does not change
acceptance status; keep Alpha until evidence supports choosing another status.


Review: 3 October 2026. Scope: this `Telepad26.3` folder as a standalone project.
The review checks for data exposure in publishable files and the JAR; it is not
an exhaustive audit of the mod's vulnerabilities.

Public repository: [Minecraft-telepad-26.3](https://github.com/zeook1337/Minecraft-telepad-26.3).
Development download: [v26.3-7.1.0-dev](https://github.com/zeook1337/Minecraft-telepad-26.3/releases/tag/v26.3-7.1.0-dev).
Corresponding source: [tag v26.3-7.1.0-dev](https://github.com/zeook1337/Minecraft-telepad-26.3/tree/v26.3-7.1.0-dev).

## Review results and limits

No embedded credentials were found in the project's code or configuration.
The previous README and acceptance record included paths containing the machine's
username: these were removed from publishable documentation. Local logs and
evidence also contain paths; they are excluded along with instances, worlds,
caches, local configurations, keys, and environment files.

The existing and rebuilt production JARs were reviewed: no personal paths or
searched secret patterns were detected, and they contained no acceptance hooks.
Both included the license, credits, and Forge notices. The current delivery is
`26.3-7.1.0-dev`, with eight-dye mixing and network protocol 2. The JAR in
`build/libs/` was rebuilt with the new image and attribution.

There was no `.git` repository in this folder or the project root when the review
started. There was no local commit history to analyze. If another history is
later imported or files are added, review them before publishing. Pattern
searches cannot guarantee detection of every arbitrary secret.

**A `.gitignore` only excludes files from Git operations.** It does not clean up
a manually compressed folder, and you should not assume GitHub's web uploader
applies it. Local files remain on your disk. Publish through Git or use a source
archive created from the files selected by Git. It also does not hide information
in files that are already tracked.
[Reference: GitHub, ignoring files](https://docs.github.com/en/get-started/git-basics/ignoring-files).

Package names, author credits, versions, and synthetic test UUIDs are part of
the source. They are not credentials. The review does not require removing
these elements or the licenses.

## GitHub: prepare only this folder

Open a terminal inside `Telepad26.3`. The repository should directly contain
`build.gradle`, `src/`, `gradle/`, `README.md`, and the licenses. You do not need to
upload the parent folder, the 1.19.2 project, downloaded tools, or OpenSpec.

Before the first commit, configure a public name and the exact `noreply` address
GitHub shows under **Settings → Emails**, if you want to hide your email address.
The email is part of commit metadata; `.gitignore` does not hide it.
[Reference: commit email](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address).

```powershell
git init
# Configure your public identity and your account's actual noreply email here.
git add .
git status --short
git diff --cached --stat
git diff --cached
```

Review before committing: `run/`, `logs/`, `.gradle/`, `build/`, `docs/evidence/`,
`.env`, keys, and local server settings must not appear. The Gradle wrapper JAR,
source, resources, tests, scripts, and licenses **must appear**. The README uses
repository-relative paths.

For the initial publication, the repository is configured with branch `main`,
remote `origin`, and a GitHub `noreply` email. Subsequent updates should review
changes before creating a commit and pushing it to the same remote. Preserve
the source for each distributed binary under its corresponding tag.

To produce a source ZIP that exactly matches the commit:

```powershell
git archive --format=zip --output=../telepads-source.zip HEAD
```

That ZIP serves as a source archive; the main mod download on CurseForge will
be the **compiled JAR**.

## CurseForge: listing requirements

You need an account and must create the project from the
[author dashboard](https://authors.curseforge.com/#/projects/create/choose-game).
Select Minecraft and the **Mods** class. Fill in the name, summary, description,
license, categories, and avatar; then add the file and wait for moderation.
[Official creation guide](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

The listing must meet the following requirements:

- A distinctive English name, without version numbers or technical information.
  `Telepad` is already the original project's name; another unofficial port also
  exists. The README's working title does not guarantee name availability.
- Summary and description in English first. Spanish may follow.
- Explain features, installation, requirements, and differences from the original;
  write your own text and link to/credit the original author.
- An original square PNG avatar of 400 × 400 pixels. Do not reuse another project's
  image or a generic game logo as the new listing's avatar.
- A descriptive changelog for each file. Put promotional links, if any, at the
  end; do not replace the description with external download links.

[Moderation policies](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies)
and [submission guide](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips).

**Prepared avatar:** [curseforge-logo.png](media/curseforge-logo.png), a
400 × 400 PNG, is a crop centered on the telepad in the supplied screenshot.
It is also used at `src/main/resources/logo.png`, replacing the original logo.
The [full screenshot](media/telepad-in-game.png) is suitable for the gallery and
README. Both were exported without embedded metadata and without AI or scene
retouching. Provenance and cropping are recorded in `media/logo-provenance.json`.
**Pending:** choose and check the availability of the final listing name.

Actual screenshots of this port help show the block and its screens. Mods do not
require the additional gallery required for texture packs; for visual content,
also review the sample policy. Before using local screenshots, check them for
names, chat, or personal data. If a promotional image modified with AI could
misrepresent the game's content, CurseForge requires clear disclosure.
[Creation](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project),
[moderation](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies).

## License and original author

The [original project](https://www.curseforge.com/minecraft/mc-mods/telepad) lists
GPLv3, and the local repository preserves its text. This adaptation declares
`GPL-3.0-only`: preserve `LICENSE.md`, `NOTICE.md`, and `FORGE-LICENSE.txt`, and
select **GNU General Public License version 3 (GPLv3)** in the listing. Do not
change to MIT or “All Rights Reserved” without resolving the rights to reused
content.

When distributing the binary under GPLv3, you must provide the complete
corresponding source code. A practical approach for this delivery is to publish
this folder's source, create a tag for each JAR's exact commit, and identify that
tag or source archive alongside the download. Keep that version accessible;
linking only to a changing branch does not necessarily identify the binary's
source. Include the source, resources, and files required to build it.
[Official GNU FAQ](https://www.gnu.org/licenses/gpl-faq.en.html).

GPLv3 permits a port that complies with its conditions, but does not imply
endorsement by the original author or automatic CurseForge approval. The new
listing must explicitly present itself as an independent adaptation and retain
credits.

## File and tags for this delivery

| Field | Value applicable to the current project |
| --- | --- |
| Class | Mods |
| Suggested primary category | Player Transport |
| Suggested additional category | Technology |
| Minecraft | 26.3, subject to availability in the dashboard selector |
| Loader | Forge |
| Environment | Client & Server |
| Java | 25, stated in the description |
| Tested Forge | 66.0.9 |
| Main file | `build/libs/telepads-26.3-1.0.jar` |
| Suggested status for the current file | Alpha, because it is still marked as development |
| Dependencies on other projects | None required by the current build |
| License | GPLv3 |
| Changelog | Mod changes in `CHANGELOG.md` |

The target Forge version is listed on the
[official site](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.3.html).
Your author dashboard has not been accessed: confirm there that Minecraft 26.3
is available when uploading the file. If it is not, contact support; do not label
the JAR as compatible with another version. Do not mark Fabric or NeoForge either.
The original Telepad is a reference and credit, **not** a dependency to install.

Choose Alpha/Beta/Release according to actual stability. The documentation states
that at least one Release file is needed to sync the project with the app; do not
change the status to Release solely to obtain that visibility. Complete the
changelog, versions, and project relationships in each file's form.
[Official file guide](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

## Short English description ready to adapt

**Summary:** Craft named teleportation pads, share destinations with your server,
and travel between dimensions with configurable costs.

**Description:**

> This independent Forge adaptation brings Telepads to Minecraft Java 26.3.
> Place and name pads, stand on one to choose a known destination, and share
> access with everyone on your server. A Transmitter enables cross-dimension departures; a
> Toggler adds redstone control. Dyeable pads, portable teleport items, configurable
> experience costs, and administrator destinations are included.
>
> Color pads in a crafting table with one pad in the center and eight dyes around
> it (`DDD / DTD / DDD`). Minecraft's dye mixing colors the frame and base rim
> together while the turquoise rune keeps its color. Recrafting replaces the
> blend; washing recovers the latest mixture once while either part still uses it.
> The refreshed platform uses custom pixel textures and an inset Ender-style motif.
>
> Requires Minecraft Java 26.3, Forge 66.0.9, and Java 25. Install the same mod
> version on every client and the dedicated server. No additional mod is required.
> This is a development build intended for new worlds. It does not import legacy
> Telepads data. Version 26.3-7.1.0-dev uses protocol 2 and requires matching builds
> on both sides; previous protocol-1 builds cannot connect. Older palettes from
> this 26.3 port remain readable. Back up your world before updating; downgrading
> after saving mixed colors requires restoring the previous backup.
> The custom block model and rewritten interface differ from the original animated
> presentation.
>
> Original Telepad by Subaraki / AbsolemJackdaw:
> https://www.curseforge.com/minecraft/mc-mods/telepad
> This is an unofficial adaptation, distributed under GPLv3. See the accompanying
> source, license, and attribution notices.

For that description, use the
[source for this delivery](https://github.com/zeook1337/Minecraft-telepad-26.3/tree/v26.3-7.1.0-dev)
and the [support/issues link](https://github.com/zeook1337/Minecraft-telepad-26.3/issues).
Binary downloads from the CurseForge listing must go through CurseForge; the
GitHub link identifies the port's source and support.

## Final checklist

1. Publish the filtered source on GitHub, with a public identity and notices.
2. Build with Java 25: `./gradlew.bat clean build`, without test properties.
3. Check that the JAR includes `META-INF/mods.toml`, all three license/notice files,
   mod classes, and resources; and excludes hooks, logs, worlds, and local data.
4. Create and preserve the corresponding source tag.
5. Choose the final name and use `docs/media/curseforge-logo.png` as the avatar;
   add an English description, an actual screenshot, and credits.
6. Upload the JAR as a Mods file for Minecraft 26.3 / Forge, with a changelog and
   the appropriate stability status. Wait for review and address any feedback.

Do not upload the entire folder, a development-environment ZIP, a Forge installer,
or the instrumented acceptance JAR as the mod's main file.

## Archived acceptance for the current version

The archived dye change records 14 passing JUnit tests and 9 passing GameTests,
checks with actual menus, two compatible clients, reconnection and server restart,
and rejection of peers with different protocols. Results are summarized in
[acceptance.md](acceptance.md). Graphical acceptance is not repeated when updating
the documentation and logo. The publication checks below refer to the package
rebuilt with those presentation changes.

## Verification before server sharing — 3 October 2026

- Temporary Git inventory, without initializing a repository in this folder:
  122 publishable files, including the new resources, scripts, and media.
  No credential patterns or personal paths detected in those files.
- Logs, evidence, instances, worlds, and caches remain excluded. The Gradle
  wrapper, source, five recipes, resources, licenses, and media remain included.
- Independent copy containing only those files: successful `clean build` with
  Java 25 and `--no-build-cache`; all 14 JUnit tests passed. Dependencies were
  resolved using the existing cache. Graphical integration and GameTests were
  not repeated; the archived local report of nine GameTests with no failures
  was checked.
- JAR: 119 entries, seven item definitions, five recipes, the dye serializer,
  new logo, and notices. No acceptance hooks or sensitive-data patterns detected.
  Of the 87 production entries, only `logo.png` differs from the JAR before this
  update; gameplay classes and resources remain the same. Attribution was also
  updated in `META-INF/NOTICE.md`.
- Identical logo and avatar, 400 × 400 PNG; 1920 × 1080 screenshot. Media exported
  without embedded metadata. Relative links checked.

The `Telepad26.3-fuentes-publicables.zip` file, delivered alongside this folder,
was refreshed with the 122 filtered files. It replaces the previous port ZIP.
The ZIP is for publishing source; the JAR is the main CurseForge download.

JAR for this review: **330975 bytes**.
SHA-256: `D2DAC004E188CCBDA8113F81991E9EC9F645F0F21877453B0374DC41ACC2DB58`.
The hash will change if the project is subsequently modified and rebuilt.

## Local artifact with server-wide sharing — 3 October 2026

The `share-telepads-with-server` change was built and verified after the previous
package. It retains the development name `26.3-7.1.0-dev`; no new version has been
published. When distributing it, install this same build on client and server,
because the form now makes the pad public to everyone.

`clean build` passes with 16 JUnit tests. The 9 GameTests, graphical form, and
dedicated integration with two clients and a restart pass. The verifier
`scripts/verify-sharing-artifact.ps1` confirms 87 runtime files match the tested
package, with no acceptance hooks, and 100 historical files remain intact.
Details are in [acceptance.md](acceptance.md).

Local JAR: **330792 bytes**.
SHA-256: `E1BD5144A34C287742E91B334E924C3A6806354CC3299D8A0857E5CDC6976912`.
The `Telepad26.3-fuentes-publicables.zip` ZIP is refreshed with the 123 publishable
files selected by Git, including the new verifier and this documentation. It is
the current source delivery; it does not include caches, worlds, logs, or local
evidence.
