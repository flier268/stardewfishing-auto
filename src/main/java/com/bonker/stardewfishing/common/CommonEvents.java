package com.bonker.stardewfishing.common;

import com.bonker.stardewfishing.SFConfig;
import com.bonker.stardewfishing.StardewFishing;
import com.bonker.stardewfishing.client.RodTooltipHandler;
import com.bonker.stardewfishing.common.init.SFAttributes;
import com.bonker.stardewfishing.common.init.SFItems;
import com.bonker.stardewfishing.common.networking.SFNetworking;
import com.bonker.stardewfishing.proxy.ClientProxy;
import com.bonker.stardewfishing.proxy.ItemUtils;
import com.bonker.stardewfishing.server.data.FishBehaviorReloadListener;
import com.bonker.stardewfishing.server.data.MinigameModifiersReloadListener;
import com.bonker.stardewfishing.server.SFCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class CommonEvents {
    @Mod.EventBusSubscriber(modid = StardewFishing.MODID)
    public static class ForgeBus {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onFishCaught(final ItemFishedEvent event) {
            event.getDrops().forEach(stack -> {
                if (ItemUtils.isLegendaryFish(stack)) {
                    ItemUtils.recordLegendaryCatch(stack, event.getEntity());
                }
            });
        }

        @SubscribeEvent
        public static void onRegisterCommands(final RegisterCommandsEvent event) {
            SFCommands.register(event.getDispatcher(), event.getBuildContext());
        }

        @SubscribeEvent
        public static void onItemStackedOnOther(final ItemStackedOnOtherEvent event) {
            if (!SFConfig.isInventoryEquippingEnabled()) {
                return;
            }

            if (event.getClickAction() != ClickAction.SECONDARY) {
                return;
            }

            ItemStack slotItem = event.getSlot().getItem();
            if (!ItemUtils.isFishingRod(slotItem)) {
                return;
            }

            ItemStack carried = event.getStackedOnItem();
            ItemStack currentBobber = ItemUtils.getBobber(slotItem);

            boolean equipped = true;
            if (ItemUtils.isBobber(carried)) {
                if (currentBobber.isEmpty()) {
                    ItemUtils.setBobber(slotItem, carried.copyWithCount(1));
                    event.getCarriedSlotAccess().get().shrink(1);
                    event.setCanceled(true);
                } else if (carried.getCount() == 1) {
                    ItemUtils.setBobber(slotItem, carried.copyWithCount(1));
                    event.getCarriedSlotAccess().set(currentBobber.copy());
                    event.setCanceled(true);
                } else if (ItemStack.isSameItemSameTags(carried, currentBobber)) {
                    int transferAmount = Math.min(carried.getMaxStackSize() - carried.getCount(), currentBobber.getCount());
                    ItemUtils.setBobber(slotItem, currentBobber.copyWithCount(currentBobber.getCount() - transferAmount));
                    event.getCarriedSlotAccess().get().grow(transferAmount);
                    event.setCanceled(true);
                    equipped = false;
                }
            } else if (!currentBobber.isEmpty() && carried.isEmpty()) {
                ItemUtils.setBobber(slotItem, ItemStack.EMPTY);
                event.getCarriedSlotAccess().set(currentBobber.copy());
                event.setCanceled(true);
                equipped = false;
            }

            if (event.isCanceled()) {
                if (event.getPlayer().level().isClientSide && FMLEnvironment.dist.isClient()) {
                    RodTooltipHandler.addShake(event.getSlot(), equipped);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onTooltipHighPriority(final ItemTooltipEvent event) {
            if (ItemUtils.isLegendaryFish(event.getItemStack())) {
                event.getToolTip().add(1, SFItems.LEGENDARY_FISH_TOOLTIP.copy().withStyle(ChatFormatting.BOLD));
                ItemUtils.addCatchTooltip(event.getItemStack(), event.getToolTip());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onTooltipLowPriority(final ItemTooltipEvent event) {
            if (ItemUtils.isFishingRod(event.getItemStack())) {
                if (!StardewFishing.TIDE_INSTALLED) {
                    ItemStack bobber = ItemUtils.getBobber(event.getItemStack());

                    if (bobber.isEmpty()) {
                        event.getToolTip().add(Component.translatable("tooltip.stardew_fishing." + (SFConfig.isInventoryEquippingEnabled() ? "no_bobber" : "no_bobber_attach_disabled"))
                                .withStyle(StardewFishing.LIGHT_COLOR));
                    } else {
                        event.getToolTip().add(Component.translatable("tooltip.stardew_fishing.bobber", bobber.getDisplayName().copy().withStyle(StardewFishing.LIGHT_COLOR))
                                .withStyle(StardewFishing.DARK_COLOR));
                    }
                }
            }

            MinigameModifiersReloadListener.getModifiers(event.getItemStack()).ifPresent(modifiers -> {
                if (!event.getToolTip().get(event.getToolTip().size() - 1).getString().isEmpty()) {
                    event.getToolTip().add(Component.empty());
                }

                if (ClientProxy.isShiftDown()) {
                    event.getToolTip().add(Component.translatable("tooltip.stardew_fishing.rod_modifier").withStyle(StardewFishing.LIGHT_COLOR));
                    modifiers.appendTooltip(event.getToolTip());
                } else {
                    event.getToolTip().add(Component.translatable("tooltip.stardew_fishing.rod_modifier_shift").withStyle(StardewFishing.LIGHT_COLOR));
                }
            });
        }

        @SubscribeEvent
        public static void onAttachCapabilities(final AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof FishingHook) {
                FishingHookLogic.attachCap(event);
            }
        }

        @SubscribeEvent
        public static void onAddReloadListeners(final AddReloadListenerEvent event) {
            event.addListener(FishBehaviorReloadListener.create());
            event.addListener(MinigameModifiersReloadListener.create());
        }
    }

    @Mod.EventBusSubscriber(modid = StardewFishing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onCommonSetup(final FMLCommonSetupEvent event) {
            SFNetworking.register();
        }

        @SubscribeEvent
        public static void onAttributeCreation(final EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, SFAttributes.LINE_STRENGTH.get());
            event.add(EntityType.PLAYER, SFAttributes.BAR_SIZE.get());
            event.add(EntityType.PLAYER, SFAttributes.TREASURE_CHANCE_BONUS.get());
            event.add(EntityType.PLAYER, SFAttributes.EXPERIENCE_MULTIPLIER.get());
        }
    }
}
