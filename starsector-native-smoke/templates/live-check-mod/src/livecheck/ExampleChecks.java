package livecheck;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.ArrayList;
import java.util.List;

// each step runs once. c.days, c.until and c.reload hold the next step back. keep ids in c.put, it survives a reload
public final class ExampleChecks {
    private ExampleChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            PlanetAPI planet = Colony.planets(1).get(0);
            MarketAPI market = Colony.make(planet, 5);
            c.put("market", market.getId());
            market.addIndustry(Industries.MINING);
            market.reapplyIndustries();
            c.expect(market.hasIndustry(Industries.MINING), "colony has mining");
            c.days(35f);
        });
        steps.add(c -> {
            c.expect(c.market("market").hasIndustry(Industries.MINING), "mining still there after a month");
            c.reload();
        });
        steps.add(c -> c.expect(c.market("market").hasIndustry(Industries.MINING), "mining still there after save and load"));
        return steps;
    }
}
