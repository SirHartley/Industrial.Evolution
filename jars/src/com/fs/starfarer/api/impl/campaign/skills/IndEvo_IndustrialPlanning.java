package com.fs.starfarer.api.impl.campaign.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketSkillEffect;

public class IndEvo_IndustrialPlanning {

    public static float INCOME_MULT = 1.1f;
    public static float UPKEEP_MULT = 0.9f;

    public static class Level1B implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) {
            market.getUpkeepMult().modifyMult(id, UPKEEP_MULT, "Distributive Economics");
            market.getIncomeMult().modifyMult(id, INCOME_MULT, "Distributive Economics");
        }

        public void unapply(MarketAPI market, String id) {
            market.getUpkeepMult().unmodifyMult(id);
            market.getIncomeMult().unmodifyMult(id);
        }

        public String getEffectDescription(float level) {
            return "-" + (int) Math.round(Math.abs((1f - UPKEEP_MULT)) * 100f) + "% upkeep for colonies, and +" + (int) Math.round((INCOME_MULT - 1f) * 100f) + "% income from colonies, including exports";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }
}


