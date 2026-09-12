# Contributing to LightFrame

Thank you for your interest in contributing to LightFrame! Whether you're reporting a bug, proposing a feature, or writing code, your help is welcome.

---

## 🌿 Branching Strategy

Our repository uses a version-based branching model:

- **`main`**: Documentation, Wiki, GitHub issue templates, and general repository hub.
- **`1.21.1`**: Active development branch for Minecraft 1.21.1.
- **`1.20.1`**: LTS branch for Minecraft 1.20.1 backports and fixes.
- **`dev`**: Experimental features branch.

> When opening a Pull Request, make sure to target the appropriate version branch (e.g. `1.21.1` for 1.21.1 features or `main` for documentation updates).

---

## 🛠️ Local Development Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AvelcFox/lightframe.git
   cd lightframe
   git checkout 1.21.1
   ```

2. **Generate Loom / Gradle sources:**
   ```bash
   ./gradlew genSources
   ```

3. **Run Minecraft client in development:**
   ```bash
   ./gradlew runClient
   ```

4. **Build release jar:**
   ```bash
   ./gradlew build
   ```

---

## 📝 Reporting Issues

- **Bugs:** Use the [Bug Report form](https://github.com/AvelcFox/lightframe/issues/new?template=bug_report.yml). Include crash reports, Minecraft version, and steps to reproduce.
- **Features:** Use the [Feature Request form](https://github.com/AvelcFox/lightframe/issues/new?template=feature_request.yml).
