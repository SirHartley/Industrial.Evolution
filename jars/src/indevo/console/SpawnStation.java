package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Random;

public class SpawnStation implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        String type;
        switch (args) {
            case "mining":
            case "miningstation":
            case "mining_station":
            case "mstation":
                type = Entities.STATION_MINING;
                break;
            case "orbital":
            case "orbital_station":
            case "orbitalstation":
            case "ostation":
            case "habitat":
            case "habstation":
            case "hab":
            case "habitat_station":
                type = Entities.ORBITAL_HABITAT;
                break;
            case "research":
            case "researchstation":
            case "research_station":
            case "rstation":
                type = Entities.STATION_RESEARCH;
                break;
            case "arsenal":
            case "arsenalstation":
            case "arstation":
            case "arsenal_station":
                type = Ids.ARSENAL_ENTITY;
                break;
            case "lab":
            case "orbital_lab":
            case "orbitallab":
            case "laboratory":
            case "orbitallaboratory":
            case "orbital_laboratory":
                type = Ids.LAB_ENTITY;
                break;
            default:
                Console.showMessage("Invalid Station hullSize. [mining] [habitat] [research]");
                return CommandResult.BAD_SYNTAX;
        }

        SectorEntityToken fleet = Global.getSector().getPlayerFleet();
        StarSystemAPI sys = (StarSystemAPI) fleet.getContainingLocation();
        SectorEntityToken entity = fleet.getContainingLocation().addCustomEntity(null, null, type, "neutral");

        float orbitRadius = Misc.getDistance(fleet, sys.getCenter());
        float orbitDays = orbitRadius / (20f + new Random().nextFloat() * 5f);
        float angle = Misc.getAngleInDegrees(sys.getCenter().getLocation(), fleet.getLocation());
        entity.setCircularOrbit(sys.getCenter(), angle, orbitRadius, orbitDays);

        Console.showMessage("Spawned a station of hullSize " + type);
        return CommandResult.SUCCESS;
    }
}
