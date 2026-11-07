package indevo.abilities.splitfleet.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.dialogue.FleetStatus;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.ids.Ids;

import java.awt.*;
import java.util.Set;

// one per live detachment, ended with a reason when the detachment merges or despawns
public class DetachmentIntel extends BaseIntelPlugin {

    public static final String BUTTON_RECALL = "IndEvo_detachmentRecall";
    public static final String BUTTON_CANCEL_RECALL = "IndEvo_detachmentCancelRecall";

    protected CampaignFleetAPI fleet;
    protected String name;
    protected String endReason = null;

    public DetachmentIntel(CampaignFleetAPI fleet) {
        this.fleet = fleet;
        this.name = fleet.getName();
    }

    public static void create(CampaignFleetAPI fleet) {
        Global.getSector().getIntelManager().addIntel(new DetachmentIntel(fleet), true);
    }

    public static DetachmentIntel get(CampaignFleetAPI fleet) {
        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(DetachmentIntel.class)) {
            DetachmentIntel intel = (DetachmentIntel) plugin;
            if (intel.fleet == fleet && !intel.isEnding()) return intel;
        }

        return null;
    }

    public static void end(CampaignFleetAPI fleet, String reason) {
        DetachmentIntel intel = get(fleet);
        if (intel == null) return;

        intel.endReason = reason;
        intel.endAfterDelay();
        //the intel manager never advances intel, the ending delay only counts down as a sector script
        Global.getSector().addScript(intel);
    }

    @Override
    protected String getName() {
        return name + (isEnding() ? " - ended" : "");
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        if (isEnding()) {
            if (endReason != null) info.addPara(endReason, tc, initPad);
            return;
        }

        FleetBehaviour base = Behaviour.getFleetBehaviour(fleet, true);
        if (base != null) info.addPara("Mode: %s", initPad, tc, Behaviour.getColourForBehaviour(base), Misc.ucFirst(base.toString().toLowerCase()));

        String state = FleetStatus.getStateName(Behaviour.getFleetBehaviour(fleet, false));
        if (state != null) info.addPara("Status: %s", 0f, tc, Misc.getNegativeHighlightColor(), state);

        if (fleet.getContainingLocation() != null) info.addPara("In %s", 0f, tc, Misc.getHighlightColor(), fleet.getContainingLocation().getName());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (isEnding()) {
            info.addPara(endReason != null ? endReason : "The detachment is no longer active.", opad);
            return;
        }

        String location = fleet.getContainingLocation() != null ? fleet.getContainingLocation().getName() : "an unknown location";
        info.addPara("%s is currently in %s.", opad, h, name, location);

        FleetStatus status = FleetStatus.getFleetStatus(fleet);
        FleetStatus.addStatusLines(info, fleet, status, opad);

        int cols = 7;
        int rows = (int) Math.ceil(fleet.getFleetData().getNumMembers() / (float) cols);
        info.addShipList(cols, rows, (width - 10f) / cols, Global.getSector().getPlayerFaction().getBaseUIColor(), fleet.getFleetData().getMembersListCopy(), opad);

        if (Behaviour.isReturning(fleet)) {
            addGenericButton(info, width, "Cancel the recall", BUTTON_CANCEL_RECALL);
        } else {
            ButtonAPI button = addGenericButton(info, width, "Recall to the main force", BUTTON_RECALL);
            button.setEnabled(!Behaviour.isDormant(fleet));
            if (Behaviour.isDormant(fleet)) info.addPara("A dormant detachment can not move until it is resupplied.", Misc.getGrayColor(), opad);
        }
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!isEnding() && fleet.isAlive()) {
            if (BUTTON_RECALL.equals(buttonId)) Behaviour.setReturning(fleet, true);
            else if (BUTTON_CANCEL_RECALL.equals(buttonId)) Behaviour.setReturning(fleet, false);
        }

        ui.updateUIForItem(this);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getAbilitySpec(Ids.ABILITY_DETACHMENT).getIconName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_LOG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return isEnding() || !fleet.isAlive() ? null : fleet;
    }

    @Override
    public String getSortString() {
        return name;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return super.shouldRemoveIntel() || (!isEnding() && !fleet.isAlive());
    }
}
