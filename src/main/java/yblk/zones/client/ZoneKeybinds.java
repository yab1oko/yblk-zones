package yblk.zones.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import yblk.zones.ZonesMod;

/**
 * Wand keys live in Options → Controls so they can be rebound and so other mods can see the conflict.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ZonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZoneKeybinds {
    public static final String CATEGORY = "key.categories.yblk_zones";

    public static final KeyMapping NEW_ZONE = bind("key.yblk_zones.new_zone", GLFW.GLFW_KEY_N);
    public static final KeyMapping EDIT = bind("key.yblk_zones.edit", GLFW.GLFW_KEY_G);
    public static final KeyMapping SETTINGS = bind("key.yblk_zones.settings", GLFW.GLFW_KEY_R);
    public static final KeyMapping OWNER = bind("key.yblk_zones.owner", GLFW.GLFW_KEY_O);
    public static final KeyMapping ADD_CORNER = bind("key.yblk_zones.add_corner", GLFW.GLFW_KEY_C);
    public static final KeyMapping REMOVE_CORNER = bind("key.yblk_zones.remove_corner", GLFW.GLFW_KEY_X);
    public static final KeyMapping RESIZE = bind("key.yblk_zones.resize", GLFW.GLFW_KEY_LEFT_ALT);

    private ZoneKeybinds() {
    }

    private static KeyMapping bind(String name, int key) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    public static KeyMapping forChip(String id) {
        return switch (id) {
            case "n" -> NEW_ZONE;
            case "g" -> EDIT;
            case "r" -> SETTINGS;
            case "o" -> OWNER;
            case "c" -> ADD_CORNER;
            case "x" -> REMOVE_CORNER;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(NEW_ZONE);
        event.register(EDIT);
        event.register(SETTINGS);
        event.register(OWNER);
        event.register(ADD_CORNER);
        event.register(REMOVE_CORNER);
        event.register(RESIZE);
    }
}
