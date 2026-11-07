package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.config.Setting;

public class VariableManufactory extends VariableAssembler {

    public static final float LOG_INCREASE = 150f;

    private static final int VPC_MARKET_SIZE_OVERRIDE = 1;

    public void apply() {
        Global.getSector().getListenerManager().addListener(this, true);

        if (!market.isPlayerOwned()) {
            AImode();
        }

        applyVPCEffects();
        toggleRampUp();

        demand(Commodities.HEAVY_MACHINERY, market.getSize() - 1);

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }

        superApply();
        applyDeficits();
    }

    @Override
    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    @Override
    protected float getLogIncrease() {
        return LOG_INCREASE;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.ADMANUF.enabled();
    }

    public boolean showWhenUnavailable() {
        return Setting.ADMANUF.enabled();
    }

    @Override
    public float getPatherInterest() {
        return 1 + super.getPatherInterest();
    }

    public String getCurrentImage() {
        return hasVPC() ? Global.getSettings().getSpriteName("IndEvo", "manufactorium") : super.getCurrentImage();
    }
}


