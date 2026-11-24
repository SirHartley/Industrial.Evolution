package indevo.industries.changeling.industry.mining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.industries.changeling.industry.BaseSubIndustry;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.List;

public class MiningSpecializations {

    public static final String BASE_ID = "base_mining";
    public static final List<String> COMMODITIES = List.of(Commodities.ORE, Commodities.RARE_ORE, Commodities.ORGANICS, Commodities.VOLATILES);

    // percent of each line, close to the old flat +2 and -2 or -3 on a size 6 colony
    public static final float FOCUS = 40f;
    public static final float ORE_MALUS = -40f;
    public static final float MALUS = -50f;

    public static final List<SubIndustryData> LIST = List.of(
            new SubIndustryData(BASE_ID, "Mining", "graphics/icons/industry/mining.png", "IndEvo_base_mining", 10000, BaseSubIndustry::new),
            variant("ore_mining", "Ore Mining", "ore_mining", "IndEvo_ore_mining", 10000, Commodities.ORE, "Ore", ORE_MALUS),
            variant("rare_mining", "Transplutonics Mining", "rare_mining", "IndEvo_rare_mining", 25000, Commodities.RARE_ORE, "Transplutonic ore", MALUS),
            variant("volatile_mining", "Volatile Extraction", "volatile_mining", "IndEvo_volatile_mining", 50000, Commodities.VOLATILES, "Volatiles", MALUS),
            variant("organics_mining", "Organics Extraction", "organics_mining", "IndEvo_organics_mining", 10000, Commodities.ORGANICS, "Organics", MALUS));

    private static SubIndustryData variant(String id, String name, String sprite, String descriptionID, float cost, String focus, String focusName, float malus) {
        return new SubIndustryData(id, name, Global.getSettings().getSpriteName("IndEvo", sprite), descriptionID, cost, data -> new MiningSubIndustry(data, focus, malus))
                .gain(focusName + " output %s", Specialization.percent(FOCUS))
                .cost("Other mining output %s", Specialization.percent(malus));
    }
}
