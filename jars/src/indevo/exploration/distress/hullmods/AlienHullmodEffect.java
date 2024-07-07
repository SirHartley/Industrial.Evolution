package indevo.exploration.distress.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.exploration.distress.intel.AlienAttackIntel;
import indevo.exploration.distress.listener.HullmodTimeTracker;
import indevo.industries.petshop.hullmods.SelfRepairingBuiltInHullmod;
import org.lazywizard.lazylib.MathUtils;

import static indevo.ids.Ids.*;

public class AlienHullmodEffect extends SelfRepairingBuiltInHullmod {
    private static final float ALIEN_ATTACK_TIMER_MIN = 200;
    private static final float ALIEN_ATTACK_TIMER_MAX = 400;
    public static final float MAX_CR_PENALTY_PERCENT = 10f;

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (Global.getSector().isPaused()) return;
        HullmodTimeTracker.TimerEntry tracker = getTrackerEntry(member.getId());

        if (tracker.isElapsed()) {
            tracker.reset((int) MathUtils.getRandomNumberInRange(ALIEN_ATTACK_TIMER_MIN, ALIEN_ATTACK_TIMER_MAX));

            boolean known = true;
            ShipVariantAPI variant = member.getVariant();
            if (variant.hasHullMod(MYSTERY_ALIEN_HULLMOD)) {
                variant.removePermaMod(MYSTERY_ALIEN_HULLMOD);
                variant.addPermaMod(ALIEN_HULLMOD);
                known = false;
            }

            AlienAttackIntel intel = new AlienAttackIntel(member, known);
            Global.getSector().getIntelManager().addIntel(intel);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        String activity = getHostileActivityString(ship);

        int minCrew = (int) ship.getHullSpec().getMinCrew();
        float marinesInCargo = ship.getFleetMember().getFleetData().getFleet().getCargo().getMarines();
        float requiredMarines = getRequiredMarines(ship.getFleetMember());

        float marinePresentFraction = Math.min(marinesInCargo / requiredMarines, 1f);
        float penaltyReductionFactor = MAX_CR_PENALTY_PERCENT - (marinePresentFraction * MAX_CR_PENALTY_PERCENT);
        int actualPenalty = (int) Math.ceil(penaltyReductionFactor);

        switch (index) {
            case 0:
                return activity;
            case 1:
                return (int) MAX_CR_PENALTY_PERCENT + "%";
            case 2:
                return "" + minCrew;
            case 3:
                return "" + Math.round(marinesInCargo);
            case 4:
                return "" + Math.round(requiredMarines);
            case 5:
                return actualPenalty + "%";
            default:
                break;
        }

        return null;
    }

    private String getHostileActivityString(ShipAPI ship) {
        float fraction = getTrackerEntry(ship.getFleetMemberId()).getFraction();

        String string;
        if (fraction < .1) string = "nonexistent";
        else if (fraction < .3) string = "vanishingly rare";
        else if (fraction < .5) string = "few and far between";
        else if (fraction < .7) string = "relatively frequent";
        else if (fraction < .9) string = "very frequent, signaling an attack may be coming";
        else string = "near constant, heralding an immediately imminent attack";

        return string;
    }

    public int getRequiredMarines(FleetMemberAPI fleetMember) {
        return (int) fleetMember.getHullSpec().getMinCrew();
    }

    public HullmodTimeTracker.TimerEntry getTrackerEntry(String id){
        HullmodTimeTracker tracker = HullmodTimeTracker.getInstanceOrRegister();
        if (!tracker.hasEntry(id)) tracker.addEntry(id, (int) MathUtils.getRandomNumberInRange(ALIEN_ATTACK_TIMER_MIN, ALIEN_ATTACK_TIMER_MAX));

        return tracker.getEntry(id);
    }
}
