# 🗺️ LightFrame Project Roadmap & Tasks

This document tracks current milestones, planned features, and community task requests.

---

## 🚀 Release Milestones

### ✅ Version 0.1.0 (Fabric 1.20.1 & 1.21.1)
- [x] Core RGB lighting engine and block tinting backend.
- [x] Basic `/lightframe` commands (`create`, `edit`, `remove`, `list`).
- [x] Client-Server networking and light synchronization.
- [x] OP-level permissions check for server security.
- [x] Release jar archives for Minecraft 1.20.1 & 1.21.1.

### ✅ Version 0.2.0 (Fabric 1.21.1)
- [x] Full Sodium 0.8+ chunk terrain vertex color injection (`SodiumBlockRendererMixin`).
- [x] Fixed entity rendering ARGB tinting order.
- [x] Configuration hot-reloading (`/lightframe reload`).
- [x] English internationalization for Modrinth & CurseForge launch.

---

## 🔨 In Progress & Planned (v0.3.0)

- [ ] **Custom Item Wand / Light Tool:**
  - A builder wand item allowing right-click to place, inspect, or configure lights easily in Creative mode.
- [ ] **Volumetric & Directional Light Beams:**
  - Support for spotlights / directional emission angles (e.g. stage spotlights).

---

## 💡 Backlog / Future Ideas

- [ ] NeoForge loader port.
- [ ] Support for moving dynamic lights (attached to player or entities).
- [ ] Audio-reactive or Redstone-controllable light sources.
- [ ] Integration with WorldEdit / Axiom builder tools.

---

## 📌 How to Propose a Feature or Help
Have an idea or want to tackle one of these tasks? Open a [Feature Request](https://github.com/AvelcFox/lightframe/issues/new?template=feature_request.yml) or submit a Pull Request!
