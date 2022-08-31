package com.fs.starfarer.api.plugins.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.ids.Tags.THEME_REMNANT;

public class IndEvo_spawnRuins implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null || s.getPlanets().isEmpty()) continue;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.getMarket() != null && p.getMarket().hasCondition(IndEvo_ids.COND_RUINS)) {
                    Console.showMessage("There are planets with industrial Ruins present in the sector, aborting spawn.");
                    return CommandResult.SUCCESS;
                }
            }
        }

        float amount = 0;
        float total = 0;

        List<String> anyRuins = new ArrayList<>();
        anyRuins.add(Conditions.RUINS_SCATTERED);
        anyRuins.add(Conditions.RUINS_WIDESPREAD);
        anyRuins.add(Conditions.RUINS_EXTENSIVE);
        anyRuins.add(Conditions.RUINS_VAST);

        //count ruinsConditions + clean all conditions from planets that are not remnant themed
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null || s.getPlanets().isEmpty() || !s.getTags().contains(THEME_REMNANT)) continue;

            boolean present = false;
            for (PlanetAPI p : s.getPlanets()) {
                if (p.getMarket() != null && p.getMarket().hasCondition(IndEvo_ids.COND_RUINS)) {
                    present = true;
                    break;
                }
            }

            if (present) continue;

            //actual check starts here
            total++;

            int currentCount = 0;
            Random r;

            float mod = 0.2f;
            if (s.getTags().contains(Tags.THEME_REMNANT_SUPPRESSED)) mod = 0.5f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) mod = 0.8f;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null) continue;

                boolean hasRuins = false;
                for (String c : anyRuins) {
                    if (p.getMarket().hasCondition(c)) {
                        hasRuins = true;
                        break;
                    }
                }

                if (hasRuins) {
                    currentCount++;
                    switch (currentCount) {
                        case 1:
                            r = new Random();
                            if (r.nextFloat() < mod) {
                                p.getMarket().addCondition(IndEvo_ids.COND_RUINS);
                                Console.showMessage(IndEvo_ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                                amount++;
                            }
                        case 2:
                            r = new Random();
                            if (r.nextFloat() < (mod / 3f)) {
                                p.getMarket().addCondition(IndEvo_ids.COND_RUINS);
                                Console.showMessage(IndEvo_ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName() + " - Second Planet");
                                amount++;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        Console.showMessage("Spawned " + amount + " industrial ruins");
        Console.showMessage("Total eligible planets found in sector: " + total);
        return CommandResult.SUCCESS;
    }
}
