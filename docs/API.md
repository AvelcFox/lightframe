# LightFrame Developer API Guide

LightFrame provides a clean and powerful public API (`ColorLightAPI`) allowing any other Fabric/Quilt mod to spawn, move, colorize, and manage custom RGB light sources dynamically.

---

## 📦 Setting Up the Dependency

### Gradle (`build.gradle`)

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/AvelcFox/lightframe")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    modImplementation "dev.puffspark:lightframe:${lightframe_version}"
}
```

Or reference a local jar in `libs/`:
```groovy
dependencies {
    modImplementation files("libs/lightframe-0.2.1+1.21.1.jar")
}
```

---

## 💡 Quick Start Example

Import the API:
```java
import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.ColorLightAPI;
import dev.puffspark.lightframe.api.LightColor;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
```

### 1. Creating a Light Source

```java
// Server-side or Client-side
Vec3d pos = new Vec3d(player.getX(), player.getY() + 1.0, player.getZ());
LightColor color = LightColor.fromRGB(1.0f, 0.2f, 0.0f); // Bright red-orange
int radius = 10;          // blocks
float intensity = 1.5f;   // brightness multiplier

ColorLight light = ColorLightAPI.create(world, pos, color, radius, intensity);

if (light != null) {
    UUID lightId = light.getId();
    // Keep reference or store ID
}
```

> [!TIP]
> Sources created on the **logical server** are authoritative and automatically synchronized to all tracking clients.
> Sources created on the **client** are rendered locally only (useful for client-side VFX or UI particles).

---

### 2. Modifying a Light in Real Time

```java
// Dynamically change color, brightness, position, or state
light.setColor(LightColor.PURPLE);
light.setIntensity(2.0f);
light.setPosition(new Vec3d(x, y, z));

// Temporarily turn off without destroying
light.setEnabled(false);

// Permanently remove
light.remove();
```

---

### 3. Querying Active Lights

```java
// Lookup by UUID
Optional<ColorLight> light = ColorLightAPI.get(world, uuid);

// Query all active lights in dimension
Collection<ColorLight> lights = ColorLightAPI.getAll(world);

// Count active lights
int total = ColorLightAPI.count(world);

// Delete all lights in dimension
ColorLightAPI.removeAll(world);
```

---

## 🎨 Color Helpers (`LightColor`)

`LightColor` represents an RGB color where channels are in the range `[0.0f, 1.0f]`:

```java
// Using presets
LightColor c1 = LightColor.RED;
LightColor c2 = LightColor.CYAN;
LightColor c3 = LightColor.PURPLE;

// From RGB floats
LightColor custom = LightColor.fromRGB(0.8f, 0.1f, 0.9f);

// From HEX code
LightColor hexColor = LightColor.fromHex("#FFA500");

// Convert to packed ARGB int
int argb = custom.toARGB();
```
