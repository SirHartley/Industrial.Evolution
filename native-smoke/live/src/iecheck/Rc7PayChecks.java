package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.dialogue.DetachmentView;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory.Loadout;
import indevo.abilities.splitfleet.listeners.DetachmentPayroll;
import indevo.ids.Ids;
import indevo.industries.academy.industry.Academy;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// 0.98.5a-RC7 build: officer pay with the four-argument Misc.getOfficerSalary in the Academy and the detachment payroll
public final class Rc7PayChecks {
    private static final Logger log = Global.getLogger(Rc7PayChecks.class);

    private Rc7PayChecks() {
    }

    // the pay before 0.98.5a: base plus level, times the mercenary mult
    private static float oldSalary(PersonAPI p) {
        float pay = Global.getSettings().getInt("officerSalaryBase") + p.getStats().getLevel() * Global.getSettings().getInt("officerSalaryPerLevel");
        if (Misc.isMercenary(p)) pay *= Global.getSettings().getFloat("officerMercPayMult");
        return pay;
    }

    private static void collect(MonthlyReport.FDNode node, List<String> out, String path) {
        if (node == null) return;
        String here = path + "/" + node.name;
        if (node.upkeep > 0f || node.income > 0f) out.add(here + " " + Math.round(node.upkeep));
        for (MonthlyReport.FDNode child : node.getChildren().values()) collect(child, out, here);
    }

    private static List<String> lines() {
        List<String> out = new ArrayList<>();
        collect(SharedData.getData().getPreviousReport().getRoot(), out, "");
        return out;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            if (!a.hasIndustry(Ids.ACADEMY)) a.addIndustry(Ids.ACADEMY);
            Academy academy = Academy.get(a);
            academy.finishBuildingOrUpgrading();
            c.note("extended mode " + Misc.isExtended());

            PersonAPI stored = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 5);
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(stored);
            academy.storeOfficer(stored);
            c.expect(academy.getOfficerStorage().contains(stored), "an officer is stored at the Academy");
            c.put("storedPay", oldSalary(stored));
            c.expect(Math.abs(Academy.getBaseSalary(stored) - oldSalary(stored)) < 0.5f, "Academy pay is the old pay (" + Academy.getBaseSalary(stored) + " vs " + oldSalary(stored) + ")");
            PersonAPI merc = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 4);
            Misc.setMercenary(merc, true);
            c.expect(Math.abs(Academy.getBaseSalary(merc) - oldSalary(merc)) < 0.5f, "a mercenary's Academy pay is the old pay (" + Academy.getBaseSalary(merc) + " vs " + oldSalary(merc) + ")");

            // a detachment with one officer
            DetachmentUiChecks.setup(c);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "falcon_Attack");
            fleet.getFleetData().addFleetMember(m);
            m.getRepairTracker().setCR(0.7f);
            PersonAPI captain = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 3);
            fleet.getFleetData().addOfficer(captain);
            m.setCaptain(captain);
            Loadout loadout = new Loadout();
            loadout.addToMembersList(m);
            loadout.behaviour = FleetBehaviour.STAY;
            loadout.targetCargo.addSupplies(300f);
            loadout.targetCargo.addFuel(100f);
            loadout.targetCargo.addCrew(100);
            LoadoutMemory.setLoadout(1, loadout);
            FleetUtils.createAndSpawnFleet(loadout, 1);
            CampaignFleetAPI det = DetachmentMemory.getDetachment(1);
            c.expect(det != null, "a detachment with an officer is out");
            c.put("captain", captain.getNameString());
            float vanilla = Misc.getOfficerSalary(captain, Misc.isMercenary(captain), true, true);
            float pay = DetachmentPayroll.getSalary(captain, det.getContainingLocation());
            c.note("detachment in " + det.getContainingLocation().getName() + ", populated " + Misc.isInCivilizedSpace() + ", same system as the player " + (det.getContainingLocation() == fleet.getContainingLocation()));
            c.put("captainPay", vanilla);
            c.expect(Math.abs(pay - vanilla) < 0.5f, "detachment pay of an active officer is the vanilla pay (" + pay + " vs " + vanilla + ")");
            // SWITCHES=empty: the detachment sits in a system without markets, so it gets the plain pay
            if (System.getProperty("ie.switches", "").contains("empty")) {
                com.fs.starfarer.api.campaign.StarSystemAPI empty = null;
                for (com.fs.starfarer.api.campaign.StarSystemAPI s : Global.getSector().getStarSystems()) {
                    if (Misc.getMarketsInLocation(s).isEmpty() && !s.hasTag(com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_HIDDEN)) {
                        empty = s;
                        break;
                    }
                }
                c.expect(empty != null, "a system without markets");
                det.getContainingLocation().removeEntity(det);
                empty.addEntity(det);
                det.setLocation(empty.getCenter().getLocation().x + 3000f, empty.getCenter().getLocation().y);
                float plain = Misc.getOfficerSalary(captain, Misc.isMercenary(captain), false, true);
                c.note("detachment moved to " + empty.getName() + ", plain pay " + plain + ", populated pay " + vanilla);
                c.expect(Math.abs(DetachmentPayroll.getSalary(captain, det.getContainingLocation()) - plain) < 0.5f, "detachment pay without markets is the plain pay (" + DetachmentPayroll.getSalary(captain, det.getContainingLocation()) + " vs " + plain + ")");
                c.put("captainPay", plain);
            }

            // two month ends, so the second report holds a full month
            int day = Global.getSector().getClock().getDay();
            c.days(32f - day + 1.5f);
        });
        steps.add(c -> {
            List<String> l = lines();
            for (String s : l) if (s.contains("Personnel") || s.contains("Detachment") || s.toLowerCase().contains("officer")) c.note("first report: " + s);
            c.days(31f);
        });
        steps.add(c -> {
            List<String> l = lines();
            for (String s : l) c.note("report: " + s);
            Academy ac = Academy.get(c.market("a"));
            c.note("academy functional " + ac.isFunctional() + ", stored officers " + ac.getOfficerStorage().size() + ", market player owned " + c.market("a").isPlayerOwned());
            String storage = l.stream().filter(s -> s.contains("Personnel storage")).findFirst().orElse("none");
            float pay = (Float) c.get("storedPay");
            c.expect(storage.endsWith(" " + Math.round(pay)), "the month report has Personnel storage at the stored officer's pay " + Math.round(pay) + " (" + storage + ")");
            String name = DetachmentMemory.getDetachment(1).getFleetData().getMembersListCopy().get(0).getCaptain().getNameString();
            String line = l.stream().filter(s -> s.contains(name + " (")).findFirst().orElse("none");
            float cpay = (Float) c.get("captainPay");
            c.expect(line.contains("(Detachment") || line.contains("(" + DetachmentMemory.getDetachment(1).getName()), "the payroll has a line for the detachment officer (" + line + ")");
            c.near(Float.parseFloat(line.substring(line.lastIndexOf(' ') + 1)), cpay, cpay * 0.05f + 1f, "the detachment officer's month pay is about the vanilla pay");
            c.keepDialog(true);
            DetachmentUiChecks.open(c, new DetachmentView());
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
