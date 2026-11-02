package indevo.industries.museum.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;

public class MuseumParadeCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    @Override
    public void apply(String id) {
        super.apply(id);
        market.addTransientImmigrationModifier(this);
        market.getStability().modifyFlat(getModId(), Setting.MUSEUM_PARADE_FLEET_STABILITY_BONUS.intValue(), getName());
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getStability().unmodify(getModId());
        market.removeTransientImmigrationModifier(this);
    }


    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getModId(), Setting.MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS.intValue(), getName());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("Increases stability by %s\nIncreases colony growth by %s", 10f, Misc.getPositiveHighlightColor(), ""+ Setting.MUSEUM_PARADE_FLEET_STABILITY_BONUS.intValue(), "" + Setting.MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS.intValue());
    }
}
