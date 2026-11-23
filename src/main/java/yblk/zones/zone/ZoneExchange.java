package yblk.zones.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pretty JSON under config/yblk_zones/export. Files are snapshots, not live zone state. */
public final class ZoneExchange {
    public static final String FOLDER = "config/yblk_zones/export";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ZoneExchange() {
    }

    public static Path directory() throws IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("yblk_zones").resolve("export");
        Files.createDirectories(dir);
        return dir;
    }

    public static Path exportOne(Zone zone) throws IOException {
        Path file = directory().resolve(fileName(zone.getName()));
        Files.writeString(file, GSON.toJson(single(zone)), StandardCharsets.UTF_8);
        return file;
    }

    public static List<Path> exportAll(ServerLevel level) throws IOException {
        JsonArray zones = new JsonArray();
        List<Path> paths = new ArrayList<>();
        for (Zone zone : ZoneManager.data(level).all()) {
            zones.add(single(zone));
            paths.add(exportOne(zone));
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.add("zones", zones);
        Path all = directory().resolve("all.json");
        Files.writeString(all, GSON.toJson(root), StandardCharsets.UTF_8);
        paths.add(all);
        return paths;
    }

    public static List<String> fileNames() {
        try {
            List<String> names = new ArrayList<>();
            try (var stream = Files.list(directory())) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .forEach(names::add);
            }
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Zone importOne(ServerLevel level, String fileName) throws IOException {
        List<Zone> created = importFile(level, fileName);
        if (created.isEmpty()) {
            throw new IOException("empty");
        }
        return created.get(0);
    }

    public static List<Zone> importAll(ServerLevel level) throws IOException {
        if (Files.isRegularFile(directory().resolve("all.json"))) {
            return importFile(level, "all.json");
        }
        List<Zone> created = new ArrayList<>();
        for (String name : fileNames()) {
            created.addAll(importFile(level, name));
        }
        return created;
    }

    public static List<Zone> importFile(ServerLevel level, String fileName) throws IOException {
        Path file = resolveFile(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing");
        }
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        List<CompoundTag> tags = new ArrayList<>();
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("zones")) {
            for (JsonElement el : parsed.getAsJsonObject().getAsJsonArray("zones")) {
                tags.add(readSnapshot(el.getAsJsonObject()));
            }
        } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("tag")) {
            tags.add(readSnapshot(parsed.getAsJsonObject()));
        } else {
            throw new IOException("shape");
        }
        List<Zone> created = new ArrayList<>();
        for (CompoundTag tag : tags) {
            created.add(place(level, tag));
        }
        if (!created.isEmpty()) {
            ZoneManager.data(level).markDirty();
            ZoneManager.sync(level);
        }
        return created;
    }

    private static Zone place(ServerLevel level, CompoundTag tag) {
        stripRuntime(tag);
        tag.putUUID("Id", UUID.randomUUID());
        Zone zone = Zone.load(tag);
        String base = zone.getName() == null || zone.getName().isBlank() ? "imported" : zone.getName();
        String name = base;
        int n = 2;
        while (nameTaken(level, name)) {
            name = base + " " + n++;
        }
        zone.setName(name);
        ZoneManager.data(level).add(zone);
        return zone;
    }

    private static boolean nameTaken(ServerLevel level, String name) {
        for (Zone zone : ZoneManager.data(level).all()) {
            if (zone.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveFile(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank() || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0 || fileName.contains("..")) {
            throw new IOException("name");
        }
        String name = fileName.toLowerCase(Locale.ROOT).endsWith(".json") ? fileName : fileName + ".json";
        Path dir = directory();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir.normalize())) {
            throw new IOException("name");
        }
        return file;
    }

    private static JsonObject single(Zone zone) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("name", zone.getName());
        root.add("tag", writeTag(snapshot(zone)));
        return root;
    }

    private static CompoundTag snapshot(Zone zone) {
        CompoundTag tag = zone.save();
        stripRuntime(tag);
        return tag;
    }

    private static void stripRuntime(CompoundTag tag) {
        tag.remove("EventActive");
        tag.remove("EventStartedAt");
        tag.remove("EventCooldownUntil");
        tag.remove("LastEventSpawnAt");
        tag.remove("EventWave");
        tag.remove("EventHadMobs");
        tag.remove("EventClearStreak");
    }

    private static CompoundTag readSnapshot(JsonObject root) {
        JsonObject body = root.has("tag") ? root.getAsJsonObject("tag") : root;
        Tag tag = readTag(body);
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("tag");
        }
        return compound;
    }

    private static JsonObject writeTag(Tag tag) {
        JsonObject o = new JsonObject();
        o.addProperty("t", tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG, Tag.TAG_FLOAT, Tag.TAG_DOUBLE ->
                    o.addProperty("v", ((NumericTag) tag).getAsNumber());
            case Tag.TAG_STRING -> o.addProperty("v", tag.getAsString());
            case Tag.TAG_COMPOUND -> {
                JsonObject v = new JsonObject();
                CompoundTag compound = (CompoundTag) tag;
                for (String key : compound.getAllKeys()) {
                    v.add(key, writeTag(compound.get(key)));
                }
                o.add("v", v);
            }
            case Tag.TAG_LIST -> {
                JsonArray array = new JsonArray();
                for (Tag child : (ListTag) tag) {
                    array.add(writeTag(child));
                }
                o.add("v", array);
            }
            case Tag.TAG_BYTE_ARRAY -> {
                JsonArray array = new JsonArray();
                for (byte b : ((ByteArrayTag) tag).getAsByteArray()) {
                    array.add(b);
                }
                o.add("v", array);
            }
            case Tag.TAG_INT_ARRAY -> {
                JsonArray array = new JsonArray();
                for (int i : ((IntArrayTag) tag).getAsIntArray()) {
                    array.add(i);
                }
                o.add("v", array);
            }
            case Tag.TAG_LONG_ARRAY -> {
                JsonArray array = new JsonArray();
                for (long i : ((LongArrayTag) tag).getAsLongArray()) {
                    array.add(i);
                }
                o.add("v", array);
            }
            default -> o.addProperty("v", tag.getAsString());
        }
        return o;
    }

    private static Tag readTag(JsonObject o) {
        int type = o.get("t").getAsInt();
        JsonElement v = o.get("v");
        return switch (type) {
            case Tag.TAG_BYTE -> net.minecraft.nbt.ByteTag.valueOf(v.getAsByte());
            case Tag.TAG_SHORT -> net.minecraft.nbt.ShortTag.valueOf(v.getAsShort());
            case Tag.TAG_INT -> net.minecraft.nbt.IntTag.valueOf(v.getAsInt());
            case Tag.TAG_LONG -> net.minecraft.nbt.LongTag.valueOf(v.getAsLong());
            case Tag.TAG_FLOAT -> net.minecraft.nbt.FloatTag.valueOf(v.getAsFloat());
            case Tag.TAG_DOUBLE -> net.minecraft.nbt.DoubleTag.valueOf(v.getAsDouble());
            case Tag.TAG_STRING -> StringTag.valueOf(v.getAsString());
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = new CompoundTag();
                for (var entry : v.getAsJsonObject().entrySet()) {
                    compound.put(entry.getKey(), readTag(entry.getValue().getAsJsonObject()));
                }
                yield compound;
            }
            case Tag.TAG_LIST -> {
                ListTag list = new ListTag();
                for (JsonElement el : v.getAsJsonArray()) {
                    list.add(readTag(el.getAsJsonObject()));
                }
                yield list;
            }
            case Tag.TAG_BYTE_ARRAY -> {
                JsonArray array = v.getAsJsonArray();
                byte[] data = new byte[array.size()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = array.get(i).getAsByte();
                }
                yield new ByteArrayTag(data);
            }
            case Tag.TAG_INT_ARRAY -> {
                JsonArray array = v.getAsJsonArray();
                int[] data = new int[array.size()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = array.get(i).getAsInt();
                }
                yield new IntArrayTag(data);
            }
            case Tag.TAG_LONG_ARRAY -> {
                JsonArray array = v.getAsJsonArray();
                long[] data = new long[array.size()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = array.get(i).getAsLong();
                }
                yield new LongArrayTag(data);
            }
            default -> throw new IllegalArgumentException("tag " + type);
        };
    }

    private static String fileName(String name) {
        String safe = name == null ? "zone" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            safe = "zone";
        }
        return safe + ".json";
    }
}
