package indevo.industries.petshop.memory;

import java.util.List;

public class PetData {
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

    public PetData(String id, String species, float value, float maxLife, List<String> tags, String desc, String naturalDeath, String icon, String hullmod, List<String> foodCommodities, float foodPerMonth) {
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
    }
}
