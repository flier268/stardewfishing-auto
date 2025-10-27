package com.bonker.stardewfishing.client;

import com.bonker.stardewfishing.StardewFishing;
import com.bonker.stardewfishing.proxy.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the auto-fishing status overlay on the HUD
 */
@Mod.EventBusSubscriber(modid = StardewFishing.MODID, value = Dist.CLIENT)
public class AutoFishingOverlay {
    private static boolean showOverlay = true;

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!showOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Only show when player has a fishing rod
        if (!hasRodInHand(mc.player)) return;

        AutoFishingController controller = AutoFishingController.getInstance();
        renderOverlay(event.getGuiGraphics(), mc.player, controller);
    }

    private static void renderOverlay(GuiGraphics graphics, LocalPlayer player, AutoFishingController controller) {
        Font font = Minecraft.getInstance().font;
        int x = 10;
        int y = 10;
        int lineHeight = 12;
        int currentLine = 0;

        // Background
        int width = 280;
        int height = 90;
        graphics.fill(x - 2, y - 2, x + width, y + height, 0x88000000);

        // Title
        graphics.drawString(font, "=== Auto-Fishing System ===", x, y + (currentLine++ * lineHeight), 0xFFFFFF00, true);
        currentLine++;

        // Auto-fishing status (combined cast + reel)
        // Both should have the same state, so just check auto-cast
        String autoFishingStatus = controller.isAutoCastEnabled() ? "ON" : "OFF";
        int autoFishingColor = controller.isAutoCastEnabled() ? 0xFF00FF00 : 0xFFFF0000;
        graphics.drawString(font, "Auto-Fishing (C): ", x, y + (currentLine * lineHeight), 0xFFFFFFFF, true);
        graphics.drawString(font, autoFishingStatus, x + 120, y + (currentLine++ * lineHeight), autoFishingColor, true);

        // Current state
        String state = getStateDisplay(controller.getCurrentState());
        int stateColor = getStateColor(controller.getCurrentState());
        graphics.drawString(font, "State: ", x, y + (currentLine * lineHeight), 0xFFFFFFFF, true);
        graphics.drawString(font, state, x + 120, y + (currentLine++ * lineHeight), stateColor, true);

        // Durability info
        String durabilityInfo = controller.getDurabilityInfo(player);
        int durabilityColor = getDurabilityColor(player, controller);
        graphics.drawString(font, durabilityInfo, x, y + (currentLine++ * lineHeight), durabilityColor, true);

        // Controls hint
        currentLine++;
        graphics.drawString(font, "Press B to hide overlay", x, y + (currentLine * lineHeight), 0xFF888888, false);
    }

    private static String getStateDisplay(AutoFishingController.FishingState state) {
        return switch (state) {
            case IDLE -> "Idle (Ready)";
            case CAST -> "Waiting for Bite";
            case FISH_BITING -> "Fish Biting!";
            case REELING -> "Reeling In";
            case IN_MINIGAME -> "In Minigame";
            case LOW_DURABILITY -> "Low Durability!";
            case NO_ROD -> "No Rod";
        };
    }

    private static int getStateColor(AutoFishingController.FishingState state) {
        return switch (state) {
            case IDLE -> 0xFFFFFFFF;          // White
            case CAST -> 0xFF00FFFF;          // Cyan
            case FISH_BITING -> 0xFFFFFF00;   // Yellow
            case REELING -> 0xFF00FF00;       // Green
            case IN_MINIGAME -> 0xFFFF00FF;   // Magenta
            case LOW_DURABILITY -> 0xFFFF0000; // Red
            case NO_ROD -> 0xFF888888;        // Gray
        };
    }

    private static int getDurabilityColor(LocalPlayer player, AutoFishingController controller) {
        InteractionHand rodHand = null;
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) {
            rodHand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof FishingRodItem) {
            rodHand = InteractionHand.OFF_HAND;
        }

        if (rodHand == null) return 0xFF888888;

        ItemStack rod = player.getItemInHand(rodHand);
        int rodDurability = rod.getMaxDamage() - rod.getDamageValue();

        // Check rod durability
        if (rodDurability <= controller.getMinRodDurability()) {
            return 0xFFFF0000; // Red - critical
        }

        // Check bobber durability
        ItemStack bobber = ItemUtils.getBobber(rod);
        if (!bobber.isEmpty() && bobber.isDamageableItem()) {
            int bobberDurability = bobber.getMaxDamage() - bobber.getDamageValue();
            if (bobberDurability <= controller.getMinBobberDurability()) {
                return 0xFFFF0000; // Red - critical
            }
        }

        // Check for low durability warning (within 2x the minimum)
        if (rodDurability <= controller.getMinRodDurability() * 2) {
            return 0xFFFFAA00; // Orange - warning
        }

        ItemStack bobber2 = ItemUtils.getBobber(rod);
        if (!bobber2.isEmpty() && bobber2.isDamageableItem()) {
            int bobberDurability = bobber2.getMaxDamage() - bobber2.getDamageValue();
            if (bobberDurability <= controller.getMinBobberDurability() * 2) {
                return 0xFFFFAA00; // Orange - warning
            }
        }

        return 0xFF00FF00; // Green - good
    }

    private static boolean hasRodInHand(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof FishingRodItem ||
               player.getOffhandItem().getItem() instanceof FishingRodItem;
    }

    public static void toggleOverlay() {
        showOverlay = !showOverlay;
    }

    public static boolean isOverlayShown() {
        return showOverlay;
    }

    public static void setOverlayShown(boolean shown) {
        showOverlay = shown;
    }
}
