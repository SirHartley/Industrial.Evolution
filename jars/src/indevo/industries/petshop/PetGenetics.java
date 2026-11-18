package indevo.industries.petshop;

import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo.Trait;
import indevo.industries.petshop.memory.PetSheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

// the trait math; no Global, PetChecks runs it on the csv files
public final class PetGenetics {

    public static final List<String> DISPOSITIONS = List.of(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS);

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
