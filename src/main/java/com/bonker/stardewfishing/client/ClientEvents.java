package com.bonker.stardewfishing.client;

import com.bonker.stardewfishing.SFConfig;
import com.bonker.stardewfishing.StardewFishing;
import com.bonker.stardewfishing.common.init.SFBlockEntities;
import com.bonker.stardewfishing.common.init.SFParticles;
import com.bonker.stardewfishing.common.init.SFSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientEvents {
    private static boolean autoFishingInitialized = false;

    @Mod.EventBusSubscriber(modid = StardewFishing.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onRenderTooltip(final RenderTooltipEvent.Pre event) {
            event.getGraphics().pose().translate(0, 0, 500);
        }

        @SubscribeEvent
        public static void onClientTick(final TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) {
                return;
            }

            // Initialize auto-fishing controller with config values (once)
            if (!autoFishingInitialized && Minecraft.getInstance().player != null) {
                initializeAutoFishing();
                autoFishingInitialized = true;
            }

            // Tick the auto-fishing controller
            AutoFishingController.getInstance().tick();

            // Handle keybindings
            handleKeybindings();

            if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen) {
                RodTooltipHandler.tick(containerScreen.hoveredSlot, containerScreen.getMenu().getCarried());
            } else {
                RodTooltipHandler.clear();
            }
        }

        /**
         * Initialize auto-fishing settings from config
         */
        private static void initializeAutoFishing() {
            AutoFishingController controller = AutoFishingController.getInstance();

            boolean autoCast = SFConfig.isAutoCastEnabledByDefault();
            boolean autoReel = SFConfig.isAutoReelEnabledByDefault();

            controller.setAutoCastEnabled(autoCast);
            controller.setAutoReelEnabled(autoReel);
            controller.setMinRodDurability(SFConfig.getMinRodDurability());
            controller.setMinBobberDurability(SFConfig.getMinBobberDurability());
            controller.setStopOnLowDurability(SFConfig.shouldStopOnLowDurability());
            controller.setAutoReplaceTool(SFConfig.shouldAutoReplaceTool());

            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.info("[DEBUG] Auto-fishing initialized: Cast={}, Reel={}", autoCast, autoReel);
            }
        }

        /**
         * Handle key press events for toggling auto-fishing features
         * Only works when player is holding a fishing rod
         */
        private static void handleKeybindings() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Check if player is holding a fishing rod
            boolean hasRod = mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem ||
                           mc.player.getOffhandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem;

            // Only process auto-fishing toggle when holding a rod
            if (hasRod) {
                // Toggle both auto-cast and auto-reel together
                if (AutoFishingKeys.TOGGLE_AUTO_FISHING.consumeClick()) {
                    // Get current state (use auto-cast as reference)
                    boolean currentlyEnabled = AutoFishingController.getInstance().isAutoCastEnabled();
                    boolean newState = !currentlyEnabled;

                    // Set both to the same state
                    AutoFishingController.getInstance().setAutoCastEnabled(newState);
                    AutoFishingController.getInstance().setAutoReelEnabled(newState);

                    String status = newState ? "ON" : "OFF";
                    mc.player.displayClientMessage(
                        Component.literal("Auto-Fishing: " + status),
                        true
                    );
                }
            }

            // Toggle status overlay (always works)
            if (AutoFishingKeys.TOGGLE_STATUS_OVERLAY.consumeClick()) {
                AutoFishingOverlay.toggleOverlay();
                String status = AutoFishingOverlay.isOverlayShown() ? "SHOWN" : "HIDDEN";
                mc.player.displayClientMessage(
                    Component.literal("Auto-Fishing Overlay: " + status),
                    true
                );
            }
        }

        @SubscribeEvent
        public static void onScreenRendered(final ContainerScreenEvent.Render.Foreground event) {
            if (SFConfig.isInventoryEquippingEnabled()) {
                RodTooltipHandler.render(event.getGuiGraphics(), Minecraft.getInstance().getPartialTick(), event.getMouseX() - event.getContainerScreen().getGuiLeft(), event.getMouseY() - event.getContainerScreen().getGuiTop());
            }
        }

        @SubscribeEvent
        public static void onSoundPlayed(final PlaySoundSourceEvent event) {
            try {
                if (event.getSound() instanceof SimpleSoundInstance instance) {
                    if (event.getSound().getLocation().getNamespace().equals("minecraft")) {
                        SoundEvent newEvent = switch (event.getSound().getLocation().getPath()) {
                            case "entity.fishing_bobber.throw" -> SFSoundEvents.CAST.get();
                            case "entity.fishing_bobber.retrieve" -> {
                                if (Minecraft.getInstance().level == null) yield null;
                                Player player = Minecraft.getInstance().level.getNearestPlayer(event.getSound().getX(), event.getSound().getY(), event.getSound().getZ(), 1, false);
                                yield player == null || player.fishing == null ? SFSoundEvents.PULL_ITEM.get() : SFSoundEvents.FISH_HIT.get();
                            }
                            case "entity.fishing_bobber.splash" -> {
                                // Notify auto-fishing controller that fish is biting (splash sound detected)
                                AutoFishingController.getInstance().notifySplashSoundDetected();
                                yield SFSoundEvents.FISH_BITE.get();
                            }
                            default -> null;
                        };

                        if (newEvent != null) {
                            event.getEngine().stop(instance);
                            event.getEngine().play(new SimpleSoundInstance(
                                    newEvent,
                                    instance.getSource(),
                                    1.0F,
                                    1.0F,
                                    SoundInstance.createUnseededRandom(),
                                    instance.getX(),
                                    instance.getY(),
                                    instance.getZ()));
                        }
                    }
                }
            } catch (Exception e) {
                StardewFishing.LOGGER.error("An exception occurred while trying to replace a sound event. I think this happens when you try to use a fishing rod in extremely laggy conditions.", e);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = StardewFishing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(SFBlockEntities.FISH_DISPLAY.get(), FishDisplayBER::new);
        }

        @SubscribeEvent
        public static void onParticleRegistration(final RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(SFParticles.SPARKLE.get(), SparkleParticle.Provider::new);
        }
    }
}
