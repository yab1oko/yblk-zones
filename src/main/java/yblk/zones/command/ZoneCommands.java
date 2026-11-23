package yblk.zones.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.zone.EffectEntry;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneLog;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneTemplates;
import yblk.zones.init.ModItems;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ZoneCommands {
    private ZoneCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("zone")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("wand")
                        .executes(ctx -> wand(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), null))
                        .then(Commands.argument("zone", StringArgumentType.string())
                                .suggests(ZONE_NAMES)
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "zone")))))
                .then(Commands.literal("flag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(FLAG_NAMES)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> flag(
                                                ctx.getSource(),
                                                null,
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "value")))
                                        .then(Commands.literal("in")
                                                .then(Commands.argument("zone", StringArgumentType.string())
                                                        .suggests(ZONE_NAMES)
                                                        .executes(ctx -> flag(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "zone"),
                                                                StringArgumentType.getString(ctx, "name"),
                                                                StringArgumentType.getString(ctx, "value"))))))))
                .then(Commands.literal("rename")
                        .then(Commands.literal("in")
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .suggests(ZONE_NAMES)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> rename(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "zone"))))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> rename(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        null))))
                .then(Commands.literal("level")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> level(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("sell")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> sell(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("buy")
                        .executes(ctx -> buy(ctx.getSource())))
                .then(Commands.literal("unclaim")
                        .executes(ctx -> unclaim(ctx.getSource())))
                .then(Commands.literal("remove")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> remove(ctx.getSource(), null))
                        .then(Commands.argument("zone", StringArgumentType.string())
                                .suggests(ZONE_NAMES)
                                .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "zone")))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> transfer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("rent")
                        .executes(ctx -> payLease(ctx.getSource())))
                .then(Commands.literal("paylease")
                        .executes(ctx -> payLease(ctx.getSource())))
                .then(Commands.literal("event")
                        .then(Commands.literal("start")
                                .executes(ctx -> eventStart(ctx.getSource(), null))
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .suggests(ZONE_NAMES)
                                        .executes(ctx -> eventStart(ctx.getSource(), StringArgumentType.getString(ctx, "zone")))))
                        .then(Commands.literal("stop")
                                .executes(ctx -> eventStop(ctx.getSource(), null))
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .suggests(ZONE_NAMES)
                                        .executes(ctx -> eventStop(ctx.getSource(), StringArgumentType.getString(ctx, "zone"))))))
                .then(Commands.literal("log")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> log(ctx.getSource())))
                .then(Commands.literal("priority")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> priority(
                                        ctx.getSource(),
                                        null,
                                        IntegerArgumentType.getInteger(ctx, "value")))
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .suggests(ZONE_NAMES)
                                        .executes(ctx -> priority(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "zone"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("template")
                        .then(Commands.literal("reload")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> templateReload(ctx.getSource()))))
                .then(Commands.literal("effect")
                        .then(Commands.literal("add")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(ctx -> effectAdd(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "id"),
                                                null))
                                        .then(Commands.literal("in")
                                                .then(Commands.argument("zone", StringArgumentType.string())
                                                        .suggests(ZONE_NAMES)
                                                        .executes(ctx -> effectAdd(
                                                                ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "zone"))))))));

        dispatcher.register(root);
    }

    private static boolean denyEdit(CommandSourceStack source, Zone zone) {
        ServerPlayer player = source.getPlayer();
        if (player != null && !ZoneManager.canModify(player, zone)) {
            source.sendFailure(Component.translatable("message.yblk_zones.need_edit_perm"));
            return true;
        }
        return false;
    }

    private static Zone requireZone(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return null;
        }
        return ZoneManager.at(player.serverLevel(), player);
    }

    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = ZoneCommands::suggestZones;
    private static final SuggestionProvider<CommandSourceStack> FLAG_NAMES = (ctx, builder) -> {
        for (ZoneFlag flag : ZoneFlag.values()) {
            builder.suggest(flag.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    };

    private static CompletableFuture<Suggestions> suggestZones(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getLevel() == null) {
            return Suggestions.empty();
        }
        for (Zone zone : ZoneManager.data(ctx.getSource().getLevel()).all()) {
            builder.suggest(zone.getName().contains(" ") ? "\"" + zone.getName() + "\"" : zone.getName());
        }
        return builder.buildFuture();
    }

    private static Zone resolve(CommandSourceStack source, String name) {
        if (name == null || name.isBlank()) {
            Zone here = requireZone(source);
            if (here == null) {
                source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            }
            return here;
        }
        for (Zone zone : ZoneManager.data(source.getLevel()).all()) {
            if (zone.getName().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        source.sendFailure(Component.translatable("message.yblk_zones.unknown_zone", name));
        return null;
    }

    private static int list(CommandSourceStack source) {
        var zones = ZoneManager.data(source.getLevel()).all();
        boolean full = source.hasPermission(2);
        int shown = 0;
        int hidden = 0;
        for (Zone zone : zones) {
            if (!full && !listedFor(source, zone)) {
                hidden++;
                continue;
            }
            shown++;
            String owner = zone.getOwner() == null ? "-" : zone.getOwner().toString().substring(0, 8);
            String flags = shortFlags(zone);
            String name = zone.getName();
            int sx = zone.sizeX();
            int sy = zone.sizeY();
            int sz = zone.sizeZ();
            source.sendSuccess(() -> Component.translatable("message.yblk_zones.list_line",
                    name, sx, sy, sz, owner, flags), false);
        }
        int listed = shown;
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.list_header", listed), false);
        if (hidden > 0) {
            int left = hidden;
            source.sendSuccess(() -> Component.translatable("message.yblk_zones.list_more", left), false);
        }
        return shown;
    }

    /** Nearby, owned, or outlined zones. Operators see every zone in the dimension. */
    private static boolean listedFor(CommandSourceStack source, Zone zone) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return false;
        }
        if (player.getUUID().equals(zone.getOwner()) || zone.has(ZoneFlag.VISIBLE)) {
            return true;
        }
        var min = zone.getMin();
        var max = zone.getMax();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double dx = px < min.getX() ? min.getX() - px : (px > max.getX() + 1 ? px - (max.getX() + 1) : 0);
        double dy = py < min.getY() ? min.getY() - py : (py > max.getY() + 1 ? py - (max.getY() + 1) : 0);
        double dz = pz < min.getZ() ? min.getZ() - pz : (pz > max.getZ() + 1 ? pz - (max.getZ() + 1) : 0);
        return dx * dx + dy * dy + dz * dz <= 128.0 * 128.0;
    }

    private static String shortFlags(Zone zone) {
        StringBuilder sb = new StringBuilder();
        for (ZoneFlag flag : zone.getFlags()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(flag.name().toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static int wand(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null || !ZoneManager.mayEditZones(player)) {
            source.sendFailure(Component.translatable("message.yblk_zones.need_edit_perm"));
            return 0;
        }
        player.getInventory().add(new ItemStack(ModItems.ZONE_WAND.get()));
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.wand_given"), true);
        return 1;
    }

    private static int info(CommandSourceStack source, String name) {
        Zone zone = resolve(source, name);
        if (zone == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.info",
                zone.getName(), zone.getLevel(), zone.getFlags().toString()), false);
        return 1;
    }

    private static int flag(CommandSourceStack source, String zoneName, String name, String value) {
        Zone zone = resolve(source, zoneName);
        if (zone == null) {
            return 0;
        }
        ZoneFlag flag;
        try {
            flag = ZoneFlag.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("message.yblk_zones.unknown_flag", flagNames()));
            return 0;
        }
        boolean on = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1");
        if (denyEdit(source, zone)) {
            return 0;
        }
        zone.setFlag(flag, on);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.literal(flag + " = " + on), true);
        return 1;
    }

    private static String flagNames() {
        StringBuilder sb = new StringBuilder();
        for (ZoneFlag flag : ZoneFlag.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(flag.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static int rename(CommandSourceStack source, String name, String zoneName) {
        ServerPlayer player = source.getPlayer();
        Zone zone = resolve(source, zoneName);
        if (player == null || zone == null) {
            return 0;
        }
        if (!ZoneManager.canModify(player, zone) && !player.hasPermissions(2)) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_owner"));
            return 0;
        }
        zone.setName(name);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.renamed", name), true);
        return 1;
    }

    private static int level(CommandSourceStack source, int level) {
        Zone zone = requireZone(source);
        if (zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        if (denyEdit(source, zone)) {
            return 0;
        }
        zone.setLevel(level);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        return 1;
    }

    private static int sell(CommandSourceStack source, ResourceLocation itemId, int count) {
        Zone zone = requireZone(source);
        if (zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        if (ForgeRegistries.ITEMS.getValue(itemId) == null) {
            source.sendFailure(Component.literal("Unknown item"));
            return 0;
        }
        if (denyEdit(source, zone)) {
            return 0;
        }
        zone.setBuyCost(itemId.toString(), count);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.for_sale", itemId.toString(), count), true);
        return 1;
    }

    private static int buy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Zone zone = requireZone(source);
        if (player == null || zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        String err = ZoneManager.tryBuy(player, zone);
        if (err != null) {
            if ("message.yblk_zones.too_many".equals(err)) {
                source.sendFailure(Component.translatable(err, yblk.zones.ZonesConfig.maxZonesPerPlayer));
            } else if ("message.yblk_zones.cannot_afford".equals(err)) {
                source.sendFailure(Component.translatable("message.yblk_zones.cannot_afford",
                        zone.getBuyCount(), zone.getBuyItemId()));
            } else {
                source.sendFailure(Component.translatable(err));
            }
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.bought", zone.getName()), false);
        yblk.zones.network.ZonesNetwork.sendToPlayer(
                new yblk.zones.network.OpenOwnerMenuPacket(zone.getId()), player);
        return 1;
    }

    private static int unclaim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Zone zone = requireZone(source);
        if (player == null || zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        if (!player.hasPermissions(2) && (zone.getOwner() == null || !zone.getOwner().equals(player.getUUID()))) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_owner"));
            return 0;
        }
        zone.setOwner(null);
        zone.setFlag(ZoneFlag.CLAIM, false);
        ZoneManager.data(player.serverLevel()).markDirty();
        ZoneManager.sync(player.serverLevel());
        return 1;
    }

    private static int remove(CommandSourceStack source, String zoneName) {
        Zone zone = resolve(source, zoneName);
        if (zone == null) {
            return 0;
        }
        ServerPlayer actor = source.getPlayer();
        if (actor != null && !ZoneManager.canDelete(actor, zone)) {
            source.sendFailure(Component.translatable("message.yblk_zones.need_edit_perm"));
            return 0;
        }
        UUID id = zone.getId();
        ZoneManager.data(source.getLevel()).remove(id);
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.removed"), true);
        return 1;
    }

    private static int transfer(CommandSourceStack source, ServerPlayer to) {
        ServerPlayer from = source.getPlayer();
        Zone zone = requireZone(source);
        if (from == null || zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        String err = ZoneManager.tryTransfer(from, zone, to);
        if (err != null) {
            source.sendFailure(Component.translatable(err));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.transferred",
                to.getGameProfile().getName()), true);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Zone zone = requireZone(source);
        if (player == null || zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        String err = ZoneManager.tryLeave(player, zone);
        if (err != null) {
            source.sendFailure(Component.translatable(err));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.left"), true);
        return 1;
    }

    private static int payLease(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Zone zone = requireZone(source);
        if (player == null || zone == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            return 0;
        }
        String err = ZoneManager.tryPayLease(player, zone);
        if (err != null) {
            if ("message.yblk_zones.cannot_afford".equals(err)) {
                source.sendFailure(Component.translatable(err));
            } else {
                source.sendFailure(Component.translatable(err));
            }
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.lease_paid"), true);
        return 1;
    }

    private static int eventStart(CommandSourceStack source, String zoneName) {
        ServerPlayer player = source.getPlayer();
        Zone zone = resolve(source, zoneName);
        if (player == null || zone == null) {
            if (zone != null) {
                source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            }
            return 0;
        }
        String err = ZoneManager.startEvent(player, zone);
        if (err != null) {
            source.sendFailure(Component.translatable(err));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.event_started"), true);
        return 1;
    }

    private static int eventStop(CommandSourceStack source, String zoneName) {
        ServerPlayer player = source.getPlayer();
        Zone zone = resolve(source, zoneName);
        if (player == null || zone == null) {
            if (zone != null) {
                source.sendFailure(Component.translatable("message.yblk_zones.not_in_zone"));
            }
            return 0;
        }
        String err = ZoneManager.stopEvent(player, zone);
        if (err != null) {
            source.sendFailure(Component.translatable(err));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.event_stopped"), true);
        return 1;
    }

    private static int log(CommandSourceStack source) {
        for (String line : ZoneLog.recent(20)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int priority(CommandSourceStack source, String zoneName, int value) {
        Zone zone = resolve(source, zoneName);
        if (zone == null) {
            return 0;
        }
        if (denyEdit(source, zone)) {
            return 0;
        }
        zone.setPriority(value);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.priority_set", value), true);
        return 1;
    }

    private static int templateReload(CommandSourceStack source) {
        ZoneTemplates.reload();
        ZoneTemplates.loadBundledIfEmpty();
        int n = ZoneTemplates.all().size();
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.template_reloaded", n), true);
        return 1;
    }

    private static int effectAdd(CommandSourceStack source, ResourceLocation effectId, String zoneName) {
        Zone zone = resolve(source, zoneName);
        if (zone == null) {
            return 0;
        }
        if (ForgeRegistries.MOB_EFFECTS.getValue(effectId) == null) {
            source.sendFailure(Component.translatable("message.yblk_zones.unknown_effect", effectId));
            return 0;
        }
        if (denyEdit(source, zone)) {
            return 0;
        }
        EffectEntry entry = EffectEntry.of(effectId.toString());
        entry.setWhileInside(true);
        entry.setOnEnter(false);
        zone.settings().effects().add(entry);
        ZoneManager.data(source.getLevel()).markDirty();
        ZoneManager.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("message.yblk_zones.effect_added", effectId), true);
        return 1;
    }
}
