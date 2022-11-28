package indevo.console;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.industries.embassy.IndEvo_AmbassadorItemHelper;
import com.fs.starfarer.campaign.BaseCampaignEntity;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.HashMap;
import java.util.Map;

public class PrintModData implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        Map<String, Integer> entityAmounts = new HashMap<>();
        Map<String, Integer> scriptAmounts = new HashMap<>();
        Map<String, Integer> listenerAmounts = new HashMap<>();

        //entity counts
        for (LocationAPI location : Global.getSector().getAllLocations()){
            for (SectorEntityToken t : location.getAllEntities()){

                //sectorEntityTokens
                if (t.getCustomEntityType() != null && (t.getCustomEntityType().toLowerCase().contains("indevo") || t.getCustomEntityType().toLowerCase().contains("splinterfleet"))){
                    String type = t.getCustomEntityType();

                    if (entityAmounts.containsKey(type)) entityAmounts.put(type, entityAmounts.get(type) +1);
                    else entityAmounts.put(type, 1);
                }

                //scripts on entities
                if (t instanceof BaseCampaignEntity){
                    for (EveryFrameScript s : ((BaseCampaignEntity) t).getScripts()){
                        if (s.getClass().getName().toLowerCase().contains("indevo") || s.getClass().getName().toLowerCase().contains("splinterfleet")) {
                            String simpleName = s.getClass().getSimpleName();

                            if (scriptAmounts.containsKey(simpleName)) scriptAmounts.put(simpleName, scriptAmounts.get(simpleName) +1);
                            else scriptAmounts.put(simpleName, 1);
                        }
                    }
                }

                //scripts on location
                for (EveryFrameScript s : location.getScripts()){
                    if (s.getClass().getName().toLowerCase().contains("indevo") || s.getClass().getName().toLowerCase().contains("splinterfleet")) {
                        String simpleName = s.getClass().getSimpleName();

                        if (scriptAmounts.containsKey(simpleName)) scriptAmounts.put(simpleName, scriptAmounts.get(simpleName) +1);
                        else scriptAmounts.put(simpleName, 1);
                    }
                }
            }
        }

        for (EveryFrameScript s : Global.getSector().getScripts()){
            if (s.getClass().getName().toLowerCase().contains("indevo") || s.getClass().getName().toLowerCase().contains("splinterfleet")) {
                String simpleName = s.getClass().getSimpleName();

                if (scriptAmounts.containsKey(simpleName)) scriptAmounts.put(simpleName, scriptAmounts.get(simpleName) +1);
                else scriptAmounts.put(simpleName, 1);
            }
        }

        for (Object o : Global.getSector().getListenerManager().getListeners(Object.class)){
            if (o instanceof CampaignEventListener) continue;

            if (o.getClass().getName().toLowerCase().contains("indevo") || o.getClass().getName().toLowerCase().contains("splinterfleet")) {
                String simpleName = o.getClass().getSimpleName();

                if (listenerAmounts.containsKey(simpleName)) listenerAmounts.put(simpleName, listenerAmounts.get(simpleName) +1);
                else listenerAmounts.put(simpleName, 1);
            }
        }

        for (CampaignEventListener o : Global.getSector().getAllListeners()){
            if (o.getClass().getName().toLowerCase().contains("indevo") || o.getClass().getName().toLowerCase().contains("splinterfleet")) {
                String simpleName = o.getClass().getSimpleName();

                if (listenerAmounts.containsKey(simpleName)) listenerAmounts.put(simpleName, listenerAmounts.get(simpleName) +1);
                else listenerAmounts.put(simpleName, 1);
            }
        }

        int ambassadorAmt = Global.getSector().getImportantPeople().getPeopleWithPost(IndEvo_AmbassadorItemHelper.POST_AMBASSADOR).size();
        Console.showMessage("People: " + ambassadorAmt);

        Console.showMessage("--------------------- Listeners ---------------------");
        for (Map.Entry<String, Integer> e : listenerAmounts.entrySet()){
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        Console.showMessage("--------------------- Scripts ---------------------");
        for (Map.Entry<String, Integer> e : scriptAmounts.entrySet()){
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        Console.showMessage("--------------------- Entities ---------------------");
        for (Map.Entry<String, Integer> e : entityAmounts.entrySet()){
            Console.showMessage(e.getKey() + " - " + e.getValue());
        }

        return CommandResult.SUCCESS;
    }
}