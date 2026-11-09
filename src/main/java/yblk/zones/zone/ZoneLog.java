package yblk.zones.zone;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ring buffer + file log for zone audits. */
public final class ZoneLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CAPACITY = 200;
    private static final Deque<String> RING = new ArrayDeque<>(CAPACITY);
    private static Path file;

    private ZoneLog() {
    }

    public static void init(Path gameDir) {
        try {
            Path logs = gameDir.resolve("logs");
            Files.createDirectories(logs);
            file = logs.resolve("yblk_zones.log");
        } catch (IOException e) {
            LOGGER.warn("Could not init zone log file", e);
        }
    }

    public static void info(String message) {
        append("INFO", message);
        LOGGER.info("[YBLK Zones] {}", message);
    }

    public static void warn(String message) {
        append("WARN", message);
        LOGGER.warn("[YBLK Zones] {}", message);
    }

    private static synchronized void append(String level, String message) {
        String line = Instant.now() + " [" + level + "] " + message;
        while (RING.size() >= CAPACITY) {
            RING.removeFirst();
        }
        RING.addLast(line);
        if (file != null) {
            try {
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }

    public static synchronized List<String> recent(int max) {
        List<String> all = new ArrayList<>(RING);
        if (all.size() <= max) {
            return all;
        }
        return all.subList(all.size() - max, all.size());
    }
}
