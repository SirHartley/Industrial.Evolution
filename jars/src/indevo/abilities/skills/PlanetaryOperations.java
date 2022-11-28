package indevo.abilities.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketSkillEffect;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class PlanetaryOperations {

    public static int LEVEL_1_BONUS = 50;
    public static float STABILITY_BONUS = 1;

    public static class Level1B implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, 1f + LEVEL_1_BONUS * 0.01f, "Planetary operations");
            market.getStability().modifyFlat(id, STABILITY_BONUS, "Planetary operations");
        }

        public void unapply(MarketAPI market, String id) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
            market.getStability().unmodify(id);
        }

        public String getEffectDescription(float level) {
            return "+" + (int) (LEVEL_1_BONUS) + "% effectiveness of ground defenses, and +" + (int) STABILITY_BONUS + " stability";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }
}


