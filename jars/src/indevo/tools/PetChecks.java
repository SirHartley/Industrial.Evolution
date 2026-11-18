package indevo.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static indevo.industries.petshop.memory.PetDataRepo.*;

public final class PetChecks {

    private static final Set<String> BODIES = Set.of("b_fourleg", "b_legs", "b_wings", "b_water", "b_float", "b_blob", "b_machine", "b_jar", "b_coil", "b_human");
    private static final Set<String> EVENTS = events();
    private static final Set<String> CONTEXTS = Set.of(CONTEXT_DOCKED, CONTEXT_STORAGE, CONTEXT_OLD, CONTEXT_HYPERSPACE);
    private static final Set<String> DISPOSITIONS = Set.of(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS);
    private static final Pattern BODY_WORDS = Pattern.compile("\\b(legs?|paws?|hands?|fur|wings?|feathers?|beak|claws?|tails?|fins?|glass|tank|swims?|fl(y|ies|ew|ying)|sniff\\w*)\\b", Pattern.CASE_INSENSITIVE);
    private static final int PICKS = 40000;
    // play time: a day is 10 s of flight, about 1.5 minutes a week with x2 and time in menus
    private static final float PLAY_MINUTES_PER_DAY = 1.5f / 7f;
    private static final Set<String> PACES = Set.of(PetData.PACE_FAST, PetData.PACE_MEDIUM, PetData.PACE_SLOW, PetData.PACE_HAMSTER);
    private static final Set<String> KINDS = Set.of(KIND_STAT, KIND_FLEET, KIND_SIGNATURE);
    private static final Set<String> MODES = Set.of(MODE_PERCENT, MODE_REDUCE, MODE_FLAT, MODE_COUNT);
    private static final float REDUCE_CAP = 90f;
    // Skeleton crew needs no crew at all from grade 10
    private static final Set<String> FULL_REDUCE = Set.of("min_crew");
    private static final int SHEETS = 40000;

    private final List<String> problems = new ArrayList<>();
    private int checks = 0;

    private PetChecks() {
    }

    public static void main(String[] args) throws IOException, JSONException {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }

        PetChecks run = new PetChecks();
        Map<String, PetData> pets = run.checkPets(run.read(Path.of(CSV_PATH)));
        Map<String, Trait> traits = run.checkTraits(run.read(Path.of(TRAITS_PATH)), pets);
        run.checkSheets(pets, traits);
        List<Line> lines = run.checkLines(run.read(Path.of(LINES_PATH)), pets);
        run.checkCoverage(lines, pets);
        run.checkWeights(pets);
        run.checkHearts();
        run.checkWants();

        if (!run.problems.isEmpty()) {
            for (String problem : run.problems) System.out.println("FAIL " + problem);
            System.out.println("Pets: " + run.problems.size() + " of " + run.checks + " checks failed");
            System.exit(1);
        }

        System.out.println("Pets: " + run.checks + " checks passed");
    }

    private JSONArray read(Path path) throws IOException, JSONException {
        byte[] bytes = Files.readAllBytes(path);
        check(bytes.length < 3 || (bytes[0] & 0xff) != 0xef, path + " starts with a BOM");

        String text = new String(bytes, StandardCharsets.UTF_8);
        check(text.chars().noneMatch(c -> "“”‘’".indexOf(c) >= 0), path + " has curly quotes");

        List<List<String>> table = SettingsChecks.parse(text);
        List<String> header = table.get(0);
        JSONArray rows = new JSONArray();
        Set<String> ids = new HashSet<>();

        for (List<String> record : table.subList(1, table.size())) {
            if (record.stream().allMatch(String::isBlank)) continue;
            check(record.size() == header.size(), path + " record " + record.get(0) + " has " + record.size() + " cells, header " + header.size());
            check(ids.add(record.get(0)), path + " repeats id " + record.get(0));

            JSONObject row = new JSONObject();
            for (int i = 0; i < header.size() && i < record.size(); i++) row.put(header.get(i), record.get(i));
            rows.put(row);
        }

        return rows;
    }

    private Map<String, PetData> checkPets(JSONArray rows) throws JSONException {
        Map<String, PetData> pets = parsePets(rows);
        check(!pets.isEmpty(), "pets.csv has pets");

        for (PetData data : pets.values()) {
            check(data.tags.stream().anyMatch(t -> t.startsWith(PetData.TAG_SPECIES_PREFIX)), data.id + " has a class tag");
            check(BODIES.contains(data.body), data.id + " body " + data.body + " is one of " + BODIES);
            check(PACES.contains(data.pace), data.id + " pace " + data.pace + " is one of " + PACES);
            check(data.isHamster() == data.id.equals("hampter"), data.id + (data.isHamster() ? " is the only hamster pace" : " has no hamster pace"));
        }

        return pets;
    }

    private Map<String, Trait> checkTraits(JSONArray rows, Map<String, PetData> pets) throws JSONException {
        Map<String, Trait> traits = parseTraits(rows);
        MutableShipStatsAPI stats = stub(MutableShipStatsAPI.class);
        Set<String> names = new HashSet<>();

        for (Trait trait : traits.values()) {
            String id = trait.id();
            check(id.startsWith(TRAIT_PREFIX), id + " starts with " + TRAIT_PREFIX);
            check(names.add(trait.name()), id + " name " + trait.name() + " is not used twice");
            check(KINDS.contains(trait.kind()), id + " kind " + trait.kind() + " is one of " + KINDS);
            check(MODES.contains(trait.mode()), id + " mode " + trait.mode() + " is one of " + MODES);
            check(trait.perLevel() > 0f && trait.per() >= 1 && trait.weight() >= 0f, id + " has a value per grade, grades per step and a weight");
            check(PetHullmod.applyStat(stats, trait.stat(), 1f, "check", "check"), id + " stat " + trait.stat() + " is a case of PetHullmod.applyStat");
            check(!trait.effect().isBlank(), id + " has an effect label");
            check(oneName(trait.desc()), id + " desc has one %s for the value and no other %");
            check(oneName(trait.flavour()) && trait.flavour().endsWith("."), id + " flavour has one %s for the name and ends with a full stop");
            check(!BODY_WORDS.matcher(trait.flavour()).find(), id + " flavour fits any body");

            if (trait.mode().equals(MODE_REDUCE)) {
                float cap = FULL_REDUCE.contains(trait.stat()) ? 100f : REDUCE_CAP;
                check(trait.cap() > 0f && trait.cap() <= cap, id + " reduction has a cap of at most " + cap);
                float most = trait.value(1000, PetConstants.HAMSTER_MULT * PetConstants.NEST_MULT);
                check(most <= trait.cap(), id + " stops at its cap, " + most + " at grade 1000 on a hamster with a nest");
            }
        }

        for (PetData data : pets.values()) {
            for (String lean : data.talentWeights.keySet()) {
                Trait trait = traits.get(lean);
                check(trait != null && trait.isTalent(), data.id + " talent lean " + lean + " is a talent in pet_traits.csv");
                check(data.getTalentWeight(lean) > 0f, data.id + " talent lean " + lean + " weighs more than 0");
            }
        }

        return traits;
    }

    // bought pets: 1 or 2 talents at grade 1 half and half, every disposition, no Pathfinder, leans count, signatures only on rare species
    private void checkSheets(Map<String, PetData> pets, Map<String, Trait> traits) {
        Random random = new Random(3);
        PetData plain = pets.get("doge");
        PetData raven = pets.get("salvageraven");
        PetData rare = pets.get("tammy");
        String scavenger = TRAIT_PREFIX + "scavenger";
        int two = 0;
        int plainScavenger = 0;
        int ravenScavenger = 0;
        Set<String> dispositions = new HashSet<>();

        boolean sizes = true;
        boolean grades = true;
        boolean unknown = true;
        boolean pathfinder = false;
        boolean plainSignature = false;
        for (int i = 0; i < SHEETS; i++) {
            PetSheet sheet = PetGenetics.rollSheet(plain, traits, random);
            int n = sheet.traits.size();
            if (n == 2) two++;
            dispositions.add(sheet.disposition);
            sizes &= n == 1 || n == 2;
            grades &= sheet.traits.values().stream().allMatch(g -> g == 1);
            unknown &= sheet.known.isEmpty();
            pathfinder |= sheet.traits.containsKey(TRAIT_PREFIX + "pathfinder");
            plainSignature |= sheet.traits.keySet().stream().anyMatch(t -> traits.get(t).isSignature());
            if (sheet.traits.containsKey(scavenger)) plainScavenger++;
            if (PetGenetics.rollSheet(raven, traits, random).traits.containsKey(scavenger)) ravenScavenger++;
        }

        check(sizes, "a bought pet has 1 or 2 talents");
        check(grades, "a bought pet's talents are grade 1");
        check(unknown, "a bought pet's talents are unknown");
        check(!pathfinder, "a bought pet has no Pathfinder, it gives nothing at grade 1");
        float share = two / (float) SHEETS;
        check(Math.abs(share - PetConstants.TWO_TALENTS_CHANCE) < 0.01f, "two talents " + share + " of the time, expected " + PetConstants.TWO_TALENTS_CHANCE);
        check(dispositions.equals(DISPOSITIONS), "bought pets get every disposition, got " + dispositions);
        check(ravenScavenger > 2 * plainScavenger, "a Salvage Raven is a Scavenger more often: " + ravenScavenger + " against " + plainScavenger);
        check(!plain.isRare() && !plainSignature, "a common pet never brings a signature talent");

        int signed = 0;
        boolean rareSizes = true;
        boolean worthless = false;
        for (int i = 0; i < SHEETS; i++) {
            PetSheet sheet = PetGenetics.rollSheet(rare, traits, random);
            long signatures = sheet.traits.keySet().stream().filter(t -> traits.get(t).isSignature()).count();
            long others = sheet.traits.size() - signatures;
            if (signatures > 0) signed++;
            rareSizes &= signatures <= 1 && others >= 1 && others <= 2;
            worthless |= sheet.traits.keySet().stream().anyMatch(t -> traits.get(t).value(1, 1f) <= 0f);
        }

        float signedShare = signed / (float) SHEETS;
        check(rare.isRare() && Math.abs(signedShare - PetConstants.SIGNATURE_CHANCE) < 0.01f, "a rare pet brings a signature " + signedShare + " of the time, expected " + PetConstants.SIGNATURE_CHANCE);
        check(rareSizes, "a rare pet has 1 or 2 talents and at most one signature");
        check(!worthless, "no pet comes with a talent worth nothing at grade 1");

        PetSheet sheet = new PetSheet(Personalities.STEADY);
        sheet.traits.put(TRAIT_PREFIX + "plating", 1);
        sheet.traits.put(TRAIT_PREFIX + "vent", 3);
        sheet.traits.put(TRAIT_PREFIX + "ward", 1);
        sheet.traits.put("IndEvo_pt_gone", 5);
        List<String> talents = PetGenetics.talents(sheet, traits);
        check(talents.equals(List.of(TRAIT_PREFIX + "vent", TRAIT_PREFIX + "plating", TRAIT_PREFIX + "ward")), "talents best first, ties in arrival order, unknown ids left out: " + talents);
        check(PetGenetics.activeCount(3, 0) == 0 && PetGenetics.activeCount(3, 2) == 2 && PetGenetics.activeCount(7, 4) == 4 && PetGenetics.activeCount(7, PetConstants.MAX_HEARTS) == 7,
                "one talent per heart, all at " + PetConstants.MAX_HEARTS);
        check(traits.get(TRAIT_PREFIX + "pathfinder").value(3, 1f) == 0f && traits.get(TRAIT_PREFIX + "pathfinder").value(8, 1f) == 2f, "Pathfinder steps every 4 grades");
    }

    private static boolean oneName(String text) {
        return text.indexOf("%s") >= 0 && text.indexOf("%s") == text.lastIndexOf("%s") && text.replace("%s", "").indexOf('%') < 0;
    }

    // stand in ship stats that hand out real stat objects, so applyStat runs without the game
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(PetChecks.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == MutableStat.class) return new MutableStat(1f);
            if (r == StatBonus.class) return new StatBonus();
            if (r == DynamicStatsAPI.class) return stub(DynamicStatsAPI.class);
            if (r == boolean.class) return false;
            if (r == float.class) return 0f;
            if (r == int.class) return 0;
            return null;
        });
    }

    private List<Line> checkLines(JSONArray rows, Map<String, PetData> pets) throws JSONException {
        List<Line> lines = parseLines(rows);
        Set<String> classes = new HashSet<>();
        for (PetData data : pets.values()) classes.add(data.getClassTag());

        Set<String> moods = new HashSet<>();
        for (PetMood mood : PetMood.values()) moods.add(mood.id());

        for (Line line : lines) {
            String id = line.id();
            check(id.startsWith("IndEvo_"), id + " starts with IndEvo_");
            check(EVENTS.contains(line.event()), id + " event " + line.event() + " is one of " + EVENTS);
            check(moods.containsAll(line.moods()), id + " moods " + line.moods() + " are in " + moods);
            check(DISPOSITIONS.containsAll(line.dispositions()), id + " dispositions " + line.dispositions() + " are in " + DISPOSITIONS);
            check(CONTEXTS.containsAll(line.contexts()), id + " contexts " + line.contexts() + " are in " + CONTEXTS);

            boolean bodyBound = false;
            for (String token : line.match()) {
                boolean pet = pets.containsKey(token);
                boolean body = BODIES.contains(token);
                check(pet || body || classes.contains(token), id + " match " + token + " is a pet id, body or class tag in use");
                bodyBound |= pet || body;
            }

            String text = line.text();
            check(!text.isBlank(), id + " has text");
            if (line.event().equals(EVENT_NAME)) {
                check(line.moods().isEmpty() && line.dispositions().isEmpty() && line.contexts().isEmpty(), id + " name has no mood, disposition or context");
                check(text.length() <= PetConstants.NAME_MAX_CHARS && text.matches("[A-Z][A-Za-z' ]*[a-z]"), id + " name is a short capitalized name");
                continue;
            }
            check(text.indexOf("%s") >= 0 && text.indexOf("%s") == text.lastIndexOf("%s"), id + " has one %s for the name");
            check(text.replace("%s", "").indexOf('%') < 0, id + " has no other %");
            check(line.event().equals(EVENT_STATUS) != text.endsWith("."), id + (line.event().equals(EVENT_STATUS) ? " status note has no full stop" : " ends with a full stop"));
            if (line.event().equals(EVENT_STATUS)) check(text.startsWith("%s "), id + " status note starts with the name");
            if (!bodyBound) check(!BODY_WORDS.matcher(text).find(), id + " fits any body (class and any lines describe no body)");
        }

        return lines;
    }

    private void checkCoverage(List<Line> lines, Map<String, PetData> pets) {
        Random random = new Random(1);

        for (PetData data : pets.values()) {
            for (PetMood mood : PetMood.values()) {
                for (String event : EVENTS) {
                    Set<String> context = event.equals(EVENT_DOCKING) ? Set.of(CONTEXT_DOCKED) : Set.of();
                    for (String disposition : DISPOSITIONS) {
                        check(pickLine(lines, event, data, mood.id(), disposition, context, random) != null,
                                data.id + " has a line for " + event + " when " + mood.id() + " and " + disposition);
                    }
                }
            }
        }
    }

    private void checkWeights(Map<String, PetData> pets) {
        PetData data = pets.values().iterator().next();
        Line pet = line("pet", data.id, "");
        Line body = line("body", data.body, "");
        Line cls = line("class", data.getClassTag(), "");
        Line any = line("any", "", "");
        Line other = line("other", "b_none", "");
        Line moody = line("moody", "", PetMood.HUNGRY.id());
        String content = PetMood.CONTENT.id();

        check(pet.weight(data, content, "", Set.of()) == WEIGHT_PET, "pet id line weighs " + WEIGHT_PET);
        check(body.weight(data, content, "", Set.of()) == WEIGHT_BODY, "body line weighs " + WEIGHT_BODY);
        check(cls.weight(data, content, "", Set.of()) == WEIGHT_CLASS, "class line weighs " + WEIGHT_CLASS);
        check(any.weight(data, content, "", Set.of()) == WEIGHT_ANY, "any line weighs " + WEIGHT_ANY);
        check(other.weight(data, content, "", Set.of()) == 0f, "a line for another body never matches");
        check(moody.weight(data, content, "", Set.of()) == 0f, "a mood line never matches another mood");
        check(any.weight(data, content, "", Set.of(CONTEXT_STORAGE)) == 0f, "a stored pet takes no status line without the storage context");
        Line stored = new Line("stored", "status", Set.of(), Set.of(), Set.of(), Set.of(CONTEXT_STORAGE), "%s");
        check(stored.weight(data, content, "", Set.of(CONTEXT_STORAGE)) == WEIGHT_ANY, "a storage status line matches a stored pet");

        List<Line> lines = List.of(pet, body, cls, any, other, moody);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Random random = new Random(7);
        for (int i = 0; i < PICKS; i++) counts.merge(pickLine(lines, "status", data, content, "", Set.of(), random).id(), 1, Integer::sum);

        float total = WEIGHT_PET + WEIGHT_BODY + WEIGHT_CLASS + WEIGHT_ANY;
        share(counts, "pet", WEIGHT_PET / total);
        share(counts, "body", WEIGHT_BODY / total);
        share(counts, "class", WEIGHT_CLASS / total);
        share(counts, "any", WEIGHT_ANY / total);
    }

    private void checkHearts() {
        float[] days = PetConstants.HEART_DAYS;
        check(days.length == PetConstants.MAX_HEARTS, "one bond day threshold per heart");
        for (int i = 1; i < days.length; i++) check(days[i] > days[i - 1], "heart " + (i + 1) + " needs more days than heart " + i);
        check(PetConstants.MASCOT_HEARTS <= PetConstants.TIME_HEARTS && PetConstants.TIME_HEARTS < PetConstants.NEST_HEARTS, "time reaches the mascot effect, not the nest");

        float first = days[0] * PLAY_MINUTES_PER_DAY;
        float third = days[PetConstants.TIME_HEARTS - 1] * PLAY_MINUTES_PER_DAY;
        float supplies = third / PetConstants.SUPPLIES_HEART_RATE;
        check(first <= 5f, "first heart in " + first + " minutes of play, at most 5");
        check(third <= 60f, "third heart in " + third + " minutes of play, at most 60");
        check(supplies <= 180f, "third heart on Supplies in " + supplies + " minutes of play, at most 180");

        check(PetData.paceFor(PetData.FAST_UNDER_DAYS - 1f).equals(PetData.PACE_FAST), "a short life span is fast");
        check(PetData.paceFor(PetData.FAST_UNDER_DAYS).equals(PetData.PACE_MEDIUM), "the fast limit is medium");
        check(PetData.paceFor(PetData.MEDIUM_UNDER_DAYS).equals(PetData.PACE_SLOW), "the medium limit is slow");
    }

    // one favourite, every want answered the day it opens, no shared events: the slowest way to hearts 4 and 5
    private void checkWants() {
        float[] days = PetConstants.HEART_DAYS;
        float timeCap = days[PetConstants.TIME_HEARTS - 1];
        float bond = 0f;
        float cooldown = 0f;
        float[] reached = new float[days.length];
        for (int day = 0; day < 5000 && bond < days[days.length - 1]; day++) {
            if (bond < timeCap) bond = Math.min(timeCap, bond + 1f);
            cooldown = Math.max(0f, cooldown - 1f);
            if (cooldown <= 0f) {
                bond = Math.min(days[days.length - 1], bond + PetConstants.WANT_BOND_DAYS);
                cooldown = PetConstants.WANT_COOLDOWN_DAYS;
            }
            for (int h = 0; h < days.length; h++) if (reached[h] == 0f && bond >= days[h]) reached[h] = day + 1f;
        }

        float fourth = reached[PetConstants.NEST_HEARTS - 1] * PLAY_MINUTES_PER_DAY;
        float fifth = reached[days.length - 1] * PLAY_MINUTES_PER_DAY;
        check(fourth > 0f && fourth <= 90f, "fourth heart from wants in " + fourth + " minutes of play, at most 90");
        check(fifth > 0f && fifth <= 180f, "fifth heart from wants in " + fifth + " minutes of play, at most 180");
        check(PetConstants.WANT_DAYS * PLAY_MINUTES_PER_DAY >= 5f, "a want stays open " + PetConstants.WANT_DAYS * PLAY_MINUTES_PER_DAY + " minutes of play, at least 5");
        check(PetConstants.WANT_GAP_DAYS * PLAY_MINUTES_PER_DAY >= 1f, "wants in the fleet at least " + PetConstants.WANT_GAP_DAYS * PLAY_MINUTES_PER_DAY + " minutes apart, at least 1");
        check(PetConstants.WANT_COOLDOWN_DAYS > PetConstants.WANT_DAYS, "a favourite's cooldown is longer than a want stays open");
    }

    private static Set<String> events() {
        Set<String> events = new HashSet<>(Set.of(EVENT_AMBIENT, EVENT_DOCKING, EVENT_STATUS, EVENT_PETTING, EVENT_PETTING_STARVING, EVENT_NAME, EVENT_PLAY, EVENT_TREAT));
        for (PetWant want : PetWant.values()) events.add(want.event());
        return events;
    }

    private void share(Map<String, Integer> counts, String id, float expected) {
        float got = counts.getOrDefault(id, 0) / (float) PICKS;
        check(Math.abs(got - expected) < 0.01f, id + " line picked " + got + " of the time, expected " + expected);
    }

    private static Line line(String id, String match, String mood) {
        Set<String> matches = match.isEmpty() ? Set.of() : Set.of(match);
        Set<String> moods = mood.isEmpty() ? Set.of() : Set.of(mood);
        return new Line(id, "status", matches, moods, Set.of(), Set.of(), "%s");
    }

    private boolean check(boolean ok, String what) {
        checks++;
        if (!ok) problems.add(what);
        return ok;
    }
}
