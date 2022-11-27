package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.industries.assembler.industry.IndEvo_AdAssem;

public class IndEvo_AdManuf extends IndEvo_AdAssem {

    private static final int VPC_MARKET_SIZE_OVERRIDE = 1;

    public void apply() {
        super.apply(true);

        Global.getSector().getListenerManager().addListener(this, true);
        applyIndEvo_VPCEffects();
        toggleRampUp();

        demand(Commodities.HEAVY_MACHINERY, market.getSize() - 1);

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }

        if (!market.isPlayerOwned()) {
            AImode();
        }

        applyDeficits();
    }

    @Override
    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("Manufactory");
    }

    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("Manufactory");
    }

    @Override
    public float getPatherInterest() {
        return 2 + super.getPatherInterest();
    }

    public String getCurrentImage() {
        return currentVPC != null ? Global.getSettings().getSpriteName("IndEvo", "manufactorium") : super.getCurrentImage();
    }

    @Override
    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);

        if (previous instanceof IndEvo_AdAssem) {
            setCurrentVPC(((IndEvo_AdAssem) previous).getCurrentVPC());
        }
    }
}


