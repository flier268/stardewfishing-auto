package com.bonker.stardewfishing.client;

import com.bonker.stardewfishing.SFConfig;
import com.bonker.stardewfishing.StardewFishing;
import com.bonker.stardewfishing.client.util.Animation;
import com.bonker.stardewfishing.client.util.RenderUtil;
import com.bonker.stardewfishing.client.util.Shake;
import com.bonker.stardewfishing.common.init.SFSoundEvents;
import com.bonker.stardewfishing.common.networking.C2SCompleteMinigamePacket;
import com.bonker.stardewfishing.common.networking.S2CStartMinigamePacket;
import com.bonker.stardewfishing.common.networking.SFNetworking;
import com.bonker.stardewfishing.proxy.ItemUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public class FishingScreen extends Screen {
    private static final Component TITLE = Component.literal("Fishing Minigame");
    private static final ResourceLocation TEXTURE = StardewFishing.resource("textures/gui/minigame.png");
    private static final ResourceLocation NETHER_TEXTURE = StardewFishing.resource("textures/gui/minigame_nether.png");
    private static final ResourceLocation CHEST_TEXTURE = StardewFishing.resource("textures/gui/chest.png");
    private static final ResourceLocation GOLDEN_CHEST_TEXTURE = StardewFishing.resource("textures/gui/golden_chest.png");

    private static final int GUI_WIDTH = 38;
    private static final int GUI_HEIGHT = 152;
    private static final int HIT_WIDTH = 73;
    private static final int HIT_HEIGHT = 29;
    private static final int PERFECT_WIDTH = 41;
    private static final int PERFECT_HEIGHT = 12;

    private static final float ALPHA_PER_TICK = 1F / 10;
    private static final float HANDLE_ROT_FAST = Mth.PI / 3;
    private static final float HANDLE_ROT_SLOW = Mth.PI / -7F;

    private static final int REEL_FAST_LENGTH = 30;
    private static final int REEL_SLOW_LENGTH = 20;
    private static final int CREAK_LENGTH = 6;

    private final FishingMinigame minigame;
    private final ItemStack fish;
    private final boolean lava;

    private int leftPos, topPos;
    private Status status = Status.HIT_TEXT;
    private double accuracy = -1;
    private boolean mouseDown = false;
    private int animationTimer = 0;
    private boolean gotChest = false;
    private boolean goldenChest = false;

    private final Animation textSize = new Animation(0);
    private final Animation progressBar;
    private final Animation bobberPos = new Animation(0);
    private final Animation bobberAlpha = new Animation(1);
    private final Animation fishPos = new Animation(0);
    private final Animation handleRot = new Animation(0);
    private final Animation chestProgress = new Animation(0);
    private final Animation chestAppear = new Animation(0);

    private final Shake shake = new Shake(0.75F, 1);
    private final Shake chestShake = new Shake(0.75F, 1);

    public int reelSoundTimer = -1;
    private int creakSoundTimer = 0;

    private float partialTick = 0;

    // Auto-fishing feature
    private boolean autoFishingEnabled = true;
    private double lastBobberVelocity = 0;

    // Strategy state machine to prevent oscillation
    private enum Strategy {
        FOLLOW_FISH,    // Primary: keep bobber on fish
        PURSUE_CHEST    // Secondary: go for treasure chest
    }
    private Strategy currentStrategy = Strategy.FOLLOW_FISH;
    private int strategyCommitmentTicks = 0; // Remaining ticks committed to current strategy
    private static final int MIN_STRATEGY_COMMITMENT = 10; // Minimum ticks before strategy change (0.5 sec)
    private float lastFishProgress = 0.0f;

    public FishingScreen(S2CStartMinigamePacket packet) {
        super(TITLE);
        this.minigame = new FishingMinigame(this, packet, Objects.requireNonNull(Minecraft.getInstance().player), packet.lineStrength(), packet.barSize());
        this.fish = packet.fish();
        this.progressBar = new Animation(minigame.getProgress());
        this.lava = packet.lava();
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (minecraft == null) return;
        partialTick = minecraft.getFrameTime();

        PoseStack poseStack = pGuiGraphics.pose();
        ResourceLocation texture = lava ? NETHER_TEXTURE : TEXTURE;

        if (status == Status.HIT_TEXT) {
            // render HIT!
            float scale = textSize.getInterpolated(partialTick) * 1.5F;
            float x = (width - HIT_WIDTH * scale) / 2;
            float y = (height - HIT_HEIGHT * scale) / 3;

            poseStack.pushPose();
            poseStack.scale(scale, scale, 1);
            RenderUtil.blitF(pGuiGraphics, texture, x * (1 / scale), y * (1 / scale), 71, 0, HIT_WIDTH, HIT_HEIGHT);
            poseStack.popPose();
        } else if (status == Status.CHEST_OPENING) {
            // darken screen
            renderBackground(pGuiGraphics);

            int frame = Math.min(30 - animationTimer, 19) / 2;
            pGuiGraphics.blit(goldenChest ? GOLDEN_CHEST_TEXTURE : CHEST_TEXTURE, leftPos + 38 / 2 - 64, topPos, 0, frame * 128, 128, 128, 128, 1280);
        } else {
            // darken screen
            renderBackground(pGuiGraphics);

            RenderUtil.drawWithShake(poseStack, shake, partialTick, status == Status.SUCCESS || status == Status.FAILURE, () -> {
                RenderUtil.drawWithBlend(() -> {
                    // draw fishing gui
                    pGuiGraphics.blit(texture, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT);

                    // draw bobber
                    RenderUtil.drawWithAlpha(bobberAlpha.getInterpolated(partialTick), () -> {
                        int size = minigame.getBarSize();
                        float bobberY = 4 - size + (142 - bobberPos.getInterpolated(partialTick));
                        // clamp decimal part to multiples of 0.1 to prevent floating point visual artifacts
                        bobberY = (int) (bobberY * 10) / 10F;

                        RenderUtil.blitF(pGuiGraphics, texture, leftPos + 18, topPos + bobberY, 38, 0, 9, 2);
                        RenderUtil.blitRepeatingF(pGuiGraphics, texture, leftPos + 18, topPos + bobberY + 2, 38, 2, 9, size - 4, 9, 1);
                        RenderUtil.blitF(pGuiGraphics, texture, leftPos + 18, topPos + bobberY + size - 2, 38, 3, 9, 2);
                    });
                });

                // draw sonar bobber
                if (minigame.hasSonarBobber()) {
                    pGuiGraphics.blit(texture, leftPos + 38, topPos + 2, 185, 0, 26, 25);

                    pGuiGraphics.renderItem(fish, leftPos + 45, topPos + 8);
                    if (pMouseX >= leftPos + 38 && pMouseY >= topPos + 5 && pMouseX <= leftPos + 64 && pMouseY <= topPos + 27) {
                        pGuiGraphics.renderTooltip(font, AbstractContainerScreen.getTooltipFromItem(minecraft, fish).subList(0, 1), fish.getTooltipImage(), fish, pMouseX, pMouseY);
                    }
                }

                RenderUtil.drawWithShake(poseStack, shake, partialTick, minigame.isBobberOnFish() && status == Status.MINIGAME, () -> {
                    float pos = fishPos.getInterpolated(partialTick);
                    int offset = 0;
                    if (ItemUtils.isLegendaryFish(fish)) {
                        offset += 15;
                        if (SFConfig.isLegendaryFlashingEnabled() && (int) (pos / 8) % 2 == 1) {
                            offset += 15;
                        }
                    }
                    // draw fish
                    float fishY = 4 - 16 + (142 - pos);
                    RenderUtil.blitF(pGuiGraphics, texture, leftPos + 14, topPos + fishY, 55, offset, 16, 15);
                });

                if (minigame.isChestVisible() || animationTimer < 0) {
                    float scale = chestAppear.getInterpolated(partialTick);
                    if (scale != 0) {
                        poseStack.pushPose();
                        poseStack.scale(scale, scale, 1);

                        float chestX = (leftPos + 24 - 8 * scale) / scale;
                        float chestY = (topPos + 4 - 13 + (142 + 8 - 8 * scale - minigame.getChestPos())) / scale;

                        RenderUtil.drawWithShake(poseStack, chestShake, partialTick, minigame.isBobberOnChest() && status == Status.MINIGAME, () -> {
                            // draw treasure chest
                            RenderUtil.blitF(pGuiGraphics, texture, chestX, chestY, 211, minigame.isGoldenChest() ? 13 : 0, 13, 13);
                        });

                        // bar bg
                        RenderUtil.fillF(pGuiGraphics, chestX + 1, chestY + 12, chestX + 12, chestY + 14, 0, 0x55000000);

                        // bar color
                        float progress = chestProgress.getInterpolated(partialTick);
                        int color = Mth.hsvToRgb(progress / 3.0F, 1.0F, 1.0F) | 0xFF000000;
                        RenderUtil.fillF(pGuiGraphics, chestX + 1, chestY + 12, chestX + 1 + progress * 11, chestY + 14, 200, color);

                        poseStack.popPose();
                    }
                }

                // draw progress bar
                float progress = progressBar.getInterpolated(partialTick);
                int color = Mth.hsvToRgb(progress / 3.0F, 1.0F, 1.0F) | 0xFF000000;
                RenderUtil.fillF(pGuiGraphics, leftPos + 33, topPos + 148, leftPos + 37, topPos + 148 - progress * 145, 0, color);

                // draw handle
                RenderUtil.drawRotatedAround(poseStack, handleRot.getInterpolated(partialTick), leftPos + 6.5F, topPos + 130.5F, () -> {
                    pGuiGraphics.blit(texture, leftPos + 5, topPos + 129, 47, 0, 8, 3);
                });

                // render PERFECT!
                if (status == Status.SUCCESS && accuracy == 1) {
                    float scale = textSize.getInterpolated(partialTick);
                    float x = leftPos + 2 + (PERFECT_WIDTH - PERFECT_WIDTH * scale) / 2;
                    float y = topPos - PERFECT_HEIGHT * scale;

                    poseStack.pushPose();
                    poseStack.scale(scale, scale, 1);
                    RenderUtil.blitF(pGuiGraphics, texture, x / scale, y / scale, 144, 0, PERFECT_WIDTH, PERFECT_HEIGHT);
                    poseStack.popPose();
                }
            });
        }

        if (status != Status.HIT_TEXT) {
            pGuiGraphics.drawString(font, StardewFishing.MOD_NAME, 2, height - 2 - font.lineHeight, 0x6969697F);

            // Display auto-fishing status
            if (status == Status.MINIGAME) {
                String autoText = autoFishingEnabled ? "Auto: ON (A)" : "Auto: OFF (A)";
                int color = autoFishingEnabled ? 0x00FF00FF : 0xFF0000FF;
                pGuiGraphics.drawString(font, autoText, 2, 2, color);
            }
        }
    }

    @Override
    protected void init() {
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;
    }

    @Override
    public void tick() {
        shake.tick();
        if (minigame.isChestVisible()) {
            chestShake.tick();
        }

        switch (status) {
            case HIT_TEXT -> {
                if (animationTimer < 20) {
                    if (++animationTimer == 20) {
                        status = Status.MINIGAME;
                        animationTimer = Integer.MAX_VALUE;
                    } else if (animationTimer <= 5) {
                        textSize.addValue(0.2F);
                    } else if (animationTimer <= 15) {
                        textSize.addValue(-0.013F);
                    } else {
                        textSize.addValue(-0.16F);
                    }
                }
            }
            case MINIGAME -> {
                // Auto-fishing: combine manual input with AI decision
                boolean effectiveMouseDown = mouseDown || shouldAutoClick();
                minigame.tick(effectiveMouseDown);

                boolean onFish = minigame.isBobberOnFish();

                progressBar.setValue(minigame.getProgress());
                bobberPos.setValue(minigame.getBobberPos());
                bobberAlpha.addValue((onFish || minigame.isBobberOnChest()) ? ALPHA_PER_TICK : -ALPHA_PER_TICK, 0.4F, 1);
                fishPos.setValue(minigame.getFishPos());
                handleRot.addValue(onFish ? HANDLE_ROT_FAST : HANDLE_ROT_SLOW);

                if (status != Status.MINIGAME) {
                    break;
                }

                if (minigame.isChestVisible()) {
                    if (animationTimer == Integer.MAX_VALUE) {
                        animationTimer = 5;
                    }

                    if (animationTimer > 0) {
                        animationTimer--;
                        chestAppear.addValue(0.2F);

                        if (animationTimer == 0) {
                            animationTimer = Integer.MIN_VALUE;
                            chestAppear.setValue(1);
                        }
                    }

                    chestProgress.setValue(minigame.getChestProgress());
                } else {
                    if (animationTimer == Integer.MIN_VALUE) {
                        animationTimer = -5;

                        playSound(SFSoundEvents.CHEST_GET.get());
                    }

                    if (animationTimer < 0) {
                        animationTimer++;
                        chestAppear.addValue(-0.2F);

                        if (animationTimer == 0) {
                            chestAppear.setValue(0);
                        }
                    }
                }

                if (reelSoundTimer == -1 || --reelSoundTimer == 0) {
                    reelSoundTimer = onFish ? REEL_FAST_LENGTH : REEL_SLOW_LENGTH;
                    playSound(onFish ? SFSoundEvents.REEL_FAST.get() : SFSoundEvents.REEL_SLOW.get());
                }

                if (creakSoundTimer > 0) {
                    creakSoundTimer--;
                }
                if (effectiveMouseDown && creakSoundTimer == 0) {
                    creakSoundTimer = CREAK_LENGTH;
                    playSound(SFSoundEvents.REEL_CREAK.get());
                }
            }
            case SUCCESS, FAILURE -> {
                if (--animationTimer == 0) {
                    if (gotChest) {
                        status = Status.CHEST_OPENING;
                        animationTimer = 30;

                        playSound(goldenChest ? SFSoundEvents.OPEN_CHEST_GOLDEN.get() : SFSoundEvents.OPEN_CHEST.get());
                    } else {
                        onClose();
                    }
                } else if (animationTimer >= 15) {
                    textSize.addValue(0.2F);
                } else if (animationTimer >= 5) {
                    textSize.addValue(-0.013F);
                } else {
                    textSize.addValue(-0.16F);
                }
            }
            case CHEST_OPENING -> {
                if (--animationTimer == 0) {
                    onClose();
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (status == Status.MINIGAME && pButton == GLFW.GLFW_MOUSE_BUTTON_1 || pButton == GLFW.GLFW_MOUSE_BUTTON_2) {
            if (!mouseDown) {
                playSound(SFSoundEvents.REEL_CREAK.get());
                mouseDown = true;
            }
            return true;
        } else {
            return super.mouseClicked(pMouseX, pMouseY, pButton);
        }
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == GLFW.GLFW_MOUSE_BUTTON_1 || pButton == GLFW.GLFW_MOUSE_BUTTON_2) {
            if (mouseDown) {
                mouseDown = false;
            }
            return true;
        } else {
            return super.mouseReleased(pMouseX, pMouseY, pButton);
        }
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        // Toggle auto-fishing with 'A' key
        if (pKeyCode == GLFW.GLFW_KEY_A && status == Status.MINIGAME) {
            autoFishingEnabled = !autoFishingEnabled;
            playSound(autoFishingEnabled ? SFSoundEvents.COMPLETE.get() : SFSoundEvents.DWOP.get());
            return true;
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
        SFNetworking.sendToServer(new C2SCompleteMinigamePacket(status == Status.SUCCESS || status == Status.CHEST_OPENING, accuracy, gotChest));

        stopReelingSounds();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return status == Status.MINIGAME;
    }

    @Override
    public boolean isPauseScreen() {
        return status != Status.HIT_TEXT;
    }

    public void setResult(boolean success, double accuracy, boolean gotChest, boolean goldenChest) {
        status = success ? Status.SUCCESS : Status.FAILURE;
        this.accuracy = accuracy;
        this.gotChest = gotChest;
        this.goldenChest = goldenChest;

        animationTimer = 20;
        textSize.reset(0.0F);

        progressBar.freeze(partialTick);
        bobberPos.freeze(partialTick);
        bobberAlpha.freeze(partialTick);
        fishPos.freeze(partialTick);
        handleRot.freeze(partialTick);
        chestProgress.freeze(partialTick);
        chestAppear.freeze(partialTick);

        playSound(success ? SFSoundEvents.COMPLETE.get() : SFSoundEvents.FISH_ESCAPE.get());
        stopReelingSounds();
        reelSoundTimer = -2;
        shake.setValues(2.0F, 1);
    }

    public void playSound(SoundEvent soundEvent) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
    }

    public void stopReelingSounds() {
        reelSoundTimer = 1;

        minecraft.getSoundManager().stop(SFSoundEvents.REEL_FAST.getId(), null);
        minecraft.getSoundManager().stop(SFSoundEvents.REEL_SLOW.getId(), null);
    }

    /**
     * Auto-fishing AI logic with state commitment to prevent oscillation
     * Returns true if the bobber should be moved up (click), false otherwise
     */
    private boolean shouldAutoClick() {
        if (!autoFishingEnabled) {
            return false;
        }

        double bobberPos = minigame.getBobberPos();
        double fishPos = minigame.getFishPos();
        int barSize = minigame.getBarSize();
        float fishProgress = minigame.getProgress();

        // Calculate bobber bar center and range
        double bobberCenter = bobberPos + (barSize / 2.0);

        // Track fish progress change rate (for emergency abort)
        float progressChange = fishProgress - lastFishProgress;
        lastFishProgress = fishProgress;

        // Decrement commitment timer
        if (strategyCommitmentTicks > 0) {
            strategyCommitmentTicks--;
        }

        // EMERGENCY ABORT CONDITIONS for chest pursuit
        // Only abort in truly critical situations to avoid over-conservative behavior
        if (currentStrategy == Strategy.PURSUE_CHEST) {
            boolean shouldAbort = false;
            String abortReason = "";

            // Emergency 1: Fish progress dropping VERY rapidly (catastrophic)
            if (progressChange < -0.025f) {
                shouldAbort = true;
                abortReason = String.format("catastrophic progress drop: %.3f", progressChange);
            }

            // Emergency 2: Fish progress critically low (about to fail)
            if (fishProgress < 0.15f) {
                shouldAbort = true;
                abortReason = String.format("critical fish progress: %.2f", fishProgress);
            }

            // Emergency 3: Low progress AND dropping rapidly
            if (fishProgress < 0.3f && progressChange < -0.02f) {
                shouldAbort = true;
                abortReason = String.format("low progress + rapid drop: %.2f / %.3f", fishProgress, progressChange);
            }

            // Emergency 4: Chest extremely far and progress declining significantly
            if (minigame.isChestVisible()) {
                int chestPos = minigame.getChestPos();
                double distanceToChest = Math.abs(chestPos - bobberCenter);
                float chestProgress = minigame.getChestProgress();

                // Only abort for extreme distance if: far + low chest progress + declining fish
                if (distanceToChest > 70 && chestProgress < 0.3f &&
                    progressChange < -0.015f && fishProgress < 0.5f) {
                    shouldAbort = true;
                    abortReason = String.format("extreme distance + bad conditions: %.1f pixels", distanceToChest);
                }
            }

            if (shouldAbort) {
                if (SFConfig.isDebugMode()) {
                    StardewFishing.LOGGER.info("[AUTO] EMERGENCY ABORT! Reason: {}", abortReason);
                }
                currentStrategy = Strategy.FOLLOW_FISH;
                strategyCommitmentTicks = MIN_STRATEGY_COMMITMENT;
            }
        }

        // Decide strategy (only if not committed to current strategy)
        if (strategyCommitmentTicks == 0 && minigame.isChestVisible() && !minigame.gotChest()) {
            Strategy newStrategy = decideStrategy(bobberCenter, fishPos, barSize, fishProgress);

            // Only change strategy if different from current
            if (newStrategy != currentStrategy) {
                if (SFConfig.isDebugMode()) {
                    StardewFishing.LOGGER.info("[AUTO] Strategy change: {} -> {}", currentStrategy, newStrategy);
                }
                currentStrategy = newStrategy;
                strategyCommitmentTicks = MIN_STRATEGY_COMMITMENT;
            }
        }

        // Execute current strategy
        if (currentStrategy == Strategy.PURSUE_CHEST && minigame.isChestVisible() && !minigame.gotChest()) {
            return executeChestPursuit(bobberCenter, fishPos, barSize);
        } else {
            return executeFishFollowing(bobberPos, fishPos, bobberCenter, barSize);
        }
    }

    /**
     * Decides which strategy to use based on current game state
     * Uses cost-benefit analysis considering progress decay rates and movement time
     */
    private Strategy decideStrategy(double bobberCenter, double fishPos, int barSize, float fishProgress) {
        // If no chest visible, always follow fish
        if (!minigame.isChestVisible() || minigame.gotChest()) {
            return Strategy.FOLLOW_FISH;
        }

        int chestPos = minigame.getChestPos();
        float chestProgress = minigame.getChestProgress();
        double bobberVelocity = minigame.getBobberVelocity();
        double fishVelocity = minigame.getFishVelocity();

        // Calculate distances
        double distanceToFish = Math.abs(fishPos - bobberCenter);
        double distanceToChest = Math.abs(chestPos - bobberCenter);

        // Predict future positions
        double predictedBobberCenter = bobberCenter + bobberVelocity * 2.5;
        double predictedFishPos = fishPos + fishVelocity * 2.5;
        double predictedDistToFish = Math.abs(predictedFishPos - predictedBobberCenter);

        // CRITICAL: Estimate time to reach chest (rough estimate based on distance)
        // Average bobber speed is around 0.7-1.5 pixels/tick
        double estimatedTicksToChest = distanceToChest / 1.0;

        // Calculate progress decay rates (from FishingMinigame.java)
        float CHEST_DECAY_RATE = 0.25f; // Per tick when not on chest
        float FISH_DECAY_RATE = 1.0f; // Approximate per tick when not on fish

        // Calculate expected losses during chest pursuit
        float expectedChestDecayIfIgnored = CHEST_DECAY_RATE * (float)estimatedTicksToChest;
        float expectedFishLossDuringPursuit = FISH_DECAY_RATE * (float)estimatedTicksToChest / 120.0f; // Normalized to 0-1

        // PRIORITY 1: Chest is almost done (>65%) - MUST finish or waste investment!
        // This is sunk cost consideration: abandoning high-progress chest wastes previous effort
        if (chestProgress > 0.65f && fishProgress > 0.25f) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.info("[AUTO] High chest progress ({}), completing it!",
                    String.format("%.2f", chestProgress));
            }
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 2: Can cover both targets in trajectory (most efficient - no decay!)
        boolean canCoverBoth = canCoverBothTargets(predictedBobberCenter, predictedFishPos,
                                                    chestPos, barSize, bobberVelocity);
        if (canCoverBoth && fishProgress > 0.3f) {
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 3: Currently on fish AND chest has some progress
        // If we're already positioned well, go get it
        if (minigame.isBobberOnFish() && chestProgress > 0.25f && fishProgress > 0.45f) {
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 4: High fish progress gives us safety margin
        // With high fish progress, we can afford to pursue chest more aggressively
        if (fishProgress > 0.7f && chestProgress > 0.15f && distanceToChest < 50) {
            // Fish is very safe, go for chest
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 5: Medium fish progress, check if chest is reasonably close
        if (fishProgress > 0.5f && distanceToChest < 40) {
            // Fish is reasonably close or moving slowly
            if (distanceToFish < barSize || Math.abs(fishVelocity) < 1.2) {
                // Some chest progress justifies pursuit
                if (chestProgress > 0.2f) {
                    return Strategy.PURSUE_CHEST;
                }
            }
        }

        // PRIORITY 6: New chest appears - try to get it early
        // Early pursuit is efficient (shorter distance to travel)
        if (chestProgress < 0.1f && fishProgress > 0.4f && distanceToChest < 35) {
            if (SFConfig.isDebugMode()) {
                StardewFishing.LOGGER.info("[AUTO] Early chest pursuit: dist={}, fishProg={}",
                    String.format("%.1f", distanceToChest),
                    String.format("%.2f", fishProgress));
            }
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 7: Fish is idle/very slow - good opportunity
        if (Math.abs(fishVelocity) < 0.5 && fishProgress > 0.5f && distanceToChest < 50) {
            return Strategy.PURSUE_CHEST;
        }

        // PRIORITY 8: Moderate conditions, but chest is reasonably close
        if (distanceToChest < 30 && fishProgress > 0.4f && chestProgress > 0.1f) {
            // Close enough to try
            boolean fishReasonablySafe = distanceToFish < barSize * 1.2 || Math.abs(fishVelocity) < 1.5;
            if (fishReasonablySafe) {
                return Strategy.PURSUE_CHEST;
            }
        }

        // Default: Follow fish (conservative approach)
        return Strategy.FOLLOW_FISH;
    }

    /**
     * Execute chest pursuit strategy with intelligent path planning
     * Tries to minimize fish progress loss while moving toward chest
     */
    private boolean executeChestPursuit(double bobberCenter, double fishPos, int barSize) {
        double bobberPos = minigame.getBobberPos();
        double bobberVelocity = minigame.getBobberVelocity();
        int maxBobberHeight = 142 - barSize;

        // Apply same boundary braking as fish following
        double BRAKE_THRESHOLD_VELOCITY = -1.0;
        double BOTTOM_BRAKE_DISTANCE = 15.0;
        double TOP_BRAKE_DISTANCE = 15.0;

        // Bottom boundary braking
        if (bobberVelocity < BRAKE_THRESHOLD_VELOCITY && bobberPos < BOTTOM_BRAKE_DISTANCE) {
            if (SFConfig.isDebugMode() && bobberPos < 5) {
                StardewFishing.LOGGER.info("[AUTO] BRAKE! (Chest pursuit) Bottom approach: pos={}, vel={}",
                    String.format("%.1f", bobberPos), String.format("%.2f", bobberVelocity));
            }
            return true; // Emergency brake
        }

        // Top boundary braking
        if (bobberVelocity > 1.0 && bobberPos > maxBobberHeight - TOP_BRAKE_DISTANCE) {
            if (SFConfig.isDebugMode() && bobberPos > maxBobberHeight - 5) {
                StardewFishing.LOGGER.info("[AUTO] BRAKE! (Chest pursuit) Top approach: pos={}, vel={}",
                    String.format("%.1f", bobberPos), String.format("%.2f", bobberVelocity));
            }
            return false; // Stop acceleration
        }

        // Intelligent chest pursuit: balance between reaching chest and staying near fish
        int chestPos = minigame.getChestPos();
        double chestCenter = chestPos;
        double distanceToChest = Math.abs(chestCenter - bobberCenter);
        double distanceToFish = Math.abs(fishPos - bobberCenter);
        float chestProgress = minigame.getChestProgress();

        // If chest progress is very high (>75%), go straight for it - almost done!
        if (chestProgress > 0.75f) {
            return chestCenter > bobberCenter;
        }

        // If chest is close (< 25 pixels), go directly
        if (distanceToChest < 25) {
            return chestCenter > bobberCenter;
        }

        // For medium distances (25-45 pixels), try to stay on fish if it's between us and chest
        if (distanceToChest >= 25 && distanceToChest < 45 && distanceToFish < barSize * 1.3) {
            // Check if fish is between us and chest
            boolean fishBetweenUsAndChest = (chestCenter > bobberCenter && fishPos > bobberCenter && fishPos < chestCenter) ||
                                            (chestCenter < bobberCenter && fishPos < bobberCenter && fishPos > chestCenter);

            if (fishBetweenUsAndChest) {
                // Follow fish toward chest (maintain progress while traveling)
                double fishDistanceFromCenter = fishPos - bobberCenter;
                double fishDeadZone = barSize * 0.25;

                if (Math.abs(fishDistanceFromCenter) > fishDeadZone) {
                    return fishPos > bobberCenter;
                }
            }
        }

        // Default: move directly toward chest
        return chestCenter > bobberCenter;
    }

    /**
     * Execute fish following strategy with predictive control and boundary braking
     * Returns movement decision to keep fish in bobber bar
     */
    private boolean executeFishFollowing(double bobberPos, double fishPos, double bobberCenter, int barSize) {
        // Get current velocity from minigame
        double bobberVelocity = minigame.getBobberVelocity();
        int maxBobberHeight = 142 - barSize;

        // CRITICAL: Predictive braking near boundaries to prevent bounce
        // When bobber is falling fast and approaching bottom, brake!
        double BRAKE_THRESHOLD_VELOCITY = -1.0; // Fast downward velocity
        double BOTTOM_BRAKE_DISTANCE = 15.0; // Distance from bottom to start braking
        double TOP_BRAKE_DISTANCE = 15.0; // Distance from top to start braking

        // Bottom boundary braking
        if (bobberVelocity < BRAKE_THRESHOLD_VELOCITY && bobberPos < BOTTOM_BRAKE_DISTANCE) {
            // Fast fall near bottom - EMERGENCY BRAKE!
            if (SFConfig.isDebugMode() && bobberPos < 5) {
                StardewFishing.LOGGER.info("[AUTO] BRAKE! Bottom approach: pos={}, vel={}",
                    String.format("%.1f", bobberPos), String.format("%.2f", bobberVelocity));
            }
            return true; // Click to go up
        }

        // Top boundary braking
        if (bobberVelocity > 1.0 && bobberPos > maxBobberHeight - TOP_BRAKE_DISTANCE) {
            // Fast rise near top - STOP ACCELERATION!
            if (SFConfig.isDebugMode() && bobberPos > maxBobberHeight - 5) {
                StardewFishing.LOGGER.info("[AUTO] BRAKE! Top approach: pos={}, vel={}",
                    String.format("%.1f", bobberPos), String.format("%.2f", bobberVelocity));
            }
            return false; // Stop clicking
        }

        // Calculate current velocity estimate for prediction
        double velocityEstimate = bobberPos - lastBobberVelocity;
        lastBobberVelocity = bobberPos;

        // Predictive control: estimate where bobber will be in next few ticks
        double predictedBobberPos = bobberPos + velocityEstimate * 2;

        // If fish is above the predicted bobber position, click to go up
        if (fishPos > predictedBobberPos + barSize / 2.0) {
            return true;
        }

        // If fish is below the predicted bobber position, don't click to go down
        if (fishPos < predictedBobberPos - barSize / 2.0) {
            return false;
        }

        // If we're close to the fish, use fine control
        // Keep the fish in the center of the bobber bar
        double distanceFromCenter = fishPos - bobberCenter;

        // Small dead zone to prevent jittering
        double deadZone = barSize * 0.15;

        if (distanceFromCenter > deadZone) {
            // Fish is above center, need to go up
            return true;
        } else if (distanceFromCenter < -deadZone) {
            // Fish is below center, need to go down
            return false;
        }

        // In dead zone, maintain current state based on whether we're on the fish
        return minigame.isBobberOnFish() && fishPos > bobberCenter;
    }

    /**
     * Determines if the bobber can cover both fish and chest in its movement trajectory
     *
     * @param predictedBobberCenter Predicted center position of the bobber
     * @param predictedFishPos Predicted position of the fish
     * @param chestCenter Position of the treasure chest (stationary)
     * @param barSize Size of the bobber bar
     * @param bobberVelocity Current velocity of the bobber
     * @return true if both targets can be covered
     */
    private boolean canCoverBothTargets(double predictedBobberCenter, double predictedFishPos,
                                         double chestCenter, int barSize, double bobberVelocity) {
        // Calculate if predicted bobber position can cover both targets
        double predictedMin = predictedBobberCenter - (barSize / 2.0);
        double predictedMax = predictedBobberCenter + (barSize / 2.0);

        // Check if fish will be in range
        boolean fishInRange = predictedFishPos >= predictedMin && predictedFishPos <= predictedMax;

        // Check if chest will be in range
        boolean chestInRange = chestCenter >= predictedMin && chestCenter <= predictedMax;

        // Both must be in range, and we need some velocity to reach them
        return fishInRange && chestInRange && Math.abs(bobberVelocity) > 0.1;
    }


    public enum Status {
        HIT_TEXT, MINIGAME, SUCCESS, FAILURE, CHEST_OPENING
    }
}
