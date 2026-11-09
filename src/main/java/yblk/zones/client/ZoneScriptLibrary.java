package yblk.zones.client;

import net.minecraftforge.fml.loading.FMLPaths;
import yblk.zones.script.ZoneScriptBundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Client-side script files under {@code config/yblk_zones/scripts/}. */
public final class ZoneScriptLibrary {
    private ZoneScriptLibrary() {
    }

    public static Path dir() {
        Path d = FMLPaths.CONFIGDIR.get().resolve("yblk_zones").resolve("scripts");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {
        }
        return d;
    }

    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        Path d = dir();
        if (!Files.isDirectory(d)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && isScriptName(p.getFileName().toString())) {
                    out.add(p);
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static boolean isScriptName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".txt") || n.endsWith(".yzs") || n.endsWith(".script");
    }

    public static String sanitizeName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            s = "script";
        }
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        s = s.replaceAll("\\s+", "_");
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        if (!isScriptName(s)) {
            s = s + ".txt";
        }
        return s;
    }

    public static Path save(String name, ZoneScriptBundle bundle) throws IOException {
        Path file = dir().resolve(sanitizeName(name));
        Files.writeString(file, bundle.encode(), StandardCharsets.UTF_8);
        return file;
    }

    public static ZoneScriptBundle load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return ZoneScriptBundle.decode(text);
    }

    public static boolean delete(Path file) throws IOException {
        return Files.deleteIfExists(file);
    }
}
