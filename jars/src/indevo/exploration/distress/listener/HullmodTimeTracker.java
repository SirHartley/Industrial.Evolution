package indevo.exploration.distress.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.skills.scripts.AdminGovernTimeTracker;
import indevo.utils.timers.NewDayListener;

import java.util.HashMap;
import java.util.Map;

public class HullmodTimeTracker implements NewDayListener {

    public static HullmodTimeTracker getInstanceOrRegister(){
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        HullmodTimeTracker tracker;

        if (manager.hasListenerOfClass(AdminGovernTimeTracker.class)) tracker = manager.getListeners(HullmodTimeTracker.class).get(0);
        else {
            tracker = new HullmodTimeTracker();
            manager.addListener(tracker);
        }

        return tracker;
    }

    public static class TimerEntry {
        int maxDays;
        int daysPassed = 0;

        public TimerEntry(int maxDays) {
            this.maxDays = maxDays;
        }

        public void advance(){
            daysPassed++;
        }

        public boolean isElapsed(){
            return daysPassed > maxDays;
        }

        public void reset(int maxDays){
            this.maxDays = maxDays;
            daysPassed = 0;
        }

        public float getFraction(){
            return (daysPassed * 1f) / maxDays;
        }
    }

    Map<String, TimerEntry> shipList = new HashMap<>();

    @Override
    public void onNewDay() {
        for (TimerEntry e : shipList.values()){
            e.advance();
        }
    }

    public boolean hasEntry(String id){
        return shipList.containsKey(id);
    }

    public void addEntry(String id, int maxDays){
        shipList.put(id, new TimerEntry(maxDays));
    }

    public TimerEntry getEntry(String id){
        return shipList.get(id);
    }

    public void resetEntry(String id, int maxDays){
        addEntry(id, maxDays);
    }
}
