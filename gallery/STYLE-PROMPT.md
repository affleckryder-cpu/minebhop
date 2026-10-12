I need a logo and a set of gallery images for my Minecraft mod's CurseForge page. I have a
house style from my other mod, MineBhop, and I want this mod's images to look like they
belong to the same family. The full style is described below so you can reproduce it without
seeing the originals.

## About this mod

- Name: [MOD NAME]
- One-line pitch: [WHAT IT DOES, IN ONE SENTENCE A PLAYER WOULD SAY]
- Loaders and versions: [e.g. Fabric + NeoForge, 26.1 – 26.3]
- Client, server, or both: [...]
- The 3–5 features worth a card each: [FEATURE: one line on why a player cares]
- Accent colour: [HEX, or "pick one that suits the mod" — MineBhop uses cyan #5CE1FF]
- Screenshots: [FOLDER PATH, or "none yet — tell me which ones to take"]

## What to make

1. A logo: 512×512 PNG, plus a 256×256 copy.
2. A cover card and one card per feature, each 1920×1080 PNG.

Every file must be under 2 MB, which is the upload limit. If a PNG comes out larger, scale
it down in steps until it fits rather than switching to JPEG, because JPEG blurs pixel text.

Write these as Python scripts using Pillow, saved in a `gallery/` folder, so I can regenerate
the images when the mod changes. Draw everything in code; don't use an image generator.

## The style

The look is a dark, modern UI with one bright accent, wrapped around crisp Minecraft pixels.
The contrast between smooth vector type and hard game pixels is what makes it work, so keep
the two apart: smooth for the card, hard-edged for anything from the game.

**Palette**
- Background: vertical gradient from #161A24 at the top to #0B0D12 at the bottom, with two
  very soft blurred glows added on top (a teal one off the top-left corner, a violet one off
  the bottom-right) so the cards aren't flat black.
- Text: white for titles, #D6DCE6 for body, #969EAC for secondary text.
- Accent: the mod's accent colour, used sparingly — the kicker, bullet markers, image
  borders, and the second half of the wordmark.
- Surfaces (chips, diagram boxes): #20242E with a #40485A outline.

**Type**
- Bahnschrift (C:\Windows\Fonts\bahnschrift.ttf), a variable font: set the weight with
  `set_variation_by_name` — Bold for titles, SemiBold for kickers and chips, Regular for body.
- Feature card: kicker 30px in the accent colour, all caps; title 84px bold white, two
  short lines, ending in a full stop ("Strafe to / gain speed."); bullets 34px.
- Titles are a benefit the player gets, not a feature name.

**Feature card layout (1920×1080)**
- Top-left: the logo at 72px with the wordmark beside it — mod name in caps, first half
  white, second half accent.
- Left column, starting at x=96, y=300: kicker, title, then 3–4 bullets, each one short
  line with a small rounded accent square as its marker.
- Right side (roughly x 880–1900): one framed image, centred vertically.
- Keep the text column clear of the image; shorten a bullet rather than let it run close.

**Framed images**
- Rounded corners (about 22px), a 3px accent border, and a soft drop shadow.
- When enlarging a screenshot by 2× or more, use nearest-neighbour so game pixels stay
  sharp. Use Lanczos only when shrinking.
- Crop tightly to what the card is about. For HUD or UI close-ups, compute the crop from
  the game's GUI scale (largest integer scale where the window is still at least 320×240
  GUI pixels) so the crop lands on whole GUI pixels, and leave a little air around the
  subject inside the frame.

**Cover card**
- A gameplay screenshot filling the frame, blurred (radius about 7) and darkened, darker
  on the left where the text sits.
- The logo at 300px, the mod name at 170px bold (first half white, second half accent), a
  one-line pitch at 54px, a second line at 38px in secondary grey, then a row of pill-shaped
  chips for loaders, versions, and client/server.

**Cards with no screenshot**
- For something that can't be photographed (how multiplayer works, say), draw a simple
  diagram instead: rounded surface boxes with a bold title and a grey subtitle, joined by
  right-angled connector lines with arrowheads. Colour each box's outline by meaning.

**Logo**
- Pixel art drawn on a 64×64 grid, then scaled up with nearest-neighbour and given rounded
  corners (radius about 1/9 of the size).
- Dark gradient background matching the cards.
- One simple motif that shows what the mod does at a glance, in an accent-to-second-colour
  gradient. MineBhop's is three hop arcs growing in size with a white block at the top of
  the last one; find the equivalent for this mod.
- A short word or abbreviation underneath in a hand-defined 5×7 pixel font at 2× scale,
  white fading slightly toward the accent, with a 1px darker drop shadow.
- It has to read at 64px, so keep it to the motif, the word, and at most one small detail.

## How to work

- Before designing, read the mod's README or main source files so the card titles and
  bullets describe what the mod really does. Don't invent features.
- After rendering each image, open it and look at it. Check for text overlapping an image,
  anything clipped by a frame edge, text that's hard to read against its background, and
  letters in the logo that could be mistaken for other letters. Fix what you find and
  re-render before showing me.
- If a card needs a screenshot I haven't provided, make the other cards, then tell me
  exactly what to capture (what should be on screen, and to press F2). Don't fill the gap
  with a placeholder.
- When you're done, tell me each file's size and dimensions, and anything you couldn't
  verify.
