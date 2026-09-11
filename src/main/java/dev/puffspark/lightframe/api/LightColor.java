package dev.puffspark.lightframe.api;

/**
 * Immutable RGB color of a light source. Channels are in [0, 1].
 *
 * <p>Color is additive: a red source contributes only to the R channel of
 * the accumulated light field, a blue source only to B, and their overlap
 * produces purple illumination.</p>
 */
public final class LightColor {

    public static final LightColor WHITE  = new LightColor(1.0f, 1.0f, 1.0f);
    public static final LightColor RED    = new LightColor(1.0f, 0.0f, 0.0f);
    public static final LightColor GREEN  = new LightColor(0.0f, 1.0f, 0.0f);
    public static final LightColor BLUE   = new LightColor(0.0f, 0.0f, 1.0f);
    public static final LightColor YELLOW = new LightColor(1.0f, 1.0f, 0.0f);
    public static final LightColor CYAN   = new LightColor(0.0f, 1.0f, 1.0f);
    public static final LightColor PURPLE = new LightColor(1.0f, 0.0f, 1.0f);
    public static final LightColor ORANGE = new LightColor(1.0f, 0.5f, 0.0f);
    public static final LightColor PINK   = new LightColor(1.0f, 0.4f, 0.7f);
    public static final LightColor BLACK  = new LightColor(0.0f, 0.0f, 0.0f);

    public final float r;
    public final float g;
    public final float b;

    private LightColor(float r, float g, float b) {
        this.r = clamp01(r);
        this.g = clamp01(g);
        this.b = clamp01(b);
    }

    public static LightColor of(float r, float g, float b) {
        return new LightColor(r, g, b);
    }

    /** Parses {@code 0xRRGGBB} (channels shifted like ARGB with zero alpha). */
    public static LightColor ofHex(int rgb) {
        return new LightColor(
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f);
    }

    /** Parses {@code "#rrggbb"}; returns null on failure. */
    public static LightColor parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return ofHex(Integer.parseInt(s, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Named colors used by the test command; case-insensitive. */
    public static LightColor byName(String name) {
        return switch (name.toLowerCase()) {
            case "red" -> RED;
            case "green" -> GREEN;
            case "blue" -> BLUE;
            case "white" -> WHITE;
            case "purple", "magenta" -> PURPLE;
            case "yellow" -> YELLOW;
            case "cyan", "aqua" -> CYAN;
            case "orange" -> ORANGE;
            case "pink" -> PINK;
            default -> null;
        };
    }

    /** Max channel; used as the "intensity" contribution to vanilla block light. */
    public float maxChannel() {
        return Math.max(r, Math.max(g, b));
    }

    /** Perceived luminance (Rec. 709). */
    public float luminance() {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public int asRgb() {
        return ((int) (r * 255.0f) << 16) | ((int) (g * 255.0f) << 8) | (int) (b * 255.0f);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    @Override
    public String toString() {
        return String.format("LightColor(%.2f, %.2f, %.2f)", r, g, b);
    }
}

