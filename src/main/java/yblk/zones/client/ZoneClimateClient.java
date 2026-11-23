package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yblk.zones.ZonesMod;
import yblk.zones.zone.Zone;
import yblk.zones.zone.ZoneSettings;

/**
 * Local (client-only) day/night and weather while inside a zone.
 * Sky is forced every render frame so server time sync cannot flicker.
 */
@Mod.EventBusSubscriber(modid = ZonesMod.MODID, value = Dist.CLIENT)
public final class ZoneClimateClient {
    private static boolean weatherActive;
    private static float savedRain = -1F;
    private static float savedThunder = -1F;
    /** Frozen day-base so we never re-read a half-synced world time. */
    private static long lockedDayBase = -1L;
    private static ZoneSettings.SkyMode lockedSky = ZoneSettings.SkyMode.DEFAULT;
    private static ZoneLoopSound loopSound;

    private ZoneClimateClient() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        applyClimate();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Weather is lerped by the client; keep it pinned each tick.
        applyClimate();
    }

    private static void applyClimate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearOverrides(mc.level);
            stopLoop(mc);
            return;
        }
        Zone zone = ClientZones.at(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
        ZoneSettings.SkyMode sky = ZoneSettings.SkyMode.DEFAULT;
        ZoneSettings.WeatherMode weather = ZoneSettings.WeatherMode.DEFAULT;
        if (zone != null) {
            ZoneSettings s = zone.settings();
            if (zone.isEventActive()) {
                sky = s.eventSkyMode() != ZoneSettings.SkyMode.DEFAULT ? s.eventSkyMode() : s.skyMode();
                weather = s.eventWeatherMode() != ZoneSettings.WeatherMode.DEFAULT
                        ? s.eventWeatherMode() : s.weatherMode();
            } else {
                sky = s.skyMode();
                weather = s.weatherMode();
            }
        }
        applySky(mc.level, sky);
        applyWeather(mc.level, weather);
        applyLoop(mc, zone);
    }

    private static void applySky(ClientLevel level, ZoneSettings.SkyMode mode) {
        if (mode == null || mode == ZoneSettings.SkyMode.DEFAULT) {
            lockedDayBase = -1L;
            lockedSky = ZoneSettings.SkyMode.DEFAULT;
            return;
        }
        if (lockedDayBase < 0L || lockedSky != mode) {
            long now = level.getDayTime();
            lockedDayBase = now - Math.floorMod(now, 24000L);
            lockedSky = mode;
        }
        level.getLevelData().setDayTime(lockedDayBase + mode.dayTimeOffset);
    }

    private static void applyWeather(ClientLevel level, ZoneSettings.WeatherMode mode) {
        if (mode == null || mode == ZoneSettings.WeatherMode.DEFAULT) {
            if (weatherActive) {
                if (savedRain >= 0F) {
                    level.setRainLevel(savedRain);
                }
                if (savedThunder >= 0F) {
                    level.setThunderLevel(savedThunder);
                }
                weatherActive = false;
                savedRain = -1F;
                savedThunder = -1F;
            }
            return;
        }
        if (!weatherActive) {
            savedRain = level.getRainLevel(1.0F);
            savedThunder = level.getThunderLevel(1.0F);
            weatherActive = true;
        }
        switch (mode) {
            case CLEAR -> {
                level.setRainLevel(0F);
                level.setThunderLevel(0F);
            }
            case RAIN -> {
                level.setRainLevel(1F);
                level.setThunderLevel(0F);
            }
            case THUNDER -> {
                level.setRainLevel(1F);
                level.setThunderLevel(1F);
            }
            default -> {
            }
        }
    }

    private static void applyLoop(Minecraft mc, Zone zone) {
        String id = zone == null ? "" : zone.settings().loopSound();
        if (id == null || id.isBlank() || mc.player == null) {
            stopLoop(mc);
            return;
        }
        float volume = zone.settings().loopSoundVolume();
        if (loopSound != null && loopSound.matches(id) && !loopSound.isStopped()) {
            loopSound.setLevel(volume);
            return;
        }
        stopLoop(mc);
        ResourceLocation soundId = ResourceLocation.tryParse(id);
        if (soundId == null) {
            return;
        }
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) {
            return;
        }
        loopSound = new ZoneLoopSound(sound, id, volume);
        mc.getSoundManager().play(loopSound);
    }

    private static void stopLoop(Minecraft mc) {
        if (loopSound != null) {
            loopSound.end();
            if (mc != null) {
                mc.getSoundManager().stop(loopSound);
            }
            loopSound = null;
        }
    }

    private static void clearOverrides(ClientLevel level) {
        lockedDayBase = -1L;
        lockedSky = ZoneSettings.SkyMode.DEFAULT;
        if (level != null && weatherActive) {
            if (savedRain >= 0F) {
                level.setRainLevel(savedRain);
            }
            if (savedThunder >= 0F) {
                level.setThunderLevel(savedThunder);
            }
        }
        weatherActive = false;
        savedRain = -1F;
        savedThunder = -1F;
    }
}
