package indevo.industries.ratsettlement.facility;

import assortment_of_things.frontiers.SettlementData;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;

public class Consulate extends SettlementFacility {

    public static final float MONTHLY_GAIN = 0.03f;
    public static final float CAP = 0.25f;

    @Override
    protected Setting parentSetting() {
        return Setting.EMBASSY;
    }

    @Override
    public boolean canBeBuild() {
        return getOwner() != null;
    }

    @Override
    public void canNotBeBuildReason(TooltipMakerAPI tooltip, SettlementData data) {
        tooltip.addPara("Needs a planet owned by another faction.", Misc.getNegativeHighlightColor(), 0f);
    }

    @Override
    public void reportEconomyMonthEnd() {
        FactionAPI owner = getOwner();
        if (owner == null) return;

        FactionAPI player = Global.getSector().getPlayerFaction();
        float rel = player.getRelationship(owner.getId());
        if (rel >= CAP) return;

        AmbassadorPersonManager.adjustRelationship(player, owner, Math.min(MONTHLY_GAIN, CAP - rel));
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("A small consulate that keeps in touch with the faction owning the planet. Raises your standing with them by %s each month, up to %s.",
                0f, Misc.getHighlightColor(), Math.round(MONTHLY_GAIN * 100) + "", Math.round(CAP * 100) + "");

        if (!isFunctional()) return;

        FactionAPI owner = getOwner();
        if (owner == null) tooltip.addPara("Idle: the planet is not owned by another faction.", Misc.getNegativeHighlightColor(), 10f);
        else tooltip.addPara("Working with %s.", 10f, owner.getBaseUIColor(), owner.getDisplayNameWithArticle());
    }

    private FactionAPI getOwner() {
        MarketAPI market = getSettlement().getPrimaryPlanet().getMarket();
        if (market == null || market.isPlanetConditionMarketOnly() || market.isPlayerOwned()) return null;

        FactionAPI faction = market.getFaction();
        if (faction == null || faction.isPlayerFaction() || Factions.NEUTRAL.equals(faction.getId())) return null;
        return faction;
    }
}
