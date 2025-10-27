package com.bonker.stardewfishing.client;

import com.bonker.stardewfishing.SFConfig;
import com.bonker.stardewfishing.StardewFishing;
import com.bonker.stardewfishing.mixin.FishingHookAccessor;
import com.bonker.stardewfishing.proxy.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the complete automatic fishing lifecycle:
 * - Auto-casting when rod is ready
 * - Auto-reeling when fish bites
 * - Durability checking and management
 * - Rod/bobber replacement from inventory
 */
public class AutoFishingController {
    private static AutoFishingController instance;

    // State tracking
    private boolean autoCastEnabled = false;
    private boolean autoReelEnabled = false;
    private FishingState currentState = FishingState.IDLE;
    private int ticksSinceLastAction = 0;
    private int castDelayTicks = 0;
    private boolean waitingForMinigame = false;
    private boolean soundBasedBiteDetected = false; // Set when splash sound is heard
    private int ticksSinceBiteDetected = -1; // Tracks ticks since fish bite (-1 = no bite)

    // Durability settings
    private int minRodDurability = 5;
    private int minBobberDurability = 3;
    private boolean stopOnLowDurability = false;
    private boolean autoReplaceTool = true;

    // Timing configuration
    private static final int MIN_CAST_DELAY = 20; // 1 second
    private static final int REEL_CHECK_INTERVAL = 5; // Check every 5 ticks
    private static final int BOBBER_BITE_DETECT_INTERVAL = 2; // Check every 2 ticks for biting
    private static final int BITE_REEL_DELAY = 2; // 100ms delay (2 ticks) before reeling after fish bites
    private static final int BITE_WAIT_TIMEOUT = 1200; // 60 seconds (1200 ticks) timeout for waiting fish bite

    public enum FishingState {
        IDLE,           // No rod in hand or rod not cast
        CAST,           // Rod has been cast, waiting for fish
        FISH_BITING,    // Fish is biting (bobber going down)
        REELING,        // Retrieving the rod
        IN_MINIGAME,    // Currently in minigame
        LOW_DURABILITY, // Rod or bobber durability too low
        NO_ROD          // No fishing rod available
    }

    private AutoFishingController() {}

    public static AutoFishingController getInstance() {
        if (instance == null) {
            instance = new AutoFishingController();
        }
        return instance;
    }

    /**
     * Main tick method - should be called every client tick
     */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }

        ticksSinceLastAction++;

        // Increment bite detection timer if active
        if (ticksSinceBiteDetected >= 0) {
            ticksSinceBiteDetected++;

            // Set bite detected flag after delay
            if (ticksSinceBiteDetected >= BITE_REEL_DELAY && !soundBasedBiteDetected) {
                soundBasedBiteDetected = true;
                if (SFConfig.isDebugMode()) {
                    StardewFishing.LOGGER.info("[DEBUG] Bite delay complete ({} ticks)", ticksSinceBiteDetected);
                }
            }
        }

        // Update current state
        updateState(mc.player);

        // NEVER execute auto-actions during minigame - double safety check
        if (currentState == FishingState.IN_MINIGAME || mc.screen instanceof FishingScreen) {
            return; // Skip all auto-actions when in minigame
        }

        // Execute auto-actions based on state
        if (autoCastEnabled) {
            handleAutoCast(mc.player);
        }

        if (autoReelEnabled) {
            handleAutoReel(mc.player);
        }
    }

    /**
     * Called by sound event handler when splash sound is detected
     * This indicates a fish has bitten the hook
     */
    public void notifySplashSoundDetected() {
        // Only start delay timer if we're currently in CAST state (rod is out)
        if (currentState == FishingState.CAST && ticksSinceBiteDetected < 0) {
            ticksSinceBiteDetected = 0; // Start counting
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.info("[DEBUG] Splash sound detected");
            }
        }
    }

    /**
     * Updates the current fishing state based on player and world state
     */
    private void updateState(LocalPlayer player) {
        FishingState oldState = currentState;

        // Check if in minigame
        if (Minecraft.getInstance().screen instanceof FishingScreen) {
            currentState = FishingState.IN_MINIGAME;
            waitingForMinigame = false;
            soundBasedBiteDetected = false; // Reset sound flag when entering minigame
            ticksSinceBiteDetected = -1; // Reset bite timer
            if (oldState != currentState && SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.info("[DEBUG] State: {} -> {}", oldState, currentState);
            }
            return;
        }

        // Check if we were waiting for minigame but it didn't start
        if (waitingForMinigame && ticksSinceLastAction > 100) { // 5 seconds timeout
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Minigame timeout, resetting");
            }
            waitingForMinigame = false;
        }

        // Find fishing rod in hand
        InteractionHand rodHand = getRodHand(player);
        if (rodHand == null) {
            currentState = FishingState.NO_ROD;
            return;
        }

        ItemStack rod = player.getItemInHand(rodHand);

        // Check durability
        if (!checkDurability(player, rod)) {
            currentState = FishingState.LOW_DURABILITY;
            if (oldState != currentState && SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Low durability detected");
            }
            return;
        }

        // Check if rod is cast
        FishingHook hook = player.fishing;
        if (hook == null || !hook.isAlive()) {
            soundBasedBiteDetected = false; // Reset sound flag when hook is gone
            ticksSinceBiteDetected = -1; // Reset bite timer
            if (waitingForMinigame) {
                currentState = FishingState.REELING;
            } else {
                currentState = FishingState.IDLE;
                // Reset cast delay when returning to IDLE to prevent immediate re-casting
                if (oldState != FishingState.IDLE) {
                    castDelayTicks = MIN_CAST_DELAY;
                }
            }
            return;
        }

        // Check if fish is biting - with detailed logging
        try {
            FishingHookAccessor accessor = (FishingHookAccessor) hook;
            int timeUntilHooked = accessor.getTimeUntilHooked();
            int nibble = accessor.getNibble();

            // Log every 20 ticks (once per second) when in CAST state (debug only)
            if (SFConfig.isDebugMode() && oldState == FishingState.CAST && ticksSinceLastAction % 20 == 0) {
                StardewFishing.LOGGER.info("[DEBUG] Fishing: timeUntilHooked={}, nibble={}, velocity.y={}",
                    timeUntilHooked, nibble, hook.getDeltaMovement().y);
            }
        } catch (Exception e) {
            // Ignore
        }

        // Check if fish is biting
        if (isFishBiting(hook)) {
            currentState = FishingState.FISH_BITING;
            if (oldState != currentState) {
                if (SFConfig.isDebugMode()) {
                    String reelStatus = autoReelEnabled ? "will auto-reel" : "auto-reel disabled";
                    StardewFishing.LOGGER.info("[DEBUG] Fish biting! ({})", reelStatus);
                }
                // Reset tick counter so auto-reel can trigger immediately
                ticksSinceLastAction = 0;
            }
            return;
        }

        // Check for bite timeout (fish not biting for too long)
        if (currentState == FishingState.CAST && ticksSinceLastAction > BITE_WAIT_TIMEOUT) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Bite timeout, reeling in to re-cast");
            }
            // Reel in and reset (reuse existing rodHand variable from earlier in the method)
            if (rodHand != null && player.fishing != null) {
                performReel(player, rodHand);
            }
            ticksSinceLastAction = 0;
            return;
        }

        currentState = FishingState.CAST;
        if (oldState != currentState) {
            ticksSinceLastAction = 0; // Reset timer when entering CAST state
        }
    }

    /**
     * Handles automatic casting logic
     */
    private void handleAutoCast(LocalPlayer player) {
        // Only decrement cast delay when in IDLE state (prevents delay from counting down during fishing)
        if (currentState == FishingState.IDLE && castDelayTicks > 0) {
            castDelayTicks--;
        }

        // Only cast when in IDLE state, delay has expired, AND hook is null
        if (currentState == FishingState.IDLE) {
            if (castDelayTicks > 0) {
                // Still in cooldown, skip
                return;
            }

            // Extra check: make sure no hook exists
            if (player.fishing != null && player.fishing.isAlive()) {
                // Hook still exists, don't cast yet
                if (SFConfig.isDebugMode()) {
                    StardewFishing.LOGGER.warn("[DEBUG] Cannot cast, hook still exists");
                }
                return;
            }

            InteractionHand rodHand = getRodHand(player);
            if (rodHand != null) {
                ItemStack rod = player.getItemInHand(rodHand);

                // Final durability check before casting
                if (checkDurability(player, rod)) {
                    if (SFConfig.isDebugMode()) {
                        StardewFishing.LOGGER.info("[DEBUG] Auto-casting rod");
                    }
                    performCast(player, rodHand);
                    castDelayTicks = MIN_CAST_DELAY;
                    ticksSinceLastAction = 0;
                } else if (autoReplaceTool) {
                    // Try to replace rod or bobber
                    if (replaceToolsFromInventory(player)) {
                        if (SFConfig.isDebugMode()) {
                            StardewFishing.LOGGER.info("[DEBUG] Replaced fishing tools");
                        }
                    } else if (SFConfig.isDebugMode()) {
                        StardewFishing.LOGGER.warn("[DEBUG] Cannot replace tools");
                    }
                }
            }
        } else if (currentState == FishingState.LOW_DURABILITY && autoReplaceTool) {
            if (replaceToolsFromInventory(player)) {
                currentState = FishingState.IDLE;
            }
        }
    }

    /**
     * Handles automatic reeling logic
     */
    private void handleAutoReel(LocalPlayer player) {
        // NEVER reel during minigame - check both state AND screen
        Minecraft mc = Minecraft.getInstance();
        if (currentState == FishingState.IN_MINIGAME || mc.screen instanceof FishingScreen) {
            return;
        }

        // Only reel ONCE when fish is biting (on first tick of FISH_BITING state)
        // Use waitingForMinigame flag to ensure we only reel once
        if (currentState == FishingState.FISH_BITING && ticksSinceLastAction == 0 && !waitingForMinigame) {
            InteractionHand rodHand = getRodHand(player);
            if (rodHand != null) {
                if (SFConfig.isDebugMode()) {
                    StardewFishing.LOGGER.info("[DEBUG] Auto-reeling");
                }
                performReel(player, rodHand);
                waitingForMinigame = true;
                ticksSinceLastAction = 1000; // Set high value to prevent any re-trigger
            }
        }
    }

    /**
     * Performs the casting action
     */
    private void performCast(LocalPlayer player, InteractionHand hand) {
        // NEVER cast during minigame - safety check (check both state AND screen)
        Minecraft mc = Minecraft.getInstance();
        if (currentState == FishingState.IN_MINIGAME || mc.screen instanceof FishingScreen) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Blocked cast during minigame");
            }
            return;
        }

        ItemStack rod = player.getItemInHand(hand);
        if (rod.getItem() instanceof FishingRodItem) {
            // Simulate right-click to cast
            if (mc.gameMode != null) {
                mc.gameMode.useItem(player, hand);
            } else {
                // Fallback to direct use
                rod.use(player.level(), player, hand);
            }
        }
    }

    /**
     * Performs the reeling action
     */
    private void performReel(LocalPlayer player, InteractionHand hand) {
        // NEVER reel during minigame - safety check (check both state AND screen)
        Minecraft mc = Minecraft.getInstance();
        if (currentState == FishingState.IN_MINIGAME || mc.screen instanceof FishingScreen) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Blocked reel during minigame");
            }
            return;
        }

        ItemStack rod = player.getItemInHand(hand);
        if (rod.getItem() instanceof FishingRodItem && player.fishing != null) {
            // Simulate right-click to reel in
            if (mc.gameMode != null) {
                mc.gameMode.useItem(player, hand);
            } else {
                // Fallback to direct use
                rod.use(player.level(), player, hand);
            }
        } else if (SFConfig.isDebugMode()) {
            StardewFishing.LOGGER.warn("[DEBUG] Cannot reel: no rod or hook");
        }
    }

    /**
     * Checks if fish is currently biting (bobber motion indicates bite)
     *
     * Detection methods:
     * 1. Vanilla: timeUntilHooked > 0
     * 2. Sound-based: Splash sound event detected
     *
     * For Stardew Fishing mod, sound-based detection is most reliable
     * as the mod overrides vanilla fishing mechanics.
     */
    private boolean isFishBiting(FishingHook hook) {
        try {
            FishingHookAccessor accessor = (FishingHookAccessor) hook;
            int timeUntilHooked = accessor.getTimeUntilHooked();

            // Return true if either detection method is positive
            return timeUntilHooked > 0 || soundBasedBiteDetected;
        } catch (Exception e) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.warn("[DEBUG] Exception in isFishBiting: {}", e.getMessage());
            }
            return false;
        }
    }

    /**
     * Checks if rod and bobber have sufficient durability
     */
    private boolean checkDurability(LocalPlayer player, ItemStack rod) {
        if (!stopOnLowDurability && !autoReplaceTool) {
            return true; // Don't check if not managing durability
        }

        // Check rod durability
        int rodDurability = rod.getMaxDamage() - rod.getDamageValue();
        if (rodDurability < minRodDurability) {
            StardewFishing.LOGGER.debug("Rod durability too low: {}/{}", rodDurability, rod.getMaxDamage());
            return false;
        }

        // Check bobber durability
        ItemStack bobber = ItemUtils.getBobber(rod);
        if (!bobber.isEmpty() && bobber.isDamageableItem()) {
            int bobberDurability = bobber.getMaxDamage() - bobber.getDamageValue();
            if (bobberDurability < minBobberDurability) {
                StardewFishing.LOGGER.debug("Bobber durability too low: {}/{}", bobberDurability, bobber.getMaxDamage());
                return false;
            }
        }

        return true;
    }

    /**
     * Attempts to replace fishing rod or bobber from inventory
     */
    private boolean replaceToolsFromInventory(LocalPlayer player) {
        InteractionHand rodHand = getRodHand(player);
        if (rodHand == null) return false;

        ItemStack currentRod = player.getItemInHand(rodHand);
        boolean replaced = false;

        // Check if we need to replace the rod
        int rodDurability = currentRod.getMaxDamage() - currentRod.getDamageValue();
        if (rodDurability < minRodDurability) {
            // Look for a better rod in inventory
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof FishingRodItem) {
                    int newRodDurability = stack.getMaxDamage() - stack.getDamageValue();
                    if (newRodDurability >= minRodDurability) {
                        // Swap rods
                        player.getInventory().setItem(i, currentRod);
                        player.setItemInHand(rodHand, stack);
                        currentRod = stack;
                        replaced = true;
                        if (SFConfig.isDebugMode()) {
                            StardewFishing.LOGGER.info("[DEBUG] Replaced rod (durability: {}/{})", newRodDurability, stack.getMaxDamage());
                        }
                        break;
                    }
                }
            }
        }

        // Check if we need to replace the bobber
        ItemStack currentBobber = ItemUtils.getBobber(currentRod);
        if (!currentBobber.isEmpty() && currentBobber.isDamageableItem()) {
            int bobberDurability = currentBobber.getMaxDamage() - currentBobber.getDamageValue();
            if (bobberDurability < minBobberDurability) {
                // Look for a better bobber in inventory
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    // Check if this is a bobber item (has the same item type)
                    if (stack.getItem() == currentBobber.getItem()) {
                        int newBobberDurability = stack.getMaxDamage() - stack.getDamageValue();
                        if (newBobberDurability >= minBobberDurability) {
                            // Replace bobber
                            ItemUtils.setBobber(currentRod, stack);
                            player.getInventory().setItem(i, currentBobber);
                            replaced = true;
                            if (SFConfig.isDebugMode()) {
                                StardewFishing.LOGGER.info("[DEBUG] Replaced bobber (durability: {}/{})", newBobberDurability, stack.getMaxDamage());
                            }
                            break;
                        }
                    }
                }
            }
        }

        return replaced;
    }

    /**
     * Finds which hand is holding a fishing rod
     */
    @Nullable
    private InteractionHand getRodHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) {
            return InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof FishingRodItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    // Getters and setters

    public boolean isAutoCastEnabled() {
        return autoCastEnabled;
    }

    public void setAutoCastEnabled(boolean enabled) {
        this.autoCastEnabled = enabled;
    }

    public void toggleAutoCast() {
        setAutoCastEnabled(!autoCastEnabled);
    }

    public boolean isAutoReelEnabled() {
        return autoReelEnabled;
    }

    public void setAutoReelEnabled(boolean enabled) {
        this.autoReelEnabled = enabled;
    }

    public void toggleAutoReel() {
        setAutoReelEnabled(!autoReelEnabled);
    }

    public FishingState getCurrentState() {
        return currentState;
    }

    public void setMinRodDurability(int durability) {
        this.minRodDurability = Math.max(0, durability);
    }

    public int getMinRodDurability() {
        return minRodDurability;
    }

    public void setMinBobberDurability(int durability) {
        this.minBobberDurability = Math.max(0, durability);
    }

    public int getMinBobberDurability() {
        return minBobberDurability;
    }

    public void setStopOnLowDurability(boolean stop) {
        this.stopOnLowDurability = stop;
    }

    public boolean shouldStopOnLowDurability() {
        return stopOnLowDurability;
    }

    public void setAutoReplaceTool(boolean replace) {
        this.autoReplaceTool = replace;
    }

    public boolean shouldAutoReplaceTool() {
        return autoReplaceTool;
    }

    /**
     * Gets durability info for display
     */
    public String getDurabilityInfo(LocalPlayer player) {
        InteractionHand rodHand = getRodHand(player);
        if (rodHand == null) {
            return "No rod";
        }

        ItemStack rod = player.getItemInHand(rodHand);
        int rodDurability = rod.getMaxDamage() - rod.getDamageValue();
        int rodMax = rod.getMaxDamage();

        StringBuilder info = new StringBuilder();
        info.append(String.format("Rod: %d/%d", rodDurability, rodMax));

        ItemStack bobber = ItemUtils.getBobber(rod);
        if (!bobber.isEmpty() && bobber.isDamageableItem()) {
            int bobberDurability = bobber.getMaxDamage() - bobber.getDamageValue();
            int bobberMax = bobber.getMaxDamage();
            info.append(String.format(" | Bobber: %d/%d", bobberDurability, bobberMax));
        }

        return info.toString();
    }

    /**
     * Resets the controller state
     */
    public void reset() {
        currentState = FishingState.IDLE;
        ticksSinceLastAction = 0;
        castDelayTicks = 0;
        waitingForMinigame = false;
    }
}
