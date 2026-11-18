package indevo.industries.petshop.memory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// no Global here, PetChecks builds sheets without the game
public class PetSheet {

    // officer personality id
    public String disposition;
    // trait id to grade or level, in the order the traits arrived
    public final Map<String, Integer> traits = new LinkedHashMap<>();
    public final Set<String> known = new HashSet<>();
    // flaws are read only by a gene scan; mutations are always known
    public boolean flawsRead = false;

    public PetSheet(String disposition) {
        this.disposition = disposition;
    }

    public int getGrade(String traitId) {
        return traits.getOrDefault(traitId, 0);
    }

    public boolean isKnown(String traitId) {
        return known.contains(traitId);
    }
}
