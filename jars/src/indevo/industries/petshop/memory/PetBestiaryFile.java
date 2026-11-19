package indevo.industries.petshop.memory;

import com.fs.starfarer.api.impl.BaseSharedJSONFile;

import java.util.Collection;
import java.util.Set;

// species and look ids seen in any game, in the common folder; never referenced from a saved object
public class PetBestiaryFile extends BaseSharedJSONFile {

    public static final String FILE = "IndEvo_pet_bestiary.json";
    static final String SPECIES = "species";
    static final String LOOKS = "looks";

    private static PetBestiaryFile instance;

    public static PetBestiaryFile get() {
        if (instance == null) {
            instance = new PetBestiaryFile();
            instance.loadIfNeeded();
        }
        return instance;
    }

    @Override
    protected String getFilename() {
        return FILE;
    }

    public Set<String> getSpecies() {
        return getSet(SPECIES);
    }

    public Set<String> getLooks() {
        return getSet(LOOKS);
    }

    // written only when something is new
    public void see(Collection<String> species, Collection<String> looks) {
        boolean changed = false;
        for (String id : species) changed |= addToSet(SPECIES, id);
        for (String id : looks) changed |= addToSet(LOOKS, id);
        if (changed) saveIfNeeded();
    }
}
