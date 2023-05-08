package indevo.industries.worldwonder.industry;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

public class LavaFortress extends WorldWonder{

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && market.getPrimaryEntity() instanceof PlanetAPI && market.hasCondition(Conditions.VERY_HOT);
    }

    @Override
    public String getUnavailableReason() {
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Can not be built on stations";
        if (!market.hasCondition(Conditions.VERY_HOT)) return "Requires a very hot climate";
        return super.getUnavailableReason();
    }
}
