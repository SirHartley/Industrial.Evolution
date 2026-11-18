package indevo.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
    private static final Set<String> EVENTS = Set.of(EVENT_AMBIENT, EVENT_DOCKING, EVENT_STATUS);
    private static final Set<String> CONTEXTS = Set.of(CONTEXT_DOCKED, CONTEXT_STORAGE, CONTEXT_OLD, CONTEXT_HYPERSPACE);
    private static final Set<String> DISPOSITIONS = Set.of(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS);
    private static final Pattern BODY_WORDS = Pattern.compile("\\b(legs?|paws?|hands?|fur|wings?|feathers?|beak|claws?|tails?|fins?|glass|tank|swims?|fl(y|ies|ew|ying)|sniff\\w*)\\b", Pattern.CASE_INSENSITIVE);
    private static final int PICKS = 40000;

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
        List<Line> lines = run.checkLines(run.read(Path.of(LINES_PATH)), pets);
        run.checkCoverage(lines, pets);
        run.checkWeights(pets);

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
        }

        return pets;
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
