package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.items.consumables.dialogue.BeaconDelegateLaunchpadDialoguePlugin;
import indevo.items.consumables.entityAbilities.DecoyMineAbility;
import indevo.items.consumables.intel.DeployableWarningBeaconIntel;
import indevo.utils.ModPlugin;

import java.awt.*;

public class BeaconAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    protected void activateImpl() {
        final InteractionDialogPlugin dialogue = new BeaconDelegateLaunchpadDialoguePlugin(this);

        Global.getSector().addScript(new EveryFrameScript() {
            boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return true;
            }

            @Override
            public void advance(float amount) {
                if (!done) {
                    done = Global.getSector().getCampaignUI().showInteractionDialog(dialogue, null);
                }
            }
        });
    }

    public void spawnBeacon(String message){
        SectorEntityToken closestJumpPoint = null;

        float maxDist = Float.MAX_VALUE;

        for (SectorEntityToken t : Global.getSector().getHyperspace().getJumpPoints()){
            float dist = Misc.getDistance(t, entity);
            if (dist < maxDist){
                closestJumpPoint = t;
                maxDist = dist;
            }
        }

        if (closestJumpPoint == null || maxDist > 400f){
            CustomCampaignEntityAPI beacon = entity.getContainingLocation().addCustomEntity(null, null, "IndEvo_warning_beacon", Factions.NEUTRAL, message);
            beacon.addTag(Tags.WARNING_BEACON);
            beacon.setDiscoverable(false);

            Global.getSector().getIntelManager().addIntel(new DeployableWarningBeaconIntel(beacon, message));

            Misc.setWarningBeaconColors(beacon, getFleet().getFaction().getColor(), getFleet().getFaction().getColor());
            beacon.setLocation(entity.getLocation().x, entity.getLocation().y);
        } else {
            CustomCampaignEntityAPI beacon = entity.getContainingLocation().addCustomEntity(null, null, "IndEvo_warning_beacon", Factions.NEUTRAL, message);

            float radius = maxDist;
            float orbitDays = radius / (10f + StarSystemGenerator.random.nextFloat() * 5f);
            beacon.setCircularOrbitPointingDown(closestJumpPoint, StarSystemGenerator.random.nextFloat() * 360f, radius, orbitDays);
            beacon.setCircularOrbitAngle(Misc.getAngleInDegrees(beacon.getLocation(), entity.getLocation()));
            beacon.setDiscoverable(false);

            Misc.setWarningBeaconColors(beacon, getFleet().getFaction().getColor(), getFleet().getFaction().getColor());

            Global.getSector().getIntelManager().addIntel(new DeployableWarningBeaconIntel(beacon, message));
        }
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return super.isUsable() && fleet.isInHyperspace();  //only in hyperspace
    }

    @Override
    protected void applyEffect(float amount, float level) {

    }

    @Override
    protected void deactivateImpl() {

    }

    @Override
    protected void cleanupImpl() {

    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        if (!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Drops a standard-issue hyperspace warning beacon at the current location. " +
                        "It will broadcast a %s across all common nav-bands for an indefinite amount of time.", opad, highlight,
                "configurable message", "indefinite");

        if (!getFleet().isInHyperspace()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Only usable in Hyperspace!");
    }
}
