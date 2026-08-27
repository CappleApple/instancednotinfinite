# Changelog

## 1.1.3 — 2026-08-27

### Fixed

- `/dungeon enter` now generates across server ticks using the same generation job and shared time budget as manifestation catalysts, then teleports the player only when the instance is ready. Named, random, and lifecycle-override variants all use this path.
- Pending command entries reject duplicate requests and cancel safely if the player disconnects, dies, changes dimension, or the instance is deleted.

## 1.1.2 — 2026-08-27

### Fixed

- Self-protecting structures such as Sky Arena now finish placing all chunks without their own protection rejecting generated blocks.
- Grounded structures with measured foundations now lower excessive flat terrain even when it buries only part of the building, fixing Burning Arena's lower floors being underground.
- Generated structures, paths, terrain cleanup, and return portals use bounded construction writes while normal gameplay protection remains unchanged.

## 1.1.1 — 2026-08-27

### Fixed

- Floating entrance routes now carve openings through obstructing walls instead of rejecting safe surface landings that lack a clear route outwards.
- Entrance carving preserves supported landing floors and stays within the configured passage width and headroom.

## 1.1 — 2026-08-27

### Added

- Added generic sky-structure placement with temporary flat biome terrain, terrain removal that preserves authored blocks, and a safe 3×3 edge landing connected to the entrance platform and return portal.
- Falling below the minimum build height in an instance now returns players to their saved entrance portal in its original dimension, clearing fall distance and falling velocity.

### Changed

- Aquatic placement now retains a sampled waterline and seabed independently of structure height, without a mod-specific ship whitelist.
- Fully submerged structures without a dry interior receive a surface arrival platform while their authored geometry remains underwater.
- Automatic environment hints are refined from generated placement, with a post-placement check for structures that defer ground projection.

## 1.0.3 — 2026-08-27

### Fixed

- Fixed Awesome Dungeon Ocean frigates and brigantines being classified as underwater structures despite their sea-level placement.
- Preserved those ships' authored waterline and hull depth instead of raising the ocean above the entire ship.

## 1.0.2 — 2026-08-27

### Changed

- Inventory and recipe-viewer catalyst placeholders now use the existing biome-colored portal cube until their generated icons are ready.
- Structure-pool inventory placeholders retain each member's resolved portal colors during icon crossfades.

## 1.0.1 — 2026-08-26

### Added

- Added a portable generated-recipe analysis cache under `config/instancednotinfinite/generated-recipes/`.
- Added the one-shot `recipes.regenerateRecipeCache` option, which resets itself after a successful rebuild.

### Changed

- Automatic portal recipes now reuse cached structure profiles until their selected structures, inference inputs, or analyzed datapack resources change.

## 1.0 — 2026-08-21

### Added

- Added a configurable exact item or `#item_tag` portal-completion offering, defaulting to blaze powder.
- Added the supplied artwork as the mod-list logo and as the placeholder for not-yet-generated 2D catalyst icons.

### Changed

- Completion offerings now resolve the exact persisted active or vacant instance through the complete rendered portal volume and consume one item only after completion succeeds.
- Structure-pool animations retain stable member slots while generated icons and miniatures populate their caches.
- Held, dropped, framed, and other non-GUI catalyst contexts retain the animated resolved-color portal cube until a completed 3D miniature is ready.

### Fixed

- Fixed custom NeoForge structure-placement wrappers such as `integrated_api:stronghold` failing `/dungeon reload` during rarity inference.
- Fixed stronghold-generation entries being treated as surface structures instead of underground structures with their authored absolute start height.
- Fixed structure-pool catalysts remapping their current member while caches populate.
- Fixed the full-scale 180-degree rotation jump at structure-pool animation interval boundaries.
- Fixed the supplied 2D placeholder replacing the held and dropped 3D portal effect.

## 0.x development history — 2026-08-21

### 0.8.1

#### Fixed

- Read placement frequency and exclusion metadata from structure-set datapack JSON without re-encoding loader-wrapped structure placements.
- Classified stronghold-generation structures as underground and retained constant absolute start heights.

### 0.8

#### Added

- Added depth-aware portal contraction, biome-derived inner colors, biome-category surface materials, safe decoration, solid underground encasement, and persistent per-item lifecycle overrides.

#### Changed

- Restricted the complete `/dungeon` command tree to permission level 2.

### 0.7.3

#### Fixed

- Made the mod's rendered-effect ray trace authoritative for Jade portal, preparation-particle, and hologram targets.

### 0.7.2

#### Changed

- Made portal dither fragments true cubes and reduced the held and dropped portal fallback to quarter scale.

### 0.7.1

#### Changed

- Restored completed 3D dungeon miniatures for in-world catalyst contexts and used the six-face resolved-color portal cube while a miniature is unavailable.
- Expanded Jade targeting to the complete rendered portal and loading volumes.

### 0.7

#### Added

- Added optional Jade-first loading and portal information with an independently configurable built-in fallback.

#### Changed

- Added synchronized resolved-color portal previews and eased inward-to-edge fragment spawning.

### 0.6.1

#### Added

- Added a configurable interval for structure-pool GUI and 3D miniature transitions.

### 0.6

#### Added

- Added named structure-tag pool catalysts, deterministic mixed recipes, ingredient and automatic-recipe denylists, and synchronized pool visuals.

### 0.5

#### Added

- Added ordinary targeted-catalyst recipes with datapack precedence, inferred roles and tiers, palette analysis, tag ingredients, reload-safe profiling, and `/dungeon recipe explain`.

### 0.4.3

#### Changed

- Made surface terrain dissolve per voxel, improved sampled portal colors, separated GUI sprites from in-world miniatures, and rendered cached miniatures behind intersected portals.

### 0.4.2

#### Changed

- Replaced disposable vanilla density terrain with bounded environment strategies, smooth deterministic noise, and tapered dithered edges.

### 0.4.1

#### Fixed

- Bounded terrain generation to the resolved dungeon envelope, added deterministic structure-start retries, corrected Nether seating, clamped vertical placement, and captured complete build-height changes.

### 0.4

#### Added

- Added vanilla terrain, surface rules, carvers, selected-biome features, layered portal-color resolution, and persisted matching endpoint colors.

### 0.3.4

#### Fixed

- Excluded temporary instance dimensions from Distant Horizons client presentation without changing ordinary-world behavior.

### 0.3.3

#### Added

- Added save-scoped persistent miniature geometry caches.

#### Fixed

- Excluded temporary instance dimensions from Distant Horizons lifecycle tracking so their files can be deleted live.

### 0.3.2

#### Fixed

- Corrected elevated ocean-structure classification and authored sea-level translation.

#### Changed

- Rendered completed exact catalysts as retained depth-tested geometry with humanized names.

### 0.3.1

#### Changed

- Made snapshots structure-only surface shells, normalized presentation fitting, added persistent exact return endpoints, and moved holograms to time-sliced face-culled GPU buffers.

### 0.3

#### Added

- Added trigger-independent manifestations, incremental generation, layered holograms, generated catalyst icons, failure-safe catalyst consumption, and UUID-bound portals.

### 0.2.2

#### Fixed

- Derived terrain-adapted structure seating from actual authored pieces and solid foundations while preserving the entrance platform.

### 0.2.1

#### Fixed

- Derived tall-structure ground planes from transformed solid template occupancy rather than adaptation-expanded bounds.

### 0.2

#### Added

- Added ocean-surface placement, large-layout reseating, inferred entrances, arrival platforms, and configurable approach paths.

### 0.1.2

#### Fixed

- Retained the generated surface height for surface entries and grounded Nether-like terrain.

### 0.1.1

#### Fixed

- Grounded surface terrain to the instance floor and prevented entry search from selecting unrelated caves below structures.

### 0.1

#### Added

- Added automatic catalogue discovery and live watched-config catalogue rebuilding.

### 0.0

#### Added

- Added the initial config-driven framework for finite, temporary, UUID-based dungeon dimensions, deterministic generation, player assignment and return, persistence, and guarded cleanup.
