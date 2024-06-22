package indevo.industries.worldwonder.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.ids.Ids;
import indevo.industries.InvisiblePlanetaryShield;
import indevo.utils.helper.Misc;

import java.io.IOException;
import java.util.Set;

public class CloudPainter extends WorldWonder {

    private String originalClouds = null;
    private int cloudNum = 0;

    @Override
    public void init(String id, MarketAPI market) {
        super.init(id, market);

        SectorEntityToken primary = market.getPrimaryEntity();
        if (!(primary instanceof PlanetAPI)) return;
        originalClouds = ((PlanetAPI) primary).getSpec().getCloudTexture();
    }

    @Override
    public void apply() {
        super.apply();

        if (market.hasIndustry(Industries.PLANETARYSHIELD)
                && market.getPrimaryEntity() instanceof PlanetAPI
                && !(market.getIndustry(Industries.PLANETARYSHIELD) instanceof InvisiblePlanetaryShield)
                && market.getIndustry(Industries.PLANETARYSHIELD).isFunctional()) {

            market.removeIndustry(Industries.PLANETARYSHIELD, null, false);
            market.addIndustry(Ids.PLANETARY_SHIELD_ALT);
        }

        if (isFunctional()) applyVisuals();
    }

    @Override
    public void unapply() {
        super.unapply();
    }

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

    public void nextTexture() {
        int max = Misc.getCSVSetFromMemory(Ids.CLOUD_LIST).size();

        cloudNum += 1;
        if (cloudNum > max) cloudNum = 0;

        applyVisuals();
    }

    /**
     * Always returns the original clouds if invalid or wrong path
     */
    public String getTexture(int i) {
        Set<String> clouds = Misc.getCSVSetFromMemory(Ids.CLOUD_LIST);

        int index = 1;
        for (String s : clouds) {
            if (index == i) {

                try {
                    Global.getSettings().loadTexture(s);
                } catch (IOException e) {
                    return originalClouds;
                }

                return s;
            }

            index++;
        }

        return originalClouds;
    }

    public void applyVisuals() {
        SectorEntityToken primary = market.getPrimaryEntity();
        if (!(primary instanceof PlanetAPI)) return;

        ((PlanetAPI) primary).getSpec().setCloudTexture(getTexture(cloudNum));
        ((PlanetAPI) primary).applySpecChanges();
    }

}
