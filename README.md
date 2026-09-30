# MineBhop

A client mod that replaces Minecraft's player movement with a port of Source engine movement —
the CS:GO model. Ground friction and acceleration, air acceleration with the 30 u/s air speed cap,
bunny hopping with configurable timing, the speed gain that comes out of air strafing, and
look-directed ladder climbing.

Built for **Fabric and NeoForge**, on **Minecraft 26.2 and 26.3**, from one shared codebase.

## Downloads

Pick the jar matching your loader *and* Minecraft version — they are not interchangeable.

| | Minecraft 26.2 | Minecraft 26.3 |
|---|---|---|
| **Fabric** | `minebhop-fabric-1.1.0+26.2.jar` | `minebhop-fabric-1.1.0+26.3.jar` |
| **NeoForge** | `minebhop-neoforge-1.1.0+26.2.jar` | `minebhop-neoforge-1.1.0+26.3.jar` |

All four land in `dist\` after a build. Use the one **without** `-sources` in its name.

| Requirement | 26.2 | 26.3 |
|---|---|---|
| Java | 25 | 25 |
| Fabric Loader | 0.19.3+ | 0.19.5+ |
| Fabric API | 0.158.0+26.2 | 0.160.6+26.3 |
| NeoForge | 26.2.0.87+ | 26.3.0.3-beta+ *(beta only so far)* |

Fabric needs the Fabric API jar alongside it. NeoForge needs nothing extra.

### Why one jar per Minecraft version

26.3 replaced GLFW with SDL3 for windowing and input, which changed the underlying key codes —
`]` is 93 on 26.2 and 48 on 26.3. The mod binds its keys through Mojang's own
`InputConstants.KEY_*` rather than raw GLFW values, so the *source* is identical on both. But those
constants are inlined into the jar at compile time, so each Minecraft version has to be compiled
separately. A 26.2 jar on 26.3 would bind the wrong physical keys even if it loaded.

## Building

Needs **JDK 25**:

```bash
winget install EclipseAdoptium.Temurin.25.JDK
```

The Gradle wrapper is checked in, so nothing else needs installing: `.\gradlew.bat` on Windows or
`./gradlew` on macOS/Linux downloads Gradle 9.5.1 on first run.

### Everything at once

```bash
powershell -ExecutionPolicy Bypass -File .\build-all.ps1
```

Builds every Minecraft version in `versions\` on both loaders and collects the jars in `dist\`.
**Use this after changing any shared code** — the movement code feeds all four builds, and this is
how you avoid shipping one stale combination. Pass a version to build just that one:
`.\build-all.ps1 26.3`.

### One combination

The Minecraft version is picked with `-Pmc` (default `26.2`):

```bash
.\gradlew.bat build -Pmc=26.3
```

For NeoForge, run the same from the `neoforge\` folder. Output goes to
`build\<mc>\libs\` — each Minecraft version gets its own build directory, so switching never
leaves classes compiled against one version inside the other's jar.

To test in a dev environment:

```bash
.\gradlew.bat runClient -Pmc=26.3
```

Each version also gets its own game directory (`run` for 26.2, `run-26.3` for 26.3), because a
world opened in 26.3 cannot be opened again in 26.2.

### Adding a Minecraft version

Everything version-specific lives in one file per version, shared by both loaders:

```
versions/26.2.properties
versions/26.3.properties
```

Copy the newest one, update the Minecraft, Fabric and NeoForge versions, and run `build-all.ps1`.
If the source still compiles against the new version, that is the whole job.

## Using it

Keybinds (rebindable in Options → Controls → MineBhop):

| Key | Action |
|---|---|
| `]` | Toggle Source movement |
| `[` | Toggle the speedometer HUD |
| unbound | Cycle hop mode (manual / auto) |

Commands:

```
/bhop                      status
/bhop on | off             toggle the simulation
/bhop hud                  toggle the HUD
/bhop mode MANUAL|AUTO     hop mode
/bhop preset <name>        csgo, css, kz, quake, official
/bhop set <key> <value>    change any setting, with tab completion
/bhop get <key>            value plus what it does
/bhop list                 every setting
/bhop reset                back to CS:GO defaults
/bhop save | reload        config file is config\minebhop.json
```

The HUD sits above the hotbar and shows speed in u/s, blocks/tick, ground/air state, and
the hop timing line: hop streak, **ground ticks on the last hop**, and speed gained or lost.
Ground ticks is the number to watch — `0t` means you left on the same tick you landed and
paid no friction at all.

`/bhop set jumpStats true` prints KZ-style stats in chat after every jump:

```
[MineBhop] 7.89 blocks | pre 372 | max 417 | strafes 2 | sync 100%
```

Distance is edge to edge, like KZ: your travel plus your width, so it is the gap you could clear.
`pre` is your speed at takeoff and `max` your top speed in the air. `sync` is the share of mouse
turns that went toward the strafe key you held, averaged over your current hop chain. `height`
appears when you land above or below where you took off. With the HUD on (`[`), the last jump
shows there instead of in chat.

`/bhop set xpBarSpeed true` turns the XP bar into a speedometer while you move: it fills toward
1000 u/s, runs green to yellow to red, and the level number shows your speed. Standing still, your
normal XP shows.

## How to actually gain speed

1. `/bhop mode AUTO` and hold space. Auto mode hops on every landing, so you can ignore
   timing while you learn the strafing.
2. Let go of `W`. Forward input does nothing useful in the air — it points your wish
   direction along your velocity, where there is no headroom left under the air speed cap.
3. Hold `D` **and** turn the mouse right, smoothly, together. Or `A` and turn left. Speed
   climbs while the direction you travel curves around.
4. Alternate: `D` + turn right for one hop, `A` + turn left for the next.
5. When that is reliable, `/bhop mode MANUAL` and time the hops yourself. Start with
   `/bhop set jumpBufferTicks 3` and lower it toward `0` as you improve.

The turn rate matters and there is an optimum. Simulating the default config, holding one
strafe key over ten hops from 250 u/s:

| Turn rate | Speed after 10 hops | Per hop |
|---|---|---|
| 10 °/s | 290 u/s | +4 |
| 40 °/s | 398 u/s | +15 |
| 60 °/s | 462 u/s | +21 |
| 120 °/s | 603 u/s | +35 |
| 240 °/s | 657 u/s | +41 |

Turning too fast is nearly as bad as turning too slowly, exactly as in CS.

## Settings that matter most

Everything is in Source units, so the defaults are literally the CS:GO cvar values and can
be compared against a CS config one-to-one.

| Setting | Default | Notes |
|---|---|---|
| `maxSpeed` | 250 | `sv_maxspeed`. Ground run speed |
| `airAcceleration` | 12 | `sv_airaccelerate`. Bhop servers use 100–1000 |
| `airSpeedCap` | 30 | The single most important number. Raise it and speed explodes |
| `friction` | 5.2 | `sv_friction`. Costs ~23% of your speed per grounded tick |
| `enableBunnyHopping` | true | Set false to reproduce official matchmaking, which clamps you to 300 u/s on every jump |
| `bhopMode` | AUTO | `MANUAL` = one hop per press |
| `jumpBufferTicks` | 3 | Press jump this early before landing and it still hops |
| `autoHopDelayTicks` | 0 | Ticks grounded before a hop fires. `0` skips friction entirely |
| `perfectHopWindowTicks` | 1 | What the HUD calls a perfect hop |
| `sourceLadders` | true | Look-directed ladder climbing. See below |
| `ladderClimbSpeed` | 200 | u/s, vs Minecraft's ~157 |
| `ladderDismountSpeed` | 270 | u/s push when you jump off a ladder |
| `preserveMomentumOnDamage` | true | Ignore server velocity corrections that would bleed speed. See below |
| `simulationTickrate` | 64 | See below |
| `unitsPerBlock` | 39.3701 | 1 unit = 1 inch. Lower it to scale the whole model up |

### Ladders

Source ladders work nothing like Minecraft's, and `sourceLadders` (on by default) replaces
them. You climb along the direction you are **looking**, not simply upward:

| Input | Result |
|---|---|
| Look straight up + `W` | climb at full speed (200 u/s) |
| Look up 45° + `W` | climb at 141 u/s — shallower is slower |
| Look level at the ladder + `W` | hang in place |
| Look down + `W` | descend |
| `A` / `D` | slide sideways across the ladder face |
| `W` + `A`/`D` together | **diagonal climb — 1.41x faster ascent** |
| `Space` | kick off along the ladder's facing at 270 u/s |

The diagonal climb is worth knowing: holding a forward/back key and a strafe key together
multiplies climb speed by `ladderDiagonalBoost` (√2 by default). Looking straight up, that is
7.2 blocks/sec against 5.1 for plain forward, and 4.0 for a vanilla ladder. Note this has to
be an explicit multiplier — a strafe vector is horizontal, so it can never contribute vertical
speed on its own no matter how the projection is arranged. Set it to `1.0` to remove the
technique.

The speed falloff is the important part and it is not a gimmick: velocity is built at full
climb speed and *then* projected onto the ladder plane, so the projection is what removes the
speed. Look steeply to climb quickly. No gravity applies while you are attached, and velocity
is assigned outright with no acceleration or friction, exactly as Source does it.

Motion *into* the ladder is removed, but motion away from it is not — that is how you step
off. Jumping sets an 8-tick no-regrab window (`ladderGrabCooldownTicks`), otherwise you would
instantly reattach, since you are still standing inside the ladder's own block.

The ladder normal comes from the block's `FACING`, which in Minecraft points away from the
wall it is mounted on — the same convention as Source's ladder normal. Vines and scaffolding
have no facing, so they are climbable from any direction with no projection at all.

### preserveMomentumOnDamage

Taking damage on the server calls `markHurt()`, which sets `hurtMarked` and makes
`ServerEntity` broadcast a `ClientboundSetEntityMotionPacket` — and for a player, that packet
goes to the damaged player too, not just to everyone tracking them. The client applies it in
`Entity#lerpMotion` by overwriting delta movement outright.

The server never simulates Source movement. Its stored velocity for you comes from position
packets and looks nothing like the several hundred u/s you are actually carrying, so applying
it wipes out the hop chain. Fall damage is the case you hit constantly: `minecraft:fall` is in
the `no_knockback` tag but *not* in `no_impact`, so no knockback is applied, yet `markHurt()`
still fires and the sync still lands.

This setting rejects those corrections, but only when they would make you *slower*. Anything
that would speed you up is real knockback — explosions, wind charges, mobs — and passes
through untouched. The vertical component is always accepted, so bounces and launches still
work.

### simulationTickrate

Minecraft ticks at 20 Hz, CS:GO at 64. Because the air speed cap is applied *per step*, a
naive 20 Hz port gains speed far more slowly than the real thing. The mod sub-steps air
acceleration and walks your view yaw across the sub-steps, which restores the real feel:

| Simulated | Speed after 10 hops at 60 °/s |
|---|---|
| 20 Hz | 418 u/s |
| 64 Hz | 462 u/s |
| 128 Hz | 474 u/s |

## Verification

Simulating the shipped physics against CS:GO reference values:

| | This mod | CS:GO |
|---|---|---|
| Ground run speed | 250.0 u/s | 250 u/s |
| Jump height | 56.8 u (1.44 blocks) | 57 u |
| Airtime per jump | 0.80 s | 0.75 s |
| Perfect hop (0 ground ticks) | 500 → 500 u/s | no loss |
| One extra ground tick | −23% speed | `sv_friction 5.2` |
| Run speed in Minecraft terms | 0.317 blocks/tick | vanilla sprint is 0.280 |

The 0.05 s airtime difference is 20 Hz quantisation — 16 ticks is the nearest whole number
of ticks to 0.75 s.

## How it works

`Player#travel` is cancelled at HEAD for the local player and replaced with the Source
pipeline, in Source's own order:

```
StartGravity -> CheckJumpButton -> Friction -> Accelerate -> Move -> FinishGravity
```

Jump before friction is the load-bearing detail. In Source's `FullWalkMove` a successful
jump clears the ground entity *before* the friction block runs, so a hop timed onto the
landing tick never pays friction and keeps all of its speed. That is the entire mechanic
behind bunny hopping, and it falls out of the ordering rather than being special-cased.

Air strafing falls out of `AirAccelerate`. The headroom it will fill is capped at 30 u/s
measured *along your input direction*, not on total speed. Hold a strafe key while turning
the same way and the wish direction stays roughly perpendicular to your velocity, so the
projection onto it stays near zero, the full 30 u/s is available every tick, and all of it
is added at a right angle to where you were already going. Perpendicular vectors sum to
something longer than either, so total speed climbs without bound while your path curves.

Minecraft keeps ownership of collision, step-up, block speed factors and sneak edge
protection — only the velocity integration is replaced. Swimming, elytra, ladders, creative
flight and riding are all left to vanilla.

Source code layout:

```
movement/SourceMovement.java   the CGameMovement formulas, no Minecraft types at all
movement/SourceMoveHandler.java  unit conversion and the tick pipeline
movement/MovementState.java    hop timing state machine
mixin/PlayerTravelMixin.java   cancels vanilla travel
mixin/LivingEntityJumpMixin.java  cancels the vanilla jump and its 10-tick cooldown
config/BhopConfig.java         every tunable, annotated; drives the commands by reflection
```

Adding a field to `BhopConfig` with a `@Tunable` annotation is all that is needed to expose
it to `/bhop set`, `/bhop get`, `/bhop list` and tab completion.

## Limits worth knowing

- **Off on multiplayer servers by default,** unless the server runs MineBhop too. To a server
  without it, Source movement is just unusually fast movement packets — to an anti-cheat plugin
  that is a speed hack, and players get kicked or banned for it. So it runs in singleplayer, when
  you host a LAN world, and on servers with the mod installed (the server tells your client on
  join; players without the mod can still join). Anywhere else it stays off and tells you once in
  chat; `/bhop set allowOnServers true` turns it on regardless. (Vanilla's own "moved too
  quickly" check isn't the problem: its threshold is 10 blocks/tick, far above anything this
  produces, and a test on a vanilla server logged no movement warnings at all.)
- **Fall damage** can be suppressed with `disableFallDamage` (off by default), because Minecraft
  accumulates fall distance over hop chains in a way Source does not. Damage is decided by the
  authoritative side, so cancelling it on the client alone would do nothing — but in
  singleplayer and on a LAN world the integrated server lives in the same JVM, so its
  `ServerPlayer` is cancelled too. That is gated on your profile UUID, so if you open a world
  to LAN, only the host stops taking fall damage. Against a dedicated server it cannot work at
  all; there, what survives is your speed, via `preserveMomentumOnDamage`.
- The `.java` files in the project root (`Ent.java`, `LE2.java`, `LP.java`, …) are the
  decompiled 26.2 sources this was originally written against. They sit outside `src/`, so nothing
  compiles them. They are handy reference; delete them if you want a clean tree.
