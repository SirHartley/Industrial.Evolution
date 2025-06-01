package indevo.industries.ruinfra.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.helper.Settings;

import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_DERELICT;
import static com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_RUINS;
import static indevo.utils.ModPlugin.log;

public class DerelictInfrastructurePlacer {
    public static final String NO_INFRA_TAG = "IndEvo_NoDerelictInfra";

    public static void placeRuinedInfrastructure() {
        if (!Settings.getBoolean(Settings.ENABLE_DERELICTS)) return;

        float amount = 0;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Global.getSector().getEconomy().getStarSystemsWithMarkets().contains(s)) continue;

            if (s == null || s.getPlanets().isEmpty()) continue;
            boolean ruinsTheme = s.getTags().contains(THEME_RUINS) || s.getTags().contains(THEME_DERELICT);

            int currentCount = 0;

            //iterate through the planets
            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || TechMining.getTechMiningRuinSizeModifier(p.getMarket()) <= 0f || p.hasTag(NO_INFRA_TAG))
                    continue;

                float chance = TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.5f;
                chance *= ruinsTheme ? 1.5f : 1f;
                chance /= 1 + (currentCount * 0.3f);

                Random r = new Random(Misc.getSalvageSeed(p));
                if (r.nextFloat() <= chance) {
                    p.getMarket().addCondition(Ids.COND_INFRA);
                    log(Ids.COND_INFRA.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                    currentCount++;
                    amount++;
                }
            }
        }

        log("Total ruined Infra in sector: " + amount);
    }
}
