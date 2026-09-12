# Frequently Asked Questions (FAQ)

### Q: Why can't players run `/lightframe create`?
**A:** By design, creating and editing persistent light sources requires operator permissions (**OP level 2+**). On dedicated servers, only server admins or builders with OP can execute these commands. This prevents normal players from spamming lights or causing visual griefing.

---

### Q: Does LightFrame affect vanilla mob spawning or light levels?
**A:** No. Vanilla Minecraft calculates monster spawning using standard block and sky light levels (0–15). LightFrame's RGB light engine is an atmospheric/visual lighting overlay designed for aesthetics and builders. It does not tamper with vanilla mob-spawning logic.

---

### Q: Does this mod cause lag or lower FPS?
**A:** LightFrame is optimized for high performance. Light values are cached per block and chunk section, and vertex colors are applied directly during rendering. You can easily have dozens of active colored lights without FPS drops. For large servers, limits can be configured in `config/lightframe.json` (`maxLightSources`).

---

### Q: Do I need to install it on both Client and Server?
**A:** Yes, for multiplayer servers. The server tracks, validates, and persists the light sources, while the client renders the colored lighting.

---

### Q: How do I report a crash or bug?
**A:** Please visit our [GitHub Issues](https://github.com/AvelcFox/lightframe/issues) and fill out the [Bug Report](https://github.com/AvelcFox/lightframe/issues/new?template=bug_report.yml) template, including your Minecraft version and crash log.
