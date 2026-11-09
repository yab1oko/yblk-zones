package yblk.zones.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneFlag;
import yblk.zones.zone.ZoneLog;
import yblk.zones.zone.ZoneManager;
import yblk.zones.zone.ZoneMobSpawner;

import java.util.List;

public final class ZoneScriptEngine {
    private ZoneScriptEngine() {
    }

    public static void run(ServerLevel level, Zone zone, ZoneTrigger trigger, ServerPlayer player) {
        List<ZoneAction> actions = zone.actions();
        for (ZoneAction action : actions) {
            if (action.trigger() != trigger) {
                continue;
            }
            try {
                execute(level, zone, action, player);
            } catch (Exception e) {
                ZoneLog.warn("Action failed in " + zone.getName() + ": " + e.getMessage());
            }
        }
    }

    private static void execute(ServerLevel level, Zone zone, ZoneAction action, ServerPlayer player) {
        switch (action.type()) {
            case EFFECT -> applyEffect(player, action);
            case SPAWN -> {
                int count = action.intArg() > 0 ? action.intArg() : 1;
                int spawned = ZoneMobSpawner.spawn(level, zone, action.value(), count, player);
                if (spawned > 0 && zone.isEventActive()) {
                    zone.setEventHadMobs(true);
                    if (zone.getEventWave() <= 0) {
                        zone.setEventWave(1);
                    }
                }
            }
            case SOUND -> playSound(level, zone, action, player);
            case PARTICLE -> particles(level, zone, action);
            case TITLE -> title(player, action);
            case COMMAND -> command(level, zone, action, player);
            case DENY_EXIT -> {
                boolean on = action.boolArg()
                        || action.value().isBlank()
                        || action.value().equalsIgnoreCase("true");
                zone.setDenyExit(on);
                // Also lock for the whole event so exit stay blocked after start scripts
                if (on && zone.isEventActive()) {
                    zone.setDenyExitWhileEvent(true);
                }
            }
            case CLEAR_MOBS -> clearMobs(level, zone);
            case FLAG_SET -> flagSet(zone, action);
            case GIVE_ITEM -> giveItem(player, action);
            case GIVE_XP -> giveXp(player, action);
            case HEAL -> heal(player, action);
        }
        ZoneManager.data(level).markDirty();
    }

    private static void giveItem(ServerPlayer player, ZoneAction action) {
        if (player == null || action.value().isBlank()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(action.value().trim());
        if (id == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return;
        }
        int count = Math.max(1, Math.min(64 * 9, action.intArg() > 0 ? action.intArg() : 1));
        while (count > 0) {
            int stackSize = Math.min(item.getMaxStackSize(), count);
            ItemStack stack = new ItemStack(item, stackSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            count -= stackSize;
        }
    }

    private static void giveXp(ServerPlayer player, ZoneAction action) {
        if (player == null) {
            return;
        }
        int xp = Math.max(1, Math.min(100000, action.intArg() > 0 ? action.intArg() : 10));
        player.giveExperiencePoints(xp);
    }

    private static void heal(ServerPlayer player, ZoneAction action) {
        if (player == null) {
            return;
        }
        float hearts = action.floatArg() > 0 ? action.floatArg() : 10.0F;
        player.heal(hearts * 2.0F);
        if (action.boolArg()) {
            player.getFoodData().eat(20, 20.0F);
        }
    }

    private static void applyEffect(ServerPlayer player, ZoneAction action) {
        if (player == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(action.value().trim());
        if (id == null) {
            return;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) {
            return;
        }
        int dur = action.intArg() > 0 ? action.intArg() : 200;
        int amp = Math.max(0, Math.min(10, (int) action.floatArg()));
        player.addEffect(new MobEffectInstance(effect, dur, amp, false, true, true));
    }

    private static void playSound(ServerLevel level, Zone zone, ZoneAction action, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(action.value().trim());
        if (id == null) {
            return;
        }
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound == null) {
            return;
        }
        float vol = action.floatArg() > 0 ? action.floatArg() : 1.0F;
        BlockPos pos = player != null ? player.blockPosition() : zone.getMin();
        level.playSound(null, pos, sound, SoundSource.AMBIENT, vol, 1.0F);
    }

    @SuppressWarnings("unchecked")
    private static void particles(ServerLevel level, Zone zone, ZoneAction action) {
        ResourceLocation id = ResourceLocation.tryParse(action.value().trim());
        if (id == null) {
            return;
        }
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(id);
        if (!(type instanceof ParticleOptions options)) {
            return;
        }
        int count = Math.max(1, Math.min(128, action.intArg() > 0 ? action.intArg() : 16));
        AABB box = zone.aabb();
        for (int i = 0; i < count; i++) {
            double x = box.minX + level.random.nextDouble() * box.getXsize();
            double y = box.minY + level.random.nextDouble() * box.getYsize();
            double z = box.minZ + level.random.nextDouble() * box.getZsize();
            level.sendParticles(options, x, y, z, 1, 0, 0.05, 0, 0.01);
        }
    }

    private static void title(ServerPlayer player, ZoneAction action) {
        if (player == null || action.value().isBlank()) {
            return;
        }
        Component text = Component.literal(action.value());
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(text));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        player.displayClientMessage(text, true);
        if (action.boolArg() || action.intArg() > 0) {
            player.sendSystemMessage(text);
        }
    }

    private static void command(ServerLevel level, Zone zone, ZoneAction action, ServerPlayer player) {
        String cmd = action.value();
        if (isBlank(cmd)) {
            return;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (!zone.allowCommands() && (player == null || !player.hasPermissions(2))) {
            ZoneLog.warn("Blocked command on zone " + zone.getName() + ": " + cmd);
            return;
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(2);
        if (player != null) {
            source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        }
        level.getServer().getCommands().performPrefixedCommand(source, cmd);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void clearMobs(ServerLevel level, Zone zone) {
        AABB box = zone.aabb().inflate(0.5);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (zone.contains(mob.getX(), mob.getY(), mob.getZ())) {
                mob.discard();
            }
        }
    }

    private static void flagSet(Zone zone, ZoneAction action) {
        try {
            String raw = action.value().trim();
            if (raw.contains("=")) {
                String[] parts = raw.split("=", 2);
                ZoneFlag flag = ZoneFlag.valueOf(parts[0].trim().toUpperCase());
                boolean on = parts[1].trim().equalsIgnoreCase("true") || parts[1].trim().equals("1");
                zone.setFlag(flag, on);
            } else {
                ZoneFlag flag = ZoneFlag.valueOf(raw.toUpperCase());
                zone.setFlag(flag, action.boolArg() || action.intArg() != 0 || raw.isEmpty());
            }
        } catch (Exception ignored) {
        }
    }
}
