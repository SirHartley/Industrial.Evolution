package indevo.industries.worldwonder.industry;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;

import java.util.Set;

public class WorldTree extends WorldWonder {

    @Override
    public boolean isAvailableToBuild() {
        Set<String> validPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.TREE_LIST);
        return super.isAvailableToBuild()
                && market.getPrimaryEntity() instanceof PlanetAPI
                && validPlanetIDs.contains(((PlanetAPI) market.getPrimaryEntity()).getTypeId())
                && market.hasCondition(Conditions.HABITABLE);
    }

    @Override
    public String getUnavailableReason() {
        Set<String> validPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.TREE_LIST);
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Can not be built on stations";
        if (!market.hasCondition(Conditions.HABITABLE)) return "Planet must be habitable";
        if (!validPlanetIDs.contains(((PlanetAPI) market.getPrimaryEntity()).getTypeId()))
            return "Planet must have abundant vegetation";
        return super.getUnavailableReason();
    }

    @Override
    public String getCurrentName() {
        return market.getName().contains("egg") ? "Eggdrasil" : super.getCurrentName();
    }

    @Override
    public void apply() {
        super.apply();
        if (isFunctional()) market.suppressCondition(Conditions.INIMICAL_BIOSPHERE);
    }

    @Override
    public void unapply() {
        super.unapply();
        market.unsuppressCondition(Conditions.INIMICAL_BIOSPHERE);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (!isBuilding() && !forUpgrade && !market.hasCondition(Conditions.INIMICAL_BIOSPHERE))
            market.addCondition(Conditions.INIMICAL_BIOSPHERE);
    }
}
