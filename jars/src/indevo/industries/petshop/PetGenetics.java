package indevo.industries.petshop;

import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetDataRepo.Trait;
import indevo.industries.petshop.memory.PetSheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

// the trait math; no Global, PetChecks runs it on the csv files
public final class PetGenetics {

    public static final List<String> DISPOSITIONS = List.of(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS);
    public static final String FECUND = PetDataRepo.TRAIT_PREFIX + "fecund";
    public static final String WEAK_BROOD = PetDataRepo.TRAIT_PREFIX + "weak_brood";
    public static final String UNSTABLE = PetDataRepo.TRAIT_PREFIX + "unstable";
    public static final String GIANT = PetDataRepo.TRAIT_PREFIX + "giant";
    public static final String SAVANT = PetDataRepo.TRAIT_PREFIX + "savant";
    public static final String SPLIT_MIND = PetDataRepo.TRAIT_PREFIX + "split_mind";
    public static final String LONG_LIVED = PetDataRepo.TRAIT_PREFIX + "long_lived";
    public static final String SHORT_LIVED = PetDataRepo.TRAIT_PREFIX + "short_lived";
    public static final String GLUTTON = PetDataRepo.TRAIT_PREFIX + "glutton";
    public static final String SULLEN = PetDataRepo.TRAIT_PREFIX + "sullen";
    public static final String RUNT = PetDataRepo.TRAIT_PREFIX + "runt";
    public static final String SLOW_GROWER = PetDataRepo.TRAIT_PREFIX + "slow_grower";
    public static final String MAYFLY = PetDataRepo.TRAIT_PREFIX + "mayfly";
    public static final String STERILE = PetDataRepo.TRAIT_PREFIX + "sterile";
    public static final String NONE = "none";
    public static final String LUMINOUS = PetDataRepo.TRAIT_PREFIX + "luminous";
    // the wanted row and cross row for a malformed young
    public static final String MALFORMED = "malformed";

    private PetGenetics() {
    }

    // bought, found and offered pets: a disposition, 1 or 2 talents at grade 1 by the species lean, a rare species maybe a signature
    public static PetSheet rollSheet(PetData data, Map<String, Trait> traits, Random random) {
        PetSheet sheet = new PetSheet(DISPOSITIONS.get(random.nextInt(DISPOSITIONS.size())));

        List<Trait> pool = new ArrayList<>();
        List<Trait> signatures = new ArrayList<>();
        for (Trait trait : traits.values()) {
            // a talent with nothing at grade 1 (Pathfinder) only comes from raising
            if (!trait.isTalent() || trait.weight() <= 0f || trait.value(1, 1f) <= 0f) continue;
            (trait.isSignature() ? signatures : pool).add(trait);
        }

        int count = random.nextFloat() < PetConstants.TWO_TALENTS_CHANCE ? 2 : 1;
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Trait pick = pick(pool, data, random);
            pool.remove(pick);
            sheet.traits.put(pick.id(), 1);
        }

        if (data.isRare() && !signatures.isEmpty() && random.nextFloat() < PetConstants.SIGNATURE_CHANCE) {
            sheet.traits.put(pick(signatures, data, random).id(), 1);
        }

        return sheet;
    }

    // talents best grade first, ties in the order they arrived; ids no loaded mod has are left out
    public static List<String> talents(PetSheet sheet, Map<String, Trait> traits) {
        List<String> list = new ArrayList<>();
        for (String id : sheet.traits.keySet()) {
            Trait trait = traits.get(id);
            if (trait != null && trait.isTalent()) list.add(id);
        }

        list.sort(Comparator.comparingInt((String id) -> -sheet.getGrade(id)));
        return list;
    }

    // one roll per trait either parent has, a new trait at the mutation rate, malformed at the inbreeding chance
    public static PetSheet rollYoung(PetData data, PetSheet a, PetSheet b, float care, int hearts, float mutationRate, float inbreeding,
                                     Map<String, Trait> traits, Random random) {
        PetSheet young = new PetSheet(random.nextBoolean() ? a.disposition : b.disposition);
        young.from = new HashMap<>();
        float sum = sumChance(data, hearts);

        boolean lookA = random.nextBoolean();
        young.look = lookA ? a.look : b.look;
        if (young.look != null) young.from.put(PetSheet.LOOK, lookA ? PetSheet.FROM_A : PetSheet.FROM_B);

        Set<String> ids = new LinkedHashSet<>(a.traits.keySet());
        ids.addAll(b.traits.keySet());
        for (String id : ids) {
            Trait trait = traits.get(id);
            if (trait == null) continue;

            int la = a.getGrade(id);
            int lb = b.getGrade(id);
            int level = inherit(trait.isFlaw(), la, lb, care, sum, random);
            if (level <= 0) continue;

            young.traits.put(id, trait.hasLevels() ? level : 1);
            young.from.put(id, la > 0 && lb > 0 && level == la + lb ? PetSheet.FROM_SUM : level == la ? PetSheet.FROM_A : PetSheet.FROM_B);
        }

        if (random.nextFloat() < mutationRate) addNewTrait(young, data, traits, random);
        if (random.nextFloat() < malformedChance(data, inbreeding)) malform(young, traits, random);
        return young;
    }

    // a trait both have sums at the sum chance, else Care tilts to the better parent; a flaw one parent has drops only at Care / (200 + Care)
    public static int inherit(boolean flaw, int a, int b, float care, float sum, Random random) {
        if (a > 0 && b > 0) {
            if (random.nextFloat() < sum) return a + b;

            boolean better = random.nextFloat() < betterChance(care);
            return flaw == better ? Math.min(a, b) : Math.max(a, b);
        }

        int level = Math.max(a, b);
        if (flaw) return random.nextFloat() < dropChance(care) ? 0 : level;
        return random.nextFloat() < betterChance(care) ? level : 0;
    }

    // one result of a trait in the cross: where it comes from (PetSheet.FROM_ constants or NONE), its level, its chance
    public record Outcome(String lane, int level, float chance) {
    }

    // the chances of inherit, a parent without the trait is 0; equal levels of both split their share between the two lanes
    public static List<Outcome> outcomes(boolean flaw, boolean levels, int a, int b, float care, float sum) {
        List<Outcome> list = new ArrayList<>();
        float better = betterChance(care);

        if (a > 0 && b > 0) {
            if (!levels) {
                list.add(new Outcome(PetSheet.FROM_SUM, 1, 1f));
                return list;
            }

            float rest = 1f - sum;
            int high = Math.max(a, b);
            int low = Math.min(a, b);
            float highChance = rest * (flaw ? 1f - better : better);
            float lowChance = rest - highChance;
            if (a == b) {
                list.add(new Outcome(PetSheet.FROM_A, a, rest / 2f));
                list.add(new Outcome(PetSheet.FROM_SUM, a + b, sum));
                list.add(new Outcome(PetSheet.FROM_B, b, rest / 2f));
            } else {
                list.add(new Outcome(PetSheet.FROM_A, a, a == high ? highChance : lowChance));
                list.add(new Outcome(PetSheet.FROM_SUM, a + b, sum));
                list.add(new Outcome(PetSheet.FROM_B, b, b == high ? highChance : lowChance));
            }
            return list;
        }

        String lane = a > 0 ? PetSheet.FROM_A : PetSheet.FROM_B;
        int level = levels ? Math.max(a, b) : 1;
        float kept = flaw ? 1f - dropChance(care) : better;
        if (lane.equals(PetSheet.FROM_A)) {
            list.add(new Outcome(lane, level, kept));
            list.add(new Outcome(NONE, 0, 1f - kept));
        } else {
            list.add(new Outcome(NONE, 0, 1f - kept));
            list.add(new Outcome(lane, level, kept));
        }
        return list;
    }

    public static float betterChance(float care) {
        return (100f + care) / (200f + care);
    }

    public static float dropChance(float care) {
        return care / (200f + care);
    }

    // hearts of both parents together
    public static float sumChance(PetData data, int hearts) {
        return PetConstants.SUM_CHANCE[paceIndex(data)] + hearts * PetConstants.SUM_CHANCE_PER_HEART;
    }

    // per young: by pace, x2 on an irradiated world, x1.5 with an Alpha core, then the Unstable points of both parents
    public static float mutationRate(PetData data, PetSheet a, PetSheet b, boolean irradiated, boolean alpha, Map<String, Trait> traits) {
        float rate = PetConstants.MUTATION_RATE[paceIndex(data)];
        if (irradiated) rate *= PetConstants.IRRADIATED_MUTATION_MULT;
        if (alpha) rate *= PetConstants.ALPHA_MUTATION_MULT;
        return rate + value(traits, UNSTABLE, a.getGrade(UNSTABLE) + b.getGrade(UNSTABLE)) * 0.01f;
    }

    // sale value from what the buyer can see: species value, x2 with a look, x (1 + read talent grades + mutation levels),
    // x1.25 with a titled ancestor, x0.9 a read flaw level, Luminous on top; malformed x0.1
    public static float saleValue(PetData data, PetSheet seen, boolean titledAncestor, Map<String, Trait> traits) {
        float points = 1f;
        int flaws = 0;
        for (Map.Entry<String, Integer> e : seen.traits.entrySet()) {
            Trait trait = traits.get(e.getKey());
            if (trait == null) continue;
            if (trait.isFlaw()) flaws += e.getValue();
            else points += e.getValue();
        }

        float value = data.value * points * (float) Math.pow(PetConstants.SALE_FLAW_MULT, flaws);
        if (seen.look != null) value *= PetConstants.SALE_LOOK_MULT;
        if (titledAncestor) value *= PetConstants.SALE_TITLED_MULT;
        value *= 1f + value(traits, LUMINOUS, seen.getGrade(LUMINOUS)) * 0.01f;
        if (seen.malformed) value *= PetConstants.SALE_MALFORMED_MULT;
        return value;
    }

    // 1.5 x inbreeding, nothing under 5%; every hamster young has its own chance on top
    public static float malformedChance(PetData data, float inbreeding) {
        float chance = inbreeding * PetConstants.MALFORMED_PER_INBREEDING;
        if (chance < PetConstants.MALFORMED_MIN) chance = 0f;
        if (data.isHamster()) chance = 1f - (1f - chance) * (1f - PetConstants.HAMSTER_MALFORMED);
        return Math.min(1f, chance);
    }

    // 1 to 3 young, 1 for a rare species, more with Fecund; each one lost at birth at the Weak brood chance of both parents; none with a Sterile parent
    public static int broodSize(PetData data, PetSheet a, PetSheet b, Map<String, Trait> traits, Random random) {
        if (a.getGrade(STERILE) > 0 || b.getGrade(STERILE) > 0) return 0;

        int size = data.isRare() ? 1 : 1 + random.nextInt(PetConstants.MAX_BROOD);
        size += Math.round(value(traits, FECUND, a.getGrade(FECUND) + b.getGrade(FECUND)));
        float lost = value(traits, WEAK_BROOD, a.getGrade(WEAK_BROOD) + b.getGrade(WEAK_BROOD)) * 0.01f;

        int born = 0;
        for (int i = 0; i < size; i++) if (random.nextFloat() >= lost) born++;
        return born;
    }

    // lowest and highest brood size; 0 at the bottom when a young can be lost at birth
    public static int[] broodRange(PetData data, PetSheet a, PetSheet b, Map<String, Trait> traits) {
        if (a.getGrade(STERILE) > 0 || b.getGrade(STERILE) > 0) return new int[]{0, 0};

        int fecund = Math.round(value(traits, FECUND, a.getGrade(FECUND) + b.getGrade(FECUND)));
        boolean weak = a.getGrade(WEAK_BROOD) + b.getGrade(WEAK_BROOD) > 0;
        int hi = (data.isRare() ? 1 : PetConstants.MAX_BROOD) + fecund;
        return new int[]{weak ? 0 : 1 + fecund, hi};
    }

    // the sheet as the player knows it, for previews: traits not read yet are left out
    public static PetSheet seen(PetSheet sheet) {
        PetSheet seen = new PetSheet(sheet.disposition);
        seen.look = sheet.look;
        seen.malformed = sheet.malformed;
        for (Map.Entry<String, Integer> e : sheet.traits.entrySet()) if (sheet.isKnown(e.getKey())) seen.traits.put(e.getKey(), e.getValue());
        seen.known.addAll(seen.traits.keySet());
        return seen;
    }

    // a new look, a mutation with its flaws at level 1, or a talent at grade 1; never one the young has
    private static void addNewTrait(PetSheet young, PetData data, Map<String, Trait> traits, Random random) {
        List<PetDataRepo.Look> looks = new ArrayList<>();
        for (PetDataRepo.Look look : data.looks) if (!look.id().equals(young.look) && look.weight() > 0f) looks.add(look);
        if (!looks.isEmpty() && random.nextFloat() < PetConstants.NEW_LOOK_CHANCE) {
            young.look = pickLook(looks, random).id();
            young.from.put(PetSheet.LOOK, PetSheet.FROM_MUTATION);
            return;
        }

        boolean mutation = random.nextFloat() < PetConstants.NEW_MUTATION_CHANCE;
        boolean signature = random.nextFloat() < PetConstants.NEW_SIGNATURE_CHANCE;

        List<Trait> pool = new ArrayList<>();
        for (Trait trait : traits.values()) {
            if (young.traits.containsKey(trait.id()) || trait.weight() <= 0f) continue;
            if (mutation ? trait.isMutation() : trait.isTalent() && trait.isSignature() == signature) pool.add(trait);
        }
        if (pool.isEmpty()) return;

        Trait pick = pick(pool, data, random);
        young.traits.put(pick.id(), 1);
        young.from.put(pick.id(), PetSheet.FROM_MUTATION);
        for (String flaw : pick.with()) {
            if (!traits.containsKey(flaw)) continue;
            if (young.traits.putIfAbsent(flaw, 1) == null) young.from.put(flaw, PetSheet.FROM_MUTATION);
        }
    }

    // a new inbreeding flaw at level 1, or one more level on a flaw it has
    private static void malform(PetSheet young, Map<String, Trait> traits, Random random) {
        List<Trait> pool = new ArrayList<>();
        for (Trait trait : traits.values()) {
            if (trait.isFlaw() && (PetDataRepo.FROM_INBREEDING.equals(trait.from()) || young.traits.containsKey(trait.id()))) pool.add(trait);
        }
        if (pool.isEmpty()) return;

        Trait pick = pool.get(random.nextInt(pool.size()));
        young.traits.merge(pick.id(), 1, (old, one) -> pick.hasLevels() ? old + one : old);
        young.from.put(pick.id(), PetSheet.FROM_MALFORMED);
        young.malformed = true;
    }

    // 30, 60 or 90 days by pace, the hamster 7; Slow grower of both parents adds, their Mayfly cuts; never past MAX_BROOD_DAYS
    public static float broodDays(PetData data, PetSheet a, PetSheet b, Map<String, Trait> traits) {
        float days = data.isHamster() ? PetConstants.HAMSTER_BROOD_DAYS : PetConstants.BROOD_DAYS[paceIndex(data)];
        days *= 1f + value(traits, SLOW_GROWER, a.getGrade(SLOW_GROWER) + b.getGrade(SLOW_GROWER)) * 0.01f;
        days *= 1f - value(traits, MAYFLY, a.getGrade(MAYFLY) + b.getGrade(MAYFLY)) * 0.01f;
        return Math.min(PetConstants.MAX_BROOD_DAYS, days);
    }

    // chance a young of a and b meets one wanted row: a talent or mutation at that grade or better, a flaw at that level or less;
    // read traits only, so a trait neither parent has never comes and a flaw neither has never does; malformed flaws are left out
    public static float wantedChance(Trait trait, int want, int a, int b, float care, float sum) {
        if (a <= 0 && b <= 0) return trait.isFlaw() ? 1f : 0f;

        float chance = 0f;
        for (Outcome o : outcomes(trait.isFlaw(), trait.hasLevels(), a, b, care, sum)) {
            if (trait.isFlaw() ? o.level() <= want : o.level() >= want) chance += o.chance();
        }
        return Math.min(1f, chance);
    }

    // per wanted row, in its order; the rows are rolled apart, so a young meets all of them at the product
    public static Map<String, Float> wantedChances(PetData data, PetSheet a, PetSheet b, int hearts, float care, float inbreeding,
                                                   Map<String, Integer> wanted, Map<String, Trait> traits) {
        Map<String, Float> chances = new LinkedHashMap<>();
        float sum = sumChance(data, hearts);
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            if (e.getKey().equals(MALFORMED)) {
                chances.put(MALFORMED, 1f - malformedChance(data, inbreeding));
                continue;
            }
            Trait trait = traits.get(e.getKey());
            if (trait != null) chances.put(e.getKey(), wantedChance(trait, e.getValue(), a.getGrade(e.getKey()), b.getGrade(e.getKey()), care, sum));
        }
        return chances;
    }

    public static float product(Map<String, Float> chances) {
        float p = 1f;
        for (float c : chances.values()) p *= c;
        return p;
    }

    // broods until one young meets the target, on the average brood size; -1 when it never does
    public static int broods(float chance, int[] range) {
        float young = (range[0] + range[1]) / 2f;
        if (chance <= 0f || young <= 0f) return -1;
        return Math.max(1, Math.round(1f / (chance * young)));
    }

    public static float youngDays(PetData data, PetSheet sheet, Map<String, Trait> traits) {
        float days = data.isHamster() ? PetConstants.HAMSTER_YOUNG_DAYS : PetConstants.YOUNG_DAYS;
        return days * (1f + bodyValue(sheet, SLOW_GROWER, traits) * 0.01f) * (1f - bodyValue(sheet, MAYFLY, traits) * 0.01f);
    }

    // sum of (1/2)^steps over every path from a up to a shared ancestor and down to b that meets only there, INBREEDING_GENERATIONS up;
    // parents gives a pet's two parent ids (either null), or null for an id it does not know
    public static float inbreeding(String a, String b, Function<String, String[]> parents) {
        Map<String, List<List<String>>> up = new HashMap<>();
        ancestors(a, new ArrayList<>(), parents, up);
        Map<String, List<List<String>>> down = new HashMap<>();
        ancestors(b, new ArrayList<>(), parents, down);

        float value = 0f;
        for (Map.Entry<String, List<List<String>>> entry : up.entrySet()) {
            List<List<String>> other = down.get(entry.getKey());
            if (other == null) continue;

            for (List<String> pa : entry.getValue()) {
                for (List<String> pb : other) {
                    if (meetOnlyAtTop(pa, pb)) value += (float) Math.pow(0.5, pa.size() - 1 + pb.size() - 1);
                }
            }
        }
        return value;
    }

    // the ancestors a path up from a and a path up from b meet at and nowhere else, with the generations up from each on the
    // shortest such pair, nearest first; a or b itself is one when it is an ancestor of the other
    public static Map<String, int[]> shared(String a, String b, Function<String, String[]> parents) {
        Map<String, List<List<String>>> up = new HashMap<>();
        ancestors(a, new ArrayList<>(), parents, up);
        Map<String, List<List<String>>> down = new HashMap<>();
        ancestors(b, new ArrayList<>(), parents, down);

        Map<String, int[]> shared = new HashMap<>();
        for (Map.Entry<String, List<List<String>>> entry : up.entrySet()) {
            List<List<String>> other = down.get(entry.getKey());
            if (other == null) continue;

            for (List<String> pa : entry.getValue()) {
                for (List<String> pb : other) {
                    if (!meetOnlyAtTop(pa, pb)) continue;
                    int[] d = shared.get(entry.getKey());
                    if (d == null || pa.size() + pb.size() - 2 < d[0] + d[1]) shared.put(entry.getKey(), new int[]{pa.size() - 1, pb.size() - 1});
                }
            }
        }

        Map<String, int[]> sorted = new LinkedHashMap<>();
        shared.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0] + e.getValue()[1]).thenComparing(Map.Entry::getKey))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    // every path from the pet up to each ancestor, the pet itself included at generation 0
    private static void ancestors(String id, List<String> path, Function<String, String[]> parents, Map<String, List<List<String>>> out) {
        if (id == null || path.contains(id)) return;

        List<String> here = new ArrayList<>(path);
        here.add(id);
        out.computeIfAbsent(id, k -> new ArrayList<>()).add(here);
        if (here.size() > PetConstants.INBREEDING_GENERATIONS) return;

        String[] pair = parents.apply(id);
        if (pair == null) return;
        for (String parent : pair) ancestors(parent, here, parents, out);
    }

    private static boolean meetOnlyAtTop(List<String> pa, List<String> pb) {
        for (int i = 0; i < pa.size() - 1; i++) if (pb.contains(pa.get(i))) return false;
        return true;
    }

    // the life a body gets: Long-lived raises it, Short lived divides it, each flaw level costs a hamster HAMSTER_FLAW_DAYS; no floor
    public static float lifetime(PetData data, PetSheet sheet, float rolled, Map<String, Trait> traits) {
        float life = rolled * (1f + bodyValue(sheet, LONG_LIVED, traits) * 0.01f) / (1f + bodyValue(sheet, SHORT_LIVED, traits));
        return data.isHamster() ? life - PetConstants.HAMSTER_FLAW_DAYS * flawLevels(sheet, traits) : life;
    }

    public static int flawLevels(PetSheet sheet, Map<String, Trait> traits) {
        int levels = 0;
        for (Map.Entry<String, Integer> entry : sheet.traits.entrySet()) {
            Trait trait = traits.get(entry.getKey());
            if (trait != null && trait.isFlaw()) levels += entry.getValue();
        }
        return levels;
    }

    // mascot effect (boost Giant) or talents (boost Savant); Runt divides both, never to zero
    public static float bodyMult(PetSheet sheet, String boost, Map<String, Trait> traits) {
        return (1f + bodyValue(sheet, boost, traits) * 0.01f) / (1f + bodyValue(sheet, RUNT, traits));
    }

    public static float heartMult(PetSheet sheet, Map<String, Trait> traits) {
        return 1f / (1f + bodyValue(sheet, SULLEN, traits) * 0.01f);
    }

    public static float foodMult(PetSheet sheet, Map<String, Trait> traits) {
        return 1f + bodyValue(sheet, GLUTTON, traits) * 0.01f;
    }

    // a mutation or flaw at its level, never scaled by hearts, nest or the hamster
    public static float bodyValue(PetSheet sheet, String id, Map<String, Trait> traits) {
        return value(traits, id, sheet.getGrade(id));
    }

    private static float value(Map<String, Trait> traits, String id, int level) {
        Trait trait = traits.get(id);
        return trait == null || level <= 0 ? 0f : trait.value(level, 1f);
    }

    private static int paceIndex(PetData data) {
        if (PetData.PACE_SLOW.equals(data.pace)) return 2;
        if (PetData.PACE_MEDIUM.equals(data.pace)) return 1;
        return 0;
    }

    // how many of n talents are on at these hearts
    public static int activeCount(int n, int hearts) {
        return hearts >= PetConstants.MAX_HEARTS ? n : Math.min(n, hearts);
    }

    private static PetDataRepo.Look pickLook(List<PetDataRepo.Look> pool, Random random) {
        float total = 0f;
        for (PetDataRepo.Look look : pool) total += look.weight();

        float roll = random.nextFloat() * total;
        for (PetDataRepo.Look look : pool) {
            roll -= look.weight();
            if (roll < 0f) return look;
        }
        return pool.get(pool.size() - 1);
    }

    // the young has the look of one parent at random; a new look is left out, as new traits are in wantedChance
    public static float lookChance(String wanted, String a, String b) {
        return (Objects.equals(wanted, a) ? 0.5f : 0f) + (Objects.equals(wanted, b) ? 0.5f : 0f);
    }

    private static Trait pick(List<Trait> pool, PetData data, Random random) {
        float total = 0f;
        for (Trait trait : pool) total += trait.weight() * data.getTalentWeight(trait.id());

        float roll = random.nextFloat() * total;
        for (Trait trait : pool) {
            roll -= trait.weight() * data.getTalentWeight(trait.id());
            if (roll < 0f) return trait;
        }

        return pool.get(pool.size() - 1);
    }
}
