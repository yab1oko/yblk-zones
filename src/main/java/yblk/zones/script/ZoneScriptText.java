package yblk.zones.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple line-based script format for the text editor.
 * <pre>
 * ON_EVENT_START SPAWN minecraft:zombie count=4
 * ON_EVENT_END GIVE_ITEM minecraft:diamond count=3 chat=false
 * ON_EVENT_START TITLE Raid! chat=true
 * ON_EVENT_END HEAL hearts=10 feed=true
 * ON_EVENT_END CLEAR_MOBS
 * # comments allowed
 * </pre>
 * Params: count / duration / xp / amp / volume / hearts / chat / feed / on
 */
public final class ZoneScriptText {
    private ZoneScriptText() {
    }

    public static String encode(List<ZoneAction> actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# trigger TYPE value [key=value...]\n");
        sb.append("# triggers: ON_EVENT_START ON_EVENT_END ON_ENTER ON_EXIT ON_EVENT_TICK BEFORE_BUY AFTER_BUY\n");
        sb.append("# types: SPAWN GIVE_ITEM GIVE_XP HEAL TITLE SOUND EFFECT PARTICLE COMMAND CLEAR_MOBS DENY_EXIT FLAG_SET\n");
        if (actions == null || actions.isEmpty()) {
            return sb.toString();
        }
        for (ZoneAction a : actions) {
            sb.append(a.trigger().name()).append(' ')
                    .append(a.type().name());
            if (!a.value().isBlank()) {
                sb.append(' ').append(quoteIfNeeded(a.value()));
            }
            appendParams(sb, a);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void appendParams(StringBuilder sb, ZoneAction a) {
        switch (a.type()) {
            case SPAWN, GIVE_ITEM, PARTICLE -> sb.append(" count=").append(Math.max(1, a.intArg()));
            case EFFECT -> {
                sb.append(" duration=").append(Math.max(1, a.intArg()));
                sb.append(" amp=").append(Math.max(0, (int) a.floatArg()));
            }
            case SOUND -> sb.append(" volume=").append(formatFloat(a.floatArg() > 0 ? a.floatArg() : 1.0F));
            case TITLE -> {
                if (a.boolArg()) {
                    sb.append(" chat=true");
                }
            }
            case GIVE_XP -> sb.append(" xp=").append(Math.max(1, a.intArg()));
            case HEAL -> {
                sb.append(" hearts=").append(formatFloat(a.floatArg() > 0 ? a.floatArg() : 10.0F));
                if (a.boolArg()) {
                    sb.append(" feed=true");
                }
            }
            case DENY_EXIT, FLAG_SET -> {
                if (a.boolArg()) {
                    sb.append(" on=true");
                } else {
                    sb.append(" on=false");
                }
            }
            default -> {
                if (a.intArg() != 0) {
                    sb.append(" int=").append(a.intArg());
                }
                if (a.floatArg() != 0 && a.floatArg() != 1.0F) {
                    sb.append(" float=").append(formatFloat(a.floatArg()));
                }
                if (a.boolArg()) {
                    sb.append(" bool=true");
                }
            }
        }
    }

    private static String formatFloat(float f) {
        if (f == (int) f) {
            return Integer.toString((int) f);
        }
        return String.format(Locale.ROOT, "%.2f", f);
    }

    private static String quoteIfNeeded(String value) {
        if (value.indexOf(' ') >= 0 || value.indexOf('=') >= 0) {
            return '"' + value.replace("\"", "'") + '"';
        }
        return value;
    }

    /** Parse text; returns actions. Throws IllegalArgumentException with line info on hard errors. */
    public static List<ZoneAction> decode(String text) {
        List<ZoneAction> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) {
                continue;
            }
            try {
                out.add(parseLine(raw));
            } catch (Exception e) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static ZoneAction parseLine(String line) {
        List<String> tokens = tokenize(line);
        if (tokens.size() < 2) {
            throw new IllegalArgumentException("need TRIGGER TYPE");
        }
        ZoneTrigger trigger = ZoneTrigger.valueOf(tokens.get(0).toUpperCase(Locale.ROOT));
        ZoneActionType type = ZoneActionType.valueOf(tokens.get(1).toUpperCase(Locale.ROOT));
        ZoneAction a = new ZoneAction(trigger, type, "");
        applyTypeDefaults(a);

        int idx = 2;
        // value = first non key=value token
        if (idx < tokens.size() && !tokens.get(idx).contains("=")) {
            a.setValue(tokens.get(idx));
            idx++;
        }
        for (; idx < tokens.size(); idx++) {
            String t = tokens.get(idx);
            int eq = t.indexOf('=');
            if (eq <= 0) {
                // leftover value fragment
                if (a.value().isBlank()) {
                    a.setValue(t);
                }
                continue;
            }
            String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = t.substring(eq + 1).trim();
            switch (key) {
                case "count", "duration", "xp", "int" -> a.setIntArg(parseInt(val));
                case "amp", "amplifier" -> a.setFloatArg(parseInt(val));
                case "volume", "hearts", "float" -> a.setFloatArg(parseFloat(val));
                case "chat", "feed", "on", "bool" -> a.setBoolArg(parseBool(val));
                case "value" -> a.setValue(val);
                default -> {
                }
            }
        }
        return a;
    }

    private static void applyTypeDefaults(ZoneAction a) {
        switch (a.type()) {
            case SPAWN -> a.setIntArg(1);
            case GIVE_ITEM -> a.setIntArg(1);
            case EFFECT -> a.setIntArg(200);
            case SOUND -> a.setFloatArg(1.0F);
            case PARTICLE -> a.setIntArg(16);
            case GIVE_XP -> a.setIntArg(10);
            case HEAL -> a.setFloatArg(10.0F);
            case DENY_EXIT -> a.setBoolArg(true);
            default -> {
            }
        }
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s.trim());
    }

    private static float parseFloat(String s) {
        return Float.parseFloat(s.trim().replace(',', '.'));
    }

    private static boolean parseBool(String s) {
        return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("yes");
    }
}
