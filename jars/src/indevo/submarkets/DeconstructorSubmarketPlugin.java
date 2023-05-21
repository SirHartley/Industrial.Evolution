package indevo.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.helper.IndustryHelper;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class DeconstructorSubmarketPlugin extends BaseSubmarketPlugin implements DynamicSubmarket {

    public static Logger log = Global.getLogger(DeconstructorSubmarketPlugin.class);
    public boolean isSetForRemoval = false;

    public Set<String> restrictedShips = new HashSet<>();
    public Set<String> allowedShips = new HashSet<>();

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("deconStorageColour"));
    }

    public void updateCargoPrePlayerInteraction() {
        restrictedShips = new HashSet<>();
        allowedShips = new HashSet<>();

        Set<String> allowedShipsInternal = IndustryHelper.getCSVSetFromMemory(Ids.PRINT_LIST);
        Set<String> bossShips = IndustryHelper.getPrismBossShips();
        Set<String> hvbShips = IndustryHelper.getVayraBossShips();

        restrictedShips.addAll(bossShips);
        restrictedShips.addAll(hvbShips);
        allowedShips.addAll(allowedShipsInternal);
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        if (market.hasIndustry(Ids.DECONSTRUCTOR)) {

            boolean allowed = allowedShips.contains(member.getHullId());
            boolean baseAllowed = allowedShips.contains(member.getHullSpec().getBaseHullId());
            boolean restricted = restrictedShips.contains(member.getHullId());
            boolean printed = member.getVariant().hasHullMod(Ids.PRINTING_INDICATOR);
            boolean hasUnremovableNonAIOfficer = member.getCaptain() != null && (Misc.isUnremovable(member.getCaptain()) && !member.getCaptain().isAICore());

            if (restricted || !(allowed || baseAllowed)) return "Can not be Deconstructed.";
            if (printed) return "Unusable - Printing Defects";
            if (hasUnremovableNonAIOfficer) return "Unremoveable Officer";
        }

        return "something broke.";
    }


    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (market.hasIndustry(Ids.DECONSTRUCTOR)) {
            if (Global.getSettings().isDevMode()) return false;

            //if illegal, return true
            boolean notAllowed = !(allowedShips.contains(member.getHullId()) || allowedShips.contains(member.getHullSpec().getBaseHullId())); //if not allowed, return true
            boolean restricted = restrictedShips.contains(member.getHullId()); //if restricted, return true
            boolean isPrinted = member.getVariant().hasHullMod(Ids.PRINTING_INDICATOR);
            boolean hasUnremovableNonAIOfficer = member.getCaptain() != null && (Misc.isUnremovable(member.getCaptain()) && !member.getCaptain().isAICore());

            boolean ignoreWhiteLists = Global.getSettings().getBoolean("IndEvo_DeconIgnoreWhitelists");

            if (ignoreWhiteLists) return isPrinted || hasUnremovableNonAIOfficer;

            return restricted || notAllowed || isPrinted;
        }

        return true;
    }

    public boolean showInFleetScreen() {
        return market.isPlayerOwned();
    }

    public boolean showInCargoScreen() {
        return false;
    }

    public void prepareForRemoval() {
        this.isSetForRemoval = true;
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
        return "what?";
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