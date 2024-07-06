package indevo.abilities.skills.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.timers.NewDayListener;

import java.util.HashMap;
import java.util.Map;

public class AdminGovernTimeTracker implements NewDayListener {

    public static AdminGovernTimeTracker getInstanceOrRegister(){
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        AdminGovernTimeTracker tracker;

        if (manager.hasListenerOfClass(AdminGovernTimeTracker.class)) tracker = manager.getListeners(AdminGovernTimeTracker.class).get(0);
        else {
            tracker = new AdminGovernTimeTracker();
            manager.addListener(tracker);
        }

        return tracker;
    }

    public static class AdminEntry {
        String adminId;
        int daysInOffice = 0;

        public AdminEntry(String adminId) {
            this.adminId = adminId;
        }

        public void adminChange(String adminId) {
            daysInOffice = 0;
            this.adminId = adminId;
        }

        public void advance(){
            daysInOffice++;
        }
    }

    Map<String, AdminEntry> adminList = new HashMap<>();

    @Override
    public void onNewDay() {
        for (MarketAPI market : Misc.getPlayerMarkets(true)){
            String marketId = market.getId();
            String adminId = market.getAdmin().getId();
            AdminEntry entry = adminList.get(marketId);

            if (entry == null) {
                entry = new AdminEntry(adminId);
                adminList.put(marketId, entry);
            }

            if (!adminId.equals(entry.adminId)) entry.adminChange(adminId);
            entry.advance();
        }
    }

    public int getValueForMarket(String id){
        return adminList.containsKey(id) ? adminList.get(id).daysInOffice : 0;
    }
}
