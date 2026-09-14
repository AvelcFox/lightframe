# Changelog

All notable changes to the **LightFrame** mod are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.3.0+1.21.1] - 2026-09-14

### Added
* **Directional / Cone Light (Направленный свет):**
  * Added directional cone light sources with position, forward direction vector, and inner/outer cone cutoff angles.
  * Implemented smooth angular Hermite falloff between inner angle (full brightness) and outer cutoff angle.
  * Added public API method `ColorLightAPI.createDirectional(...)` and interface methods `isDirectional()`, `getDirection()`, `setDirection()`, `getInnerAngle()`, `getOuterAngle()`, `setConeAngles()`.
  * Added testing command `/create_cone_light <color> [radius] [intensity] [innerAngle] [outerAngle]`.
  * Added network synchronization for directional parameters across server and client.
* **Colored Light Bloom (Мягкое цветное свечение):**
  * Added an independent additive atmospheric bloom / glow pass rendering soft colored coronas around bright light sources and handheld torches.
  * Built completely decoupled from voxel light propagation and screen blending (does not affect voxel calculations).
  * Smooth camera distance fade preventing camera clipping artifacts.
  * Configurable via Mod Menu toggle (`enableBloom`) and strength slider (`bloomIntensity`).
* **Entity Directional Lighting (Направленное освещение сущностей):**
  * Added 6-axis ambient lighting cube (`AmbientLightCube`) evaluating surface normals for player and mob model geometry.
  * Faces oriented towards light sources receive rich directional diffuse tinting, while back faces receive realistic shadow bounces.
  * Configurable via Mod Menu toggle (`entityDirectionalLighting`).
* **Mod Menu Configuration Screen:**
  * Added fully featured in-game configuration GUI accessible directly from Mod Menu.
  * Full English and Russian localization with detailed tooltips for all settings.

### Fixed
* **Replay Mod & Paused State Lighting (Flashback / ReplayMod):**
  * Fixed terrain and block colored lighting not rendering in replay recordings or when the game is paused.
  * Added `MinecraftClientMixin` to process queued light propagation and flush dirty chunk section rebuilds on every rendered frame (`MinecraftClient.render`) even when regular client ticks are frozen.
  * Fixed chunk section dirtying invoker mismatch in Sodium (`cl$scheduleChunkRender(x, y, z, boolean)`), ensuring entire 16x16x16 chunk sections are marked dirty for meshing.
  * Added `ClientTorchScanner` to automatically detect placed colored torches in chunk sections (`ChunkSection.hasAny(...)`) and timeline scrubbing, allowing torches placed on the map to emit full colored lighting in offline replays and camera flybys without requiring server-side sync.
  * Increased default `maxLightSources` limit from 128 to 512 for large scenes and cinematic recording.
  * Added `EngineRegistry.clearSourcesLocal(world)` to properly reset stale light sources when seeking through timeline keyframes or receiving bulk sync packets in replays.

---

## [0.2.0+1.21.1] - 2026-09-12

### Added
* **Sodium & Iris Compatibility:**
  * Implemented `SodiumBlockRendererMixin` hooking directly into Sodium's chunk mesher pipeline (`BlockRenderer.processQuad`).
  * Full terrain block colored lighting support when Sodium and/or Iris are installed.
  * Fast chunk section culling (`hasLightNearSection`) preventing overhead on unlit chunks during chunk compilation.
  * Dynamic conditional mixin loading through `LightFrameMixinPlugin` (zero overhead and zero crash risk if Sodium is absent).
* **Multi-threaded Dynamic Lighting:**
  * Replaced dynamic light collection with lock-free `volatile DynamicLight[]` snapshots, allowing Sodium's background chunk worker threads to safely sample held torches without locks or race conditions.

### Fixed
* **Entity & Mob Tinting in Minecraft 1.21.1:**
  * Fixed `TintingVertexConsumer.color(int argb)` to correctly unpack, multiply, and repack ARGB colors, enabling full colored light tinting on mob models, player models, and armor.
  * Corrected vertex lightmap bit-shifting for Minecraft 1.21 (`<< 4`), ensuring light boost works properly on entities in dark environments.
* Fixed Mixin injection point specifier from invalid `INVOKEVIRTUAL` to `INVOKE`.

---

## [0.1.0+1.21.1] - 2026-09-12

### Added
* **Minecraft 1.21.1 Port:**
  * Full port from 1.20.1 to Minecraft 1.21.1 and Java 21.
  * Updated networking payload system to Fabric Networking v1 `CustomPayload` API.
  * Updated block render layers and creative tab registrations for 1.21.1.
  * Retained pure vanilla rendering support without Sodium.

---

## [0.1.0+1.20.1] - 2026-09-11

### Added
* **Core RGB Lighting Engine:**
  * Dedicated BFS-based RGB light propagation per color channel (Red, Green, Blue).
  * Sparse chunk storage in 16x16x16 sections with fast bitmask dirty region tracking.
  * Two-tier lighting model: vertex color tinting blended with vanilla block light boost.
  * Real-time dynamic lights for held colored torches and moving entities.
* **Content:**
  * 16 colored torches (White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, Black).
  * Custom crafting recipes for all torch colors using vanilla dyes.
  * Wall-mounted and floor-standing models with matching flame and smoke particles.
* **API & Tools:**
  * Developer API (`ColorLightAPI`) for third-party mod integration.
  * In-game operator commands: `/create_light`, `/lightframe list`, `/lightframe remove`, `/lightframe clear`.
  * Debug HUD and in-world light radius visualization toggleable via keybind `K`.
  * Iris compatibility fallback detection when shaderpacks are active.
