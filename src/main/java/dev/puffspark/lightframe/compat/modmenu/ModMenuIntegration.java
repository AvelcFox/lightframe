package dev.puffspark.lightframe.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.puffspark.lightframe.client.gui.LightFrameConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LightFrameConfigScreen::new;
    }
}
