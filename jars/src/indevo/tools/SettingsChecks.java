package indevo.tools;

import com.fs.starfarer.api.Global;
import indevo.config.PlayerSettings;
import indevo.config.Setting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SettingsChecks {

    static final String CSV = "data/config/LunaSettings.csv";
    static final List<String> COLUMNS = List.of("fieldID", "fieldName", "fieldType", "defaultValue", "secondaryValue",
            "fieldDescription", "minValue", "maxValue", "tab");
    static final String QUICK = "Quick Settings";
    static final List<String> TABS = List.of(QUICK, "Exploration & Items", "Meteor Swarms", "Minefields", "Artillery",
            "Industries", "Production", "Derelict Industries", "Colony Services");
    static final Map<String, Setting.Type> TYPES = Map.of("Boolean", Setting.Type.BOOLEAN, "Int", Setting.Type.INT,
            "Double", Setting.Type.DOUBLE);
    static final Pattern INT = Pattern.compile("-?[0-9]+");
    static final Pattern NUMBER = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");
    static final Pattern NEW_GAME_NOTE = Pattern.compile("(^|\\. )Needs a new game\\.");
    static final Pattern DEFAULT_NOTE = Pattern.compile("(^|[.!)] )Default (on|off|-?[0-9]+(\\.[0-9]+)?)\\.");
    static final Pattern LUNA_ACCESS = Pattern.compile("\\bLunaSettings(Loader)?\\b(?!\\.csv)");
    static final List<String> OWN_ACCESS = List.of("jars/src/indevo/config/", "jars/src/indevo/tools/");

    private final List<String> problems = new ArrayList<>();
    private int checks;

    private SettingsChecks() {
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        System.out.println("Settings: " + run(root, ReleaseBuild.sources(root)) + " checks passed");
    }

    public static int run(Path root, List<Path> sources) throws IOException {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }
        SettingsChecks run = new SettingsChecks();
        run.checkSelf();
        Map<String, List<String>> rows = run.checkCsv(Files.readAllBytes(root.resolve(CSV)));
        run.checkConstants(rows);
        run.checkSanitize();
        run.checkPairs();
        run.checkSources(root, sources);
        if (!run.problems.isEmpty()) {
            throw new IllegalStateException("Settings checks failed:\n  " + String.join("\n  ", run.problems));
        }
        return run.checks;
    }

    private void checkSelf() {
        check(parse("a,\"b,\"\"c\"\"\"\r\n,\n").equals(List.of(List.of("a", "b,\"c\""), List.of("", ""))),
                "csv parser reads quotes and line ends");
        check(throwsState(() -> parse("a,b\"c\n")) && throwsState(() -> parse("\"a\"b\n"))
                && throwsState(() -> parse("\"a\n")), "csv parser rejects stray quotes");
        check(formats("50%% more") && !formats("50% more") && !formats("%s"), "format check");
        check(highlights("a [b] c") && highlights("[]") && !highlights("a [b c") && !highlights("a ]b[ c"),
                "highlight check");
        check(DEFAULT_NOTE.matcher("Off. Default on.").find() && DEFAULT_NOTE.matcher("Default 0.5.").find()
                && !DEFAULT_NOTE.matcher("Defaults vary. Default: 5").find(), "default note check");
    }

    private Map<String, List<String>> checkCsv(byte[] bytes) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        boolean bom = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;
        check(!bom, CSV + " starts with a BOM");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            check(false, CSV + " is not UTF-8");
            return rows;
        }
        check(text.chars().noneMatch(c -> "“”‘’".indexOf(c) >= 0), CSV + " has curly quotes");
        List<List<String>> table;
        try {
            table = parse(text);
        } catch (IllegalStateException e) {
            check(false, CSV + ": " + e.getMessage());
            return rows;
        }
        if (!check(!table.isEmpty() && table.get(0).equals(COLUMNS), CSV + " header is not " + COLUMNS)) return rows;

        List<String> tabs = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            String record = CSV + " record " + (i + 1);
            if (!check(row.size() == COLUMNS.size(), record + " has " + row.size() + " columns")) continue;
            String id = row.get(0);
            if (id.isEmpty()) {
                // lunalib skips rows without an id
                check(String.join("", row).isEmpty(), record + " has no fieldID but is not empty");
                continue;
            }
            check(id.startsWith("IndEvo_"), id + " has no IndEvo_ prefix");
            check(rows.put(id, row) == null, id + " is listed twice");
            String tab = row.get(8);
            check(TABS.contains(tab), id + " is in unknown tab \"" + tab + "\"");
            if (!tabs.contains(tab)) tabs.add(tab);
            checkText(id, row);
        }
        // lunalib orders tabs by their first row
        check(tabs.equals(TABS), "tab order is " + tabs + ", expected " + TABS);
        return rows;
    }

    private void checkText(String id, List<String> row) {
        String type = row.get(2);
        if (type.equals("Header")) {
            check(!row.get(3).isBlank(), id + " header has no title in defaultValue");
            return;
        }
        if (type.equals("Text")) {
            checkShown(id, "defaultValue", row.get(3), true);
            return;
        }
        check(TYPES.containsKey(type), id + " has unsupported type " + type);
        checkShown(id, "fieldName", row.get(1), false);
        checkShown(id, "fieldDescription", row.get(5), true);
    }

    private void checkShown(String id, String column, String text, boolean highlighted) {
        if (!check(!text.isBlank(), id + " has no " + column)) return;
        check(formats(text), id + " " + column + " breaks String.format, write % as %%");
        if (highlighted) check(highlights(text), id + " " + column + " has a [ without a ] after it");
    }

    private void checkConstants(Map<String, List<String>> rows) {
        Set<Setting> blocks = EnumSet.noneOf(Setting.class);
        for (Setting setting : Setting.values()) {
            List<String> row = rows.get(setting.id);
            if (!check(row != null, setting.name() + " has no row " + setting.id)) continue;
            if (!check(TYPES.get(row.get(2)) == setting.type, setting.id + " row type " + row.get(2)
                    + " does not match " + setting.type)) continue;
            checkDefault(setting, row.get(3));
            if (setting.type != Setting.Type.BOOLEAN) checkBounds(setting, row.get(6), row.get(7));
            boolean newGame = setting.apply == Setting.Apply.NEW_GAME;
            check(NEW_GAME_NOTE.matcher(row.get(5)).find() == newGame, setting.id + " help text "
                    + (newGame ? "lacks" : "has") + " \"Needs a new game.\" but apply is " + setting.apply);
            checkDefaultNote(setting, row.get(5));
            if (setting.block != null && blocks.add(setting.block)) checkBlock(setting.block, rows.get(setting.block.id));
        }
        for (Map.Entry<String, List<String>> entry : rows.entrySet()) {
            if (!TYPES.containsKey(entry.getValue().get(2))) continue;
            try {
                Setting.byId(entry.getKey());
                checks++;
            } catch (IllegalArgumentException e) {
                check(false, entry.getKey() + " has no Setting constant");
            }
        }
    }

    private void checkDefault(Setting setting, String text) {
        boolean matches = switch (setting.type) {
            // lunalib reads it with Boolean.parseBoolean, anything but true is false
            case BOOLEAN -> (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))
                    && Boolean.parseBoolean(text) == (Boolean) setting.fallback;
            case INT -> INT.matcher(text).matches() && Integer.parseInt(text) == (Integer) setting.fallback;
            case DOUBLE -> NUMBER.matcher(text).matches()
                    && Double.parseDouble(text) == ((Number) setting.fallback).doubleValue();
        };
        check(matches, setting.id + " row default " + text + " does not match " + setting.fallback);
    }

    private void checkDefaultNote(Setting setting, String help) {
        Matcher note = DEFAULT_NOTE.matcher(help);
        boolean matches = note.find() && switch (setting.type) {
            case BOOLEAN -> note.group(2).equals((Boolean) setting.fallback ? "on" : "off");
            case INT, DOUBLE -> !note.group(2).equals("on") && !note.group(2).equals("off")
                    && Double.parseDouble(note.group(2)) == ((Number) setting.fallback).doubleValue();
        };
        check(matches, setting.id + " help text needs \"Default <value>.\" matching the default " + setting.fallback);
    }

    private void checkBounds(Setting setting, String min, String max) {
        // lunalib uses 0 and 100 for an empty bound
        if (!check(NUMBER.matcher(min).matches() && NUMBER.matcher(max).matches(),
                setting.id + " needs plain number bounds, has \"" + min + "\" and \"" + max + "\"")) return;
        check(Double.parseDouble(min) == setting.min && Double.parseDouble(max) == setting.max,
                setting.id + " row bounds " + min + " to " + max + " do not match " + setting.min + " to " + setting.max);
        double fallback = ((Number) setting.fallback).doubleValue();
        check(setting.min <= fallback && fallback <= setting.max, setting.id + " default is outside its bounds");
    }

    private void checkBlock(Setting block, List<String> blockRow) {
        check(block.type == Setting.Type.BOOLEAN && block.block == null,
                block.id + " is a block and must be a Boolean without a block");
        check(blockRow != null && blockRow.get(8).equals(QUICK), block.id + " is a block and belongs in " + QUICK);
    }

    private void checkSanitize() {
        for (Setting setting : Setting.values()) {
            Object fallback = setting.fallback;
            String name = setting.id + " sanitize ";
            check(Setting.same(setting.sanitize(fallback), fallback), name + "changes the default");
            check(Setting.same(setting.sanitize(null), fallback), name + "keeps a missing value");
            check(Setting.same(setting.sanitize("1"), fallback), name + "keeps a string");
            if (setting.type == Setting.Type.BOOLEAN) {
                check(Setting.same(setting.sanitize(!(Boolean) fallback), !(Boolean) fallback), name + "drops a boolean");
                check(Setting.same(setting.sanitize(1), fallback), name + "keeps a number");
                continue;
            }
            check(Setting.same(setting.sanitize(true), fallback), name + "keeps a boolean");
            check(Setting.same(setting.sanitize(Double.NaN), fallback), name + "keeps NaN");
            check(Setting.same(setting.sanitize(Double.POSITIVE_INFINITY), fallback)
                    && Setting.same(setting.sanitize(Double.NEGATIVE_INFINITY), fallback), name + "keeps infinity");
            Object low = setting.sanitize(setting.min - 1);
            Object high = setting.sanitize(setting.max + 1);
            if (setting.type == Setting.Type.INT) {
                check(low instanceof Integer && high instanceof Integer, name + "returns a non Int for an Int");
                check(Setting.same(low, Math.round(setting.min)) && Setting.same(high, Math.round(setting.max)),
                        name + "does not clamp");
                if (setting.max - setting.min >= 1) {
                    check(Setting.same(setting.sanitize(setting.min + 0.6), Math.round(setting.min + 0.6)),
                            name + "does not round");
                }
            } else {
                check(Setting.same(low, setting.min) && Setting.same(high, setting.max), name + "does not clamp");
                double middle = (setting.min + setting.max) / 2;
                check(Setting.same(setting.sanitize(middle), middle), name + "changes a valid value");
            }
        }
    }

    private void checkPairs() {
        Map<Setting, Object> values = PlayerSettings.defaults();
        PlayerSettings.applyPairRule(values);
        check(values.equals(PlayerSettings.defaults()), "pair rule changes the defaults");
        for (Setting min : Setting.values()) {
            if (!min.name().contains("_MIN_")) continue;
            Setting max;
            try {
                max = Setting.valueOf(min.name().replace("_MIN_", "_MAX_"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!check(min.type == max.type && min.type != Setting.Type.BOOLEAN,
                    min.id + " and " + max.id + " need the same number type")) continue;
            Map<Setting, Object> inverted = new EnumMap<>(PlayerSettings.defaults());
            inverted.put(min, min.sanitize(min.max));
            inverted.put(max, max.sanitize(max.min));
            PlayerSettings.applyPairRule(inverted);
            check(((Number) inverted.get(max)).doubleValue() >= ((Number) inverted.get(min)).doubleValue(),
                    "pair rule does not keep " + max.id + " at or above " + min.id
                            + "; add it to PlayerSettings.PAIRS or fix the bounds");
        }
    }

    private void checkSources(Path root, List<Path> sources) throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources) {
            String name = root.relativize(path).toString().replace('\\', '/');
            if (OWN_ACCESS.stream().anyMatch(name::startsWith)) continue;
            if (LUNA_ACCESS.matcher(Files.readString(path)).find()) offenders.add(name);
        }
        check(offenders.isEmpty(), "LunaSettings used outside indevo/config, read through Setting: " + offenders);
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean end = c == ',' || c == '\n' || c == '\r';
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                    closed = true;
                }
                continue;
            }
            if (closed && !end) throw new IllegalStateException("text after a closing quote in record " + (rows.size() + 1));
            if (c == '"') {
                if (cell.length() > 0) throw new IllegalStateException("stray quote in record " + (rows.size() + 1));
                quoted = true;
            } else if (end) {
                row.add(cell.toString());
                cell.setLength(0);
                closed = false;
                if (c == ',') continue;
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (quoted) throw new IllegalStateException("unclosed quote in record " + (rows.size() + 1));
        if (!row.isEmpty() || cell.length() > 0 || closed) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    static boolean formats(String text) {
        try {
            String.format(text);
            return true;
        } catch (IllegalFormatException e) {
            return false;
        }
    }

    // translation of the highlight loop in lunalib LunaSettingsUISettingsPanel, it throws on a [ with no ] after it
    static boolean highlights(String text) {
        while (text.indexOf('[') >= 0) {
            int open = text.indexOf('[');
            int close = text.indexOf(']');
            if (close < open + 1) return false;
            text = text.substring(0, open) + text.substring(open + 1, close) + text.substring(close + 1);
        }
        return true;
    }

    private static boolean throwsState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private boolean check(boolean condition, String problem) {
        checks++;
        if (!condition) problems.add(problem);
        return condition;
    }
}
