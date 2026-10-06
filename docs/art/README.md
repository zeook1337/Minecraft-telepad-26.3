# Telepad texture refresh

The four block textures in `src/main/resources/assets/telepads/textures/block/`
are 32×32 PNGs. The new motif uses dark stone, a stepped turquoise teleport ring,
and an angular Ender rune. The grayscale frame and base accept the existing dye
tints; the inset teleport surface keeps its own color.

`telepad-texture-atlas.png` is the original artwork generated with OpenAI's
built-in image generation tool. `telepad-texture-prompt.txt` contains the exact
generation prompt. The atlas quadrants are top, frame, base, and underside in
reading order. Each quadrant was reduced to 32×32 with nearest-neighbor sampling.

Regenerate the model with `scripts/generate-telepad-model.ps1`. The general
resource generator calls the same script, so it preserves the updated model.
`telepad-preview.png` shows the model rendered by Minecraft with default colors
and two pairs of dyes. The preview harness is excluded from the delivered code.
