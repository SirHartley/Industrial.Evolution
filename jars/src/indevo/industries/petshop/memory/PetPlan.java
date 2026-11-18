package indevo.industries.petshop.memory;

import indevo.industries.petshop.PetGenetics;

import java.util.LinkedHashMap;
import java.util.Map;

// a planned young: what the player wants from it and the two places for its parents, each a pet id, a plan id or null
public class PetPlan {

    public static final String ID_PREFIX = "plan_";

    public final String id;
    public final String typeID;
    public String name;
    public String parentA = null;
    public String parentB = null;
    // look id, PetGenetics.NONE for Wild type, null for any
    public String look = null;
    // trait id or PetGenetics.MALFORMED to the grade it needs at least, for a flaw the level it may have at most, 0 for none
    public final Map<String, Integer> wanted = new LinkedHashMap<>();

    public PetPlan(String id, String typeID, String name) {
        this.id = id;
        this.typeID = typeID;
        this.name = name;
    }

    public static boolean isPlan(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    public PetData getData() {
        return PetDataRepo.get(typeID);
    }

    public boolean hasParents() {
        return parentA != null && parentB != null;
    }

    public String getParent(int place) {
        return place == 0 ? parentA : parentB;
    }

    public void setParent(int place, String id) {
        if (place == 0) parentA = id;
        else parentB = id;
    }

    // what it counts as when it stands in a parent place: exactly its target, every trait read
    public PetSheet sheet() {
        PetSheet sheet = new PetSheet(PetGenetics.DISPOSITIONS.get(0));
        // any look counts as Wild type
        if (look != null && !look.equals(PetGenetics.NONE)) sheet.look = look;
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            if (e.getValue() > 0 && !e.getKey().equals(PetGenetics.MALFORMED)) sheet.traits.put(e.getKey(), e.getValue());
        }
        sheet.known.addAll(sheet.traits.keySet());
        sheet.flawsRead = true;
        return sheet;
    }
}
