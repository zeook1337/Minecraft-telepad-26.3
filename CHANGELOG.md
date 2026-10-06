# Changelog

## 1.0.1 — unpublished Alpha candidate

- Reject the supported vanilla arrival hazards in all normal, missing,
  configured and portable travel, including fractional support contact.
  Preserve the bounded search and charge/consume only after successful arrival.
- Advance offered and exact accepted `telepads:main` compatibility to protocol 3.
  Update server and clients together; protocol-1/2 peers are incompatible.
  Persistent codecs, original private registrations and public access are retained.
- Preserve checksum-identified original packages and isolated baseline worlds.
  Back up the complete world, server configuration and old binary before upgrade;
  restore both world backup and its matching binary to roll back.
- Version numbering does not establish Release eligibility. Candidate-specific
  upgrade, packaged compatibility, ordinary installation and endurance gates
  remain pending; no stable release is announced here.

## 1.0 — 2026-10-04

- Start independent public version numbering at 1.0 for Minecraft 26.3.
  Artifact: `telepads-26.3-1.0.jar`; internal mod version: `1.0`.
  Earlier 7.x development numbers are historical, not the public release sequence.
- Include the in-game crafting recipe screenshot in the README and gameplay guide.

- **Changed placement sharing:** `Share with server` now publishes the pad for
  every player, including future connections, in survival and with no friends.
  Publication preserves existing registrations and does not copy the friends list.
- Unchecked or canceled naming stays private. Existing private pads shared with
  friends remain private with their saved registrations; existing public pads
  remain public. No save-format migration is required.
- Install the updated build on both client and server; normal travel rules and
  creative access-tool restrictions still apply.

## 26.3-7.1.0-dev — 2026-10-03

- Added `DDD / DTD / DDD` crafting: one center telepad and eight dyes, using
  Minecraft's shared blend on the frame and base rim. The turquoise motif is unchanged.
- Preserve names and other item components; recrafting replaces the old palette.
- Persist mixed RGBs and one bounded eight-dye receipt alongside legacy dye IDs.
- Washing returns the latest mixture once and current direct dyes; individual
  overrides clear only their part and discard the receipt after the last override.
- Advance the network protocol to 2; matching client/server builds are required.
- Add color codec, recipe, actual crafting-menu, placement and wash checks.
- Added custom 32×32 pixel textures: an Ender-inspired teleport ring and central
  rune, stone-metal frame, carved foundation, and dark underside.
- Replaced the raised center bars with an inset teleport surface. Frame and base
  rim retain their dye colors; the turquoise rune keeps its own color.
- Updated the in-mod logo and prepared a 400×400 project avatar from a supplied
  in-game screenshot, cropped and resized without AI retouching.
- Refreshed publication documentation and the filtered source archive for this build.

## 26.3-7.0.0-dev — 2026-10-02

Initial independent development port for Minecraft Java 26.3 and Forge 66.0.9.

- Reimplemented pad registration, player catalogs, naming, permissions, and friends.
- Added server-validated travel sessions, experience costs, safe arrivals, and
  saved locations for missing pads.
- Ported Transmitter and Toggler upgrades, colors, portable items, anvil conversion,
  public access tools, and administrator destinations.
- Added a simplified platform model and rewritten user interface.
- Retained credited item textures and legacy translations from the original mod.
- Added JUnit tests, optional GameTests, and local integration checks.
- Preserved GPLv3, original-author attribution, and Forge scaffolding notices.

This development version does not import legacy worlds or reproduce the original
animated renderer. It requires the mod on both the client and server.
