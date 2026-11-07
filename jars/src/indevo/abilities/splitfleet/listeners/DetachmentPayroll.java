package indevo.abilities.splitfleet.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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

// bills detachment crew, marines and officers into the vanilla fleet payroll nodes, same rates as CoreScript.reportEconomyTick
public class DetachmentPayroll implements EconomyTickListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(DetachmentPayroll.class)) manager.addListener(new DetachmentPayroll());
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (TutorialMissionIntel.isTutorialInProgress() || !DetachmentMemory.isAnyDetachmentActive()) return;

        int crewSalary = Global.getSettings().getInt("crewSalary");
        int marineSalary = Global.getSettings().getInt("marineSalary");
        float f = 1f / Global.getSettings().getFloat("economyIterPerMonth");

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode fleetNode = report.getNode(MonthlyReport.FLEET);

        for (CampaignFleetAPI detachment : DetachmentMemory.getFleetMap().values()) {
            if (detachment == null || !detachment.isAlive() || Behaviour.isDormant(detachment)) continue;

            FDNode crewNode = getNode(report, fleetNode, MonthlyReport.CREW, "Crew payroll");
            crewNode.upkeep += detachment.getCargo().getCrew() * crewSalary * f;

            if (marineSalary > 0) {
                FDNode marineNode = getNode(report, fleetNode, MonthlyReport.MARINES, "Marine payroll");
                marineNode.upkeep += detachment.getCargo().getMarines() * marineSalary * f;
            }

            FDNode officersNode = getNode(report, fleetNode, MonthlyReport.OFFICERS, "Officer payroll");
            for (FleetMemberAPI member : detachment.getFleetData().getMembersListCopy()) {
                PersonAPI officer = member.getCaptain();
                if (officer == null || officer.isDefault() || officer.isAICore() || officer.isPlayer()) continue;

                FDNode oNode = report.getNode(officersNode, officer.getId());
                oNode.name = officer.getNameString() + " (" + detachment.getName() + ")";
                oNode.upkeep += Misc.getOfficerSalary(officer) * f;
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
}
