package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.industry.Museum;

import java.util.ArrayList;
import java.util.List;

public final class ParadeChecks {
    private ParadeChecks() {
    }

    private static CargoAPI cargo(MarketAPI market) {
        return market.getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo();
    }

    private static int tagged(MarketAPI market) {
        int count = 0;
        for (FleetMemberAPI member : cargo(market).getMothballedShips().getMembersListCopy()) {
            if (member.getVariant().hasTag(MuseumConstants.ON_PARADE_TAG)) count++;
        }
        return count;
    }

    private static int active(Museum museum) {
        int count = 0;
        for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) if (profile.hasActiveFleet()) count++;
        return count;
    }

    private static void fill(MarketAPI market, int ships) {
        CargoAPI cargo = cargo(market);
        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        for (int i = 0; i < ships; i++) {
            cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault"));
        }
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            List<PlanetAPI> planets = Colony.planets(2);
            MarketAPI a = Colony.make(planets.get(0), 6);
            c.put("a", a.getId());
            a.addIndustry(Ids.MUSEUM);
            a.reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            Museum museum = (Museum) a.getIndustry(Ids.MUSEUM);
            museum.setAICoreId(Commodities.ALPHA_CORE);
            a.reapplyIndustries();
            fill(a, 78);
            c.note("single colony, max parades " + museum.getMaxParades());
            for (int month = 1; month <= 5; month++) {
                museum.reportEconomyTick(5);
                c.note("month " + month + ": active parades " + active(museum) + ", ships tagged on parade " + tagged(a));
            }
            c.expect(active(museum) == 1, "one parade fits a single colony system");
            c.expect(tagged(a) <= 16, "failed parade attempts leave no ship stuck on parade (" + tagged(a) + " of 78)");
            c.put("tagged", tagged(a));
            c.days(115f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            Museum museum = (Museum) a.getIndustry(Ids.MUSEUM);
            c.note("after the parade: active " + active(museum) + ", tagged " + tagged(a));
            ((Museum) a.getIndustry(Ids.MUSEUM)).setFlyParades(false);
            for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) profile.despawnFleet();
            c.expect(active(museum) == 0, "no parade left");
            c.expect(tagged(a) == 0, "all ships back from parade (" + tagged(a) + " tagged)");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            Museum museum = (Museum) a.getIndustry(Ids.MUSEUM);
            PlanetAPI other = null;
            for (PlanetAPI planet : a.getStarSystem().getPlanets()) {
                if (!planet.isStar() && planet.getMarket() != null && planet.getMarket().isPlanetConditionMarketOnly()) other = planet;
            }
            MarketAPI b = Colony.make(other, 4);
            a.addTag(MuseumConstants.ON_PARADE_TAG);
            museum.setFlyParades(true);
            museum.setAICoreId(null);
            a.reapplyIndustries();
            museum.reportEconomyTick(5);
            a.removeTag(MuseumConstants.ON_PARADE_TAG);
            c.expect(active(museum) == 1, "parade out to the second colony");
            c.expect(tagged(a) > 0, "ships on parade (" + tagged(a) + ")");

            b.setPlayerOwned(false);
            Global.getSector().getEconomy().removeMarket(b);
            for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) profile.despawnFleet();
            c.expect(active(museum) == 0, "parade over with its target colony gone");
            c.expect(tagged(a) == 0, "ships come back when the target colony is gone (" + tagged(a) + " tagged)");
        });
        return steps;
    }
}
