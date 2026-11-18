package indevo.industries.petshop.memory;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.awt.*;
import java.util.List;

public class PetData {
    public static final String TAG_NO_SELL = "no_sell";
    public static final String TAG_NO_DROP = "no_drop";
    public static final String TAG_SPECIES_PREFIX = "c_";
    public static final String BODY_PREFIX = "b_";
    public static final String PACE_FAST = "fast";
    public static final String PACE_MEDIUM = "medium";
    public static final String PACE_SLOW = "slow";
    public static final String PACE_HAMSTER = "hamster";
    public static final float FAST_UNDER_DAYS = 1800f;
    public static final float MEDIUM_UNDER_DAYS = 3000f;

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
    public String body;
    public String pace;

    public PetData(String id, String species, float value, float maxLife, List<String> tags, String desc, String naturalDeath, String icon, String hullmod, List<String> foodCommodities, float foodPerMonth, float rarity, String body, String pace) {
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
        this.body = body;
        this.pace = pace == null || pace.isEmpty() ? paceFor(maxLife) : pace;
    }

    // a blank pace column follows the life span
    public static String paceFor(float maxLife) {
        if (maxLife < FAST_UNDER_DAYS) return PACE_FAST;
        if (maxLife < MEDIUM_UNDER_DAYS) return PACE_MEDIUM;
        return PACE_SLOW;
    }

    // what a Treat want takes: the first food that is not Supplies, null when it eats only Supplies
    public String getFavouriteFood() {
        for (String food : foodCommodities) if (!Commodities.SUPPLIES.equals(food)) return food;
        return null;
    }

    public boolean isHamster() {
        return PACE_HAMSTER.equals(pace);
    }

    public boolean isNoSell() {
        return tags.contains(TAG_NO_SELL);
    }

    public boolean isNoDrop() {
        return tags.contains(TAG_NO_DROP);
    }

    public String getClassTag() {
        for (String tag : tags) {
            if (tag.startsWith(TAG_SPECIES_PREFIX)) return tag;
        }

        return TAG_SPECIES_PREFIX + "alien";
    }

    public Pair<String, Color> getRarityDesc(){
        return rarity == 0f? new Pair<>("[Unique]", new Color(240, 20, 30, 255))
                : rarity == 0.1f ? new Pair<>("[Epic]", new Color(180, 50, 255, 255))
                : Misc.isBetween(0f, 0.4f, rarity) ? new Pair<>("[Rare]", new Color(80, 100, 255, 255))
                : Misc.isBetween(0.5f, 0.8f, rarity) ? new Pair<>("[Uncommon]", new Color(80, 200, 80, 255)) :
                new Pair<>("[Common]", Color.white);
    }
}
