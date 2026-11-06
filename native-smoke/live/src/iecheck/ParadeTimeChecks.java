package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.fleet.ParadeFleetAssignmentAI;
import indevo.industries.museum.industry.Museum;

import java.util.ArrayList;
import java.util.List;

public final class ParadeTimeChecks {
    private ParadeTimeChecks() {
    }

    private static Museum museum(MarketAPI market) {
        return (Museum) market.getIndustry(Ids.MUSEUM);
    }

    private static void fill(MarketAPI market) {
        CargoAPI cargo = market.getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo();
        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        for (int i = 0; i < 10; i++) {
            cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault"));
        }
    }

    private static ParadeFleetProfile active(MarketAPI market) {
        for (ParadeFleetProfile profile : museum(market).getParadeFleetProfiles()) if (profile.hasActiveFleet()) return profile;
        return null;
    }

    private static MarketAPI market(ParadeFleetProfile profile) {
        return Global.getSector().getEconomy().getMarket(ParadeFleetAssignmentAI.get(profile.getCurrentFleet()).getTargetMarketId());
    }

    private static MarketAPI target(Ctx c) {
        return Global.getSector().getEconomy().getMarket((String) c.get("target"));
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            c.market("a").addIndustry(Ids.MUSEUM);
            c.market("b").addIndustry(Ids.MUSEUM);
            c.market("a").reapplyIndustries();
            c.market("b").reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            fill(a);
            fill(c.market("b"));
            museum(c.market("b")).setFlyParades(false);
            museum(a).setFlyParades(false);
            museum(a).reportEconomyTick(5);
            ParadeFleetProfile profile = museum(a).getParadeFleetProfiles().get(0);
            profile.setDuration(62);
            museum(a).setFlyParades(true);
            museum(a).reportEconomyTick(5);
            museum(a).setFlyParades(false);
            c.expect(profile.hasActiveFleet(), "parade out");
            c.put("target", ParadeFleetAssignmentAI.get(profile.getCurrentFleet()).getTargetMarketId());
            c.days(3f);
        });
        steps.add(c -> c.reload());
        steps.add(c -> c.until(() -> target(c).hasCondition(Ids.COND_PARADE), 45f, "906 parade reloaded on the way still gives its bonus on arrival"));
        steps.add(c -> {
            c.put("arrived", Global.getSector().getClock().getTimestamp());
            c.days(10f);
        });
        steps.add(c -> c.reload());
        steps.add(c -> c.until(() -> !target(c).hasCondition(Ids.COND_PARADE), 80f, "906 parade reloaded at the target ends when it leaves"));
        steps.add(c -> {
            float days = Global.getSector().getClock().getElapsedDaysSince((Long) c.get("arrived"));
            c.near(days, 62f, 3f, "902 a 2 month parade stays 62 days");
            // at 60x the fleet overshoots the 50 unit arrival check and never lands
            Time.mult = 3f;
            c.until(() -> active(c.market("a")) == null, 31f, "parade comes home");
        });
        steps.add(c -> {
            Time.mult = 60f;
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            museum(a).setFlyParades(true);
            museum(b).setFlyParades(true);
            museum(a).reportEconomyTick(5);
            museum(b).reportEconomyTick(5);
            museum(a).setFlyParades(false);
            museum(b).setFlyParades(false);
            ParadeFleetProfile fromA = active(a);
            ParadeFleetProfile fromB = active(b);
            c.expect(fromA != null && fromB != null, "two museums, two parades");
            MarketAPI targetA = market(fromA);
            MarketAPI targetB = market(fromB);
            c.note("A parades at " + targetA.getName() + ", B parades at " + targetB.getName());
            c.expect(targetA != targetB, "the parades use different colonies");
            ParadeFleetAssignmentAI.addParadeConditionToMarket(targetA);
            ParadeFleetAssignmentAI.addParadeConditionToMarket(targetB);
            fromA.despawnFleet();
            c.expect(!targetA.hasCondition(Ids.COND_PARADE), "the parade that ended clears its own colony");
            c.expect(targetB.hasCondition(Ids.COND_PARADE), "907 the other museum keeps its parade");
            fromB.despawnFleet();

            museum(a).setFlyParades(true);
            museum(a).reportEconomyTick(5);
            museum(a).setFlyParades(false);
            ParadeFleetProfile home = active(a);
            MarketAPI target = market(home);
            MarketAPI other = target == a ? b : a;
            ParadeFleetAssignmentAI.get(home.getCurrentFleet()).forceReturnToSource();

            other.addTag(MuseumConstants.ON_PARADE_TAG);
            museum(b).setFlyParades(true);
            museum(b).reportEconomyTick(5);
            museum(b).setFlyParades(false);
            other.removeTag(MuseumConstants.ON_PARADE_TAG);
            ParadeFleetProfile next = active(b);
            c.expect(next != null && market(next) == target, "next parade picks the colony the first one just left");
            ParadeFleetAssignmentAI.addParadeConditionToMarket(target);
            home.despawnFleet();
            c.expect(target.hasCondition(Ids.COND_PARADE), "907 a fleet getting home leaves the next parade's bonus alone");
            c.expect(target.hasTag(MuseumConstants.ON_PARADE_TAG), "907 and its claim on the colony");
            next.despawnFleet();
            c.expect(!target.hasCondition(Ids.COND_PARADE) && !target.hasTag(MuseumConstants.ON_PARADE_TAG), "the last parade clears the colony");
        });
        return steps;
    }
}
