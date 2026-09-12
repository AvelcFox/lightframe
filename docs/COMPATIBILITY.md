# LightFrame Compatibility Matrix

LightFrame modifies how blocks, fluids, entities, and block entities are tinted and lit. Below is the tested compatibility status with popular rendering and performance mods.

---

## 🎮 Renderer & Performance Mods

| Mod | Minecraft Version | Compatibility Status | Notes |
| :--- | :--- | :--- | :--- |
| **Vanilla Minecraft** | 1.20.1, 1.21.1 | 🟢 **Full Support** | Native vertex consumer tinting for blocks, fluids, and entities. |
| **Sodium** | 1.21.1 (0.8.x+) | 🟢 **Full Support** | Integrated via `SodiumBlockRendererMixin` with proper chunk terrain tinting. |
| **Sodium** | 1.20.1 (0.5.x) | 🟢 **Full Support** | Supported via standard pipeline hooks. |
| **Iris Shaders** | 1.20.1, 1.21.1 | 🟡 **Compatible** | Supported with internal/simple shader pipelines. Complex deferred/path-tracing shaders that override lighting buffers might override custom vertex colors. |
| **Indium** | 1.20.1, 1.21.1 | 🟢 **Compatible** | Does not interfere with LightFrame tinting. |
| **Embeddium** | 1.20.1 | 🟢 **Compatible** | Works similarly to Sodium. |
| **Canvas Renderer** | Any | 🔴 **Untested / Incompatible** | Custom shader pipeline replaces vanilla vertex coloring. |

---

## 🛠️ Server & Multiplayer Compatibility

- **Pure Server (Dedicated):**
  LightFrame handles light state, validation, storage, and networking. Light sources are persistent across server restarts and synced to joining clients.
- **Client Without Mod on Server:**
  If a player connects without LightFrame installed, they simply won't see the custom RGB lights (packets are safely ignored or filtered).
- **Singleplayer:**
  Works seamlessly with integrated server and client.
