# Changelog

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
