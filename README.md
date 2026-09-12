# Instanced Not Infinite

Instanced Not Infinite turns normal Minecraft structures into disposable dungeon instances.

The idea is pretty simple: point the mod at a structure (or a structure tag), open a portal to it, run the dungeon, and clean the instance up when everyone is done. Each run gets its own temporary world, so the same dungeon can be reused without players fighting over one copy in the overworld.

It is built for Minecraft 1.21.1 on NeoForge and is mainly aimed at modpacks that already have structures they like but want to use them more like repeatable dungeons.

## What it does

- Creates a separate instance for each dungeon run.
- Works with registered worldgen structures and NBT structure templates.
- Can pull dungeons from individual structure IDs or structure tags.
- Keeps players in different runs separated from one another.
- Builds a small amount of terrain around structures when they need it instead of generating a normal full dimension.
- Places a return portal and remembers where players came from.
- Supports multiple players sharing the same run.
- Can close instances after completion, vacancy, or a configured timeout.
- Prevents unrelated structures from randomly generating inside dungeon instances.
- Supports datapack definitions when the automatic setup is not enough.

The mod does **not** generate procedural rooms, add bosses, or replace a structure's normal loot. It is meant to wrap existing content rather than become a dungeon generator of its own.

## Manifestations and catalysts

Dungeons can be opened through a manifestation catalyst. While an instance is being prepared, the entrance can show a miniature of the actual structure as it forms before the portal opens.

Catalysts can target a specific dungeon or a configured structure pool. JEI and EMI integration exposes those targets when either mod is installed, and Jade can show dungeon/loading information on the portal.

The default catalyst can also be used without a fixed target, in which case a dungeon is selected from the configured catalogue.

Example for an exact dungeon:

```mcfunction
/give @s instancednotinfinite:manifestation_catalyst[instancednotinfinite:manifestation_target={kind:"dungeon",id:"minecraft:igloo"}]
```

Example for a structure-tag pool:

```mcfunction
/give @s instancednotinfinite:manifestation_catalyst[instancednotinfinite:manifestation_target={kind:"structure_pool",id:"idas:rare"}]
```

Portal appearance, opening/closing behavior, catalyst consumption, instance lifetime, completion offerings, and manifestation animation are configurable.

## Automatic recipes

Exact-dungeon catalysts can receive normal shaped crafting recipes automatically. The mod uses information from the structure and its environment to choose ingredients, while datapacks can override the generated result when a pack needs tighter control over progression.

Recipe cost tiers and ingredient pools are datapack-driven, so packs can replace the defaults without patching the mod.

## Structure handling

For the common case, pack authors only need to list structures or structure tags in the server configuration. Instanced Not Infinite reads the registered structure information and chooses an appropriate biome/environment automatically.

Advanced definitions are available for packs that need custom templates, entry behavior, biome choices, portal colors, or other per-dungeon settings.

Surface, underground, cave, and floating structures are handled differently when choosing an entrance so players do not simply spawn in a wall or fall out of the dungeon. The generated instance is finite and everything outside its useful area remains void.

## Completion and cleanup

An instance can be completed through the API, commands, or by throwing the configured completion offering into one of its portals. The default offering is blaze powder.

When an instance is finished, the server unloads it before removing its files. Cleanup is limited to worlds created and marked by this mod rather than deleting arbitrary dimension folders.

If a player reconnects after an instance has already disappeared, the mod still tries to recover their saved return location instead of leaving them stranded in a missing world.

## Optional integrations

- **JEI / EMI** - dungeon and structure-pool catalyst entries.
- **Jade** - portal names, loading progress, and close countdowns.
- **Sable** - portals can be attached to assembled contraptions. Sable is optional.

There are no required party, scripting, structure-library, or dynamic-dimension dependencies.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or a compatible 21.1 build
- Java 21

Install the mod on the server and on connecting clients.

## For pack authors

The automatic configuration is intended to cover most structures. Datapacks and the public manifestation API are there for packs that want to build their own rituals, quests, boss triggers, KubeJS integration, or other ways of opening dungeons.

There is intentionally no built-in ritual or multiblock requirement. The catalyst and commands are reference implementations of the same system other mods can call.

## Building from source

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The built jar is written to `build/libs/`.
