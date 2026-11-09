package yblk.zones.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneManager;

/**
 * Boosts chest / container loot when the ORIGIN is inside a zone with harvestLevel &gt; 0.
 * Works for vanilla loot chests and any mod (e.g. CTS) that rolls via {@link LootTable#getRandomItems}.
 */
public final class ZoneChestLootModifier extends LootModifier {
    public static final Codec<ZoneChestLootModifier> CODEC = RecordCodecBuilder.create(inst ->
            codecStart(inst).apply(inst, ZoneChestLootModifier::new));

    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);

    public ZoneChestLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    @NotNull
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        if (REENTRY.get()) {
            return generated;
        }
        // Skip block-break loot; chests use ORIGIN without BLOCK_STATE/TOOL
        if (context.hasParam(LootContextParams.BLOCK_STATE)) {
            return generated;
        }
        if (!context.hasParam(LootContextParams.ORIGIN)) {
            return generated;
        }
        ServerLevel level = context.getLevel();
        Vec3 origin = context.getParam(LootContextParams.ORIGIN);
        Zone zone = ZoneManager.at(level, BlockPos.containing(origin));
        if (zone == null) {
            return generated;
        }
        int harvest = zone.settings().harvestLevel();
        if (harvest <= 0) {
            return generated;
        }

        RandomSource random = context.getRandom();
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>(generated.size() * 2);

        // Quantity + quality pass on already rolled stacks
        for (ItemStack stack : generated) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            int boosted = boostCount(copy.getCount(), harvest, random);
            copy.setCount(Mth.clamp(boosted, 1, copy.getMaxStackSize()));
            maybeUpgrade(copy, harvest, random);
            out.add(copy);
            // Chance to duplicate a stack at higher levels
            if (random.nextFloat() < 0.08f * harvest) {
                ItemStack extra = copy.copy();
                extra.setCount(Mth.clamp(1 + random.nextInt(Math.max(1, copy.getCount())), 1, extra.getMaxStackSize()));
                out.add(extra);
            }
        }

        // Extra luck-weighted rolls from the same table (raw = no GLM recursion)
        var tableId = context.getQueriedLootTableId();
        if (tableId != null
                && !net.minecraftforge.common.loot.LootTableIdCondition.UNKNOWN_LOOT_TABLE.equals(tableId)) {
            LootTable table = level.getServer().getLootData().getLootTable(tableId);
            if (table != null && table != LootTable.EMPTY) {
                REENTRY.set(true);
                try {
                    float luck = harvest * 1.35f;
                    LootParams params = new LootParams.Builder(level)
                            .withParameter(LootContextParams.ORIGIN, origin)
                            .withLuck(luck)
                            .create(LootContextParamSets.CHEST);
                    ObjectArrayList<ItemStack> bonus = new ObjectArrayList<>();
                    LootContext bonusCtx = new LootContext.Builder(params)
                            .withQueriedLootTableId(tableId)
                            .create(tableId);
                    table.getRandomItemsRaw(bonusCtx, bonus::add);
                    int keep = Math.min(bonus.size(), 1 + harvest);
                    for (int i = 0; i < keep; i++) {
                        ItemStack s = bonus.get(i);
                        if (s.isEmpty()) {
                            continue;
                        }
                        ItemStack c = s.copy();
                        maybeUpgrade(c, harvest, random);
                        out.add(c);
                    }
                } finally {
                    REENTRY.set(false);
                }
            }
        }

        return out;
    }

    private static int boostCount(int count, int harvest, RandomSource random) {
        float mul = 1.0f + 0.35f * harvest;
        int next = Math.round(count * mul);
        if (random.nextFloat() < 0.2f * harvest) {
            next += 1 + random.nextInt(harvest);
        }
        return Math.max(count, next);
    }

    private static void maybeUpgrade(ItemStack stack, int harvest, RandomSource random) {
        if (stack.isEmpty()) {
            return;
        }
        // Enchant gear / books more often at higher harvest
        if (!stack.getItem().isEnchantable(stack) && !(stack.getItem() instanceof EnchantedBookItem)) {
            return;
        }
        if (stack.isEnchanted()) {
            return;
        }
        if (random.nextFloat() < 0.12f + 0.1f * harvest) {
            int power = 5 + harvest * 7 + random.nextInt(1 + harvest * 3);
            EnchantmentHelper.enchantItem(random, stack, power, false);
        }
    }
}
