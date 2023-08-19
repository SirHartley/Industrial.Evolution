package indevo.industries.petshop.memory;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.awt.*;
import java.util.List;

public class PetData {
    public static final String TAG_NO_SELL = "no_sell";
    public static final String TAG_NO_DROP = "no_drop";
    public static final String TAG_SPECIES_PREFIX = "c_";

    public String id;
    public String species;
    public float value;
    public float maxLife;
    public List<String> tags;
    public String desc;
    public String naturalDeath;
    public String icon;
    public String hullmod;
    public List<String> foodCommodities;
    public float foodPerMonth;
    public float rarity;

    public PetData(String id, String species, float value, float maxLife, List<String> tags, String desc, String naturalDeath, String icon, String hullmod, List<String> foodCommodities, float foodPerMonth, float rarity) {
        this.id = id;
        this.species = species;
        this.value = value;
        this.maxLife = maxLife;
        this.tags = tags;
        this.desc = desc;
        this.naturalDeath = naturalDeath;
        this.icon = icon;
        this.hullmod = hullmod;
        this.foodCommodities = foodCommodities;
        this.foodPerMonth = foodPerMonth;
        this.rarity = rarity;
    }

    public boolean isNoSell() {
        return tags.contains(TAG_NO_SELL);
    }

    public boolean isNoDrop() {
        return tags.contains(TAG_NO_DROP);
    }

    public String getAnimalClass() {
        for (String tag : tags) {
            if (tag.startsWith(TAG_SPECIES_PREFIX)) return tag.substring(TAG_SPECIES_PREFIX.length());
        }

        return "alien";
    }

    public Pair<String, Color> getRarityDesc(){
        return rarity == 0f? new Pair<>("[Unique]", new Color(240, 20, 30, 255))
                : rarity == 0.1f ? new Pair<>("[Epic]", new Color(180, 50, 255, 255))
                : Misc.isBetween(0f, 0.4f, rarity) ? new Pair<>("[Rare]", new Color(80, 100, 255, 255))
                : Misc.isBetween(0.5f, 0.8f, rarity) ? new Pair<>("[Uncommon]", new Color(80, 200, 80, 255)) :
                new Pair<>("[Common]", Color.white);
    }
}
