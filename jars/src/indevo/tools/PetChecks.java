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
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.industries.petshop.plugins.PetTreeLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
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
    private static final Set<String> KINDS = Set.of(KIND_STAT, KIND_FLEET, KIND_SIGNATURE, KIND_MUTATION, KIND_FLAW);
    private static final Set<String> MODES = Set.of(MODE_PERCENT, MODE_REDUCE, MODE_FLAT, MODE_COUNT, MODE_DIVIDE, MODE_NONE);
    private static final int YOUNG = 100000;
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
        run.checkTitles();
        run.checkGenetics(pets, traits);
        run.checkTree();

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
            if (trait.isTalent() || !trait.stat().isEmpty()) {
                check(!trait.isMutation(), id + " mutation acts on the pet, not on a ship stat");
                check(PetHullmod.applyStat(stats, trait.stat(), 1f, "check", "check"), id + " stat " + trait.stat() + " is a case of PetHullmod.applyStat");
            }
            check(!trait.effect().isBlank(), id + " has an effect label");
            if (trait.hasLevels()) check(oneName(trait.desc()), id + " desc has one %s for the value and no other %");
            else check(trait.isFlaw() && trait.desc().indexOf('%') < 0, id + " without levels is a flaw with no % in desc");
            check(trait.isMutation() != trait.with().isEmpty(), id + (trait.isMutation() ? " mutation arrives with flaws" : " has no with column"));
            for (String flaw : trait.with()) check(traits.containsKey(flaw) && traits.get(flaw).isFlaw(), id + " arrives with " + flaw + ", a flaw in pet_traits.csv");
            check(trait.from().isEmpty() || trait.isFlaw() && trait.from().equals(FROM_INBREEDING), id + " from is blank or inbreeding on a flaw");
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
    // odds over 100,000 seeded young per rule, at Care 40 (a player owned center)
    private void checkGenetics(Map<String, PetData> pets, Map<String, Trait> traits) {
        PetData fast = byPace(pets, PetData.PACE_FAST);
        PetData medium = byPace(pets, PetData.PACE_MEDIUM);
        PetData slow = byPace(pets, PetData.PACE_SLOW);
        PetData hamster = byPace(pets, PetData.PACE_HAMSTER);
        PetData rare = pets.get("tammy");
        float care = 40f;
        float better = PetGenetics.betterChance(care);
        float drop = PetGenetics.dropChance(care);
        String talent = TRAIT_PREFIX + "plating";
        String flaw = TRAIT_PREFIX + "glutton";
        String giant = TRAIT_PREFIX + "giant";

        check(near(better, 140f / 240f) && near(PetGenetics.betterChance(20f), 120f / 220f) && near(drop, 40f / 240f), "Care 40 tilts to " + better + " and drops a flaw at " + drop);
        check(near(PetGenetics.sumChance(fast, 0), 0.10f) && near(PetGenetics.sumChance(medium, 0), 0.25f) && near(PetGenetics.sumChance(slow, 10), 0.70f)
                && near(PetGenetics.sumChance(hamster, 0), 0.10f), "sum chance 10, 25, 40% by pace, +3% a heart, the hamster as fast");

        Random random = new Random(11);
        Map<Integer, Integer> one = new LinkedHashMap<>();
        Map<Integer, Integer> both = new LinkedHashMap<>();
        Map<Integer, Integer> flawOne = new LinkedHashMap<>();
        Map<Integer, Integer> flawBoth = new LinkedHashMap<>();
        PetSheet plain = sheet();
        PetSheet a = sheet(talent, 3, flaw, 2);
        PetSheet b = sheet(talent, 2, flaw, 1);
        for (int i = 0; i < YOUNG; i++) {
            one.merge(PetGenetics.rollYoung(medium, a, plain, care, 0, 0f, 0f, traits, random).getGrade(talent), 1, Integer::sum);
            PetSheet young = PetGenetics.rollYoung(medium, a, b, care, 0, 0f, 0f, traits, random);
            both.merge(young.getGrade(talent), 1, Integer::sum);
            flawBoth.merge(young.getGrade(flaw), 1, Integer::sum);
            flawOne.merge(PetGenetics.rollYoung(medium, a, plain, care, 0, 0f, 0f, traits, random).getGrade(flaw), 1, Integer::sum);
        }
        float sum = PetGenetics.sumChance(medium, 0);
        share("talent of one parent kept", one, 3, better);
        share("talent of one parent lost", one, 0, 1f - better);
        share("talent of both summed", both, 5, sum);
        share("talent of both at the higher grade", both, 3, (1f - sum) * better);
        share("talent of both at the lower grade", both, 2, (1f - sum) * (1f - better));
        share("flaw of one parent dropped", flawOne, 0, drop);
        share("flaw of both summed", flawBoth, 3, sum);
        share("flaw of both at the lower level", flawBoth, 1, (1f - sum) * better);
        check(!flawBoth.containsKey(0), "a flaw both parents have never drops");

        float rate = PetGenetics.mutationRate(medium, plain, plain, false, false, traits);
        check(near(rate, 0.16f) && near(PetGenetics.mutationRate(fast, plain, plain, true, true, traits), 0.24f)
                && near(PetGenetics.mutationRate(slow, sheet(TRAIT_PREFIX + "unstable", 1), sheet(TRAIT_PREFIX + "unstable", 1), false, false, traits), 0.30f),
                "mutation rate 8, 16, 24% by pace, x2 irradiated, x1.5 Alpha, +3 points an Unstable level");
        int fresh = 0;
        int mutations = 0;
        int signatures = 0;
        boolean withFlaws = true;
        for (int i = 0; i < YOUNG; i++) {
            PetSheet young = PetGenetics.rollYoung(medium, plain, plain, care, 0, rate, 0f, traits, random);
            if (young.traits.isEmpty()) continue;
            fresh++;
            for (String id : young.traits.keySet()) {
                Trait trait = traits.get(id);
                if (trait.isMutation()) {
                    mutations++;
                    for (String with : trait.with()) withFlaws &= young.getGrade(with) == 1;
                }
                if (trait.isSignature()) signatures++;
            }
        }
        share("young with a new trait", fresh, rate);
        check(Math.abs(mutations / (float) fresh - PetConstants.NEW_MUTATION_CHANCE) < 0.02f, "new traits are mutations " + mutations / (float) fresh + " of the time");
        check(Math.abs(signatures / (float) (fresh - mutations) - PetConstants.NEW_SIGNATURE_CHANCE) < 0.02f, "new talents are signatures " + signatures / (float) (fresh - mutations) + " of the time");
        check(withFlaws, "a new mutation arrives with its flaws at level 1");

        int sibling = 0;
        int faint = 0;
        int runts = 0;
        for (int i = 0; i < YOUNG; i++) {
            if (!PetGenetics.rollYoung(medium, plain, plain, care, 0, 0f, 0.5f, traits, random).traits.isEmpty()) sibling++;
            if (!PetGenetics.rollYoung(medium, plain, plain, care, 0, 0f, 0.02f, traits, random).traits.isEmpty()) faint++;
            if (!PetGenetics.rollYoung(hamster, plain, plain, care, 0, 0f, 0f, traits, random).traits.isEmpty()) runts++;
        }
        share("siblings' young malformed", sibling, 0.75f);
        check(faint == 0, "no young malformed below 5%");
        share("hamster young malformed", runts, PetConstants.HAMSTER_MALFORMED);
        check(PetGenetics.malformedChance(hamster, 0.5f) > PetGenetics.malformedChance(medium, 0.5f), "the hamster chance comes on top of inbreeding");

        float plainBrood = 0f;
        float fecundBrood = 0f;
        float weakBrood = 0f;
        int rareMax = 0;
        for (int i = 0; i < YOUNG; i++) {
            plainBrood += PetGenetics.broodSize(medium, plain, plain, traits, random);
            fecundBrood += PetGenetics.broodSize(medium, sheet(TRAIT_PREFIX + "fecund", 1), plain, traits, random);
            weakBrood += PetGenetics.broodSize(medium, sheet(TRAIT_PREFIX + "weak_brood", 1), plain, traits, random);
            rareMax = Math.max(rareMax, PetGenetics.broodSize(rare, plain, plain, traits, random));
        }
        check(Math.abs(plainBrood / YOUNG - 2f) < 0.02f, "a brood is 1 to 3 young, " + plainBrood / YOUNG + " on average");
        check(Math.abs(fecundBrood / YOUNG - 3f) < 0.02f, "Fecund 1 adds one young, " + fecundBrood / YOUNG + " on average");
        check(Math.abs(weakBrood / YOUNG - 1.5f) < 0.02f, "Weak brood 1 loses a quarter, " + weakBrood / YOUNG + " on average");
        check(rare.isRare() && rareMax == 1, "a rare species has 1 young");

        check(!PetGenetics.rollYoung(medium, sheet(giant, 1), plain, 0f, 0, 0f, 0f, traits, new Random(5)).traits.containsKey(TRAIT_PREFIX + "sterile"), "Sterile comes only from inbreeding");

        checkBody(medium, hamster, plain, traits);
        checkBroods(fast, medium, slow, hamster, plain, traits);
        checkPlan(medium, traits.get(talent), traits.get(flaw), care, better, drop, sum);
    }

    private void checkPlan(PetData medium, Trait talent, Trait flaw, float care, float better, float drop, float sum) {
        check(near(PetGenetics.wantedChance(talent, 3, 3, 0, care, sum), better) && near(PetGenetics.wantedChance(talent, 1, 3, 0, care, sum), better),
                "a talent of one parent is planned at the keep chance");
        check(near(PetGenetics.wantedChance(talent, 5, 3, 2, care, sum), sum) && near(PetGenetics.wantedChance(talent, 3, 3, 2, care, sum), sum + (1f - sum) * better),
                "a talent of both is planned at the sum chance, a lower grade adds the higher grade");
        check(near(PetGenetics.wantedChance(flaw, 0, 2, 0, care, sum), drop) && near(PetGenetics.wantedChance(flaw, 1, 2, 1, care, sum), (1f - sum) * better)
                && near(PetGenetics.wantedChance(flaw, 0, 2, 1, care, sum), 0f), "a dropped flaw is planned at the drop chance, never when both parents have it");
        check(near(PetGenetics.wantedChance(talent, 1, 0, 0, care, sum), 0f) && near(PetGenetics.wantedChance(flaw, 0, 0, 0, care, sum), 1f),
                "a talent neither parent has never comes, a flaw neither has never does");

        Map<String, Integer> wanted = new LinkedHashMap<>();
        wanted.put(talent.id(), 3);
        wanted.put(PetGenetics.MALFORMED, 0);
        Map<String, Float> chances = PetGenetics.wantedChances(medium, sheet(talent.id(), 3), sheet(), 0, care, 0.5f, wanted, Map.of(talent.id(), talent));
        float healthy = 1f - PetGenetics.malformedChance(medium, 0.5f);
        check(near(chances.get(PetGenetics.MALFORMED), healthy) && near(PetGenetics.product(chances), better * healthy), "plan rows multiply, the malformed row is the healthy chance");
        check(PetGenetics.broods(0.25f, new int[]{1, 3}) == 2 && PetGenetics.broods(0.9f, new int[]{1, 3}) == 1 && PetGenetics.broods(0f, new int[]{1, 3}) == -1,
                "broods to a target on the average brood size, never under 1, none when it cannot come");
    }

    private void checkBroods(PetData fast, PetData medium, PetData slow, PetData hamster, PetSheet plain, Map<String, Trait> traits) {
        check(near(PetGenetics.broodDays(fast, plain, plain, traits), 30f) && near(PetGenetics.broodDays(medium, plain, plain, traits), 60f)
                && near(PetGenetics.broodDays(slow, plain, plain, traits), 90f) && near(PetGenetics.broodDays(hamster, plain, plain, traits), 7f), "brood time 30, 60, 90 days by pace, the hamster 7");
        check(near(PetGenetics.broodDays(fast, sheet(PetGenetics.SLOW_GROWER, 1), plain, traits), 60f), "Slow grower 1 doubles the brood time");
        check(near(PetGenetics.broodDays(slow, sheet(PetGenetics.SLOW_GROWER, 1), plain, traits), PetConstants.MAX_BROOD_DAYS), "a brood never takes more than 90 days");
        check(near(PetGenetics.broodDays(medium, sheet(PetGenetics.MAYFLY, 1), sheet(PetGenetics.MAYFLY, 1), traits), 30f), "Mayfly of both parents adds up");
        check(near(PetGenetics.youngDays(medium, plain, traits), 30f) && near(PetGenetics.youngDays(hamster, plain, traits), 7f)
                && near(PetGenetics.youngDays(medium, sheet(PetGenetics.MAYFLY, 2), traits), 15f), "Young for 30 days, the hamster 7, Mayfly cuts it");

        Map<String, String[]> tree = new HashMap<>();
        tree.put("a", new String[]{"p", "q"});
        tree.put("b", new String[]{"p", "q"});
        tree.put("c", new String[]{"p", "r"});
        tree.put("d", new String[]{"a", "s"});
        tree.put("e", new String[]{"b", "t"});
        tree.put("f", new String[]{"a", "b"});
        Function<String, String[]> parents = tree::get;
        check(near(PetGenetics.inbreeding("x", "y", parents), 0f), "strangers are not related");
        check(near(PetGenetics.inbreeding("a", "b", parents), 0.5f), "siblings 50%");
        check(near(PetGenetics.inbreeding("a", "p", parents), 0.5f), "parent and child 50%");
        check(near(PetGenetics.inbreeding("a", "c", parents), 0.25f), "half siblings 25%");
        check(near(PetGenetics.inbreeding("d", "e", parents), 0.125f), "cousins 12.5%");
        check(near(PetGenetics.inbreeding("f", "a", parents), 0.75f), "a young of two siblings with one of them 75%");

        Random random = new Random(3);
        PetSheet a = sheet(TRAIT_PREFIX + "plating", 3, PetGenetics.GLUTTON, 1);
        PetSheet b = sheet(TRAIT_PREFIX + "plating", 2, TRAIT_PREFIX + "nimble", 1);
        boolean origins = true;
        for (int i = 0; i < 2000; i++) {
            PetSheet young = PetGenetics.rollYoung(medium, a, b, 40f, 10, 0.5f, 0.5f, traits, random);
            for (Map.Entry<String, Integer> e : young.traits.entrySet()) {
                String from = young.getFrom(e.getKey());
                int la = a.getGrade(e.getKey());
                int lb = b.getGrade(e.getKey());
                boolean ok = switch (from == null ? "" : from) {
                    case PetSheet.FROM_SUM -> e.getValue() == la + lb;
                    case PetSheet.FROM_A -> e.getValue() == la;
                    case PetSheet.FROM_B -> e.getValue() == lb;
                    case PetSheet.FROM_MUTATION -> traits.get(e.getKey()).isMutation() ? la == 0 && lb == 0 : e.getValue() == 1;
                    case PetSheet.FROM_MALFORMED -> young.malformed;
                    default -> false;
                };
                origins &= ok;
            }
        }
        check(origins, "every trait of a young says where it came from, and the level fits");
        check(PetGenetics.broodSize(medium, sheet(PetGenetics.STERILE, 1), plain, traits, random) == 0, "a Sterile parent gives no young");

        int[] range = PetGenetics.broodRange(medium, plain, plain, traits);
        check(range[0] == 1 && range[1] == PetConstants.MAX_BROOD, "a plain pair shows 1 to " + PetConstants.MAX_BROOD + " young, got " + range[0] + " to " + range[1]);
        check(PetGenetics.broodRange(medium, sheet(PetGenetics.WEAK_BROOD, 1), plain, traits)[0] == 0, "a Weak brood parent shows 0 at the bottom");
        check(PetGenetics.broodRange(medium, sheet(PetGenetics.STERILE, 1), plain, traits)[1] == 0, "a Sterile parent shows no young");

        PetSheet hidden = sheet(PetGenetics.SLOW_GROWER, 1);
        hidden.known.remove(PetGenetics.SLOW_GROWER);
        PetSheet seen = PetGenetics.seen(hidden);
        check(seen.getGrade(PetGenetics.SLOW_GROWER) == 0 && PetGenetics.broodDays(medium, seen, plain, traits) == PetGenetics.broodDays(medium, plain, plain, traits),
                "a preview leaves out traits not read yet");

        checkOutcomes();
    }

    // the chart shows the chances inherit rolls: per case the outcomes add up to 1 and match 20000 rolls by level
    private void checkOutcomes() {
        Random random = new Random(11);
        int[][] cases = {{3, 2}, {2, 2}, {1, 4}, {2, 0}, {0, 3}};
        boolean sums = true;
        boolean rolls = true;
        for (boolean flaw : new boolean[]{false, true}) {
            for (int[] c : cases) {
                List<PetGenetics.Outcome> outcomes = PetGenetics.outcomes(flaw, true, c[0], c[1], 40f, 0.2f);
                float total = 0f;
                Map<Integer, Float> byLevel = new HashMap<>();
                for (PetGenetics.Outcome o : outcomes) {
                    total += o.chance();
                    byLevel.merge(o.level(), o.chance(), Float::sum);
                }
                sums &= near(total, 1f);

                Map<Integer, Integer> counts = new HashMap<>();
                for (int i = 0; i < 20000; i++) counts.merge(PetGenetics.inherit(flaw, c[0], c[1], 40f, 0.2f, random), 1, Integer::sum);
                for (Map.Entry<Integer, Float> e : byLevel.entrySet()) rolls &= Math.abs(counts.getOrDefault(e.getKey(), 0) / 20000f - e.getValue()) < 0.02f;
                for (int level : counts.keySet()) rolls &= byLevel.containsKey(level);
            }
        }
        check(sums, "the chances of a cross add up to 1");
        check(rolls, "the cross chart matches what inherit rolls");

        List<PetGenetics.Outcome> both = PetGenetics.outcomes(false, false, 1, 1, 40f, 0.2f);
        check(both.size() == 1 && both.get(0).lane().equals(PetSheet.FROM_SUM) && near(both.get(0).chance(), 1f), "a trait without levels both parents have always passes on");
    }

    private void checkBody(PetData medium, PetData hamster, PetSheet plain, Map<String, Trait> traits) {
        String runt = PetGenetics.RUNT;
        check(near(PetGenetics.lifetime(medium, plain, 300f, traits), 300f) && near(PetGenetics.bodyMult(plain, PetGenetics.GIANT, traits), 1f)
                && near(PetGenetics.heartMult(plain, traits), 1f) && near(PetGenetics.foodMult(plain, traits), 1f), "a clean pet keeps its life, effects, heart rate and food");
        check(near(PetGenetics.lifetime(medium, sheet(PetGenetics.SHORT_LIVED, 1), 300f, traits), 150f)
                && near(PetGenetics.lifetime(medium, sheet(PetGenetics.SHORT_LIVED, 2), 300f, traits), 100f), "Short lived halves life at level 1, a third at level 2");
        check(near(PetGenetics.lifetime(medium, sheet(PetGenetics.LONG_LIVED, 2), 300f, traits), 450f), "Long-lived 2 adds half the life");
        check(near(PetGenetics.lifetime(hamster, sheet(PetGenetics.SHORT_LIVED, 1, PetGenetics.GLUTTON, 2), 31f, traits), 31f / 2f - 9f), "a hamster loses 3 days of life a flaw level, after Short lived");
        check(PetGenetics.lifetime(hamster, sheet(PetGenetics.GLUTTON, 12), 31f, traits) < 0f, "a hamster's life has no floor");
        check(near(PetGenetics.lifetime(medium, sheet(PetGenetics.GLUTTON, 2), 300f, traits), 300f), "flaw levels cost only a hamster life");
        check(near(PetGenetics.bodyMult(sheet(PetGenetics.GIANT, 1), PetGenetics.GIANT, traits), 2f)
                && near(PetGenetics.bodyMult(sheet(PetGenetics.SAVANT, 2), PetGenetics.SAVANT, traits), 1.5f), "Giant 1 doubles the mascot effect, Savant 2 adds half to talents");
        check(near(PetGenetics.bodyMult(sheet(runt, 1), PetGenetics.SAVANT, traits), 0.5f) && near(PetGenetics.bodyMult(sheet(runt, 2, PetGenetics.GIANT, 1), PetGenetics.GIANT, traits), 2f / 3f),
                "Runt divides by 1 + level, never to zero");
        check(near(PetGenetics.bodyMult(sheet(PetGenetics.GIANT, 1), PetGenetics.SAVANT, traits), 1f), "Giant does not raise talents");
        check(near(PetGenetics.heartMult(sheet(PetGenetics.SULLEN, 1), traits), 0.5f), "Sullen 1 halves the heart rate");
        check(near(PetGenetics.foodMult(sheet(PetGenetics.GLUTTON, 1), traits), 3f), "Glutton 1 eats three times as much");
        check(near(PetGenetics.bodyValue(sheet(PetGenetics.SPLIT_MIND, 2), PetGenetics.SPLIT_MIND, traits), 2f), "Split mind counts its level");
        check(PetGenetics.flawLevels(sheet(PetGenetics.GLUTTON, 2, PetGenetics.GIANT, 1, runt, 1), traits) == 3, "flaw levels leave mutations out");
    }

    private static PetData byPace(Map<String, PetData> pets, String pace) {
        for (PetData data : pets.values()) if (pace.equals(data.pace) && !data.isRare()) return data;
        throw new IllegalStateException("no plain pet with pace " + pace);
    }

    private static PetSheet sheet(Object... idsAndLevels) {
        PetSheet sheet = new PetSheet(Personalities.STEADY);
        for (int i = 0; i < idsAndLevels.length; i += 2) sheet.traits.put((String) idsAndLevels[i], (Integer) idsAndLevels[i + 1]);
        return sheet;
    }

    private static boolean near(float got, float expected) {
        return Math.abs(got - expected) < 0.001f;
    }

    private void share(String what, Map<Integer, Integer> counts, int level, float expected) {
        share(what, counts.getOrDefault(level, 0), expected);
    }

    private void share(String what, int count, float expected) {
        float got = count / (float) YOUNG;
        check(Math.abs(got - expected) < 0.01f, what + " " + got + " of the time, expected " + expected);
    }

    private void checkTitles() {
        Set<String> labels = new HashSet<>();
        for (PetTitle title : PetTitle.values()) {
            check(labels.add(title.label), title + " label " + title.label + " is not used twice");
            check(title.reason.contains("%s") == title.count > 0, title + " reason has a %s exactly when it has a count");
            check(title.getReason().endsWith(".") && !title.getReason().contains("%"), title + " reason reads as a sentence: " + title.getReason());
        }
    }

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

    // the family tree of the mockups: four generations, a pet with two mates, back crossing, a dead branch, an unrelated family
    private void checkTree() {
        List<PetTreeLayout.Item> items = new ArrayList<>(List.of(
                item("Jack", null, null), item("Sable", null, null), item("Hook", null, null), item("Mist", null, null),
                item("Corvo", "Jack", "Sable"), item("Magpie", "Jack", "Sable"), item("Flint", "Hook", "Mist"), item("Dusk", null, null),
                item("Jet", "Dusk", "Corvo"), item("Biscuit", "Magpie", "Flint"), item("Rook", "Magpie", "Flint"), item("Tank", null, null), item("Wren", null, null),
                item("Crumb", "Tank", "Biscuit"), item("Fuse", "Tank", "Biscuit"), item("Spoon", "Tank", "Biscuit"), item("Pip", "Biscuit", "Wren"),
                item("Mote", "Biscuit", "Fuse"), item("Speck", "Biscuit", "Mote"), item("Dot", "Fuse", "Mote"), item("Chip", "Dot", "Speck"),
                item("Ash", "Jack", "Sable"), item("Cinder", null, null), item("Soot", "Ash", "Cinder"), item("Coal", "Ash", "Cinder"),
                item("Onyx", null, null), item("Kettle", null, null), item("Nix", "Onyx", "Kettle"), item("Grit", null, null), item("Lost", null, null), item("Gone", null, null)));
        Set<String> gone = Set.of("Jack", "Sable", "Hook", "Mist", "Magpie", "Ash", "Cinder", "Soot", "Coal", "Lost", "Gone");

        PetTreeLayout.Folded folded = PetTreeLayout.fold(items, gone, Set.of(), Set.of(), true);
        List<String> branch = folded.stubs().get("stub:Ash");
        check(branch != null && branch.size() == 4, "a dead branch folds with its mate and young into one stub of 4, got " + branch);
        check(folded.stubs().getOrDefault(PetTreeLayout.SINGLES, List.of()).size() == 2, "gone pets with no family fold into one stub");
        check(folded.items().stream().anyMatch(i -> i.id().equals("stub:Ash") && "Jack".equals(i.a()) && "Sable".equals(i.b())), "the stub hangs under the parents of the branch");
        check(folded.items().stream().anyMatch(i -> i.id().equals("Jack")), "a dead ancestor of living pets stays");
        check(!PetTreeLayout.fold(items, gone, Set.of("Soot"), Set.of(), true).stubs().containsKey("stub:Ash"), "a pet in focus does not fold, nor do its parents");
        check(!PetTreeLayout.fold(items, gone, Set.of(), Set.of("stub:Ash"), true).stubs().containsKey("stub:Ash"), "an opened stub shows its pets");

        for (PetTreeLayout.Metrics m : List.of(PetTreeLayout.FULL, PetTreeLayout.SMALL)) {
            PetTreeLayout.Result result = PetTreeLayout.layout(folded.items(), folded.pairs(), m, true);
            check(result.families().size() == 2, "two families, got " + result.families().size());
            check(result.singles().containsAll(List.of("Grit", PetTreeLayout.SINGLES)), "pets with no family stand apart, got " + result.singles());
            checkRows(result, folded.items(), m);
        }

        PetTreeLayout.Result pair = PetTreeLayout.layout(PetTreeLayout.family(items, "Biscuit"), List.of(new PetTreeLayout.Pair("Biscuit", "Jet", true)), PetTreeLayout.FULL, false);
        check(pair.broods().stream().anyMatch(b -> b.dashed && b.kids.isEmpty()), "the pair view joins a pair without young by a dashed line");
        check(pair.broods().stream().noneMatch(b -> b.dashed && b.has("Biscuit") && b.has("Fuse")), "a pair with young gets no dashed line");

        List<PetTreeLayout.Item> mates = List.of(item("Gran", null, null), item("Gramps", null, null), item("Opa", null, null), item("Oma", null, null),
                item("Mom", "Gran", "Gramps"), item("Aunt", "Gran", "Gramps"), item("Dad", "Opa", "Oma"), item("Dadtwo", null, null), item("Uncle", null, null),
                item("Goneone", "Gran", "Gramps"), item("Gonetwo", "Goneone", "Uncle"),
                item("Kidone", "Mom", "Dad"), item("Kidtwo", "Mom", "Dad"), item("Kidthree", "Mom", "Dadtwo"), item("Cousin", "Aunt", "Uncle"));
        PetTreeLayout.Folded kin = PetTreeLayout.fold(mates, Set.of("Oma", "Goneone", "Gonetwo"), Set.of("Kidone"), Set.of(), false);
        for (PetTreeLayout.Metrics m : List.of(PetTreeLayout.FULL, PetTreeLayout.SMALL)) {
            Map<String, PetTreeLayout.Node> n = PetTreeLayout.layout(kin.items(), kin.pairs(), m, false).nodes();
            float mom = n.get("Mom").x;
            check((n.get("Dad").x - mom) * (n.get("Dadtwo").x - mom) < 0f, "a second mate stands on the other side of the first, got Dad " + n.get("Dad").x + ", Mom " + mom + ", Dadtwo " + n.get("Dadtwo").x);
        }

        Map<String, String[]> parents = new HashMap<>();
        for (PetTreeLayout.Item i : items) parents.put(i.id(), new String[]{i.a(), i.b()});
        Map<String, int[]> shared = PetGenetics.shared("Biscuit", "Jet", parents::get);
        check(shared.keySet().equals(Set.of("Jack", "Sable")) && shared.values().stream().allMatch(d -> d[0] == 2 && d[1] == 2), "cousins share their two grandparents and nothing above them");
        check(PetGenetics.shared("Biscuit", "Mote", parents::get).containsKey("Biscuit"), "a parent is the nearest shared ancestor of its young");
        check(PetGenetics.shared("Biscuit", "Nix", parents::get).isEmpty(), "two families share no ancestor");
    }

    // every pet one row under its younger parent, a lane through each row a pair spans, no two tiles closer than a tile and a gap
    private void checkRows(PetTreeLayout.Result result, List<PetTreeLayout.Item> items, PetTreeLayout.Metrics m) {
        Map<String, PetTreeLayout.Node> nodes = result.nodes();
        for (PetTreeLayout.Item i : items) {
            PetTreeLayout.Node n = nodes.get(i.id());
            int row = -1;
            if (nodes.containsKey(i.a())) row = Math.max(row, nodes.get(i.a()).row);
            if (nodes.containsKey(i.b())) row = Math.max(row, nodes.get(i.b()).row);
            if (row >= 0) check(n.row == row + 1, i.id() + " stands one row under its younger parent");
        }
        for (PetTreeLayout.Brood b : result.broods()) {
            if (b.b == null) continue;
            int span = Math.abs(nodes.get(b.a).row - nodes.get(b.b).row);
            check(b.lane.size() == span, b.a + " x " + b.b + " has a lane node in each of the " + span + " rows it spans");
        }

        Map<Integer, List<PetTreeLayout.Node>> rows = new HashMap<>();
        for (PetTreeLayout.Node n : nodes.values()) rows.computeIfAbsent(n.row, k -> new ArrayList<>()).add(n);
        for (List<PetTreeLayout.Node> row : rows.values()) {
            row.sort((x, y) -> Float.compare(x.x, y.x));
            for (int k = 1; k < row.size(); k++) {
                PetTreeLayout.Node a = row.get(k - 1);
                PetTreeLayout.Node b = row.get(k);
                float need = ((a.lane ? m.laneW() : a.w) + (b.lane ? m.laneW() : b.w)) / 2f + m.gap() - 1f;
                check(b.x - a.x >= need, a.id + " and " + b.id + " keep apart in row " + a.row + ": " + (b.x - a.x) + " of " + need);
            }
        }
    }

    private static PetTreeLayout.Item item(String id, String a, String b) {
        return new PetTreeLayout.Item(id, a, b);
    }

    private static Set<String> events() {
        Set<String> events = new HashSet<>(Set.of(EVENT_AMBIENT, EVENT_DOCKING, EVENT_STATUS, EVENT_PETTING, EVENT_PETTING_STARVING, EVENT_NAME, EVENT_PLAY, EVENT_TREAT, EVENT_SCAVENGER, EVENT_JUMP, EVENT_FIGHT));
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
