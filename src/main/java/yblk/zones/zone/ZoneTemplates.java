package yblk.zones.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** JSON templates under config/yblk_zones/templates/*.json */
public final class ZoneTemplates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();
    private static Path dir;

    public record Template(String id, String name, CompoundTag settingsNbt, List<String> flags,
                           List<CompoundTag> actions) {
    }

    private ZoneTemplates() {
    }

    public static void init(Path configDir) {
        dir = configDir.resolve("yblk_zones").resolve("templates");
        try {
            Files.createDirectories(dir);
            writeDefaults();
            reload();
            loadBundledIfEmpty();
        } catch (IOException e) {
            LOGGER.warn("Zone templates init failed", e);
        }
    }

    public static void loadBundledIfEmpty() {
        if (!TEMPLATES.isEmpty()) {
            return;
        }
        try (var in = ZoneTemplates.class.getResourceAsStream("/data/yblk_zones/templates/claim_plot.json")) {
            if (in == null) {
                return;
            }
            loadFromJson("claim_plot", GSON.fromJson(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8),
                    JsonObject.class));
        } catch (Exception e) {
            LOGGER.warn("Bundled zone template load failed", e);
        }
    }

    private static void writeDefaults() throws IOException {
        // Always rewrite built-in presets so hatch-only / rename updates ship to existing configs.
        writeDefault("claim_plot.json", "Claim plot",
                List.of("VISIBLE", "DISPLAY", "CLAIM"),
                Map.of("outline", "HATCH", "outlineProximity", "ALWAYS", "hatchHeight", 2.5));
        writeDefault("safe_zone.json", "Safe zone",
                List.of("VISIBLE", "DISPLAY", "CLAIM", "MOB_DENY_ENTRY", "MOB_DENY_SPAWN"),
                Map.of("outline", "HATCH", "outlineProximity", "NEAR_REVEAL",
                        "hatchProximity", "NEAR_REVEAL", "hatchHeight", 2.5));
        writeDefault("dungeon.json", "Dungeon",
                List.of("VISIBLE", "DISPLAY", "MOB_BUFF", "PLAYER_DAMAGE", "MOB_DENY_SPAWN"),
                Map.of("outline", "HATCH", "outlineProximity", "ALWAYS", "harvestLevel", 3, "hatchHeight", 3.0));
        writeDefault("forbidden_zone.json", "Forbidden zone",
                List.of("VISIBLE", "DISPLAY", "CLAIM", "MOB_DENY_ENTRY", "MOB_DENY_SPAWN"),
                Map.of("outline", "HATCH", "outlineProximity", "NEAR_HIDE", "hatchHeight", 2.5));
        writeDefault("loot_cache.json", "Loot cache",
                List.of("VISIBLE", "DISPLAY", "CLAIM"),
                Map.of("outline", "HATCH", "harvestLevel", 4, "enterTitle", "Loot cache", "hatchHeight", 2.0));
        // Remove obsolete PvP arena preset if present
        try {
            Files.deleteIfExists(dir.resolve("arena.json"));
        } catch (IOException ignored) {
        }
    }

    private static void writeDefault(String file, String name, List<String> flags, Map<String, Object> settings)
            throws IOException {
        Path path = dir.resolve(file);
        JsonObject sample = new JsonObject();
        sample.addProperty("name", name);
        JsonArray flagArr = new JsonArray();
        for (String f : flags) {
            flagArr.add(f);
        }
        sample.add("flags", flagArr);
        JsonObject s = new JsonObject();
        for (Map.Entry<String, Object> e : settings.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number n) {
                s.addProperty(e.getKey(), n);
            } else if (v instanceof Boolean b) {
                s.addProperty(e.getKey(), b);
            } else {
                s.addProperty(e.getKey(), String.valueOf(v));
            }
        }
        sample.add("settings", s);
        Files.writeString(path, GSON.toJson(sample));
    }

    public static void reload() {
        TEMPLATES.clear();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(ZoneTemplates::loadOne);
        } catch (IOException e) {
            LOGGER.warn("Zone templates reload failed", e);
        }
    }

    private static void loadOne(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            String id = path.getFileName().toString().replace(".json", "");
            loadFromJson(id, GSON.fromJson(reader, JsonObject.class));
        } catch (Exception e) {
            LOGGER.warn("Bad template {}", path, e);
        }
    }

    private static void loadFromJson(String id, JsonObject root) {
        if (root == null) {
            return;
        }
        String name = root.has("name") ? root.get("name").getAsString() : id;
        List<String> flags = new ArrayList<>();
        if (root.has("flags") && root.get("flags").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("flags")) {
                flags.add(e.getAsString().toUpperCase(Locale.ROOT));
            }
        }
        CompoundTag settings = new CompoundTag();
        if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject s = root.getAsJsonObject("settings");
            for (Map.Entry<String, JsonElement> e : s.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    var p = e.getValue().getAsJsonPrimitive();
                    if (p.isBoolean()) {
                        settings.putBoolean(e.getKey(), p.getAsBoolean());
                    } else if (p.isNumber()) {
                        String key = e.getKey().toLowerCase(Locale.ROOT);
                        if (key.contains("level")
                                || key.contains("count")
                                || key.contains("ticks")
                                || e.getKey().equals("harvestLevel")
                                || e.getKey().equals("outlineColor")) {
                            settings.putInt(e.getKey(), p.getAsInt());
                        } else {
                            settings.putFloat(e.getKey(), p.getAsFloat());
                        }
                    } else {
                        settings.putString(e.getKey(), p.getAsString());
                    }
                }
            }
        }
        TEMPLATES.put(id, new Template(id, name, settings, flags, List.of()));
    }

    public static List<Template> all() {
        return List.of(TEMPLATES.values().toArray(new Template[0]));
    }

    public static Template get(String id) {
        return TEMPLATES.get(id);
    }

    public static void apply(Template template, ZoneSettings settings, java.util.EnumSet<ZoneFlag> flags) {
        if (template == null) {
            return;
        }
        flags.clear();
        // Reset mutable fields so previous template leftovers (e.g. harvestLevel) do not stick.
        settings.load(new ZoneSettings().save());
        CompoundTag bridge = new CompoundTag();
        for (String key : template.settingsNbt.getAllKeys()) {
            if (template.settingsNbt.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) {
                String v = template.settingsNbt.getString(key);
                switch (key) {
                    case "outline" -> bridge.putString("Outline", v);
                    case "outlineProximity" -> bridge.putString("OutlineProximity", v);
                    case "hatchProximity" -> bridge.putString("HatchProximity", v);
                    case "enterTitle" -> bridge.putString("EnterTitle", v);
                    case "enterSound" -> bridge.putString("EnterSound", v);
                    default -> bridge.putString(key, v);
                }
            } else if (template.settingsNbt.contains(key, net.minecraft.nbt.Tag.TAG_INT)) {
                int v = template.settingsNbt.getInt(key);
                if ("outlineColor".equals(key)) {
                    bridge.putInt("OutlineColor", v);
                } else if ("harvestLevel".equals(key)) {
                    bridge.putInt("HarvestLevel", v);
                }
            } else if (template.settingsNbt.contains(key, net.minecraft.nbt.Tag.TAG_FLOAT)
                    || template.settingsNbt.contains(key, net.minecraft.nbt.Tag.TAG_DOUBLE)) {
                if ("outlineColor".equals(key)) {
                    bridge.putInt("OutlineColor", (int) template.settingsNbt.getFloat(key));
                } else if ("proximityRadius".equals(key)) {
                    bridge.putFloat("ProximityRadius", template.settingsNbt.getFloat(key));
                } else if ("hatchApproachDistance".equals(key)) {
                    bridge.putFloat("HatchApproach", template.settingsNbt.getFloat(key));
                } else if ("hatchHeight".equals(key)) {
                    bridge.putFloat("HatchHeight", template.settingsNbt.getFloat(key));
                } else if ("harvestLevel".equals(key)) {
                    bridge.putInt("HarvestLevel", (int) template.settingsNbt.getFloat(key));
                }
            }
        }
        settings.loadPartialTemplate(bridge);
        for (String f : template.flags) {
            try {
                flags.add(ZoneFlag.valueOf(f));
            } catch (Exception ignored) {
            }
        }
        if (flags.isEmpty()) {
            flags.add(ZoneFlag.VISIBLE);
            flags.add(ZoneFlag.DISPLAY);
        }
    }
}
