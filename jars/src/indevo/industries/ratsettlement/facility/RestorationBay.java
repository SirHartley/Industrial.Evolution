package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.RestorationDocks;

import java.util.Random;

// one ship in settlement storage at a time; it has to stay there DAYS_PER_DMOD days per d-mod, leaving storage resets the timer
public class RestorationBay extends SettlementFacility {

    public static final int DAYS_PER_DMOD = 31;
    private static final String STATUS = "status";

    private String memberId = null;
    private int days = 0;
    private int maxDMods = 0;

    @Override
    protected Setting parentSetting() {
        return Setting.DRYDOCK;
    }

    @Override
    public void onNewDay() {
        FleetMemberAPI ship = getShip();
        if (ship == null) {
            pick();
            return;
        }

        days++;
        if (days < DAYS_PER_DMOD || Global.getSector().getPlayerFleet().getCargo().getCredits().get() <= 0) return;

        float cost = RestorationDocks.getSingleDModRepairCost(ship, maxDMods, 1f);
        RestorationDocks.removeOneDMod(ship.getVariant(), new Random());
        RestorationDocks.restoreHullIfClean(ship.getVariant());
        charge("repairs", "Ship repairs", cost);
        notifyRepair(ship, cost);

        days = 0;
        if (DModManager.getNumNonBuiltInDMods(ship.getVariant()) == 0) memberId = null;
    }

    private FleetMemberAPI getShip() {
        if (memberId == null) return null;

        for (FleetMemberAPI member : getStorageCargo().getMothballedShips().getMembersListCopy()) {
            if (member.getId().equals(memberId) && DModManager.getNumNonBuiltInDMods(member.getVariant()) > 0) return member;
        }

        memberId = null;
        days = 0;
        return null;
    }

    private void pick() {
        CargoAPI storage = getStorageCargo();
        FleetMemberAPI best = null;
        int bestCount = Integer.MAX_VALUE;

        for (FleetMemberAPI member : storage.getMothballedShips().getMembersListCopy()) {
            int count = DModManager.getNumNonBuiltInDMods(member.getVariant());
            if (count > 0 && count < bestCount) {
                best = member;
                bestCount = count;
            }
        }

        if (best == null) return;
        memberId = best.getId();
        maxDMods = bestCount;
        days = 0;
    }

    private void notifyRepair(FleetMemberAPI ship, float cost) {
        MessageIntel intel = new MessageIntel("The %s removed a D-mod from the %s.", Misc.getTextColor(), new String[]{getName(), ship.getShipName()}, Misc.getHighlightColor(), Misc.getHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "Cost: %s", Misc.getTextColor(), new String[]{Misc.getDGSCredits(cost)}, Misc.getHighlightColor());
        intel.setIcon(Global.getSettings().getSpriteName("intel", "repairs_finished"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.INCOME_TAB, Tags.INCOME_REPORT);
    }

    @Override
    protected boolean hasOptions() {
        return true;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Check the Restoration Bay", STATUS);
        dialog.getOptionPanel().setTooltip(new OptionKey(getID(), STATUS), getStatus());
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        dialog.getTextPanel().addPara(getStatus());
    }

    private String getStatus() {
        FleetMemberAPI ship = getShip();
        if (ship == null) return "No ship with D-mods in settlement storage.";

        return "Working on the " + ship.getShipName() + " (" + ship.getHullSpec().getHullName() + "), next D-mod in " + Math.max(0, DAYS_PER_DMOD - days) + " days.";
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Works on one ship with D-mods in settlement storage at a time, fewest D-mods first. The ship must stay in storage for %s days per D-mod. Taking it out resets the timer.",
                0f, Misc.getHighlightColor(), DAYS_PER_DMOD + "");
        tooltip.addPara("Each removal is billed at the Restoration Docks price.", Misc.getGrayColor(), 10f);

        if (isFunctional()) tooltip.addPara(getStatus(), Misc.getHighlightColor(), 10f);
    }
}
