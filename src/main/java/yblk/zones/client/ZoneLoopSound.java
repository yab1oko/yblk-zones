package yblk.zones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import yblk.zones.zone.Zone;

/** Follows the local player and stops itself when they leave the zone that started it. */
public class ZoneLoopSound extends AbstractTickableSoundInstance {
    private final String soundId;

    public ZoneLoopSound(SoundEvent sound, String soundId, float volume) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.soundId = soundId;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    public boolean matches(String id) {
        return soundId.equals(id);
    }

    public void setLevel(float volume) {
        this.volume = volume;
    }

    public void halt() {
        stop();
    }

    public void end() {
        stop();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        Zone zone = ClientZones.at(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
        if (zone == null || !soundId.equals(zone.settings().loopSound())) {
            stop();
            return;
        }
        this.volume = zone.settings().loopSoundVolume();
    }
}
