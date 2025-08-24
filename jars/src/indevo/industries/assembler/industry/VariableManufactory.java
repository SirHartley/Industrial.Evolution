package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.utils.helper.Settings;

public class VariableManufactory extends VariableAssembler {

    private static final int VPC_MARKET_SIZE_OVERRIDE = 1;

    public void apply() {
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

        superApply();
        applyDeficits();
    }

    @Override
    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.ADMANUF);
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.ADMANUF);
    }

    @Override
    public float getPatherInterest() {
        return 1 + super.getPatherInterest();
    }

    public String getCurrentImage() {
        return currentVPC != null ? Global.getSettings().getSpriteName("IndEvo", "manufactorium") : super.getCurrentImage();
    }

    @Override
    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);

        if (previous instanceof VariableAssembler) {
            setCurrentVPC(((VariableAssembler) previous).getCurrentVPC());
        }
    }
}


