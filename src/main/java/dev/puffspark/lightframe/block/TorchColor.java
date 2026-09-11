package dev.puffspark.lightframe.block;

import dev.puffspark.lightframe.api.LightColor;
import net.minecraft.util.StringIdentifiable;

public enum TorchColor implements StringIdentifiable {
    WHITE("white", 0xFFFFFF, 1.0f, 1.0f, 1.0f, "White Torch", "Белый факел"),
    ORANGE("orange", 0xFF6A00, 1.0f, 0.42f, 0.0f, "Orange Torch", "Оранжевый факел"),
    MAGENTA("magenta", 0xFF00FF, 1.0f, 0.0f, 1.0f, "Magenta Torch", "Пурпурный факел"),
    LIGHT_BLUE("light_blue", 0x3399FF, 0.2f, 0.6f, 1.0f, "Light Blue Torch", "Голубой факел"),
    YELLOW("yellow", 0xFFFF00, 1.0f, 1.0f, 0.0f, "Yellow Torch", "Жёлтый факел"),
    LIME("lime", 0x44FF00, 0.27f, 1.0f, 0.0f, "Lime Torch", "Лаймовый факел"),
    PINK("pink", 0xFF66B2, 1.0f, 0.4f, 0.7f, "Pink Torch", "Розовый факел"),
    GRAY("gray", 0x666666, 0.45f, 0.45f, 0.45f, "Gray Torch", "Серый факел"),
    LIGHT_GRAY("light_gray", 0xAAAAAA, 0.67f, 0.67f, 0.67f, "Light Gray Torch", "Светло-серый факел"),
    CYAN("cyan", 0x00FFFF, 0.0f, 1.0f, 1.0f, "Cyan Torch", "Бирюзовый факел"),
    PURPLE("purple", 0x8800FF, 0.53f, 0.0f, 1.0f, "Purple Torch", "Фиолетовый факел"),
    BLUE("blue", 0x0033FF, 0.0f, 0.2f, 1.0f, "Blue Torch", "Синий факел"),
    BROWN("brown", 0x8B4513, 0.55f, 0.27f, 0.07f, "Brown Torch", "Коричневый факел"),
    GREEN("green", 0x00BB00, 0.0f, 0.73f, 0.0f, "Green Torch", "Зелёный факел"),
    RED("red", 0xFF0000, 1.0f, 0.0f, 0.0f, "Red Torch", "Красный факел"),
    BLACK("black", 0x330044, 0.2f, 0.0f, 0.27f, "Dark Torch", "Тёмный факел");

    private final String name;
    private final int rgbHex;
    private final float r, g, b;
    private final String englishName;
    private final String russianName;
    private final LightColor lightColor;

    TorchColor(String name, int rgbHex, float r, float g, float b, String englishName, String russianName) {
        this.name = name;
        this.rgbHex = rgbHex;
        this.r = r;
        this.g = g;
        this.b = b;
        this.englishName = englishName;
        this.russianName = russianName;
        this.lightColor = LightColor.of(r, g, b);
    }

    @Override
    public String asString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public int getRgbHex() {
        return rgbHex;
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getRussianName() {
        return russianName;
    }

    public LightColor lightColor() {
        return lightColor;
    }
}

