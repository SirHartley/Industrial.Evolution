package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import indevo.industries.petshop.industry.PetShop;

// animal center instance held by the Pet Habitat, never added to the market and never applied, so no pet market, upkeep or listener
public class HabitatPetShop extends PetShop {

    public static final String NAME = "Pet Habitat";
    public static final int HABITATS = 1;

    // the settlement market has no conditions, irradiation comes from the planet it sits on
    private final SectorEntityToken planet;

    public HabitatPetShop(SectorEntityToken planet) {
        this.planet = planet;
    }

    @Override
    public boolean ownedByPlayer() {
        return true;
    }

    @Override
    protected boolean isIrradiated() {
        MarketAPI market = planet.getMarket();
        return market != null && market.hasCondition(Conditions.IRRADIATED);
    }

    @Override
    public int getHabitatCount() {
        return HABITATS;
    }

    @Override
    public String getCurrentName() {
        return NAME;
    }
}
