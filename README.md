# LightFrame 💡🎨
**True Dynamic RGB Lighting Engine for Minecraft (Fabric 1.20.1 & 1.21.1)**

LightFrame brings **genuine multi-colored dynamic lighting** to Minecraft without requiring heavy shaderpacks. Light sources have customizable color, intensity, and radius, physically coloring blocks and entities. Multiple light sources seamlessly blend in real time (e.g., placing red and blue lights together creates a natural purple illumination).

> ⚠️ **Disclaimer:** LightFrame is currently in active development. The author does not guarantee flawless compatibility across every third-party modpack or custom mod setup. Please back up your worlds regularly.

---

## ✨ Features

* 🌟 **Pure Vanilla Graphics Support** — No shaders, OptiFine, or Iris required! The colored lighting engine operates directly on top of Minecraft's native rendering pipeline.
* ⚡ **Sodium & Iris Compatibility (v0.2.0+)** — Native chunk meshing pipeline integration for Sodium. Enjoy full block colored lighting with maximum frame rates.
* 🔦 **Dynamic Handheld Lighting** — Hold a colored torch in your main hand or off-hand to illuminate your path smoothly as you move, with zero lag or stutter.
* 🕯️ **16 Colored Torches** — Craftable torches for every vanilla dye color with floor and wall variants, authentic flame animations, and colored spark particles.
* 👥 **Entity & Mob Tinting** — Mobs, players, armor, and dropped items reflect the surrounding colored light.
* 🌈 **Physical Color Mixing** — Advanced BFS light propagation across red, green, and blue channels with smooth falloff and block translucency calculations.
* ⚙️ **Two-Tier Lighting Model** — Simultaneously applies vertex color multipliers and vanilla lightmap boosting, ensuring vibrant colors remain visible even in pitch-black caves.
* 🛠️ **Developer API** — Clean and simple Java API (`ColorLightAPI`) for third-party mod integration.
* 📊 **Built-In Debug Tools** — Toggleable on-screen HUD and 3D light sphere visualization (press `K`).

---

## 🛠️ Crafting Recipes

Craft any colored torch right in your inventory or crafting table:
* **1 Vanilla Torch** + **1 Dye (any color)** ➔ **1 Colored Torch**

Available in all 16 Minecraft colors: White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, and Black.

---

## 🎮 Commands & Controls

### Keybinds
* **`K`** — Toggle debug overlay (shows active lights count, FPS, dirty sections, and 3D light radius wireframes).

### Operator Commands (Permission Level 2 required)
```bash
# Create a point light source in front of your crosshair:
/create_light <color> [radius] [intensity]

# List all active light sources in the current dimension:
/lightframe list

# Remove a specific light source:
/lightframe remove <uuid>

# Clear all light sources in the current world:
/lightframe clear

# Reload configuration from disk:
/lightframe reload

# Toggle debug mode:
/lightframe debug on|off
```

> **Color formats:** Use color names (`red`, `green`, `blue`, `white`, `purple`, `yellow`, `cyan`, `orange`, `pink`) or hex codes (`#ff00aa`).

---

## ⚙️ Configuration (`config/LightFrame.json`)

The config file is generated automatically on first launch:

```json
{
  "enableRGBLighting": true,
  "maxLightSources": 128,
  "maxLightRadius": 32,
  "lightingQuality": "MEDIUM",
  "updateBudget": 2.0,
  "debugMode": false,
  "boostVanillaLight": true,
  "affectGameplayLighting": true,
  "tintStrength": 1.0,
  "tintEntities": true,
  "tintBlockEntities": true,
  "maxSectionsRebuiltPerTick": 12,
  "irisFallbackKeepDynamicLight": true,
  "logIrisFallback": true,
  "debugLogIntervalSeconds": 5
}
```

---

## 💻 Developer API (`ColorLightAPI`)

Easily spawn and manage custom colored lights from your own mod:

```java
import dev.puffspark.lightframe.api.ColorLightAPI;
import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.LightColor;

// Spawn a colored light source in the world:
ColorLight light = ColorLightAPI.create(world, pos, LightColor.RED, 10, 1.0f);

// Dynamically adjust properties:
light.setColor(LightColor.PURPLE);
light.setIntensity(1.5f);
light.setPosition(newPos);

// Temporarily disable or delete:
light.setEnabled(false);
light.remove();
```

---

## 👥 Credits & License

* **Developer:** Avelc
* **Team:** PuffSpark
* **License:** [GNU Lesser General Public License v3.0 or later (LGPL-3.0-or-later)](LICENSE)
