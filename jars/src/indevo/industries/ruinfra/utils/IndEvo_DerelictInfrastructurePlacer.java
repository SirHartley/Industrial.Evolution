package indevo.industries.ruinfra.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.IndEvo_ids;

import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_DERELICT;
import static com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_RUINS;
import static indevo.utils.IndEvo_modPlugin.log;

public class IndEvo_DerelictInfrastructurePlacer {

    public static void placeRuinedInfrastructure() {
        if (!Global.getSettings().getBoolean("Enable_Indevo_Derelicts")) return;

        float amount = 0;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Global.getSector().getEconomy().getStarSystemsWithMarkets().contains(s)) continue;

            if (s == null || s.getPlanets().isEmpty()) continue;
            boolean ruinsTheme = s.getTags().contains(THEME_RUINS) || s.getTags().contains(THEME_DERELICT);

            int currentCount = 0;

            //iterate through the planets
            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || TechMining.getTechMiningRuinSizeModifier(p.getMarket()) <= 0f)
                    continue;

                float chance = TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.5f;
                chance *= ruinsTheme ? 1.5 : 1;
                chance /= 1 + (currentCount * 0.3f);

                Random r = new Random(Misc.getSalvageSeed(p));
                if (r.nextFloat() <= chance) {
                    p.getMarket().addCondition(IndEvo_ids.COND_INFRA);
                    log(IndEvo_ids.COND_INFRA.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                    currentCount++;
                    amount++;
                }
            }
        }

        log("Total ruined Infra in sector: " + amount);
    }
}
