package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.utils.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.fleet.FleetMemberStatus;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class IndEvo_NanitesAbilityPlugin extends IndEvo_BaseConsumableAbilityPlugin {

    private static final float HULL_RESTORE_AMT = 25000f;
    private static final float MAX_CR_LIMIT = 0.5f;
    private static final float CR_RESTORE_AMT = 0.20f;

    //restore a fixed amount of hull instantly 25k HP, cost of 0,2c / HP = 5k item cost, doubled for good measure, 10k item cost
    //Average ship has 5k hull
    //restore 20% CR on all ships up to max 50%

    @Override
    protected void activateImpl() {

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        List<FleetMemberAPI> members = new ArrayList<>(playerFleet.getFleetData().getMembersListCopy());
        List<FleetMemberAPI> orderedRepairList = new LinkedList<>();

        //add flagship
        FleetMemberAPI flagship = playerFleet.getFlagship();
        if (flagship != null && !flagship.isMothballed()) {
            orderedRepairList.add(flagship);
            members.remove(flagship);
        }

        //add all ships with captain
        for (FleetMemberAPI curr : playerFleet.getFleetData().getMembersListCopy()) {
            if (!curr.getCaptain().isDefault()) {
                orderedRepairList.add(curr);
                members.remove(curr);
            }
        }

        //add the rest
        orderedRepairList.addAll(members);

        float repairBudget = HULL_RESTORE_AMT;
        for (FleetMemberAPI m : orderedRepairList){
            if (!m.isMothballed() && (m.getStatus().getHullFraction() < 1 || m.getRepairTracker().getBaseCR() < MAX_CR_LIMIT)) {
                float cr = m.getRepairTracker().getBaseCR();
                if(cr < MAX_CR_LIMIT){
                    //restore up to 20% when under 50%
                    float crRepairAmt = Math.min(CR_RESTORE_AMT, MAX_CR_LIMIT - cr);
                    m.getRepairTracker().applyCREvent(crRepairAmt, "Restoration Nanites");
                }

                if(repairBudget > 0){
                    float baseHull =  m.getStats().getHullBonus().computeEffective(m.getHullSpec().getHitpoints());
                    float actualHull = baseHull * m.getStatus().getHullFraction();
                    float toRepair = Math.min(baseHull - actualHull, repairBudget);
                    float fractionAfterRepair = (actualHull + toRepair) / baseHull;
                    m.getStatus().setHullFraction(fractionAfterRepair);

                    repairBudget -= toRepair;
                    IndEvo_modPlugin.log(m.getSpecId() + "hull repair: " + toRepair + " remaining budget " + repairBudget);

                    FleetMemberStatus fleetMemberStatus = (FleetMemberStatus) m.getStatus();
                    for (int i = 0; i < fleetMemberStatus.getNumStatuses(); i++){
                        FleetMemberStatus.ShipStatus s = fleetMemberStatus.getStatus(i);

                        float baseArmor = m.getStats().getArmorBonus().computeEffective(m.getHullSpec().getArmorRating());
                        float actualArmor = baseArmor * s.computeAverageArmorFraction();
                        float armorToRepair = Math.min(baseArmor - actualArmor, repairBudget);

                        toRepair = Math.min(repairBudget, armorToRepair);
                        try {
                            s.repairArmorUsingCapacity(toRepair);
                        } catch (NullPointerException e)  {
                            IndEvo_modPlugin.log("could not repair armor, status: " + s + " to repair: " + toRepair);
                        }

                        repairBudget -= toRepair;

                        IndEvo_modPlugin.log(m.getSpecId() + "armor repair: " + toRepair + " remaining budget " + repairBudget);
                    }
                }
            }
        }
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

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Restores up to %s to ships in your fleet (total), and raises combat readiness by %s up to a %s per ship. " +
                        "Starts with the flagship and then goes on to officer-controlled ships, in the " +
                        "order they are placed in the fleet.", opad, highlight,
                "25.000 hull or armor integrity",
                IndEvo_StringHelper.getAbsPercentString(CR_RESTORE_AMT, false),
                "maximum of " + IndEvo_StringHelper.getAbsPercentString(MAX_CR_LIMIT, false));
    }
}
