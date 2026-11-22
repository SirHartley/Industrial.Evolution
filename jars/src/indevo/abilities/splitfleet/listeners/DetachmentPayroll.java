package indevo.abilities.splitfleet.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.ids.Ids;
import indevo.utils.helper.ExtModeHelper;

// bills detachment crew, marines and officers into the vanilla fleet payroll nodes, same calculation as CoreScript.reportEconomyTick
public class DetachmentPayroll implements EconomyTickListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(DetachmentPayroll.class)) manager.addListener(new DetachmentPayroll());
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (TutorialMissionIntel.isTutorialInProgress() || !DetachmentMemory.isAnyDetachmentActive()) return;

        int crewSalary = (int) (Global.getSettings().getInt("crewSalary") * ExtModeHelper.mult("ext_crewSalaryMult"));
        int marineSalary = (int) (Global.getSettings().getInt("marineSalary") * ExtModeHelper.mult("ext_marineSalaryMult"));
        float f = 1f / Global.getSettings().getFloat("economyIterPerMonth");

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode fleetNode = report.getNode(MonthlyReport.FLEET);

        for (CampaignFleetAPI detachment : DetachmentMemory.getFleetMap().values()) {
            if (detachment == null || !detachment.isAlive() || Behaviour.isDormant(detachment)) continue;

            FDNode crewNode = getNode(report, fleetNode, MonthlyReport.CREW, "Active-duty crew payroll");
            int paidCrew = 0;
            for (FleetMemberAPI member : detachment.getFleetData().getMembersListCopy()) {
                int crew = (int) Math.min(member.getMinCrew(), member.getCrewComposition().getCrewInt());
                if (crew <= 0) continue;

                paidCrew += crew;
                int salary = Misc.computeFleetMemberCrewSalary(member, true);
                if (salary <= 0) continue;

                FDNode mNode = report.getNode(crewNode, member.getId());
                mNode.name = member.getShipName() + " (" + detachment.getName() + ")";
                mNode.upkeep += salary * f;
                mNode.custom = member;
            }

            int reserveCrew = Math.max(0, detachment.getCargo().getCrew() - paidCrew);
            if (reserveCrew * crewSalary > 0) {
                FDNode reserveNode = getNode(report, fleetNode, MonthlyReport.RESERVE_CREW, "Reserve crew payroll");
                reserveNode.upkeep += reserveCrew * crewSalary * f;
            }

            if (marineSalary > 0) {
                FDNode marineNode = getNode(report, fleetNode, MonthlyReport.MARINES, "Marine payroll");
                marineNode.upkeep += detachment.getCargo().getMarines() * marineSalary * f;
            }

            FDNode officersNode = getNode(report, fleetNode, MonthlyReport.OFFICERS, "Officer payroll");
            for (FleetMemberAPI member : detachment.getFleetData().getMembersListCopy()) {
                PersonAPI officer = member.getCaptain();
                if (officer == null || officer.isDefault() || officer.isAICore() || officer.isPlayer() || officer.hasTag(Ids.TAG_PET_CAPTAIN)) continue;

                FDNode oNode = report.getNode(officersNode, officer.getId());
                oNode.name = officer.getNameString() + " (" + detachment.getName() + ")";
                oNode.upkeep += getSalary(officer, detachment.getContainingLocation()) * f;
            }
        }
    }

    //vanilla sets the same name and tooltip, this only matters when this listener runs first
    private FDNode getNode(MonthlyReport report, FDNode parent, String id, String name) {
        FDNode node = report.getNode(parent, id);
        if (node.name == null) {
            node.name = name;
            node.custom = id;
            node.tooltipCreator = report.getMonthlyReportTooltip();
        }

        return node;
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    // vanilla officer pay (Misc.getOfficerSalary(officer, true)); its populated system check reads the current location, here the detachment's own
    public static float getSalary(PersonAPI officer, LocationAPI where) {
        float pay = Misc.getOfficerSalary(officer, Misc.isMercenary(officer), false, true);
        if (Misc.isOfficerActive(officer) && isPopulated(where)) pay *= Global.getSettings().getFloat("officerSalaryMultInPopulatedSystems");
        return pay;
    }

    // Misc.isInCivilizedSpace for any location
    private static boolean isPopulated(LocationAPI where) {
        if (where == null) return false;
        for (MarketAPI market : Misc.getMarketsInLocation(where)) if (!market.isHidden() && market.isInEconomy()) return true;
        return false;
    }
}
