package indevo.industries.petshop.memory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// no Global here, PetChecks builds sheets without the game
public class PetSheet {

    public static final String FROM_A = "a";
    public static final String FROM_B = "b";
    public static final String FROM_SUM = "sum";
    public static final String FROM_MUTATION = "mutation";
    public static final String FROM_MALFORMED = "malformed";
    // key of the look in from
    public static final String LOOK = "look";

    // look id from pet_morphs.csv, null for Wild type; always known
    public String look = null;
    // officer personality id
    public String disposition;
    // trait id to grade or level, in the order the traits arrived
    public final Map<String, Integer> traits = new LinkedHashMap<>();
    public final Set<String> known = new HashSet<>();
    // flaws are read only by a gene scan; mutations are always known
    public boolean flawsRead = false;
    // a young's trait id to where it came from (FROM_ constants); null for a bought, found or offered pet
    public Map<String, String> from = null;
    public boolean malformed = false;

    public PetSheet(String disposition) {
        this.disposition = disposition;
    }

    public int getGrade(String traitId) {
        return traits.getOrDefault(traitId, 0);
    }

    public boolean isKnown(String traitId) {
        return known.contains(traitId);
    }

    public String getFrom(String traitId) {
        return from != null ? from.get(traitId) : null;
    }
}
