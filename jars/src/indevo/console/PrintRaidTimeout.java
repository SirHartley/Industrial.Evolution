package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import indevo.utils.helper.IndustryHelper;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.HashMap;
import java.util.Map;

import static indevo.utils.timers.RaidTimeout.AI_RAID_TIMEOUT_KEY;
import static indevo.utils.timers.RaidTimeout.RAID_TIMEOUT_KEY;

public class PrintRaidTimeout implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        Map<String, Float> raidTimeoutMap = IndustryHelper.getMapFromMemory(RAID_TIMEOUT_KEY);
        Map<String, Float> raidTimeoutMap_AI = IndustryHelper.getMapFromMemory(AI_RAID_TIMEOUT_KEY);

        Map<String, StarSystemAPI> systemAPIMap = new HashMap<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            systemAPIMap.put(s.getId(), s);
        }

        Console.showMessage("PLAYER privateer bases: [Star System] Timed out for [num] months:");
        if (raidTimeoutMap.isEmpty()) Console.showMessage("No timed out systems.");
        for (Map.Entry<String, Float> entry : raidTimeoutMap.entrySet()) {
            Console.showMessage(systemAPIMap.get(entry.getKey()).getName() + ": " + entry.getValue());
        }

        Console.showMessage("AI privateer bases: [Star System] Timed out for [num] months:");
        if (raidTimeoutMap_AI.isEmpty()) Console.showMessage("No timed out systems.");
        for (Map.Entry<String, Float> entry : raidTimeoutMap_AI.entrySet()) {
            Console.showMessage(systemAPIMap.get(entry.getKey()).getName() + ": " + entry.getValue());
        }

        return CommandResult.SUCCESS;
    }
}


