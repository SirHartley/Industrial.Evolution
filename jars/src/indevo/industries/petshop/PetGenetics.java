package indevo.industries.petshop;

import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetDataRepo.Trait;
import indevo.industries.petshop.memory.PetSheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// the trait math; no Global, PetChecks runs it on the csv files
public final class PetGenetics {

    public static final List<String> DISPOSITIONS = List.of(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS);
    public static final String FECUND = PetDataRepo.TRAIT_PREFIX + "fecund";
    public static final String WEAK_BROOD = PetDataRepo.TRAIT_PREFIX + "weak_brood";
    public static final String UNSTABLE = PetDataRepo.TRAIT_PREFIX + "unstable";

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
        float sum = sumChance(data, hearts);

        Set<String> ids = new LinkedHashSet<>(a.traits.keySet());
        ids.addAll(b.traits.keySet());
        for (String id : ids) {
            Trait trait = traits.get(id);
            if (trait == null) continue;

            int level = inherit(trait.isFlaw(), a.getGrade(id), b.getGrade(id), care, sum, random);
            if (level > 0) young.traits.put(id, trait.hasLevels() ? level : 1);
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

    // 1.5 x inbreeding, nothing under 5%; every hamster young has its own chance on top
    public static float malformedChance(PetData data, float inbreeding) {
        float chance = inbreeding * PetConstants.MALFORMED_PER_INBREEDING;
        if (chance < PetConstants.MALFORMED_MIN) chance = 0f;
        if (data.isHamster()) chance = 1f - (1f - chance) * (1f - PetConstants.HAMSTER_MALFORMED);
        return Math.min(1f, chance);
    }

    // 1 to 3 young, 1 for a rare species, more with Fecund; each one lost at birth at the Weak brood chance of both parents
    public static int broodSize(PetData data, PetSheet a, PetSheet b, Map<String, Trait> traits, Random random) {
        int size = data.isRare() ? 1 : 1 + random.nextInt(PetConstants.MAX_BROOD);
        size += Math.round(value(traits, FECUND, a.getGrade(FECUND) + b.getGrade(FECUND)));
        float lost = value(traits, WEAK_BROOD, a.getGrade(WEAK_BROOD) + b.getGrade(WEAK_BROOD)) * 0.01f;

        int born = 0;
        for (int i = 0; i < size; i++) if (random.nextFloat() >= lost) born++;
        return born;
    }

    // a mutation with its flaws at level 1, or a talent at grade 1; never one the young has
    private static void addNewTrait(PetSheet young, PetData data, Map<String, Trait> traits, Random random) {
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
        for (String flaw : pick.with()) if (traits.containsKey(flaw)) young.traits.merge(flaw, 1, Math::max);
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
