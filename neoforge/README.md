# MineBhop — NeoForge

The NeoForge build of MineBhop, for **Minecraft 26.2 and 26.3**. Same mod, same physics, same
config, same `/bhop` command — just the other loader.

| | 26.2 | 26.3 |
|---|---|---|
| NeoForge | 26.2.0.87+ | 26.3.0.3-beta+ *(beta only so far)* |
| Java | 25 | 25 |
| ModDevGradle | 2.0.147 | 2.0.147 |

## Building

Needs the Fabric project sitting next to it (see *Shared sources* below). Normally you build
everything at once from the project root with `build-all.ps1` — see the main README. For just
this loader:

```bash
cd neoforge
.\gradlew.bat build -Pmc=26.3
```

`-Pmc` defaults to `26.2`. Versions are read from `../versions/<mc>.properties`, the same files
the Fabric build uses. Output is `build\<mc>\libs\minebhop-neoforge-1.1.0+<mc>.jar`.

Drop it into a NeoForge instance of the **matching** Minecraft version. Unlike the Fabric build,
no Fabric API equivalent is needed — NeoForge ships the APIs this mod uses.

To test in a dev environment:

```bash
.\gradlew.bat runClient -Pmc=26.3
```

## Shared sources

This is not a copy-paste fork. `build.gradle` pulls the loader-agnostic code straight out of the
Fabric project:

```gradle
sourceSets.main.java {
    srcDir '../src/main/java'
    exclude 'com/minebhop/fabric/**'
    exclude 'com/minebhop/hud/SpeedHud.java'
}
```

So a change to the movement code, the config or a mixin lands in both builds at once, and there is
exactly one copy of the physics to reason about.

What that means practically: **this project will not build if you move it away from the Fabric
project.** It expects `../src/main/java` to exist.

### What is shared

Everything that matters:

- `movement/` — the whole Source simulation, `SourceMoveHandler`, hop timing state
- `mixin/` — all four mixins, byte-for-byte identical
- `config/` — `BhopConfig`, `ConfigManager`, presets, the `@Tunable` reflection
- `command/BhopCommand` — the whole `/bhop` tree
- `hud/SpeedometerRenderer` — the speedometer drawing
- `assets/minebhop/lang/en_us.json`, `minebhop.mixins.json`

This works because **Minecraft 26.x Fabric runs on Mojang mappings natively**, the same namespace
NeoForge has always used. Before 26.2 this would have needed the mixins written twice against two
different mapping sets. The same source also compiles unchanged against both 26.2 and 26.3.

### What is not shared

Only registration glue, four files in total:

| Fabric | NeoForge |
|---|---|
| `fabric/MineBhopFabric` (`ClientModInitializer`) | `neoforge/MineBhopNeoForge` (`@Mod`) |
| `hud/SpeedHud` (`HudElement`) | `neoforge/NeoForgeSpeedHud` (`GuiLayer`) |
| `fabric.mod.json` | `META-INF/neoforge.mods.toml` |

Three small abstractions made that possible:

- `MineBhop` is a loader-agnostic holder for the config and movement state. The mixins call into it,
  and each loader's entrypoint calls `MineBhop.init(configPath)` during startup.
- `ConfigManager` takes its file path as a constructor argument instead of asking a loader for it.
- `BhopCommand` is generic over the command source and built from raw Brigadier, because Fabric
  hands you a `FabricClientCommandSource` and NeoForge a `CommandSourceStack`. Each loader passes a
  small `BhopCommand.Feedback` to bridge the one thing that really differs — how text reaches the
  player.

Conveniently, `GuiLayer.render` and Fabric's `HudElement.extractRenderState` take the same two
arguments (`GuiGraphicsExtractor`, `DeltaTracker`), so both HUD classes are one-line delegates.

### Registration differences worth knowing

NeoForge splits events across two buses, and in this version `@EventBusSubscriber` has **no `bus`
attribute** — it targets the game bus only. So:

- `RegisterKeyMappingsEvent` and `RegisterGuiLayersEvent` are `IModBusEvent`s and are wired from the
  mod constructor's `IEventBus`.
- `RegisterClientCommandsEvent` and `ClientTickEvent.Post` are game bus events and go through
  `@EventBusSubscriber`.

The HUD attaches above `VanillaGuiLayers.CAMERA_OVERLAYS`, which is NeoForge's equivalent of the
slot Fabric calls `MISC_OVERLAYS`.

## Everything else

Controls, config reference, the physics explanation and the verification numbers are all in the
[main README](../README.md). The two builds behave identically.
