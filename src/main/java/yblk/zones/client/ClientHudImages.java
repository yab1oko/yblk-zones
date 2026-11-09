package yblk.zones.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Loads HUD images from config/yblk_zones/hud_images into DynamicTextures. */
@OnlyIn(Dist.CLIENT)
public final class ClientHudImages {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    public static final String FILE_PREFIX = "file:";

    private ClientHudImages() {
    }

    public static Path imagesDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("yblk_zones").resolve("hud_images");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    /** Settings value like file:hud_images/foo.png or a normal ResourceLocation string. */
    public static ResourceLocation resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(FILE_PREFIX)) {
            return ResourceLocation.tryParse(stored);
        }
        String key = stored.substring(FILE_PREFIX.length());
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Path file = FMLPaths.CONFIGDIR.get().resolve("yblk_zones").resolve(key.replace('\\', '/'));
        if (!Files.isRegularFile(file)) {
            file = imagesDir().resolve(Path.of(key).getFileName().toString());
        }
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            NativeImage image = readAnyImage(file);
            if (image == null) {
                return null;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            DynamicTexture tex = new DynamicTexture(image);
            ResourceLocation loc = Minecraft.getInstance().getTextureManager()
                    .register("yblk_zones/hud_dyn/" + Integer.toHexString(key.hashCode()), tex);
            CACHE.put(key, loc);
            SIZES.put(loc, new int[]{w, h});
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    /** Copy into hud_images as PNG (converts jpg/gif/etc). Returns settings path. */
    public static String importFile(Path source) throws IOException {
        Path dir = imagesDir();
        String rawName = source.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        String base = rawName;
        int dot = rawName.lastIndexOf('.');
        if (dot > 0) {
            base = rawName.substring(0, dot);
        }
        String destName = base + ".png";
        Path dest = dir.resolve(destName);

        String lower = rawName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            NativeImage image = readAnyImage(source);
            if (image == null) {
                throw new IOException("Unsupported image: " + source);
            }
            try {
                image.writeToFile(dest.toFile());
            } finally {
                image.close();
            }
        }
        CACHE.remove("hud_images/" + destName);
        return FILE_PREFIX + "hud_images/" + destName;
    }

    /** Width/height of a previously resolved dynamic HUD texture. */
    public static int[] sizeOf(ResourceLocation loc) {
        if (loc == null) {
            return new int[]{16, 16};
        }
        int[] s = SIZES.get(loc);
        return s != null ? s : new int[]{16, 16};
    }

    private static NativeImage readAnyImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            } catch (Exception ignored) {
            }
        }
        try {
            BufferedImage bi = ImageIO.read(file.toFile());
            if (bi == null) {
                return null;
            }
            int w = bi.getWidth();
            int h = bi.getHeight();
            NativeImage ni = new NativeImage(w, h, true);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = bi.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    // NativeImage.setPixelRGBA expects ABGR packing used by MC
                    ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            return ni;
        } catch (Exception e) {
            return null;
        }
    }
}
