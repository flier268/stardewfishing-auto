package com.bonker.stardewfishing.client;

import com.bonker.stardewfishing.StardewFishing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Manages keybindings for the auto-fishing system
 */
@Mod.EventBusSubscriber(modid = StardewFishing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AutoFishingKeys {
    private static final String CATEGORY = "key.categories." + StardewFishing.MODID;

    // Combined toggle for auto-cast and auto-reel (both enabled/disabled together)
    public static final KeyMapping TOGGLE_AUTO_FISHING = new KeyMapping(
            "key." + StardewFishing.MODID + ".toggle_auto_fishing",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );

    public static final KeyMapping TOGGLE_STATUS_OVERLAY = new KeyMapping(
            "key." + StardewFishing.MODID + ".toggle_status_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTO_FISHING);
        event.register(TOGGLE_STATUS_OVERLAY);
    }
}
