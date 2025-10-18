package indevo.utils.timers;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.*;
import java.util.stream.Collectors;

public class TimeTracker implements EveryFrameScript {

    private final ArrayList<ArrayList<Object>> tagTrackers = new ArrayList<>();
    public boolean firstTick = true;
    public int lastDayChecked = 0;

    public TimeTracker() {
    }

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = Global.getSettings().isDevMode(); //set to false once done
        if (DEBUG) {
            Global.getLogger(TimeTracker.class).info(Text);
        }
    }

    public void addMarketTimeTagTracker(MarketAPI market, String ident) {
        ArrayList<Object> l = new ArrayList<>();
        l.add(market);
        l.add(ident);

        if (!marketHasTimeTag(market, ident)) {
            market.addTag(ident + 0);
        }

        tagTrackers.add(l);
    }

    public void removeMarketTimeTagTracker(MarketAPI market, String ident) {
        for (Iterator<ArrayList<Object>> i = tagTrackers.iterator(); i.hasNext(); ) {
            ArrayList<Object> l = i.next();

            if (l.get(0) == market && l.get(1) == ident) {
                tagTrackers.remove(l);
                break;
            }
        }
    }

    public static int getTimeTagPassed(MarketAPI market, String ident) {
        int timePassed = 0;
        for (String s : market.getTags()) {
            if (s.contains(ident)) {
                timePassed = Integer.parseInt(s.substring(ident.length()));
                break;
            }
        }
        return timePassed;
    }

    public static boolean marketHasTimeTag(MarketAPI market, String ident) {
        for (String s : market.getTags()) {
            if (s.contains(ident)) {
                return true;
            }
        }
        return false;
    }

    public static int getTagNum(MarketAPI market, String ident) {
        int timePassed = 0;
        for (String s : market.getTags()) {
            if (s.contains(ident)) {
                timePassed = Integer.parseInt(s.substring(ident.length()));
                break;
            }
        }
        return timePassed;
    }

    public static void incrementTagNum(MarketAPI market, String ident, Integer incrementAmount) {
        for (Iterator<String> i = market.getTags().iterator(); i.hasNext(); ) {
            String tag = i.next();
            if (tag.contains(ident)) {
                int num = Integer.parseInt(tag.substring(ident.length()));
                market.removeTag(tag);
                market.addTag(ident + (num + incrementAmount));
                break;
            }
        }
        market.addTag(ident + 0);
    }

    public static void removeTimeTag(MarketAPI market, String ident) {
        for (Iterator<String> i = market.getTags().iterator(); i.hasNext(); ) {
            String tag = i.next();
            if (tag.contains(ident)) {
                market.removeTag(tag);
                break;
            }
        }
    }

    public static TimeTracker getTimeTrackerInstance() {
        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (s.getClass().equals(TimeTracker.class)) return (TimeTracker) s;
        }

        return null;
    }

    public void advance(float amount) {
        if (newDay()) {
            debugMessage("newDay");
            onNewDay();
            updateMarketTagTimePassed();
        }
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    private void onNewDay() {
        Set<NewDayListener> listenerSet = new HashSet<>();
        listenerSet.addAll(Global.getSector().getListenerManager().getListeners(NewDayListener.class));

        listenerSet.addAll(
                Global.getSector().getAllListeners().stream()
                        .filter(NewDayListener.class::isInstance)
                        .map(NewDayListener.class::cast)
                        .collect(Collectors.toSet())
        );

        for (NewDayListener listener : listenerSet) {
            debugMessage("Running onNewDay for " + listener.getClass().getName());
            listener.onNewDay();
        }
    }

    private boolean newDay() {
        CampaignClockAPI clock = Global.getSector().getClock();
        if (firstTick) {
            lastDayChecked = clock.getDay();
            firstTick = false;
            return false;
        } else if (clock.getDay() != lastDayChecked) {
            lastDayChecked = clock.getDay();
            return true;
        }
        return false;
    }

    private void updateMarketTagTimePassed() {
        for (ArrayList<Object> l : tagTrackers) {
            if (l.size() > 2) {
                continue;
            }

            MarketAPI market = (MarketAPI) l.get(0);
            String ident = (String) l.get(1);

            for (Iterator<String> i = market.getTags().iterator(); i.hasNext(); ) {
                String tag = i.next();
                if (tag.contains(ident)) {
                    int timePassed = getTimeTagPassed(market, ident) + 1;
                    market.removeTag(tag);
                    market.addTag(ident + timePassed);
                    break;
                }
            }
        }
    }
}