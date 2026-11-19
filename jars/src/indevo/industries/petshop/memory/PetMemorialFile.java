package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.BaseSharedJSONFile;
import indevo.industries.petshop.PetTitle;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// favourites that died, in the common folder so every game remembers them; never referenced from a saved object
public class PetMemorialFile extends BaseSharedJSONFile {

    public static final String FILE = "IndEvo_pet_memorial.json";
    static final String PETS = "pets";

    private static final Logger log = Global.getLogger(PetMemorialFile.class);
    private static PetMemorialFile instance;

    private List<Lost> pets = null;

    // icon is the portrait of the species or look, null when the species is gone with its mod
    public record Lost(String id, String name, String species, String icon, String age, int hearts, List<String> titles) {
    }

    public static PetMemorialFile get() {
        if (instance == null) {
            instance = new PetMemorialFile();
            instance.loadIfNeeded();
        }
        return instance;
    }

    @Override
    protected String getFilename() {
        return FILE;
    }

    // oldest first, as written; species no loaded mod has are left out
    public List<Lost> getPets() {
        if (pets == null) pets = read();
        return pets;
    }

    private List<Lost> read() {
        List<Lost> out = new ArrayList<>();
        JSONArray array = json.optJSONArray(PETS);
        if (array == null) return out;

        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;

            PetData data = PetDataRepo.get(o.optString("species", ""));
            if (data == null) continue;

            PetDataRepo.Look look = data.getLook(o.optString("look", null));
            List<String> titles = new ArrayList<>();
            JSONArray t = o.optJSONArray("titles");
            for (int j = 0; t != null && j < t.length(); j++) titles.add(t.optString(j));
            out.add(new Lost(o.optString("id"), o.optString("name"), data.species, look != null ? look.icon() : data.icon, o.optString("age"), o.optInt("hearts"), titles));
        }
        return out;
    }

    public boolean contains(String id) {
        for (Lost lost : getPets()) if (lost.id().equals(id)) return true;
        return false;
    }

    // the age as text, a pet from another game has no clock to count with
    public void remember(Pet pet) {
        if (contains(pet.id)) return;

        try {
            JSONObject o = new JSONObject();
            o.put("id", pet.id);
            o.put("name", pet.name);
            o.put("species", pet.typeID);
            PetDataRepo.Look look = pet.getLook();
            if (look != null) o.put("look", look.id());
            o.put("age", pet.getSpan());
            o.put("hearts", pet.getHearts());
            JSONArray titles = new JSONArray();
            for (PetTitle title : pet.getTitles()) titles.put(title.label);
            o.put("titles", titles);

            JSONArray array = json.optJSONArray(PETS);
            if (array == null) {
                array = new JSONArray();
                json.put(PETS, array);
            }
            array.put(o);
        } catch (JSONException e) {
            log.warn("Could not remember " + pet.name + " in " + FILE, e);
            return;
        }
        pets = null;
        saveIfNeeded();
    }
}
