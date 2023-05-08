package indevo.industries.worldwonder.industry;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

public class CloudPainter extends WorldWonder{

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && market.getPrimaryEntity() instanceof PlanetAPI && !market.hasCondition(Conditions.NO_ATMOSPHERE);
    }

    @Override
    public String getUnavailableReason() {
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Can not be built on stations";
        if (market.hasCondition(Conditions.NO_ATMOSPHERE)) return "Requires an atmosphere";
        return super.getUnavailableReason();
    }

    // TODO: 09/05/2023 add custom cloud layer
}
