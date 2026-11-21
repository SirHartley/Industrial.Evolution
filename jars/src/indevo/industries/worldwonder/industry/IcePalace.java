package indevo.industries.worldwonder.industry;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import indevo.industries.compatibility.AotdManager;

public class IcePalace extends WorldWonder {

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && market.getPrimaryEntity() instanceof PlanetAPI && (market.hasCondition(Conditions.COLD) || market.hasCondition(Conditions.VERY_COLD));
    }

    @Override
    public String getUnavailableReason() {
        String research = AotdManager.getMissingResearchReason(this);
        if (research != null) return research;

        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Can not be built on stations";
        if (!(market.hasCondition(Conditions.COLD) || market.hasCondition(Conditions.VERY_COLD)))
            return "Requires a cold climate";
        return super.getUnavailableReason();
    }
}
