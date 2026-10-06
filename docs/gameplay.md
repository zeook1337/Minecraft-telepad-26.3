# Gameplay guide

Manual for the independent adaptation for Minecraft Java 26.3 and Forge 66.0.9.

## Naming, access, and friends

When you place a telepad, it is immediately registered to you with the name
`Telepad`. The form lets you change it to a name of up to 16 characters. Closing
without confirming keeps the default name. To confirm, you must remain near the
same block and submit within 30 seconds.

New telepads are private. To discover another player's pad, visit it and interact
while sneaking with an empty main hand. Repeating the interaction removes only
your registration. A public telepad reports that it does not allow this individual
change.

The default **period (`.`)** key opens the friends list; you can rebind it in
Minecraft's controls. Enter the name of an online player and use **Add**. You can
have up to nine distinct friends; **Remove** removes one and **Clear list** empties
your own list. The server resolves each player's identity by UUID.
In the initial form, confirming **Share with server** makes the destination public
to everyone, including players who join later, without friendship or prior
registration. This works in survival and with an empty friends list, subject to
travel rules. The checkbox starts unchecked; confirming without sharing or
canceling keeps the new telepad private. Changing friends does not change telepad
permissions. Private destinations shared before this update keep their
registrations and remain private; public destinations remain public after saving
and restarting. Install this same build on both client and server so the label
matches the scope of sharing.

The catalog, permissions, friends, and forgotten destinations are saved per world
and UUID. Changing dimensions, reconnecting, or dying neither transfers them to
another world nor deletes them. Breaking a telepad leaves a missing destination;
placing it again creates a new identity.

## Installation and your first trip

Install Minecraft Java **26.3** and Forge **66.0.9**, with Java **25**. Copy the JAR
from `build/libs/` to `mods/` on every client and the dedicated server. Use the
same mod version on both sides. Create a new world; automatic import of Telepads
1.19.2 worlds or data is not provided.
If the server uses a whitelist, add each player from its console with
`whitelist add PlayerName` before connecting.

1. Obtain two telepads using the recipe below or from the Telepads creative tab.
   Place the first, confirm `Home`, and place the second farther away, confirming
   `Mine`.
2. Stand on `Home` for three seconds. Choose `Mine`; from `Mine`, wait and choose
   `Home` to return. The mouse wheel scrolls the rows; the buttons switch
   dimensions or pages. Duplicate names represent distinct destinations.
3. Canceling releases the activation. Leaving, switching platforms, dying, or
   disconnecting also resets the wait. Sessions expire after 60 seconds; stepping
   off and back on lets you open another selection.

The default costs are zero. `xpLevels` takes priority over `xpPoints` if both are
positive. All available experience, including progress between levels, is checked;
the cost is charged once after a successful trip. A blocked or inaccessible
destination, insufficient arrival space, or a Forge-canceled trip preserves your
experience.

A destroyed destination appears as **Missing**. Opening it offers **Travel to saved
location** or **Forget for me**. Confirming uses the location saved by the server
and the usual cost; forgetting only hides it for you. Travel requires an existing
dimension and an arrival point with support, space, and valid boundaries.

All travel flows, including missing and configured destinations, Ender Beads and
necklaces, reject normal/soul fire, lava, magma blocks, cactus, lit normal/soul
campfires, sweet berry bushes, Wither roses and powder snow intersecting the
standing body or its support contact. This policy applies even with immunity,
protective equipment or crouching. Unlit campfires and ordinary terrain remain
usable when collision, support and boundary checks pass. The bounded search
checks at most 441 positions, within three horizontal and four vertical blocks,
and never builds a platform. A safe alternative charges/consumes once; exhaustion
preserves position, XP and items and gives no necklace recovery.
This is the supported vanilla hazard list, not a guarantee for third-party
damaging blocks or combinations with other mods.

Candidate 1.0.1 uses network protocol 3. Update the server and all clients
together: protocol-1/2 builds, including historical `26.3-7.1.0-dev` and Alpha
`1.0`, cannot join. Back up the world, its `serverconfig` and original JAR before
upgrading; restore that entire backup with its matching JAR to roll back. Earlier
private registrations remain private, and public pads retain public access.
Stable-release and candidate world-upgrade acceptance remain pending.

## Recipes and items

Telepad recipe in Minecraft:

![Telepad crafting recipe in Minecraft](media/telepad-recipe.png)

The other recipes retain the main ingredients from the reference version:

| Item | Pattern / ingredients |
| --- | --- |
| Transmitter | `III / RDR / III`: I iron ingot, R redstone, D diamond |
| Toggler | `RBR / DDD`: R repeater, B redstone block, D redstone |
| Necklace | Shapeless: two Ender Beads and three string |
| Dye Telepad | `DDD / DTD / DDD`: T telepad, D any dye; requires eight |

In a crafting table, put a telepad in the center and a dye in each of the eight
surrounding slots. All 16 dyes are accepted, repeated or mixed. The preview shows
a telepad whose frame and base rim share Minecraft's blend; the turquoise ring
and rune retain their color. Each result consumes one telepad and one dye from
each slot, preserves the item's name and other data, and supports shift-click.
Stack sizes do not affect the blend. Repeating the recipe replaces the colors
using only the eight new dyes. A 2×2 grid, an incomplete ring, or an off-center
telepad does not work.

With `enableAnvilConversion=true`, put Ender Pearls in **both** anvil inputs.
The total must be between two and eight: this produces twice as many Ender Beads,
consumes both entire stacks, and charges as many levels as there are pearls.
For example, two plus two pearls produce eight beads and cost four levels.
Nine pearls or a disabled option produce no result.

Using a bead randomly chooses a usable telepad in the current dimension. The
necklace chooses the nearest by spatial distance. Both require access, a present
and active pad, and a safe arrival, without standing on a platform or paying XP.
In survival, a successful trip consumes one item; the necklace returns one or
two string to your inventory, or drops them if there is no room. Failure preserves
the item. The server can disable each item separately.

## Upgrades and customization

Right-click with a **Transmitter** to install the upgrade once, consuming one in
survival. It lets you choose destinations in other dimensions from that departure
pad; the receiving pad does not need another transmitter. Applying a **Toggler**
makes a redstone signal disable the telepad as both a departure and a destination;
removing the signal reactivates it. Signals do not affect platforms without a
toggler.

The **Public Access Tool**, available in creative or through commands, toggles
public/private access. It is restricted to creative players or operators of level
2 or higher. Switching back to private preserves all previous registrations.

By default, the server blocks departures from End platforms while the dragon
is alive. `blockEndWhileDragonAlive=false` disables this rule; it also applies
to administrator destinations and is checked when confirming travel.

Applying a dye directly to the block colors the frame first and then the base
rim; subsequent applications replace the rim color. Each change consumes one dye
in survival. On a telepad crafted with a blend, the first application preserves
the rim's blend and the second replaces it.

A water bucket resets both colors and returns the eight dyes from the latest
crafting operation once, while either part retains the blend, plus the currently
applied direct dyes. For example, four red and four blue dyes return those eight;
after one direct lime application, that dye is also returned. If both parts have
been directly replaced, only the two current direct dyes are returned. Recrafting
replaces the previous receipt; washing an uncolored telepad again creates no dyes.
In survival, washing leaves an empty bucket; in creative, it keeps the water
bucket. Dyes that do not fit are dropped, and installed upgrades remain.
Breaking with an appropriate tool drops a block with its colors and one item
per installed upgrade. Placing it again preserves the colors, lets you name it
again, and starts without upgrades.

## Configuration and administrator destinations

Forge creates `telepads-server.toml` in the world's `serverconfig/` directory and
`config/telepads-client.toml` on each client. The server controls:
`waitSeconds=3`, `xpLevels=0`, `xpPoints=0`, `blockEndWhileDragonAlive=true`,
`enableEnderBead=true`, `enableEnderBeadNecklace=true`, `enableAnvilConversion=true`,
and `destinations=[]`. The wait is read on each activation; costs are read when
confirming. The client controls `particles=true`; disabling it preserves all
travel functionality.

`destinations` accepts `x/y/z/dimension/name` strings. Examples:

```toml
destinations = [
  "0/100/0/minecraft:overworld/Tower",
  "-20#-10/80/10#20/minecraft:the_nether/Nether area",
  "-100#100/random/-100#100/minecraft:overworld/Surface"
]
```

Ranges are inclusive and ordered; negative values are supported. `random` works
for X/Z, Y, or dimension. Random Y searches for the surface of the chosen dimension.
Values respect height and world-border limits; ranges are limited to their
intersection with those bounds. Prepare support and space for the fixed-Y
examples: the mod does not build a platform. The arrival search is limited to
three horizontal and four vertical blocks around the point; a failed search
reports a diagnostic without charging a cost or leaving permanent chunk tickets.

The **Configured Destination Tool**, also restricted to creative players/operators,
cycles through the configured strings when right-clicking the block, then returns
to normal mode. Once the wait completes, it travels to the selected destination
without a receiving pad, selector, transmitter, or XP cost. An invalid definition
produces a diagnostic; if the selection is removed during a configuration reload,
the pad returns to normal mode with a warning.

## Presentation and credits

The platform is a simplified low-profile model: dyeable frame/base, two blue
marks for a transmitter, a red corner for a toggler, and a red bar when disabled.
The six original icons are reused; the animated renderer and 1.19.2 screens are
not reproduced. Screens and messages use translatable keys with `en_us` coverage.

Mod license: GPL-3.0-only, in `LICENSE.md`; attribution in `NOTICE.md`.
The Forge scaffolding license is in `FORGE-LICENSE.txt`.
