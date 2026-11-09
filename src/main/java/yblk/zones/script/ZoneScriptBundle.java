package yblk.zones.script;

import yblk.zones.zone.ZoneSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Script file payload: actions + optional event settings for import/export between zones.
 */
public final class ZoneScriptBundle {
    private final List<ZoneAction> actions = new ArrayList<>();
    private boolean hasEventMeta;
    private boolean eventEnabled = true;
    private boolean eventNeedsConfirm;
    private boolean denyExitWhileEvent;
    private String eventSpawnMob = "";
    private int eventSpawnCount;
    private int eventSpawnInterval = 100;
    private int eventWaves;
    private boolean eventWinOnClear;
    private int eventDurationTicks = 6000;
    private int eventMinTicks;
    private int eventCooldownTicks = 1200;
    private String eventSky = "DEFAULT";
    private String eventWeather = "DEFAULT";

    public List<ZoneAction> actions() {
        return actions;
    }

    public boolean hasEventMeta() {
        return hasEventMeta;
    }

    public static ZoneScriptBundle fromEditor(List<ZoneAction> actions, boolean eventEnabled,
                                              boolean eventNeedsConfirm, boolean denyExitWhileEvent,
                                              ZoneSettings settings) {
        ZoneScriptBundle b = new ZoneScriptBundle();
        if (actions != null) {
            for (ZoneAction a : actions) {
                b.actions.add(a.copy());
            }
        }
        b.hasEventMeta = true;
        b.eventEnabled = eventEnabled;
        b.eventNeedsConfirm = eventNeedsConfirm;
        b.denyExitWhileEvent = denyExitWhileEvent;
        if (settings != null) {
            b.eventSpawnMob = settings.eventSpawnMob();
            b.eventSpawnCount = settings.eventSpawnCount();
            b.eventSpawnInterval = settings.eventSpawnInterval();
            b.eventWaves = settings.eventWaves();
            b.eventWinOnClear = settings.eventWinOnClear();
            b.eventDurationTicks = settings.eventDurationTicks();
            b.eventMinTicks = settings.eventMinTicks();
            b.eventCooldownTicks = settings.eventCooldownTicks();
            b.eventSky = settings.eventSkyMode().name();
            b.eventWeather = settings.eventWeatherMode().name();
        }
        return b;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append("# yblk_zones script v1\n");
        sb.append("# Import into another zone: Scripts → Import\n");
        if (hasEventMeta) {
            sb.append("@meta")
                    .append(" event_enabled=").append(eventEnabled)
                    .append(" event_needs_confirm=").append(eventNeedsConfirm)
                    .append(" deny_exit_while_event=").append(denyExitWhileEvent)
                    .append('\n');
            sb.append("@event")
                    .append(" spawn_mob=").append(eventSpawnMob.isBlank() ? "-" : eventSpawnMob)
                    .append(" spawn_count=").append(eventSpawnCount)
                    .append(" spawn_interval=").append(eventSpawnInterval)
                    .append(" waves=").append(eventWaves)
                    .append(" win_on_clear=").append(eventWinOnClear)
                    .append(" duration=").append(eventDurationTicks)
                    .append(" min=").append(eventMinTicks)
                    .append(" cooldown=").append(eventCooldownTicks)
                    .append(" sky=").append(eventSky)
                    .append(" weather=").append(eventWeather)
                    .append('\n');
        }
        sb.append(ZoneScriptText.encode(actions));
        return sb.toString();
    }

    public static ZoneScriptBundle decode(String text) {
        ZoneScriptBundle b = new ZoneScriptBundle();
        if (text == null || text.isBlank()) {
            return b;
        }
        StringBuilder actionText = new StringBuilder();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String raw = line.trim();
            if (raw.startsWith("@meta")) {
                b.hasEventMeta = true;
                parseKv(raw.substring(5).trim(), b, true);
                continue;
            }
            if (raw.startsWith("@event")) {
                b.hasEventMeta = true;
                parseKv(raw.substring(6).trim(), b, false);
                continue;
            }
            actionText.append(line).append('\n');
        }
        b.actions.addAll(ZoneScriptText.decode(actionText.toString()));
        return b;
    }

    private static void parseKv(String rest, ZoneScriptBundle b, boolean meta) {
        for (String part : rest.split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).trim();
            if (meta) {
                switch (key) {
                    case "event_enabled" -> b.eventEnabled = parseBool(val);
                    case "event_needs_confirm" -> b.eventNeedsConfirm = parseBool(val);
                    case "deny_exit_while_event" -> b.denyExitWhileEvent = parseBool(val);
                    default -> {
                    }
                }
            } else {
                switch (key) {
                    case "spawn_mob" -> b.eventSpawnMob = "-".equals(val) ? "" : val;
                    case "spawn_count" -> b.eventSpawnCount = parseInt(val, 0);
                    case "spawn_interval" -> b.eventSpawnInterval = Math.max(20, parseInt(val, 100));
                    case "waves" -> b.eventWaves = Math.max(0, parseInt(val, 0));
                    case "win_on_clear" -> b.eventWinOnClear = parseBool(val);
                    case "duration" -> b.eventDurationTicks = Math.max(0, parseInt(val, 6000));
                    case "min" -> b.eventMinTicks = Math.max(0, parseInt(val, 0));
                    case "cooldown" -> b.eventCooldownTicks = Math.max(0, parseInt(val, 1200));
                    case "sky" -> b.eventSky = val.toUpperCase(Locale.ROOT);
                    case "weather" -> b.eventWeather = val.toUpperCase(Locale.ROOT);
                    default -> {
                    }
                }
            }
        }
    }

    public void applyEventTo(ZoneSettings settings, BooleanHolder eventEnabled,
                             BooleanHolder eventNeedsConfirm, BooleanHolder denyExitWhileEvent) {
        if (!hasEventMeta || settings == null) {
            return;
        }
        if (eventEnabled != null) {
            eventEnabled.value = this.eventEnabled;
        }
        if (eventNeedsConfirm != null) {
            eventNeedsConfirm.value = this.eventNeedsConfirm;
        }
        if (denyExitWhileEvent != null) {
            denyExitWhileEvent.value = this.denyExitWhileEvent;
        }
        settings.setEventSpawnMob(eventSpawnMob);
        settings.setEventSpawnCount(eventSpawnCount);
        settings.setEventSpawnInterval(eventSpawnInterval);
        settings.setEventWaves(eventWaves);
        settings.setEventWinOnClear(eventWinOnClear);
        settings.setEventDurationTicks(eventDurationTicks);
        settings.setEventMinTicks(eventMinTicks);
        settings.setEventCooldownTicks(eventCooldownTicks);
        try {
            settings.setEventSkyMode(ZoneSettings.SkyMode.valueOf(eventSky));
        } catch (Exception ignored) {
        }
        try {
            settings.setEventWeatherMode(ZoneSettings.WeatherMode.valueOf(eventWeather));
        } catch (Exception ignored) {
        }
    }

    /** Tiny mutable bool for applyEventTo without lambdas fighting finals. */
    public static final class BooleanHolder {
        public boolean value;

        public BooleanHolder(boolean value) {
            this.value = value;
        }
    }

    private static boolean parseBool(String s) {
        return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("on");
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
