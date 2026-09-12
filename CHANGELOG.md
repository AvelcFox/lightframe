# Changelog

All notable changes to the **LightFrame** mod are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
