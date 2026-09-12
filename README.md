# LightFrame 💡🎨

<div align="center">

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/lightframe)
[![GitHub release](https://img.shields.io/github/v/release/AvelcFox/lightframe?style=for-the-badge&logo=github&color=blue)](https://github.com/AvelcFox/lightframe/releases)
[![Minecraft Versions](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-red?style=for-the-badge&logo=minecraft&logoColor=white)](https://github.com/AvelcFox/lightframe)
[![Fabric Loader](https://img.shields.io/badge/Loader-Fabric-lightgrey?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0-orange?style=for-the-badge)](LICENSE)

**True Dynamic Multi-Colored RGB Lighting Engine for Minecraft**

[📖 Documentation & Wiki](docs/README.md) • [🗺️ Roadmap & Tasks](ROADMAP.md) • [🐛 Report Bug](https://github.com/AvelcFox/lightframe/issues/new?template=bug_report.yml) • [💡 Request Feature](https://github.com/AvelcFox/lightframe/issues/new?template=feature_request.yml) • [🤝 Contributing](CONTRIBUTING.md)

</div>

---

## 🌟 Overview

LightFrame brings **genuine multi-colored dynamic lighting** to Minecraft without requiring shaders or high-end graphics cards. Light sources feature customizable RGB colors, intensity multipliers, and adjustable radius, physically tinting surrounding blocks, fluids, and entities. When multiple lights intersect, their colors naturally blend in real time (e.g. red + blue = purple).

> ⚠️ **Disclaimer:** LightFrame is actively developed. Please test on staging or back up your worlds regularly before installing in production environments.

---

## 🌿 Version & Branch Matrix

| Version | Branch | Status | Download |
| :--- | :--- | :--- | :--- |
| **Minecraft 1.21.1** | [`1.21.1`](https://github.com/AvelcFox/lightframe/tree/1.21.1) | 🟢 **Active / Latest** | [Download v0.2.1+1.21.1](releases/lightframe-0.2.1+1.21.1.jar) |
| **Minecraft 1.20.1** | [`1.20.1`](https://github.com/AvelcFox/lightframe/tree/1.20.1) | 🟢 **LTS Supported** | [Download v0.1.0+1.20.1](releases/lightframe-0.1.0+1.20.1.jar) |
| **Main / Docs** | [`main`](https://github.com/AvelcFox/lightframe/tree/main) | 📘 **Wiki, Issues & Roadmap** | — |
| **Development** | [`dev`](https://github.com/AvelcFox/lightframe/tree/dev) | 🧪 **Experimental** | — |

---

## ✨ Features

* 🌟 **Pure Vanilla Graphics Support** — Operates directly on top of Minecraft's native vertex rendering pipeline without needing OptiFine, shaders, or Iris.
* ⚡ **Sodium & Iris Compatibility (v0.2.0+)** — Native chunk terrain vertex coloring with full Sodium compatibility at maximum framerates.
* 🔦 **Dynamic Handheld Lighting** — Hold colored torches in your main hand or offhand to smoothly illuminate your surroundings on the move.
* 🕯️ **16 Colored Torches** — Craftable torches in all 16 dye colors with custom particle flames and floor/wall variants.
* 👥 **Entity & Mob Tinting** — Mobs, players, armor, and dropped items reflect realistic colored ambient light.
* 🌈 **Physical Color Mixing** — Real-time BFS light propagation across red, green, and blue channels with smooth distance falloff.
* 🛠️ **Public Java API** — Clean and simple [`ColorLightAPI`](docs/API.md) for third-party mod integration.
* 📊 **Built-In Debug Tools** — Toggleable on-screen HUD and 3D light sphere visualization (press `K`).

---

## 📚 Documentation & Wiki

Explore our comprehensive guides in the [`docs/`](docs/README.md) directory:

- 📖 **[Command Reference](docs/COMMANDS.md)** — In-depth guide to `/lightframe create`, `edit`, `remove`, `list`, and color formats.
- ⚙️ **[Configuration Guide](docs/CONFIG.md)** — Options in `config/lightframe.json` for server & client performance tuning.
- 💻 **[Developer API Guide](docs/API.md)** — How to integrate `ColorLightAPI` into your own mods with Gradle & code samples.
- 🎮 **[Compatibility Matrix](docs/COMPATIBILITY.md)** — Status with Sodium, Iris shaders, and other rendering mods.
- ❓ **[Frequently Asked Questions](docs/FAQ.md)** — Common questions and troubleshooting tips.

---

## 🛠️ Crafting Recipes

Craft colored torches directly in your inventory or crafting table:
* **1 Vanilla Torch** + **1 Dye (any color)** ➔ **1 Colored Torch**

Available in 16 colors: *White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, Black*.

---

## 🎮 Operator Commands

Commands require Operator permissions (**level 2+**):

```bash
# Create a light source at your position:
/lightframe create <color> [radius] [intensity]

# Create a light source at target coordinates:
/lightframe createpos <x> <y> <z> <color> [radius] [intensity]

# Edit existing light:
/lightframe edit <uuid> color <color>
/lightframe edit <uuid> radius <radius>

# Remove lights:
/lightframe remove <uuid>
/lightframe remove nearby [radius]

# List active lights:
/lightframe list

# Reload config from disk:
/lightframe reload
```

---

## 🗺️ Project Roadmap & Tasks

Track planned features, progress, and milestones in **[ROADMAP.md](ROADMAP.md)**:

---

## 👥 Credits & License

* **Developer:** AvelcFox
* **Team:** PuffSpark
* **License:** [GNU Lesser General Public License v3.0 or later (LGPL-3.0-or-later)](LICENSE)
