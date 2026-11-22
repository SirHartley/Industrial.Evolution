package indevo.industries.petshop.memory;

import com.fs.starfarer.api.impl.BaseSharedJSONFile;

import java.util.Collection;
import java.util.Set;

// species and look ids seen, Pets panel tabs unlocked and tab help seen in any game, in the common folder; never referenced from a saved object
public class PetBestiaryFile extends BaseSharedJSONFile {

    public static final String FILE = "IndEvo_pet_bestiary.json";
    static final String SPECIES = "species";
    static final String LOOKS = "looks";
    static final String UNLOCKS = "unlocks";
    static final String HELP = "help";

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

    public boolean isUnlocked(String id) {
        return doesSetContain(UNLOCKS, id);
    }

    // true when it was locked
    public boolean unlock(String id) {
        if (!addToSet(UNLOCKS, id)) return false;
        saveIfNeeded();
        return true;
    }

    // true the first time; the ? of that tab pulses once
    public boolean seeHelp(String tab) {
        if (!addToSet(HELP, tab)) return false;
        saveIfNeeded();
        return true;
    }

    // locks the tabs and lets every ? pulse again
    public void lockAll() {
        clear(UNLOCKS);
        clear(HELP);
        saveIfNeeded();
    }

    // unset leaves the set cache alone
    private void clear(String key) {
        unset(key);
        setCache.remove(key);
    }
}
