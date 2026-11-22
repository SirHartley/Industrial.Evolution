package indevo.console;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.industries.embassy.AmbassadorItemHelper;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.HashMap;
import java.util.Map;

public class PrintModData implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        Map<String, Integer> entityAmounts = new HashMap<>();
        Map<String, Integer> scriptAmounts = new HashMap<>();
        Map<String, Integer> listenerAmounts = new HashMap<>();

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (SectorEntityToken t : location.getAllEntities()) {
                String type = t.getCustomEntityType();
                if (type != null && isIE(type)) entityAmounts.merge(type, 1, Integer::sum);

                for (EveryFrameScript s : t.getScripts()) count(scriptAmounts, s);
            }

            for (EveryFrameScript s : location.getScripts()) count(scriptAmounts, s);
        }

        for (EveryFrameScript s : Global.getSector().getScripts()) count(scriptAmounts, s);

        for (Object o : Global.getSector().getListenerManager().getListeners(Object.class)) {
            if (!(o instanceof CampaignEventListener)) count(listenerAmounts, o);
        }

        for (CampaignEventListener o : Global.getSector().getAllListeners()) count(listenerAmounts, o);

        int ambassadorAmt = Global.getSector().getImportantPeople().getPeopleWithPost(AmbassadorItemHelper.POST_AMBASSADOR).size();
        Console.showMessage("People: " + ambassadorAmt);

        Console.showMessage("--------------------- Listeners ---------------------");
        for (Map.Entry<String, Integer> e : listenerAmounts.entrySet()) {
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        Console.showMessage("--------------------- Scripts ---------------------");
        for (Map.Entry<String, Integer> e : scriptAmounts.entrySet()) {
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        Console.showMessage("--------------------- Entities ---------------------");
        for (Map.Entry<String, Integer> e : entityAmounts.entrySet()) {
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        return CommandResult.SUCCESS;
    }

    private static boolean isIE(String name) {
        String lower = name.toLowerCase();
        return lower.contains("indevo") || lower.contains("splinterfleet");
    }

    private static void count(Map<String, Integer> amounts, Object o) {
        if (isIE(o.getClass().getName())) amounts.merge(o.getClass().getSimpleName(), 1, Integer::sum);
    }
}
