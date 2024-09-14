package indevo.abilities.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketSkillEffect;

public class MicroManagement {

    public static class Level1 implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) {
        }

        public void unapply(MarketAPI market, String id) {
        }

        public String getEffectDescription(float level) {
            return "Allows remote management of items and AI cores after 3 months in office.";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }
}
