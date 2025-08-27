package indevo.submarkets.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.submarkets.DynamicSubmarket;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReverseEngineeringSubmarketPlugin extends BaseSubmarketPlugin implements DynamicSubmarket {
    public static Logger log = Global.getLogger(ReverseEngineeringSubmarketPlugin.class);

    public Set<String> restrictedShips = new HashSet<>();
    public Set<String> allowedShips = new HashSet<>();

    @Deprecated
    public Set<String> roleListings = new HashSet<>();

    public boolean isSetForRemoval = false;

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("engHubStorageColour"));
    }

    public void updateCargoPrePlayerInteraction() {
        restrictedShips = new HashSet<>();
        allowedShips = new HashSet<>();

        Set<String> allowedShipsInternal = MiscIE.getCSVSetFromMemory(Ids.REVERSE_LIST);
        Set<String> bossShips = MiscIE.getPrismBossShips();
        Set<String> hvbShips = MiscIE.getVayraBossShips();

        restrictedShips.addAll(bossShips);
        restrictedShips.addAll(hvbShips);
        allowedShips.addAll(allowedShipsInternal);

        //blacklist dweller and threat
        for (FactionSpecAPI faction : Global.getSettings().getAllFactionSpecs()) {
            for (String id : faction.getKnownShips()) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
                if (!Collections.disjoint(spec.getTags(), Arrays.asList("threat", "dweller"))) restrictedShips.add(id);
            }
        }

        if (Settings.getBoolean(Settings.ENGHUB_STRICT_WL)) return;

        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            for (String tag : spec.getTags()) {
                if (tag.contains("_bp")) allowedShips.add(spec.getHullId());
            }
        }

        /*
        //too permissive!
        for (FactionSpecAPI faction : Global.getSettings().getAllFactionSpecs()) {
            for (String id : faction.getKnownShips()) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
                if (Collections.disjoint(spec.getHints(), Arrays.asList("HIDE_IN_CODEX", "STATION"))
                        && Collections.disjoint(spec.getTags(), Arrays.asList("restricted", "no_sell", "no_dealer", "threat", "dweller"))) allowedShips.add(id);

                if (!Collections.disjoint(spec.getTags(), Arrays.asList("threat", "dweller"))) restrictedShips.add(id);
            }
        }*/
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

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        if (market.hasIndustry(Ids.ENGHUB)) {

            boolean allowed = allowedShips.contains(member.getHullId());
            boolean baseAllowed = allowedShips.contains(member.getHullSpec().getBaseHullId());
            boolean restricted = restrictedShips.contains(member.getHullId());
            boolean canNotGenerateRSPoints = ((EngineeringHub) market.getIndustry(Ids.ENGHUB)).getResearchValue(member.getVariant()) <= 0f;
            boolean isPrinted = member.getVariant().hasHullMod(Ids.PRINTING_INDICATOR);
            //boolean hasNoDefaultRole = !(roleListings.contains(member.getHullId()) || roleListings.contains(member.getHullSpec().getBaseHullId()));

            //if(hasNoDefaultRole) return "Can not be Reverse Engineered - NDR";
            if (restricted || !(allowed || baseAllowed)) return "Can not be Reverse Engineered.";
            if (canNotGenerateRSPoints) return "Too damaged to be of use.";
            if (isPrinted) return "Unusable due to printing defects.";
        }

        return "something broke.";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (market.hasIndustry(Ids.ENGHUB)) {

            if (Global.getSettings().isDevMode()) return false;

            float rsVal = ((EngineeringHub) market.getIndustry(Ids.ENGHUB)).getResearchValue(member.getVariant());

            //if illegal, return true
            boolean notAllowed = !(allowedShips.contains(member.getHullId()) || allowedShips.contains(member.getHullSpec().getBaseHullId())); //if not allowed, return true
            boolean restricted = restrictedShips.contains(member.getHullId()); //if restricted, return true
            boolean canNotGenerateRSPoints = rsVal <= 0f; //if it can't generate points, return true
            boolean isPrinted = member.getVariant().hasHullMod(Ids.PRINTING_INDICATOR); //not not printed
            //boolean hasNoDefaultRole = !(roleListings.contains(member.getHullId()) || roleListings.contains(member.getHullSpec().getBaseHullId()));

            boolean ignoreWhiteLists = Settings.getBoolean(Settings.ENGHUB_IGNORE_WHITELISTS);

            if (ignoreWhiteLists) return canNotGenerateRSPoints || isPrinted;
            else return restricted || notAllowed || canNotGenerateRSPoints || isPrinted;
        }

        return true;
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

    @Override
    public boolean isTooltipExpandable() {
        return true;
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;

        if (market.hasIndustry(Ids.ENGHUB)) {
            if (!expanded) {
                addShipStorageValueTooltip(tooltip);

                tooltip.addPara("Expand this tooltip for a %s.", 10f, Misc.getHighlightColor(), "progress overview");
            } else {
                ((EngineeringHub) market.getIndustry(Ids.ENGHUB)).addShipProgressOverview(tooltip, Industry.IndustryTooltipMode.NORMAL, true);
                ((EngineeringHub) market.getIndustry(Ids.ENGHUB)).addCurrentDeconstTooltip(tooltip, Industry.IndustryTooltipMode.NORMAL);
            }
        }
    }

    public void addShipStorageValueTooltip(TooltipMakerAPI tooltip) {

        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;


        tooltip.addSectionHeading("Possible Research Progress", color, dark, Alignment.MID, opad);

        tooltip.addPara("This chart shows the %s for each ship in storage.", 10f, Misc.getHighlightColor(), "progress that will be gained");

        //faction for colours, height of each row, [column 1 header, column 1 width, column 2 header, column 2 width, column 3...)
        tooltip.beginTable(marketFaction, 20f, "Ship Hull", 190f, "D-Mods", 100f, "Progress", 100f);

        for (FleetMemberAPI member : getCargo().getMothballedShips().getMembersListCopy()) {

            //define what you want in the row
            EngineeringHub engineeringHub = ((EngineeringHub) market.getIndustry(Ids.ENGHUB));

            String designation = member.getHullSpec().getHullName();
            String dmods = engineeringHub.getNumNonBuiltInDMods(member.getVariant()) + "";
            String progress = "+ " + Math.round(engineeringHub.getResearchValue(member.getVariant()) * 100) + "%";

            //add the row
            tooltip.addRow(designation, dmods, progress);
        }

        //add the table to the tooltip
        tooltip.addTable("No ships in storage.", 0, opad);
    }
}