# LightFrame Commands Reference

All in-game manipulation of colored light sources is done using the `/lightframe` command.

> [!NOTE]
> All `/lightframe` commands require Operator permissions (**permission level 2+** or cheats enabled in singleplayer).

---

## Command Syntax Overview

```
/lightframe create <color> [radius] [intensity]
/lightframe createpos <x> <y> <z> <color> [radius] [intensity]
/lightframe edit <id> color <color>
/lightframe edit <id> radius <radius>
/lightframe edit <id> intensity <intensity>
/lightframe edit <id> toggle
/lightframe remove <id>
/lightframe remove nearby [radius]
/lightframe remove all
/lightframe list
/lightframe info <id>
/lightframe reload
```

---

## 🎨 Color Formats

Whenever `<color>` is required in commands, you can supply it in three different ways:

1. **Named Presets:**
   - `white`, `red`, `green`, `blue`, `yellow`, `cyan`, `magenta`, `orange`, `purple`, `pink`, `lime`, `amber`
2. **HEX Code:**
   - `#FF0000` (Red)
   - `#00FF00` (Green)
   - `#40E0D0` (Turquoise)
   - `#FFA500` (Orange)
3. **RGB Floats (0.0 to 1.0):**
   - `1.0 0.2 0.0` (Vibrant fiery orange)

---

## Detailed Command Descriptions

### 1. Creating Lights

- `/lightframe create <color> [radius] [intensity]`
  Creates a light source directly at your player's eye position.
  - `color`: Preset name, HEX (`#RRGGBB`), or RGB floats.
  - `radius`: Sphere/cube radius of light effect in blocks (default: `8`, min: `1`, max: `32`).
  - `intensity`: Brightness intensity multiplier (default: `1.0`, min: `0.1`, max: `4.0`).
  - *Example:* `/lightframe create #FF5500 12 1.5`

- `/lightframe createpos <x> <y> <z> <color> [radius] [intensity]`
  Creates a light source at specific coordinates in the current world.
  - *Example:* `/lightframe createpos 100 64 -250 purple 8 1.0`

---

### 2. Editing Existing Lights

- `/lightframe edit <id> color <color>`
  Changes the color of an existing light source.
  - *Example:* `/lightframe edit a1b2c3d4-0000-... color cyan`

- `/lightframe edit <id> radius <radius>`
  Changes the radius of influence in blocks.
  - *Example:* `/lightframe edit a1b2c3d4-0000-... radius 14`

- `/lightframe edit <id> intensity <intensity>`
  Changes the brightness intensity multiplier.
  - *Example:* `/lightframe edit a1b2c3d4-0000-... intensity 2.0`

- `/lightframe edit <id> toggle`
  Toggles the light source on or off without deleting it.

---

### 3. Removing Lights

- `/lightframe remove <id>`
  Deletes the specified light source permanently.

- `/lightframe remove nearby [radius]`
  Deletes all light sources within the given radius from the player (default radius: `16` blocks).
  - *Example:* `/lightframe remove nearby 32`

- `/lightframe remove all`
  Deletes all LightFrame sources in the current dimension.

---

### 4. Inspection & Utilities

- `/lightframe list`
  Lists all active light sources in the current world with their UUIDs, coordinates, colors, and radii. Includes clickable chat links to teleport to or delete them.

- `/lightframe info <id>`
  Displays detailed properties of a specific light source.

- `/lightframe reload`
  Reloads `config/lightframe.json` from disk without needing to restart the server or client.
