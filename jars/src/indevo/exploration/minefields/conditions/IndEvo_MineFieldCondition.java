package indevo.exploration.minefields.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import indevo.exploration.minefields.IndEvo_MineBeltTerrainPlugin;
import indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;

public class IndEvo_MineFieldCondition extends BaseMarketConditionPlugin {

    public static final String NO_ADD_BELT_VISUAL = "$IndEvo_NoAsteroids";
    public static final String MINE_FIELD_ID_KEY = "$IndEvo_MineFieldRefKey";
    public static final String PLANET_KEY = "$IndEvo_PlanetMinefieldKey";
    public static float DEFENSE_BONUS = 2.5f;

    public static final Logger log = Global.getLogger(CombatAndDerelictionScript.class);

    @Override
    public void apply(String id) {
        float bonus = DEFENSE_BONUS;

        if(!market.isPlanetConditionMarketOnly()) market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getModId(), bonus, getName());
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);

        if(!market.isPlanetConditionMarketOnly()) market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
    }

    public void addMineField() {
        SectorEntityToken field = null;
        SectorEntityToken primaryEntity = market.getPrimaryEntity();
        StarSystemAPI system = market.getPrimaryEntity().getStarSystem();

        for (SectorEntityToken t : system.getTerrainCopy()) {
            if (t.getCustomPlugin() instanceof BaseRingTerrain) {
                SectorEntityToken related = t.getOrbitFocus();
                if (related != null && related.getMarket() != null && related.getMarket().getId().equals(this.market.getId())) {
                    field = IndEvo_MineBeltTerrainPlugin.addMineBelt(primaryEntity, t.getRadius(), 100f, 30f, 40f, primaryEntity.getName() + " Minefield");
                    field.getMemoryWithoutUpdate().set(PLANET_KEY, primaryEntity);
                    break;
                }
            }
        }

        if (field == null) {
            field = IndEvo_MineBeltTerrainPlugin.addMineBelt(primaryEntity, 500f, 100f, 30f, 40f, primaryEntity.getName() + " Minefield");
            field.getMemoryWithoutUpdate().set(PLANET_KEY, primaryEntity);

            if (!market.getMemoryWithoutUpdate().contains(NO_ADD_BELT_VISUAL)){
                system.addRingBand(primaryEntity, "misc", "rings_dust0", 256f, 2, Color.white, 256f, 500f, 31f);
            }

        }

        market.getMemoryWithoutUpdate().set(MINE_FIELD_ID_KEY, field.getId());
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!market.getMemoryWithoutUpdate().contains(MINE_FIELD_ID_KEY)) {
            addMineField();
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Increases Ground Defence Strength by %s", 10, Misc.getHighlightColor(), (double) Math.round(DEFENSE_BONUS * 100) / 100 + "x");
    }
}
