# Instanced Not Infinite

Instanced Not Infinite is a config-driven dungeon framework for Minecraft 1.21.1 on NeoForge. It turns registered worldgen structures or NBT structure templates into isolated, renewable dungeon runs. Every run receives its own UUID, deterministic seed, inexpensive finite terrain, player assignments, return locations, persistent lifecycle record, and guarded cleanup job.

For the normal workflow, a modpack author lists structure IDs or structure tags in one server config. The mod discovers each structure's allowed biomes and worldgen metadata automatically; per-structure datapack JSON remains available for advanced definitions and templates. The mod does not assemble procedural rooms, add bosses, or replace structure loot.

## Requirements and installation

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer compatible 21.1 build
- Java 21
- JEI 19.18+ and/or EMI 1.1+ on the client are optional; when installed, each resolved dungeon receives its own catalyst entry.

Copy the built JAR into the `mods` folder on the server and connecting clients. There are no required structure, biome, party, scripting, or dynamic-dimension library dependencies.

Build from source with:

```powershell
./gradlew.bat build
```

The distributable is written to `build/libs/instancednotinfinite-1.1.3.jar`.

## How an instance works

1. An explicit dungeon ID is requested, or the server selects from all automatic and advanced definitions using their weights.
2. The selected structure's inferred or overridden biome pool resolves against the live registry. A deterministic weighted choice is snapshotted.
3. A UUID dimension named `instancednotinfinite:instances/<uuid-without-dashes>` is registered.
4. One structure layout is generated using the instance seed and retained. Its environment selects a controlled shape strategy, while its real selected biome supplies biome identity, colors, weather, mechanics, spawn data, the surface palette, and configured decoration. The authored foundation determines the terrain seating plane, and the actual structure bounds are expanded by configured safety padding.
5. Minecraft's structure APIs place that same retained `StructureStart`, or an exactly measured NBT `StructureTemplate`. Bounded worldgen writes allow structures with self-protecting blocks to finish generating; ordinary gameplay block protection remains intact afterward.
6. Surface-like automatic structures detect a front from ground-level doors, gates, or open boundary geometry. Underground/cave structures choose a deterministic-random walkable point inside their real authored piece boxes and a direction through the surrounding solid stone. The server cuts the configured bridge/path and arrival platform, places the player on that platform facing the chosen interior, and places an exact-instance return portal behind the arrival point. Floating structures use a walkable 3×3 surface edge connected to the path and platform. Other non-underground advanced definitions retain their configured relative entry behavior.
7. Players are teleported with persistent return locations. Entering the UUID-bound destination portal or falling below the instance's minimum build height returns them to the entrance portal's saved exit position in its original dimension. Void returns clear fall distance and velocity; a cooldown prevents immediate portal bounce. Multiple assigned players can occupy the same run, while other runs remain separate worlds.
8. Completion, explicit deletion, or vacant timeout moves the record through `UNLOADING` and `DELETE_PENDING`.
9. The server saves, detaches, and closes the level. A worker deletes only a validated UUID directory containing this mod's marker.

Terrain is generated deterministically and lazily inside the dungeon's finite instance envelope without invoking vanilla density terrain, surface rules, or carvers. Broad interpolated noise creates smooth height variation; the outer band tapers in thickness and uses a deterministic three-dimensional dither mask to dissolve horizontally and vertically into void instead of ending in intact columns. Surface and floating-island palettes follow common biome categories (grass/dirt, sand/sandstone, red sand/terracotta, mycelium, or stone). `SAFE` decoration runs only the selected biome's vegetation and top-layer stages, preserving features such as trees, plants, kelp, snow, and ice without the expensive terrain pipeline. For retained terrain, the guaranteed padded structure area never receives holes, chunks outside the envelope remain void, and the world border matches the actual envelope. Floating structures instead clear their disposable terrain after placement and retain only their authored blocks and entrance construction.

Normal structure-set generation is disabled unconditionally in every instance dimension. Only the selected dungeon structure is explicitly placed, so villages, outposts, mineshafts, and structures from other mods do not appear incidentally inside a run.

## Dungeon manifestations and portals

A manifestation creates the real dungeon incrementally while presenting a client-side miniature at an ordinary-world origin. The miniature comes from the exact retained layout, not a second structure roll or a separately authored approximation. It transmits the structure's complete retained surface shell without the generated terrain envelope, and the world hologram renders every retained block instead of puncturing large builds with client-side level-of-detail gaps.

The server owns the lifecycle:

```text
PREPARING -> GENERATING -> MANIFESTING -> FINALIZING -> COLLAPSING
          -> PORTAL_OPENING -> PORTAL_OPEN -> CLOSING -> COMPLETE
          \-> FAILED or CANCELLED
```

Generation is a resumable server-thread job. Its primary global limit is elapsed milliseconds per tick; a block-operation cap is the secondary safety rail. Terrain and heightmap preparation report generation progress while animation remains at zero. During that preparation, a configurable client-side colored particle pattern marks the future hologram/portal area and stops as soon as the first structure batch arrives. The configured animation duration begins with that batch, its reveal coordinates are normalized to the known structure bounds, and the earliest captured score maps to time zero. Animation progress therefore never skips an invisible opening percentage or outruns generation progress, and forcing the presentation to finish does not bypass generation. While the player aims at the visible hologram within `portalHudDistance`, the tooltip reports that animation clock directly rather than the independent generation clock. Jade owns this presentation by default when it is installed and the integration is enabled; otherwise the optional built-in top-center `Loading: XX%` panel and progress bar are used. The miniature is fitted and centered from the exact retained structure shell rather than the surrounding terrain. Once the instance is ready, the portal expands smoothly from the hologram's final collapse point to its configured width, height, and optional depth; collision and interaction remain gated until that opening phase completes. Its activation volume is that same rendered portal shape rather than the single anchor block.

Portal colors resolve independently for the inner and outer layers. An advanced datapack definition's `#RRGGBBAA` value has first priority and retains its own alpha. Otherwise, both layers use the selected dungeon biome's fog RGB by default. Inner color can be shaded from `-1.0` (black), through `0.0` (unchanged), to `1.0` (white), then receives a `0.0` (opaque) to `1.0` (invisible) transparency value; outer color retains its opacity percentage. The global `portalInnerColor` or `portalOuterColor` remains the fallback when derivation is disabled. The resolved colors are stored with the instance, synchronized during manifestation generation, and reused by both source and destination portals after reload. Portals retain the player's whole-degree placement yaw rather than snapping to a cardinal grid direction, with their broad face aligned to Minecraft's player-yaw convention at diagonal as well as cardinal angles. Their paired inner/outer dither fragments begin slightly inside the current edge, fade to full opacity while moving onto the edge, and only then begin the existing outward travel and shrink animation. As the synchronized close timer runs down, the solid portal contracts in width, height, and depth while its bottom remains fixed, stopping at the independently configured minima (default `1.0 × 2.0 × 0.35` blocks); final closure breaks that doorway into paired dither tiles that disperse radially, lift, travel through the portal plane, and shrink away in the air instead of vanishing in place or scaling the whole doorway to nothing. While Jade integration is active, Jade shows the dungeon name, generated catalyst icon, loading progress or synchronized close countdown for both the client-rendered source and block-backed return portal. Without Jade integration, the built-in panel provides the same targeting information when enabled. Entry targets the portal's exact UUID instance through the same guarded path used by `/dungeon join`, and closure completes the dither dissolve before removing the anchor and completing or cancelling the manifestation.

Manifestation ordering is deterministic from the instance seed, manifestation UUID, bounds, and selected mode. Available modes are `GROUND_UP`, `MIDDLE_OUT`, `OUTSIDE_IN`, `SINGLE_ORIGIN`, `MULTI_ORIGIN`, `CHAOTIC`, `RANDOM_ORDER`, `NONE`, and `RANDOM_MODE`. Coherent low-frequency noise prevents the structured modes from becoming visibly perfect planes or shells. `RANDOM_MODE` chooses deterministically from the configured allowlist.

Clients receive bounded start, block-batch, progress, and removal payloads only while they are in range and in the correct dimension. Late joiners receive the current snapshot and state. A client converts each snapshot revision into a static, hidden-face-culled mesh over a bounded amount of work per tick. The animation score is compiled into 64 reveal buckets, so ordinary frames only draw the completed GPU buffers instead of sorting blocks, querying models, or rebuilding geometry. Complex animated block models retain a tightly capped fallback path. The previous mesh remains visible while a newer streamed revision builds, and resource reloads safely rebuild the GPU resources. This keeps every visible structure detail without terrain rendering or perforating level-of-detail sampling.

World holograms and catalyst icons interpret the cached snapshot mesh. Exact-dungeon inventory and recipe-viewer icons are rendered lazily as size-normalized, isometric 2D sprites in natural block colors, rasterized once at the independently configured icon resolution, and held in a bounded LRU cache. While those GUI sprites are not ready, the placeholder uses the same animated, biome-colored portal cube as the held item. Held, dropped, framed, and other in-world item contexts use the completed upright 3D miniature whenever one is ready, otherwise retaining the animated quarter-scale portal cube and its six-face particle field; unbound catalysts retain that portal effect because they have no singular miniature. A structure-pool catalyst keeps one stable animation slot per catalogue member, using the matching resolved-color portal cube for an unavailable GUI sprite or in-world miniature. Its in-world presentation shrinks, swaps at zero scale, expands, and rotates continuously at the configured `poolItemSwapIntervalSeconds`, while its GUI presentation crossfades across the same stable member slots, including each member's portal-cube placeholder. Adding a newly generated icon therefore no longer changes the animation's count or remaps its current index. Once a complete exact-dungeon snapshot has produced an icon, its palette-compressed structure geometry is cached below the client game directory under `cache/instancednotinfinite/miniatures/<world UUID>`. Rejoining the same world rebuilds the GUI icon from that cache without manifesting the dungeon again; a persistent identity synchronized from the save prevents equally named dungeons in unrelated worlds from sharing geometry. The supplied artwork remains the mod-list logo and is no longer used as an inventory placeholder.

The `instancednotinfinite:manifestation_catalyst` item is available in Tools & Utilities. With no component it selects from the configured weighted catalogue. An exact target can be given with:

```mcfunction
/give @s instancednotinfinite:manifestation_catalyst[instancednotinfinite:manifestation_target={kind:"dungeon",id:"minecraft:igloo"}]
```

A named structure-tag pool uses `kind:"structure_pool"`, for example:

```mcfunction
/give @s instancednotinfinite:manifestation_catalyst[instancednotinfinite:manifestation_target={kind:"structure_pool",id:"idas:rare"}]
```

JEI and EMI receive every exposed exact-target catalyst plus every configured structure-pool catalyst. A targeted catalyst is named from the final path of its dungeon ID, with separators replaced by spaces and words title-cased (`cataclysm:acropolis` becomes `Acropolis`, and `minecraft:trial_chambers` becomes `Trial Chambers`). Pool names combine the owning mod's display name, the title-cased tag name, and `Structure Pool`; `idas:rare` therefore displays as `Integrated Dungeons and Structures - Rare Structure Pool`. These entries initially use the biome-colored portal-cube placeholder and replace it incrementally as that client's snapshot-backed caches finish. The configured raster resolution controls generated sprites, and a catalogue reload also updates the viewer index without restarting the client.

Use the item on a replaceable portal location. `catalystConsumptionPolicy` supports `ON_ACTIVATION`, `ON_SUCCESS`, and `NEVER`. Survival catalysts are escrowed immediately for both consuming modes; `ON_SUCCESS` is always restored if creation fails, while `ON_ACTIVATION` follows `refundOnFailure`. The policy and refund marker are persistent, so a logged-out owner receives an owed refund on the next login without duplication.

Throw one item matching `completionOffering` into either endpoint of an open portal to mark that portal's exact dungeon instance complete. The default is `minecraft:blaze_powder`; prefix a configured resource ID with `#` to accept an item tag instead. The server checks the full rendered portal volume, completes the instance before consuming anything, and removes exactly one matching item from the dropped stack.

There is deliberately no built-in ritual or multiblock. Quests, bosses, structures, KubeJS adapters, and other mods should call the public manifestation API; the item and commands are reference triggers using that same path.

### Automatic portal recipes

Every eligible exact-dungeon catalyst receives a normal 3x3 shaped crafting recipe unless one already exists. Pool-only membership and the recipe-target exclusion list can deliberately suppress this path. These are ordinary Minecraft recipe-manager entries, so the crafting table, recipe book, JEI, EMI, and other viewers all consume the same recipe. Precedence is:

1. an ordinary datapack recipe whose result is the exact targeted catalyst;
2. a matching `dungeonOverrides` recipe ingredient override;
3. structure/worldgen inference;
4. the shipped safe generic ingredient pools.

A custom recipe can also persist per-item instance timing on its result with the `instancednotinfinite:instance_lifecycle` data component:

```json
"result": {
  "id": "instancednotinfinite:manifestation_catalyst",
  "count": 1,
  "components": {
    "instancednotinfinite:instance_lifecycle": {
      "open_seconds": 900,
      "post_visit_seconds": 120,
      "force_collapse_seconds": -1
    }
  }
}
```

Each value accepts `-1` for no deadline. `open_seconds` governs an instance that has never been entered, `post_visit_seconds` governs vacancy after the last player leaves, and `force_collapse_seconds` is an absolute deadline measured from instance activation. The item values override the server defaults only for the instance it manifests; if all three resolve to `-1`, it has no natural close deadline.

Automatic recipes use the recognizable shape:

```text
S T S
T C T
S P S
```

`S` is a distinctive obtainable palette material when one can be found, otherwise an environment pool. `T` comes from the strongest inferred theme, `C` comes from the selected cost tier, and `P` is the universal portal catalyst. Selection from a tag is stable: the structure ID and role are SHA-256 hashed after candidates are sorted, so the same datapack/mod configuration always chooses the same item.

Inference reads registered structure biome restrictions and tags, temperature, terrain adaptation, generation step, structure-set placement/frequency/weights/exclusion zones, jigsaw pool metadata, reachable NBT templates, template block palettes and footprint, the resolved dungeon environment, and low-priority structure-ID tokens. It never places a structure or generates a chunk. Analysis is bounded to 128 pools, 512 templates, and 250,000 blocks per template, cached by dungeon ID, and rebuilt after datapack or relevant server-config reloads. Unsupported custom structures degrade to the generic pools with a warning.

Cost tiers are datapack files at `data/<namespace>/portal_recipe_tiers/<path>.json`. INI ships `common`, `uncommon`, `rare`, `epic`, and `legendary`; for example:

```json
{
  "priority": 0,
  "rarity_min": 0.55,
  "rarity_max": 0.75,
  "core": "#instancednotinfinite:portal_core/rare",
  "themes": ["fire"],
  "archetypes": ["boss"]
}
```

`themes` and `archetypes` are optional match requirements. Higher priority wins overlapping matches, then the narrowest range, then resource ID. Core and theme economy is tag-driven under `data/instancednotinfinite/tags/item`: the five `portal_core/*` tags, `portal_catalyst`, and `recipe_theme/*` pools can be replaced or extended by a pack. The catalyst defaults to an Ender Pearl.

Every configured structure tag also receives a category catalyst. Its 3x3 recipe takes each slot from a deterministically selected member recipe; the member choice is hashed from the world seed, pool tag, and slot, so different worlds can have different mixtures while reloads in one world remain stable. A normal datapack recipe whose result targets that `structure_pool` still takes precedence.

## Automatic structure catalogue

NeoForge creates `instancednotinfinite-server.toml` in the world's `serverconfig` directory. Its `catalogue` section is the primary way to add dungeons:

The generated catalogue is empty by default. The production JAR contains no built-in dungeon datapacks or structure templates, so a modpack or server must opt in through this config or its own datapack.

```toml
[catalogue]
structures = [
    "minecraft:woodland_mansion",
    "minecraft:ancient_city",
    "some_mod:forgotten_catacombs"
]
structureTags = ["within:instanced_dungeons"]
poolItemOnlyStructureTags = ["idas:rare"]
excludedStructures = ["some_mod:broken_structure"]
dungeonOverrides = [
    "minecraft:ancient_city;environment=CAVE;horizontal_padding=96;vertical_padding=64;weight=2",
    "some_mod:forgotten_catacombs;biomes=some_mod:ashen_caves,#some_mod:is_crypt;placement=NATURAL"
]
```

Direct IDs and all members of each structure registry tag are combined, deduplicated, and then filtered by `excludedStructures`. Every `structureTags` entry creates a category catalyst. Tags in `poolItemOnlyStructureTags` are also included automatically, but their members do not receive exposed exact catalysts or exact INI-generated recipes unless that structure is explicitly present in `structures`. This does not remove an ordinary datapack recipe supplied by another pack. Missing IDs, missing tags, invalid overrides, and structures that cannot expose a usable biome pool are logged and skipped individually; they do not stop the server or suppress valid entries. The automatic dungeon ID is the structure ID itself, so the first example is entered with `/dungeon enter minecraft:woodland_mansion`.

At catalogue rebuild time the resolver reads the structure type, allowed-biome `HolderSet`, terrain adaptation, generation step, and encoded authored start height. These supply inexpensive environment hints; no layouts are generated during startup. Stronghold, buried, and encapsulated structures retain their underground handlers. When an automatic instance is created, its actual structure generation runs against a flat sample in the selected biome. Ocean and river samples have separate sea-level and seabed heights. Actual piece bounds, placement-ground levels, and ocean-floor heightmap queries distinguish water-surface structures from seabed structures; water never rises with the structure's roof. Structures whose actual pieces sit at least eight blocks above the sampled surface use the floating handler. A post-placement check keeps ground under pieces that defer terrain projection until placement, such as igloos. No mod IDs or ship-name/type whitelist participates in this decision. Explicit environment overrides and advanced definitions remain authoritative.

Each `dungeonOverrides` string begins with a structure ID followed by optional semicolon-separated `key=value` fields. Supported fields are:

| Key | Value |
| --- | --- |
| `environment` | `SURFACE`, `UNDERGROUND`, `CAVE`, `FLOATING_ISLAND`, `OCEAN_SURFACE`, `UNDERWATER`, `NETHER_LIKE`, `END_LIKE`, or `CUSTOM` |
| `custom_strategy` | Registered terrain strategy resource ID; required when `environment=CUSTOM` |
| `biomes` | Comma-separated biome IDs and/or `#biome_tags`; replaces the inferred biome pool |
| `horizontal_padding`, `vertical_padding` | Non-negative guaranteed padding around the actual generated bounds |
| `maximum_radius` | Per-structure outer-envelope safety cap, also capped globally |
| `weight` | Positive no-argument selection weight |
| `placement` | `DIRECT` or `NATURAL` |
| `natural_mob_spawning` | `true` or `false` |
| `reentry` | `NEVER`, `WHILE_ACTIVE`, `UNTIL_COMPLETE`, or `ALWAYS_UNTIL_DELETED` |
| `cost_tier` | Forces a loaded tier ID, such as `instancednotinfinite:epic`, while retaining automatic ingredients. |
| `recipe_signature`, `recipe_theme`, `recipe_core`, `recipe_catalyst` | Explicit item ID or `#item_tag` for one recipe role. Any of these marks the generated recipe as an explicit per-dungeon override; omitted roles remain inferred. |

Overrides are partial: omitted properties remain inferred or use the global defaults. A config override only applies to automatic options.

The final precedence is Minecraft registry metadata, then global config defaults, then a matching partial config override, then an advanced datapack definition with the same dungeon ID. The datapack definition wins completely for that ID. `/dungeon inspect <structure>` shows the automatic source, biome count, classification and reason, padding, type, adaptation, generation step, placement, and variable-size status.

## Advanced datapack definitions

Place definitions at:

```text
data/<namespace>/instanced_dungeons/<path>.json
```

That file becomes dungeon ID `<namespace>:<path>`. This remains useful for NBT templates or options that need complete manual control. A malformed file is rejected individually with its dungeon ID, field, and reason; it does not invalidate other files. A definition whose dungeon ID equals an automatic structure ID replaces that automatic option.

Example:

```json
{
  "formatVersion": 1,
  "structure": "minecraft:igloo/top",
  "structureKind": "template",
  "weight": 10,
  "biomes": [
    { "id": "minecraft:snowy_plains", "weight": 3 },
    "minecraft:snowy_taiga"
  ],
  "height": { "min": 64, "max": 120 },
  "environment": { "type": "surface" },
  "terrain": {
    "horizontalPadding": 40,
    "verticalPadding": 28,
    "maximumRadius": 128
  },
  "portal": {
    "innerColor": "#12091FF5",
    "outerColor": "#79C7FF73"
  },
  "entry": { "x": 0, "y": 1, "z": 0, "yaw": 0, "pitch": 0 },
  "placement": { "mode": "direct" },
  "decoration": { "mode": "safe" },
  "allowNaturalMobSpawning": true,
  "reentry": "while_active"
}
```

### Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `formatVersion` | yes | Schema version. Version 1 is currently supported. |
| `structure` | yes | Resource ID of a registered worldgen structure or NBT structure template. |
| `structureKind` | no | `auto`, `worldgen`, or `template`; defaults to `auto`. `auto` prefers a registered worldgen structure when both exist. |
| `weight` | no | Positive weight used by no-argument random creation; defaults to 1. Explicit IDs ignore pool weights. |
| `biomes` | yes | Non-empty list of biome IDs, `#` biome tags, or weighted objects. |
| `height.min`, `height.max` | no | Source-world environmental context, not physical instance Y. Deep ranges influence the material palette. Defaults to 48..80. |
| `environment.type` | yes | Terrain strategy: `surface`, `underground`, `cave`, `floating_island`, `ocean_surface`, `underwater`, `nether_like`, `end_like`, or `custom`. |
| `environment.customStrategy` | for `custom` | Strategy ID registered by another mod through the Java API. |
| `terrain.horizontalPadding` | no | Blocks added around measured structure X/Z bounds. Uses the global default when absent. |
| `terrain.verticalPadding` | no | Blocks added around measured structure Y bounds. Uses the global default when absent. |
| `terrain.maximumRadius` | no | Per-definition radius limit; defaults to 192 and is capped by server config. Creation fails rather than truncating a structure that cannot fit. |
| `portal.innerColor` | no | Per-dungeon interior color in strict `#RRGGBBAA` form. Overrides automatic sampling and the global fallback for this layer. |
| `portal.outerColor` | no | Per-dungeon outer-glow color in strict `#RRGGBBAA` form. Overrides automatic sampling and the global fallback for this layer. |
| `entry.x/y/z` | no | Position relative to the measured structure origin. The nearest safe standing point within 12 horizontal and 32 vertical blocks is retained. |
| `entry.yaw/pitch` | no | Player rotation on entry. |
| `placement.mode` | no | `direct` or best-effort `natural`; defaults to `direct`. See limitations below. |
| `decoration.mode` | no | `none`, `safe`, or `full`; defaults to `safe`. See limitations below. |
| `allowNaturalMobSpawning` | no | Allows ordinary `NATURAL` and `CHUNK_GENERATION` mob spawning for this run, subject to the global switch; defaults to true. Spawners, structure mobs, commands, and spawn eggs are not suppressed. |
| `reentry` | no | `never`, `while_active`, `until_complete`, or `always_until_deleted`; defaults to `while_active`. |

`NEVER` permits the assigned player's first entry only. `WHILE_ACTIVE` and `UNTIL_COMPLETE` permit assigned players to return while the record is `ACTIVE` or `VACANT`; the separate names leave room for future pre-completion states. `ALWAYS_UNTIL_DELETED` also permits re-entry during the short completed exit delay. No policy can enter a level once unloading begins.

Two source-tree fixtures are used by development runs and GameTests but are deliberately excluded from the production JAR:

- `instancednotinfinite:surface_igloo` uses the vanilla `minecraft:igloo/top` NBT template and a surface island.
- `instancednotinfinite:cave_mineshaft` uses the vanilla worldgen mineshaft and a cave envelope.

See [surface_igloo.json](src/testFixtures/resources/data/instancednotinfinite/instanced_dungeons/surface_igloo.json) and [cave_mineshaft.json](src/testFixtures/resources/data/instancednotinfinite/instanced_dungeons/cave_mineshaft.json).

## Biome selection

Biome entries may be strings or weighted objects:

```json
"biomes": [
  "minecraft:plains",
  "#minecraft:is_forest",
  { "id": "example:maple_forest", "weight": 4 }
]
```

Tags are expanded through the registry at instance creation. If the same biome appears through several rules, it is included once with the highest matching weight. Missing IDs, empty tags, or an empty resolved pool produce a useful creation error. The selected actual biome is used by a `FixedBiomeSource`, so biome identity, colors, weather behavior, mechanics, and spawn data are not faked as plains; only the disposable terrain shape and material layers are controlled by this mod.

## Environment strategies

- `SURFACE` and `NETHER_LIKE` use solid terrain below the authored seating plane, tapering into a thin noisy rim before dissolving into void.
- `FLOATING_ISLAND` generates temporary flat biome terrain, places the structure, then removes that terrain while preserving authored blocks and block entities. Later chunks and restored instances remain void beneath the structure. Biome decoration is omitted from disposable terrain.
- `END_LIKE` retains a finite suspended island with smooth/dithered edges unless actual automatic placement selects the floating handler.
- `UNDERGROUND` and `CAVE` begin as finite, fully solid encasements with a three-dimensional dithered outer falloff. The selected structure pieces carve only their own authored interiors, followed by the configured arrival route.
- `OCEAN_SURFACE` and `UNDERWATER` retain a contained water basin with a sampled waterline and seabed stored independently of structure height. Both planes move with any necessary vertical fitting.
- Fully submerged automatic structures without a dry interior receive a dry surface arrival platform and return portal, leaving their authored geometry underwater.
- `CUSTOM` uses a finite strategy registered before creation with `InstancedDungeons.registerTerrainStrategy`.

The actual generated structure bounding box is expanded independently along X, Y, and Z for placement safety, entry search, world-border sizing, manifestation normalization, and validation against the configured maximum radius. Standard environments write their final controlled block layers directly and never overwrite already-loaded player changes. `NONE` omits biome placed features, `SAFE` runs only vegetation and top-layer modification, and `FULL` delegates the complete biome feature pass while still omitting density terrain and carvers. Floating instances omit decoration and clear only disposable terrain before entry becomes available. The selected dungeon structure is still the only explicitly registered structure start.

Floating entrances require a sky-exposed, walkable 3×3 area on an authored surface, preferring the nearest edge. The route outwards does not need an existing opening: the builder cuts through walls and other obstructions within the configured path width and headroom, preserving supported floor blocks. A bridge connects that landing to an arrival platform and return portal; bridge width and platform size have a minimum of three blocks even when configured smaller. This also applies to explicit `FLOATING_ISLAND` definitions. If no safe landing exists or the approach cannot fit inside the instance bounds, creation fails cleanly rather than placing a player on an unsafe ledge. Existing saved instances retain their original terrain model; recreate an instance to apply the new placement behavior.

Automatic surface, ocean-surface, Nether-like, and End-like structures detect a front from a ground-level door, fence gate, or walkable boundary opening. A door's facing axis and its nearest structure edge determine which side is outside. If no authored opening can be identified, a side-centered fallback is used. Cave and underground structures instead choose a deterministic-random walkable location connected to another walkable block inside the actual authored piece boxes, choose a direction that reaches the surrounding solid encasement, and cut the bridge/platform through that stone. The configured flat path reaches the structure, the arrival platform is centered the configured distance farther out, and the retained entry yaw faces back toward the structure. The platform extends as needed to the return portal's exact behind-entry offset. Grounded structures with measured template foundations cap the sampled terrain at their authored foundation, including partial burial below the roof, using the registered structure's effective placement properties: real piece bounds, jigsaw ground-level deltas, terrain adaptation, and transformed solid template occupancy. Unadapted structures sit below their lowest substantial foundation, while terrain-adapted structures keep their dense authored foundation buried. The 12-block vanilla terrain-adaptation envelope is never mistaken for building geometry. Advanced datapack definitions other than underground and floating environments retain their explicit relative entry and nearby-search behavior.

## Commands

`<uuid>` is the canonical dashed instance UUID. Commands that accept an instance UUID autocomplete all current instance records. Every `/dungeon` subcommand requires permission level 2, including player-oriented `join`, `leave`, `info`, and `complete` forms.

| Command | Permission | Behavior |
| --- | --- | --- |
| `/dungeon list` | operator | Lists automatic/datapack option counts and all non-final instance records. |
| `/dungeon create [dungeon]` | operator | Creates an instance without entering. With no ID, performs weighted pool selection. |
| `/dungeon spawn <dungeon> <pos> [north\|south\|east\|west]` | operator | Starts an exact-dungeon manifestation at loaded coordinates. Coordinates may be relative. |
| `/dungeon spawn-pool <pos> [north\|south\|east\|west]` | operator | Starts a manifestation using weighted catalogue selection. |
| `/dungeon enter [dungeon]` | operator | Queues generation across server ticks and enters when ready. With no ID, performs weighted pool selection. |
| `/dungeon join <uuid>` | operator | Enters an existing eligible instance. |
| `/dungeon leave` | operator | Returns to the saved origin or configured fallback. |
| `/dungeon complete [uuid]` | operator | Marks the current or specified run complete. |
| `/dungeon info [uuid]` | operator | Shows current/assigned or specified instance state. |
| `/dungeon inspect <dungeon>` | operator | Shows metadata and inference reasoning for an automatic option. |
| `/dungeon recipe explain <dungeon>` | operator | Shows recipe source/precedence, rarity and size scores, tier, themes, evidence, selected tag items, and fallbacks. |
| `/dungeon delete <uuid>` | operator | Returns occupants and queues staged unload plus deletion. |
| `/dungeon cleanup` | operator | Retries eligible `DELETE_PENDING` records. |
| `/dungeon reload` | operator | Reloads datapacks and rebuilds the automatic catalogue from current config values. Active snapshots are preserved. |
| `/dungeon manifestation list` | operator | Lists persistent manifestation records and progress. |
| `/dungeon manifestation info <uuid>` | operator | Shows one manifestation's lifecycle, dungeon, instance, origin, mode, and progress. |
| `/dungeon manifestation cancel <uuid>` | operator | Cancels generation/presentation, dither-dissolves any visible portal before removal, and queues safe instance cleanup. |
| `/dungeon manifestation finish <uuid>` | operator | Finishes only the visual clock; instance readiness still gates the portal. |
| `/dungeon manifestation test <dungeon> <mode>` | operator | Starts a mode-specific presentation at the command source for development. |

Every command that creates an instance accepts a final `lifecycle <openSeconds> <postVisitSeconds> <forceCollapseSeconds>` clause. This includes `create`, `enter`, exact `spawn`, `spawn-pool`, and `manifestation test`, including their optional dungeon/orientation variants. Each argument accepts `-1` with the same infinite semantics as item components and server config.

## Server configuration

NeoForge writes the server config as `instancednotinfinite-server.toml` under the world's `serverconfig` directory. In addition to the automatic catalogue described above, it includes:

- never-entered vacancy timeout in seconds (default `300`)
- post-visit vacancy timeout in seconds (default `60`)
- absolute forced-collapse timeout from activation in seconds (default `-1`, disabled)
- completed-instance exit delay
- cleanup retry interval
- default horizontal and vertical padding
- maximum terrain radius
- maximum concurrent instances
- global natural mob spawning switch
- fallback return dimension
- debug logging switch

The `approach` section controls surface-like and underground arrivals:

```toml
[approach]
distance = 12
platformRadius = 2
pathWidth = 3
pathClearanceHeight = 3
platformClearanceHeight = 4
platformBlock = "minecraft:crying_obsidian"
pathBlock = "minecraft:smooth_stone"
```

The default platform is 5x5. The bridge/path clears three air blocks above its floor and the platform clears four; both heights are configurable from 2 through 16. Blocks may be namespaced blocks from other installed mods, but they must resolve to a block with collision. Surface automatic definitions and every cave/underground definition expand their effective horizontal padding when necessary so the configured path and platform fit; the definition and global maximum-radius limits still apply.

The `recipes` section controls how automatic analysis runs; it does not hardcode the resulting economy:

```toml
[recipes]
automaticRecipeGeneration = true
regenerateRecipeCache = false
paletteInference = true
biomeInference = true
dimensionInference = true
nameInference = true
rarityInference = true
excludedRecipeBlocks = ["minecraft:bedrock", "#minecraft:logs"]
excludedAutomaticRecipeTargets = ["some_mod:disabled_structure", "#some_mod:disabled_pool"]
```

The corresponding tier and ingredient contents remain datapack-controlled. `excludedRecipeBlocks` accepts exact blocks and `#block_tags`; matching block items are removed from palette, theme, core, catalyst, and fallback candidate resolution. `excludedAutomaticRecipeTargets` accepts exact structure IDs plus `#structure_tags`; an excluded tag suppresses its category recipe and automatic recipes for every member. Neither option removes ordinary datapack recipes. Disabling one inference source leaves the others active and deterministic. `automaticRecipeGeneration=false` removes INI-generated recipes while retaining ordinary datapack recipes.

The expensive structure/template analysis is persisted in `config/instancednotinfinite/generated-recipes/`. Cache filenames and contents are deterministic and contain no world path or seed, so that folder can be shipped with a modpack. The cache is invalidated automatically when the active dungeon/source-structure set, recipe inference settings, matching template set, placement inputs, or any structure, structure-set, template-pool, or template resource used by the analysis changes. Normal recipes are still installed into Minecraft's recipe manager on every load, so crafting, recipe books, JEI, and EMI keep using the canonical runtime recipes. Set `regenerateRecipeCache=true` to force one rebuild on the next server start, world load, config reload, or `/dungeon reload`; it writes the replacement cache atomically and changes itself back to `false` only after that succeeds.

The `manifestation` section controls presentation and workload independently of dungeon definitions:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `enabled` | `true` | Enables item/API/command manifestations and portals. |
| `hologramMaxWidth`, `hologramMaxHeight`, `hologramMaxDepth` | `3.0` | Fit box for the world miniature. |
| `defaultAnimationMode` | `RANDOM_MODE` | Default deterministic reveal mode. |
| `allowedRandomModes` | six structured modes | Eligibility list for `RANDOM_MODE`. |
| `generationTimeBudgetMillis` | `4.0` | Primary global generation time budget per server tick. |
| `maximumBlockOperationsPerTick` | `5000` | Secondary operation safety cap. |
| `animationDurationMinimumTicks`, `animationDurationMaximumTicks` | `100`, `400` | Deterministic duration range. |
| `collapseDurationTicks` | `40` | Final collapse before portal opening. |
| `portalGrowthDurationTicks` | `30` | Small-to-full portal growth duration. |
| `portalCloseDurationTicks` | `30` | Final blockwise portal-dissolve duration. |
| `renderDistance` | `64` | Server sync and client presentation range. |
| `terrainAlpha` | `0.35` | Retained compatibility setting; structure-only snapshots contain no terrain blocks. |
| `structureAlpha` | `1.0` | Structure hologram opacity; lower values deliberately allow overlapping rooms to blend. |
| `preparationParticleStyle` | `SPIRAL` | Immediate pre-hologram cue: `NONE`, `RING`, `SPIRAL`, or `CONVERGING`. |
| `preparationParticleColor` | `#2AAAFF` | Dust particle color in `#RRGGBB` format. |
| `preparationParticleRate` | `3` | Particles emitted per client tick; `0` disables emission without changing the selected style. |
| `preparationParticleScale`, `preparationParticleRadius` | `0.7`, `1.75` | Particle size and horizontal area around the future hologram/portal. |
| `maximumSnapshotBlocks` | `50000` | Hard retained structure-shell cap. |
| `iconResolution`, `iconCacheLimit` | `256`, `128` | Rasterized 3D-miniature icon size (16..2048) and LRU entry cap. This is independent of the full-resolution world hologram; very high values consume substantial GPU memory per cached icon. |
| `poolItemSwapIntervalSeconds` | `5` | Seconds between stable member slots for structure-pool catalyst GUI icons and in-world miniatures (1..3600). |
| `portalWidth`, `portalHeight`, `portalDepth` | `1.5`, `2.5`, `0.35` | Rendered and interactive portal volume dimensions; depth `0` produces a thin usable plane. |
| `portalMinimumWidth`, `portalMinimumHeight`, `portalMinimumDepth` | `1.0`, `2.0`, `0.35` | Minimum visual dimensions reached as the timer expires; the bottom edge remains fixed. Each value is capped by its full portal dimension, and minimum depth therefore matches full depth by default. |
| `portalInnerColor` | `#010104F5` | Interior `#RRGGBBAA` color, including opacity. |
| `portalOuterColor` | `#2AAAFF73` | Pulsing glow `#RRGGBBAA` color, including base opacity. |
| `derivePortalInnerColorFromBiome` | `true` | When no datapack inner color exists, derive its RGB from the selected dungeon biome's fog color. This replaces the former opt-in key. |
| `derivePortalOuterColorFromBiomeFog` | `true` | When no datapack outer color exists, derive its RGB from the selected dungeon biome's fog color. |
| `portalInnerTransparency` | `0.04` | Transparency applied to an automatically derived inner color: `0.0` is opaque and `1.0` is invisible. Explicit hex colors retain their alpha. |
| `portalInnerBiomeBrightness` | `0.0` | Shades the sampled biome RGB from `-1.0` (black), through `0.0` (unchanged), to `1.0` (white). Intermediate values linearly darken or lighten it. |
| `portalOuterOpacityPercent` | `45` | Opacity applied to an automatically derived outer color. Explicit hex colors retain their alpha. |
| `destinationPortalBehindEntryBlocks` | `2` | Return-portal offset opposite the player's arrival facing. |
| `sourcePortalExitOffsetBlocks` | `4` | Where a destination return portal places the player outward from the source entrance portal. |
| `portalLifetimeMinutes` | `0` | Optional portal timeout; zero follows instance lifetime. |
| `portalHudDistance` | `16` | Maximum targeting distance for the icon, name, and close-countdown panel. |
| `completionOffering` | `minecraft:blaze_powder` | Exact item ID or `#item_tag`; one matching dropped item completes the exact instance bound to either open portal endpoint. |
| `catalystConsumptionPolicy` | `ON_SUCCESS` | `ON_ACTIVATION`, `ON_SUCCESS`, or `NEVER`. |
| `refundOnFailure` | `true` | Failure refund for `ON_ACTIVATION`; `ON_SUCCESS` always refunds. |

Client tooltip presentation is stored separately in `instancednotinfinite-client.toml`:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `jadeIntegration` | `true` | Use Jade for source/return portal names, loading progress, generated icons, and close countdowns when Jade is installed. |
| `builtInPortalTooltips` | `true` | Enable the built-in fallback panel when Jade is unavailable or its integration is disabled. |

Jade and the built-in fallback target the rendered effect itself: the initial loading-particle volume, the animated hologram volume, and the full portal volume of any loaded entry or return endpoint. Looking at the portal's anchor block is not required. When Jade is active, Instanced Not Infinite performs this visual trace first and supplies Jade with a synthetic block target, so Jade can display the current information even when the crosshair points at portal or hologram pixels in otherwise empty air.

NeoForge's config watcher automatically schedules a safe catalogue rebuild when this server config changes. `/dungeon reload` can also reload the datapack layer and rebuild the catalogue explicitly. New instances use the rebuilt options; every active instance retains its snapshotted definition, selected biome, seed, actual bounds, padding, generation-surface height, entry, terrain plan, and resolved portal appearance.

## Persistence, cleanup, and recovery

The overworld stores instance records and player return locations in separate `SavedData` files. Records include the definition snapshot, selected structure kind, biome, seed, bounds, origin, retained generation-surface height, entry position and yaw, assigned player UUIDs, resolved three-part lifecycle timing, activation timestamp, other timestamps, and lifecycle state.

Lifecycle states are:

```text
CREATING -> ACTIVE <-> VACANT -> COMPLETED -> UNLOADING -> DELETE_PENDING -> DELETED
                    \                         /
                     -------- FAILED --------
```

On startup:

- `ACTIVE` and `VACANT` dimensions are re-registered from their snapshots and existing chunks are loaded.
- open and closing manifestation portals are restored with their persisted dimensions, colors, endpoint UUID, and close outcome.
- interrupted `CREATING`, `FAILED`, `COMPLETED`, and `UNLOADING` records are conservatively moved toward cleanup.
- `DELETE_PENDING` jobs are retried.
- a player whose saved logout dimension was temporary is returned using the external return record; if that dimension cannot load, normal-world placement wins and the stale return is cleared.
- an already open manifestation portal is restored and rebound to its instance.
- an interrupted generation or collapse is failed conservatively, its partial instance is queued for guarded cleanup, and any owed catalyst refund remains claimable by its owner on login. Snapshot block data is intentionally transient rather than bloating world saves.

Filesystem deletion never receives a path from dungeon JSON. The backend computes the dedicated root `world/dimensions/instancednotinfinite/instances`, requires one lowercase 32-hex UUID child, rejects links/path escapes, and validates `.instancednotinfinite-instance` contents before deletion. Cleanup runs off the server thread only after Minecraft has saved, detached, unloaded, and closed the level. Locked-file failures leave the record pending for retry. Distant Horizons is intentionally excluded only from `instancednotinfinite:instances/*`: distant LOD storage has no value for these finite temporary levels, and avoiding its per-level SQLite connection lets `DELETE_PENDING` finish live while DH remains active in ordinary dimensions. On the client, DH's LOD, shader-deferred, fade, fog-override, framebuffer, and block-update presentation hooks are likewise suspended while the active level is temporary, then resume automatically on return to a permanent dimension. This does not rewrite the player's global DH configuration.

## Java API and events

The stable instance entry point is `com.cappleapple.instancednotinfinite.api.InstancedDungeons`. It supports explicit or weighted creation, create-and-enter, joining, leaving, completion, instance queries, player-instance queries, custom terrain strategy registration, and an exact-dungeon `spawnManifestation` convenience method. The trigger-independent presentation entry point is `com.cappleapple.instancednotinfinite.api.DungeonManifestationApi`; it accepts an exact `DungeonTarget.dungeon(...)` or `DungeonTarget.configuredPool()`, orientation and animation options, and provides cancel/get/get-at operations. World mutation calls must run on the Minecraft server thread.

NeoForge events are posted for:

- `DungeonInstanceCreatedEvent`
- `DungeonPlayerEnteredEvent`
- `DungeonCompletedEvent`
- `DungeonInstanceDeletingEvent`
- `DungeonManifestationStartingEvent`
- `DungeonManifestationReadyEvent`
- `DungeonPortalOpenedEvent`
- `DungeonPortalClosedEvent`

This is intentionally small enough for a future KubeJS or quest-mod adapter without making either a dependency.

## Known limitations

- `DIRECT` is reliable for NBT templates and uses Minecraft's full template placement, including block entities, loot NBT, entities, and template placement behavior. Registered worldgen structures use their normal generated start and pieces. `NATURAL` is best-effort because arbitrary third-party structures may reject the constrained generator or assume normal neighboring terrain.
- Structure-block DATA markers and jigsaw metadata are not yet converted into generic entry/exit/boss markers. Relative entries work for every structure.
- Ambient structure-set generation is always disabled in instance dimensions; there is no config switch to enable unrelated structure spawning there.
- Surface palettes use common NeoForge biome categories and cover grass, desert/sandy, badlands, mushroom, and stony surfaces. Arbitrary modded surface-rule graphs are not copied; unclassified land biomes receive grass and dirt.
- `SAFE` decoration intentionally includes only vegetation and top-layer modification. Use `FULL` only when a pack accepts the cost of the selected biome's complete placed-feature list; neither mode restores density terrain or carvers.
- One-off worldgen structure placement happens after required chunks become live. Some structures whose fluid behavior relies specifically on the normal proto-chunk post-processing stage may need an NBT-template definition instead.
- Manifestations and every `/dungeon enter` variant use the same resumable, server-thread generation job and share the configured global time budget. Command entry waits until the platform and return portal are ready; pending requests cancel if the player disconnects, dies, changes dimension, or the instance is deleted. Duplicate pending entries for one player are rejected. `/dungeon create` and the older synchronous Java creation APIs retain their existing behavior. An individual structure start, chunk, or finalization step can still exceed the budget because Minecraft worldgen work cannot be interrupted mid-step.
- Exact catalyst thumbnails require that the client has received one complete snapshot at least once for that world. JEI/EMI exact-target entries begin with the biome-colored portal cube, update after that dungeon has been manifested, and then restore from the world-scoped client cache across reconnects; unbound weighted-pool catalysts cannot know their dungeon before server selection and retain the animated portal-cube fallback.
- Runtime level registration necessarily touches version-sensitive Minecraft internals. That code is isolated behind `DynamicLevelBackend` and one accessor mixin. DynamicDimensions 0.9.1 was evaluated as a compatible future adapter, but the built-in backend is retained because its complete lifecycle is directly tested and avoids another required mod.

## Verification

Run unit tests and build:

```powershell
./gradlew.bat test build
```

Run the NeoForge integration suite:

```powershell
./gradlew.bat runGameTestServer
```

For an isolated visual client while the development server is running:

```powershell
./gradlew.bat -PquickPlayServer=localhost runClient
```

Add `-PjeiRuntime=true` or `-PemiRuntime=true` to validate one viewer independently. `-PrecipeViewerRuntime=true` includes both; EMI's optional JEMI bridge may emit duplicate-recipe diagnostics when both viewers index the same vanilla data.

The client uses `run-client/`, so its options and logs do not contend with the dedicated server's `run/` world.

The integration suite covers automatic registry/biome discovery, fixed and variable structure bounds, roof-burial prevention, terrain-adaptation-aware foundation depth, dominant template-foundation detection, authored-foundation terrain seating, controlled finite generator selection, biome-aware palettes and safe surface decoration, dithered solid-to-void falloff, detected-front and authored-underground platform/path/return-portal construction with independent clearances, fully solid underground encasement, ocean placement, rectangular padding and elongated bounds, two simultaneous isolated dimensions, two players sharing and re-entering one instance, disconnect/reconnect return recovery, staged unload, marker-verified deletion, cleanup completion, incremental manifestation generation, per-item lifecycle components, controlled-terrain-differenced structure-only snapshots, biome-fog inner/outer portal colors with shading/transparency and fallback precedence, portal opening and three-axis shrink/dither closure, rotated rendered-shape activation outside the anchor block, exact item/tag completion offerings outside the anchor block, source-side return offset, exact-instance entry, matching destination-portal appearance and return, and manifestation cleanup.

For implementation research, runtime findings, and compatibility notes, see [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT. See [LICENSE](LICENSE).
