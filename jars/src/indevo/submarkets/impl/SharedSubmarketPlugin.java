package indevo.submarkets.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import indevo.industries.SharedSubmarketUserAPI;
import indevo.industries.derelicts.industry.HullForge;
import indevo.submarkets.DynamicSubmarket;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SharedSubmarketPlugin extends BaseSubmarketPlugin implements DynamicSubmarket {

    public static final Logger log = Global.getLogger(HullForge.class);

    public boolean isSetForRemoval = false;

    public void init(SubmarketAPI submarket) {
        super.init(submarket);

        this.submarket.setFaction(Global.getSector().getFaction("indStorageColour"));
    }

    public void updateCargoPrePlayerInteraction() {
    }

    public static List<Industry> getSharedStorageUsers(MarketAPI market) {
        List<Industry> l = new ArrayList<>();
        for (Industry ind : market.getIndustries()) {
            if (ind instanceof SharedSubmarketUserAPI) {
                l.add(ind);
            }
        }

        return l;
    }

    public boolean showInFleetScreen() {
        return false;
    }

    public boolean showInCargoScreen() {
        return !isSetForRemoval || !market.isPlayerOwned();
    }

    public void prepareForRemoval() {
        this.isSetForRemoval = true;
    }

    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (this.isSetForRemoval) {
            return true;
        }

        boolean debug = false;
        if (Global.getSettings().isDevMode()) debug = true;

        if (action == TransferAction.PLAYER_BUY) return false;

        String id = null;

        if (debug) log.info("id " + id);

        for (Industry ind : getSharedStorageUsers(market)) {
            boolean allowed = ((SharedSubmarketUserAPI) ind).isLegalOnSharedSubmarket(stack);
            if (allowed) return false;
        }

        return true;
    }

    public boolean isParticipatesInEconomy() {
        return false;
    }

    public float getTariff() {
        return 0f;
    }

    @Override
    public boolean isFreeTransfer() {
        return true;
    }

    @Override
    public String getBuyVerb() {
        return "Take";
    }

    @Override
    public String getSellVerb() {
        return "Leave";
    }

    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (this.isSetForRemoval) {
            return "This storage is currently being deconstructed.";
        }
        return "Can not be stored here.";
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        for (Industry ind : getSharedStorageUsers(market)) {
            ((SharedSubmarketUserAPI) ind).addTooltipLine(tooltip, expanded);
        }
    }

    public boolean isEnabled(CoreUIAPI ui) {
        return true;
    }

    public OnClickAction getOnClickAction(CoreUIAPI ui) {
        return OnClickAction.OPEN_SUBMARKET;
    }

    public String getTooltipAppendix(CoreUIAPI ui) {
        return null;
    }

    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        return null;
    }
}

