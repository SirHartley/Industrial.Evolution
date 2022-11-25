package industrial_evolution.campaign.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketSkillEffect;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class IndEvo_FleetLogistics {

    public static final float ACCESS_1 = 0.15f;
    public static final float FLEET_SIZE = 25f;

    public static class Market1 implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) {
            market.getAccessibilityMod().modifyFlat(id, ACCESS_1, "Fleet logistics");
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(id, FLEET_SIZE / 100f, "Fleet logistics");
        }

        public void unapply(MarketAPI market, String id) {
            market.getAccessibilityMod().unmodifyFlat(id);
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(id);
        }

        public String getEffectDescription(float level) {
            return "+" + (int) Math.round(ACCESS_1 * 100f) + "% accessibility, and " + (int) Math.round(FLEET_SIZE) + "% larger fleets";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }
}
