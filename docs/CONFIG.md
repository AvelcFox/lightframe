# LightFrame Configuration Guide

The configuration file is located at `.minecraft/config/lightframe.json` (or `config/lightframe.json` on a dedicated server). It is generated automatically on the first launch.

---

## Configuration Options

```json
{
  "maxLightSources": 512,
  "maxLightRadius": 32,
  "maxIntensity": 4.0,
  "renderDistance": 64,
  "smoothLighting": true,
  "sodiumCompatibility": true,
  "debugHud": false
}
```

### Options Breakdown

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `maxLightSources` | Integer | `512` | Maximum number of active light sources allowed simultaneously per world. Prevents memory or network overload. |
| `maxLightRadius` | Integer | `32` | Maximum block radius that can be specified in `/lightframe create` or via the API. |
| `maxIntensity` | Float | `4.0` | Maximum brightness intensity cap. |
| `renderDistance` | Integer | `64` | Distance in blocks within which clients will render and tint blocks/entities from light sources. |
| `smoothLighting` | Boolean | `true` | Enables smooth distance falloff gradient for RGB light emission. |
| `sodiumCompatibility` | Boolean | `true` | Enables chunk quad color interception when Sodium is present. |
| `debugHud` | Boolean | `false` | When enabled, shows debug overlay info on client (active sources, render time). |

---

## Hot-Reloading

You can modify `config/lightframe.json` at runtime and reload it immediately without restarting the game or server using:

```
/lightframe reload
```
