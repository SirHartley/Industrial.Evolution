package indevo.industries.privateer.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import indevo.ids.Ids;


public class PirateSubpopulationCondition extends BaseHazardCondition implements MarketImmigrationModifier {

    public float stabMod = 0;

    public void apply(String id) {
        super.apply(id);

        stabMod = -2;
        if (market.hasIndustry(Ids.PIRATEHAVEN)) stabMod += 2;
        if (isNonHostileToEvilFaction()) stabMod += 2;

        market.getStability().modifyFlat(id, stabMod, "Lawless subpopulation");
        market.addTransientImmigrationModifier(this);
    }

    public void unapply(String id) {
        super.unapply(id);
        market.getStability().unmodify(id);

        market.removeTransientImmigrationModifier(this);
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.add(Factions.POOR, 10f);
        incoming.getWeight().modifyFlat(getModId(), getImmigrationBonus(), Misc.ucFirst(condition.getName().toLowerCase()));
    }

    protected float getImmigrationBonus() {
        float immigrationIncrease;

        if (market.hasIndustry(Ids.PIRATEHAVEN)) {
            immigrationIncrease = !isNonHostileToEvilFaction() ? 0 : market.getSize();

        } else {
            immigrationIncrease = !isNonHostileToEvilFaction() ? -Math.round(market.getSize()) : -Math.round(market.getSize() / 2f);
        }
        return immigrationIncrease;
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addSectionHeading("Effects", Alignment.MID, 10f);

        if (!isNonHostileToEvilFaction()) tooltip.addPara("You are %s well regarded amongst the shady factions of the Persean Sector.",
                10f, Misc.getNegativeHighlightColor(),
                "not");
        else tooltip.addPara("You are %s amongst the shady factions of the Persean Sector.",
                10f, Misc.getPositiveHighlightColor(),
                "respected");

        if (!market.hasIndustry(Ids.PIRATEHAVEN)) tooltip.addPara("There is %s on this planet, causing unrest.",
                3f, Misc.getPositiveHighlightColor(),
                "no " + getName());

        String s = stabMod > 0 ? "+" + (int) Math.round(stabMod) : "" + (int) Math.round(stabMod);
        String t = getImmigrationBonus() > 0 ? "+" + (int) Math.round(getImmigrationBonus()): "" + (int) Math.round(getImmigrationBonus());

        if (stabMod != 0) tooltip.addPara("%s stability.",
                10f, Misc.getHighlightColor(),
                s);
        if (getImmigrationBonus() != 0) tooltip.addPara("%s population growth (based on market size).",
                10f, Misc.getHighlightColor(),
                t);
    }

    public boolean isNonHostileToEvilFaction(){
        FactionAPI marketFaction = market.getFaction();

        if (Global.getSettings().getModManager().isModEnabled("nexerelin")){
            for (FactionAPI f : Global.getSector().getAllFactions()){
                if (NexConfig.getFactionConfig(f.getId()).morality == NexFactionConfig.Morality.EVIL){
                    if (f.isAtWorst(marketFaction, RepLevel.NEUTRAL)) return true;
                }
            }
        }

        return Global.getSector().getFaction(Factions.PIRATES).isAtWorst(marketFaction, RepLevel.NEUTRAL)
                || Global.getSector().getFaction(Factions.LUDDIC_PATH).isAtWorst(marketFaction, RepLevel.NEUTRAL);
    }
}





