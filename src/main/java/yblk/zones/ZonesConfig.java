package yblk.zones;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ZonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZonesConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_ZONE_SIZE = B
            .comment("Max edge length of a survival-created zone (blocks).")
            .defineInRange("maxZoneSize", 32, 4, 512);

    private static final ForgeConfigSpec.IntValue MAX_ZONE_VOLUME = B
            .comment("Max block volume (width × depth × height) of a survival-created zone.")
            .defineInRange("maxZoneVolume", 4096, 64, 2_000_000);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_CREATIVE_OR_OP = B
            .comment("When true, only operators (permission 2) or creative players may create, reshape, or delete unowned zones. Claimed-plot owners keep their owner menu.")
            .define("requireCreativeOrOpToEdit", true);

    private static final ForgeConfigSpec.BooleanValue DEFAULT_CREATOR_OWNS = B
            .comment("When true, the player who creates a zone becomes its owner automatically.")
            .define("defaultCreatorOwns", false);

    private static final ForgeConfigSpec.IntValue DEFAULT_LEASE_PERIOD_TICKS = B
            .comment("Default lease duration in ticks when leasing is enabled.")
            .defineInRange("defaultLeasePeriodTicks", 72000, 1200, 1728000);

    private static final ForgeConfigSpec.DoubleValue DEFAULT_PROXIMITY_RADIUS = B
            .comment("Default proximity radius for zone interactions (blocks).")
            .defineInRange("defaultProximityRadius", 2.0, 1.5, 2.5);

    private static final ForgeConfigSpec.IntValue MAX_ZONES_PER_PLAYER = B
            .comment("How many claimed zones one player may own.")
            .defineInRange("maxZonesPerPlayer", 5, 1, 100);

    private static final ForgeConfigSpec.IntValue DEFAULT_DAMAGE_INTERVAL = B
            .comment("Ticks between damage ticks in a damage zone.")
            .defineInRange("damageIntervalTicks", 40, 5, 200);

    private static final ForgeConfigSpec.DoubleValue DEFAULT_DAMAGE = B
            .comment("Vanilla damage units per tick in a damage zone. 1.0 is half a heart, 2.0 is one heart. This is the same number passed to player.hurt.")
            .defineInRange("damageAmount", 1.0, 0.0, 40.0);

    private static final ForgeConfigSpec.DoubleValue GRADIENT_HEIGHT = B
            .comment("How high the edge gradient rises (blocks).")
            .defineInRange("gradientHeight", 2.5, 0.5, 16.0);

    static final ForgeConfigSpec SPEC = B.build();

    public static int maxZoneSize;
    public static int maxZoneVolume;
    public static boolean requireCreativeOrOpToEdit;
    public static boolean defaultCreatorOwns;
    public static int defaultLeasePeriodTicks;
    public static float defaultProximityRadius;
    public static int maxZonesPerPlayer;
    public static int damageIntervalTicks;
    public static float damageAmount;
    public static float gradientHeight;

    private ZonesConfig() {
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        maxZoneSize = MAX_ZONE_SIZE.get();
        maxZoneVolume = MAX_ZONE_VOLUME.get();
        requireCreativeOrOpToEdit = REQUIRE_CREATIVE_OR_OP.get();
        defaultCreatorOwns = DEFAULT_CREATOR_OWNS.get();
        defaultLeasePeriodTicks = DEFAULT_LEASE_PERIOD_TICKS.get();
        defaultProximityRadius = DEFAULT_PROXIMITY_RADIUS.get().floatValue();
        maxZonesPerPlayer = MAX_ZONES_PER_PLAYER.get();
        damageIntervalTicks = DEFAULT_DAMAGE_INTERVAL.get();
        damageAmount = DEFAULT_DAMAGE.get().floatValue();
        gradientHeight = GRADIENT_HEIGHT.get().floatValue();
    }
}
