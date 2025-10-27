package com.bonker.stardewfishing;

import com.bonker.stardewfishing.common.init.SFAttributes;
import com.bonker.stardewfishing.server.AttributeCache;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StardewFishing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFConfig {
    static final ForgeConfigSpec SERVER_SPEC;
    static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue QUALITY_1_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue QUALITY_2_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue QUALITY_3_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue QUALITY_1_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue QUALITY_2_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue QUALITY_3_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue BITE_TIME_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue TREASURE_CHEST_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GOLDEN_CHEST_CHANCE;
    private static final ForgeConfigSpec.BooleanValue INVENTORY_BOBBER_EQUIPPING;
    private static final ForgeConfigSpec.BooleanValue LEGENDARY_FISH_FLASHING;
    private static final ForgeConfigSpec.DoubleValue LEGENDARY_FISH_CHANCE;

    // Auto-fishing configuration
    private static final ForgeConfigSpec.BooleanValue AUTO_CAST_ENABLED;
    private static final ForgeConfigSpec.BooleanValue AUTO_REEL_ENABLED;
    private static final ForgeConfigSpec.IntValue MIN_ROD_DURABILITY;
    private static final ForgeConfigSpec.IntValue MIN_BOBBER_DURABILITY;
    private static final ForgeConfigSpec.BooleanValue STOP_ON_LOW_DURABILITY;
    private static final ForgeConfigSpec.BooleanValue AUTO_REPLACE_TOOL;
    private static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    static {
        // Server configuration
        QUALITY_1_THRESHOLD = SERVER_BUILDER
                .comment("The minimum accuracy that grants an item of quality 1.")
                .defineInRange("quality1Threshold", 0.75, 0, 1);

        QUALITY_2_THRESHOLD = SERVER_BUILDER
                .comment("The minimum accuracy that grants an item of quality 2.")
                .defineInRange("quality2Threshold", 0.9, 0, 1);

        QUALITY_3_THRESHOLD = SERVER_BUILDER
                .comment("The minimum accuracy that grants an item of quality 3.")
                .defineInRange("quality3Threshold", 1.0, 0, 1);

        QUALITY_1_MULTIPLIER = SERVER_BUILDER
                .comment("The multiplier that is applied to experience gained from fishing a quality 1 reward.")
                .defineInRange("quality1Multiplier", 1.5, 1, 10);

        QUALITY_2_MULTIPLIER = SERVER_BUILDER
                .comment("The multiplier that is applied to experience gained from fishing a quality 2 reward.")
                .defineInRange("quality2Multiplier", 2.5, 1, 10);

        QUALITY_3_MULTIPLIER = SERVER_BUILDER
                .comment("The multiplier that is applied to experience gained from fishing a quality 3 reward.")
                .defineInRange("quality3Multiplier", 4.0, 1, 10);

        BITE_TIME_MULTIPLIER = SERVER_BUILDER
                .comment("The multiplier that is applied to the time it takes for a fish to bite after casting your rod.")
                .defineInRange("biteTimeMultiplier", 0.8, 0, 1);

        TREASURE_CHEST_CHANCE = SERVER_BUILDER
                .comment("The chance for finding a treasure chest each time you play the fishing minigame.")
                .defineInRange("treasureChestChance", 0.15, 0, 1);

        GOLDEN_CHEST_CHANCE = SERVER_BUILDER
                .comment("The chance that a treasure chest found in the fishing minigame is a golden chest.")
                .defineInRange("goldenChestChance", 0.1, 0, 1);

        INVENTORY_BOBBER_EQUIPPING = SERVER_BUILDER
                .comment("Whether it be possible to attach bobber items by hovering over a fishing rod in an inventory and right clicking.")
                .define("inventoryBobberEquipping", true);

        LEGENDARY_FISH_FLASHING = SERVER_BUILDER
                .comment("Whether legendary fish will have a strobe effect when moving in the minigame.")
                .define("legendaryFishFlashing", true);

        LEGENDARY_FISH_CHANCE = SERVER_BUILDER
                .comment("The chance that any fish that bites is a legendary fish.")
                .defineInRange("legendaryFishChance", 0.01, 0, 1);

        SERVER_SPEC = SERVER_BUILDER.build();

        // Client configuration (auto-fishing)
        CLIENT_BUILDER.comment("Auto-Fishing System Configuration").push("autoFishing");

        AUTO_CAST_ENABLED = CLIENT_BUILDER
                .comment("Whether auto-casting is enabled by default (can be toggled with 'C' key).")
                .define("autoCastEnabled", false);

        AUTO_REEL_ENABLED = CLIENT_BUILDER
                .comment("Whether auto-reeling is enabled by default (can be toggled with 'V' key).")
                .define("autoReelEnabled", false);

        MIN_ROD_DURABILITY = CLIENT_BUILDER
                .comment("Minimum rod durability before stopping auto-cast or attempting replacement.")
                .defineInRange("minRodDurability", 5, 0, 1000);

        MIN_BOBBER_DURABILITY = CLIENT_BUILDER
                .comment("Minimum bobber durability before stopping auto-cast or attempting replacement.")
                .defineInRange("minBobberDurability", 3, 0, 1000);

        STOP_ON_LOW_DURABILITY = CLIENT_BUILDER
                .comment("Whether to stop auto-fishing when rod/bobber durability is below minimum.")
                .define("stopOnLowDurability", true);

        AUTO_REPLACE_TOOL = CLIENT_BUILDER
                .comment("Whether to automatically replace rod/bobber from inventory when durability is low.")
                .define("autoReplaceTool", true);

        DEBUG_MODE = CLIENT_BUILDER
                .comment("Whether to show detailed debug logs for auto-fishing (useful for troubleshooting).")
                .define("debugMode", false);

        CLIENT_BUILDER.pop();

        CLIENT_SPEC = CLIENT_BUILDER.build();
    }

    public static int getQuality(double accuracy) {
        if (accuracy >= SFConfig.QUALITY_3_THRESHOLD.get()) {
            return 3;
        } else if (accuracy >= SFConfig.QUALITY_2_THRESHOLD.get()) {
            return 2;
        } else if (accuracy >= SFConfig.QUALITY_1_THRESHOLD.get()) {
            return 1;
        }
        return 0;
    }

    public static double getMultiplier(double accuracy, Player player, double expMultiplierStat) {
        double multiplier = switch (getQuality(accuracy)) {
            case 3 -> QUALITY_3_MULTIPLIER.get();
            case 2 -> QUALITY_2_MULTIPLIER.get();
            case 1 -> QUALITY_1_MULTIPLIER.get();
            default -> 1;
        };

        multiplier *= expMultiplierStat;
        multiplier *= AttributeCache.getAttribute(player, SFAttributes.EXPERIENCE_MULTIPLIER.get());

        return multiplier;
    }

    public static double getBiteTimeMultiplier() {
        return BITE_TIME_MULTIPLIER.get();
    }

    public static double getTreasureChestChance() {
        return TREASURE_CHEST_CHANCE.get();
    }

    public static double getGoldenChestChance() {
        return GOLDEN_CHEST_CHANCE.get();
    }

    public static boolean isInventoryEquippingEnabled() {
        return INVENTORY_BOBBER_EQUIPPING.get();
    }

    public static boolean isLegendaryFlashingEnabled() {
        return LEGENDARY_FISH_FLASHING.get();
    }

    public static float getLegendaryFishChance(float luck) {
        return (float) (LEGENDARY_FISH_CHANCE.get() + luck * 0.01F);
    }

    // Auto-fishing getters
    public static boolean isAutoCastEnabledByDefault() {
        return AUTO_CAST_ENABLED.get();
    }

    public static boolean isAutoReelEnabledByDefault() {
        return AUTO_REEL_ENABLED.get();
    }

    public static int getMinRodDurability() {
        return MIN_ROD_DURABILITY.get();
    }

    public static int getMinBobberDurability() {
        return MIN_BOBBER_DURABILITY.get();
    }

    public static boolean shouldStopOnLowDurability() {
        return STOP_ON_LOW_DURABILITY.get();
    }

    public static boolean shouldAutoReplaceTool() {
        return AUTO_REPLACE_TOOL.get();
    }

    public static boolean isDebugMode() {
        return DEBUG_MODE.get();
    }
}
