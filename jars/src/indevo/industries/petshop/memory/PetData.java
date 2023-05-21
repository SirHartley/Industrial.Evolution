package indevo.industries.petshop.memory;

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
}
