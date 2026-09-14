package dev.puffspark.lightframe.client.gui;

import dev.puffspark.lightframe.config.ColorLightConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class LightFrameConfigScreen extends GameOptionsScreen {

    public LightFrameConfigScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("lightframe.config.title"));
    }

    @Override
    protected void addOptions() {
        ColorLightConfig cfg = ColorLightConfig.get();

        // 1. Enable RGB Lighting & Quality
        CyclingButtonWidget<Boolean> enableBtn = CyclingButtonWidget.onOffBuilder(cfg.enableRGBLighting)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.enable_rgb.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.enable_rgb"), (btn, val) -> {
                    cfg.enableRGBLighting = val;
                });

        CyclingButtonWidget<String> qualityBtn = CyclingButtonWidget.<String>builder(Text::literal)
                .values("LOW", "MEDIUM", "HIGH")
                .initially(cfg.lightingQuality)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.quality.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.quality"), (btn, val) -> {
                    cfg.lightingQuality = val;
                });

        this.body.addWidgetEntry(enableBtn, qualityBtn);

        // 2. Boost Vanilla Light & Affect Gameplay Lighting
        CyclingButtonWidget<Boolean> boostBtn = CyclingButtonWidget.onOffBuilder(cfg.boostVanillaLight)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.boost_vanilla.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.boost_vanilla"), (btn, val) -> {
                    cfg.boostVanillaLight = val;
                });

        CyclingButtonWidget<Boolean> gameplayBtn = CyclingButtonWidget.onOffBuilder(cfg.affectGameplayLighting)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.gameplay_lighting.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.gameplay_lighting"), (btn, val) -> {
                    cfg.affectGameplayLighting = val;
                });

        this.body.addWidgetEntry(boostBtn, gameplayBtn);

        // 3. Tint Strength & Max Sources
        SliderWidget tintSlider = new SliderWidget(0, 0, 150, 20, Text.empty(), cfg.tintStrength) {
            {
                updateMessage();
                setTooltip(Tooltip.of(Text.translatable("lightframe.config.tint_strength.tooltip")));
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(value * 100.0);
                setMessage(Text.translatable("lightframe.config.tint_strength", pct + "%"));
            }

            @Override
            protected void applyValue() {
                cfg.tintStrength = (float) value;
            }
        };

        // maxLightSources: range 64 to 1024
        double sourceVal = (cfg.maxLightSources - 64) / (1024.0 - 64.0);
        SliderWidget sourcesSlider = new SliderWidget(0, 0, 150, 20, Text.empty(), sourceVal) {
            {
                updateMessage();
                setTooltip(Tooltip.of(Text.translatable("lightframe.config.max_sources.tooltip")));
            }

            @Override
            protected void updateMessage() {
                int count = 64 + (int) Math.round(value * (1024 - 64));
                setMessage(Text.translatable("lightframe.config.max_sources", count));
            }

            @Override
            protected void applyValue() {
                cfg.maxLightSources = 64 + (int) Math.round(value * (1024 - 64));
            }
        };

        this.body.addWidgetEntry(tintSlider, sourcesSlider);

        // 4. Max Light Radius & Rebuild Sections
        double radiusVal = (cfg.maxLightRadius - 8) / (48.0 - 8.0);
        SliderWidget radiusSlider = new SliderWidget(0, 0, 150, 20, Text.empty(), radiusVal) {
            {
                updateMessage();
                setTooltip(Tooltip.of(Text.translatable("lightframe.config.max_radius.tooltip")));
            }

            @Override
            protected void updateMessage() {
                int r = 8 + (int) Math.round(value * (48 - 8));
                setMessage(Text.translatable("lightframe.config.max_radius", r));
            }

            @Override
            protected void applyValue() {
                cfg.maxLightRadius = 8 + (int) Math.round(value * (48 - 8));
            }
        };

        double rebuildVal = (cfg.maxSectionsRebuiltPerTick - 4) / (64.0 - 4.0);
        SliderWidget rebuildSlider = new SliderWidget(0, 0, 150, 20, Text.empty(), rebuildVal) {
            {
                updateMessage();
                setTooltip(Tooltip.of(Text.translatable("lightframe.config.max_rebuild.tooltip")));
            }

            @Override
            protected void updateMessage() {
                int s = 4 + (int) Math.round(value * (64 - 4));
                setMessage(Text.translatable("lightframe.config.max_rebuild", s));
            }

            @Override
            protected void applyValue() {
                cfg.maxSectionsRebuiltPerTick = 4 + (int) Math.round(value * (64 - 4));
            }
        };

        this.body.addWidgetEntry(radiusSlider, rebuildSlider);

        // 5. Tint Entities & Tint Block Entities
        CyclingButtonWidget<Boolean> entitiesBtn = CyclingButtonWidget.onOffBuilder(cfg.tintEntities)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.tint_entities.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.tint_entities"), (btn, val) -> {
                    cfg.tintEntities = val;
                });

        CyclingButtonWidget<Boolean> blockEntitiesBtn = CyclingButtonWidget.onOffBuilder(cfg.tintBlockEntities)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.tint_block_entities.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.tint_block_entities"), (btn, val) -> {
                    cfg.tintBlockEntities = val;
                });

        this.body.addWidgetEntry(entitiesBtn, blockEntitiesBtn);

        // 6. Shader Dynamic Light & Debug Mode
        CyclingButtonWidget<Boolean> shaderDynBtn = CyclingButtonWidget.onOffBuilder(cfg.irisFallbackKeepDynamicLight)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.shader_dynamic.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.shader_dynamic"), (btn, val) -> {
                    cfg.irisFallbackKeepDynamicLight = val;
                });

        CyclingButtonWidget<Boolean> debugBtn = CyclingButtonWidget.onOffBuilder(cfg.debugMode)
                .tooltip(val -> Tooltip.of(Text.translatable("lightframe.config.debug_mode.tooltip")))
                .build(0, 0, 150, 20, Text.translatable("lightframe.config.debug_mode"), (btn, val) -> {
                    cfg.debugMode = val;
                });

        this.body.addWidgetEntry(shaderDynBtn, debugBtn);

        // 7. Reset to defaults
        ButtonWidget resetBtn = ButtonWidget.builder(Text.translatable("lightframe.config.reset"), btn -> {
            ColorLightConfig def = new ColorLightConfig();
            cfg.enableRGBLighting = def.enableRGBLighting;
            cfg.lightingQuality = def.lightingQuality;
            cfg.boostVanillaLight = def.boostVanillaLight;
            cfg.affectGameplayLighting = def.affectGameplayLighting;
            cfg.tintStrength = def.tintStrength;
            cfg.maxLightSources = def.maxLightSources;
            cfg.maxLightRadius = def.maxLightRadius;
            cfg.maxSectionsRebuiltPerTick = def.maxSectionsRebuiltPerTick;
            cfg.tintEntities = def.tintEntities;
            cfg.tintBlockEntities = def.tintBlockEntities;
            cfg.irisFallbackKeepDynamicLight = def.irisFallbackKeepDynamicLight;
            cfg.debugMode = def.debugMode;
            ColorLightConfig.save();
            if (this.client != null) {
                this.client.setScreen(new LightFrameConfigScreen(this.parent));
            }
        }).dimensions(0, 0, 310, 20).tooltip(Tooltip.of(Text.translatable("lightframe.config.reset.tooltip"))).build();

        this.body.addWidgetEntry(resetBtn, null);
    }

    @Override
    public void removed() {
        ColorLightConfig.save();
    }
}
