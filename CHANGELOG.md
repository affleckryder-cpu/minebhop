# Changelog

All builds share one codebase. A given mod version behaves identically on every Minecraft version
and both loaders — only the compatibility metadata differs.

## 1.1.0

### Added
- **NeoForge support.** Same features as Fabric, from the same shared sources.
- **Minecraft 26.1, 26.1.1, 26.1.2 and 26.3 support**, alongside the original 26.2.
- **Source-style ladders** (`sourceLadders`, on by default). You climb along your look direction
  rather than straight up: look up to ascend, look down to descend, strafe to slide across the
  ladder face. Climb speed scales with how steeply you look, up to 200 u/s. Looking level at a
  ladder holds you in place, as in Source.
- **Diagonal ladder climb** (`ladderDiagonalBoost`, √2 by default). Holding a forward/back key and
  a strafe key together climbs about 41% faster — 7.2 blocks/sec against 5.1 for forward alone, and
  4.0 for a vanilla ladder.
- **Ladder dismount** (`ladderDismountSpeed`, 270 u/s). Jumping pushes you along the ladder's
  facing, with no upward component, so you drop away as you leave.
- **`preserveMomentumOnDamage`** (on by default). Taking damage no longer destroys your speed.

### Fixed
- **Fall damage no longer ends a hop chain.** Damage makes the server call `markHurt()`, which
  pushes its own idea of your velocity back at the client. The server does not simulate Source
  movement, so applying it wiped out hundreds of u/s. Corrections that would *speed you up* are
  still applied, so explosions and mob knockback keep working.
- **`disableFallDamage` now actually works** in singleplayer and for a LAN host. It previously
  cancelled only the client's prediction while the authoritative side applied the damage anyway.
  Gated on your profile UUID, so on a LAN world only the host is affected.

### Changed
- **Fall damage is on by default** (`disableFallDamage` now defaults to `false`). Keeping your
  momentum is what mattered; the damage itself is part of the game.
- **The speedometer HUD is off by default** (`hud` now defaults to `false`). Press `[` for it.
- Keys are bound through Mojang's `InputConstants` rather than raw GLFW constants. Minecraft 26.3
  replaced GLFW with SDL3 and the underlying key codes changed, so raw values would have bound the
  wrong physical keys there.

## 1.0.0

Initial release. Fabric, Minecraft 26.2.

- Source engine ground movement: friction with `sv_stopspeed` behaviour, and acceleration toward a
  250 u/s run.
- Air acceleration with the 30 u/s air speed cap — the mechanic air strafing is built on.
- Bunny hopping, with the jump check ordered ahead of friction exactly as Source does it, so a hop
  timed onto the landing tick pays no friction and keeps all of its speed.
- Configurable hop timing: `bhopMode` (MANUAL/AUTO), `jumpBufferTicks`, `autoHopDelayTicks`,
  `perfectHopWindowTicks`, `minTicksBetweenHops`.
- `simulationTickrate` (64 by default), which sub-steps air acceleration and interpolates view yaw
  across the sub-steps. Minecraft ticks at 20 Hz and the air speed cap applies per step, so without
  this a naive port gains speed far too slowly.
- Speedometer HUD: speed in u/s and blocks/tick, ground/air state, hop streak, ground ticks per hop
  and speed gained or lost.
- `/bhop` command tree with tab completion over every setting, plus presets: `csgo`, `css`, `kz`,
  `quake`, `official`.
