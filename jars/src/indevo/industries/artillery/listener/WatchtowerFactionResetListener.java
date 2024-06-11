package indevo.industries.artillery.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.ObjectiveEventListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.scripts.ArtilleryStationScript;
import indevo.utils.helper.Settings;
import indevo.utils.timers.NewDayListener;

import java.awt.*;
import java.util.*;
import java.util.List;

public class WatchtowerFactionResetListener implements ObjectiveEventListener, NewDayListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();

        if (manager.hasListenerOfClass(WatchtowerFactionResetListener.class)) return;
        manager.addListener(new WatchtowerFactionResetListener());
    }

    private Map<String, Integer> watchtowerTimeoutMap = new HashMap<>();

    @Override
    public void reportObjectiveChangedHands(SectorEntityToken objective, FactionAPI from, FactionAPI to) {
        if (objective.getTags().contains(Ids.TAG_WATCHTOWER) && !watchtowerTimeoutMap.containsKey(objective.getId())) {
            watchtowerTimeoutMap.put(objective.getId(), Settings.getInt(Settings.ARTILLERY_WATCHTOWER_FACTION_RESET_TIME));
        }
    }

    @Override
    public void reportObjectiveDestroyed(SectorEntityToken objective, SectorEntityToken stableLocation, FactionAPI enemy) {

    }

    @Override
    public void onNewDay() {
        Iterator<Map.Entry<String, Integer>> iterator = watchtowerTimeoutMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int newValue = entry.getValue() - 1;
            if (newValue < 0) {
                resetControllingFaction(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(newValue);
            }
        }
    }

    private void resetControllingFaction(String id) {
        SectorEntityToken watchtower = Global.getSector().getEntityById(id);
        if (watchtower == null) return;

        List<SectorEntityToken> artilleries = ArtilleryStationEntityPlugin.getArtilleriesInLoc(watchtower.getContainingLocation());

        SectorEntityToken closestArtillery = null;
        float shortestDistance = Float.MAX_VALUE;

        for (SectorEntityToken artillery : artilleries) {
            float distance = Misc.getDistance(artillery, watchtower);
            if (distance < shortestDistance) {
                shortestDistance = distance;
                closestArtillery = artillery;
            }
        }

        if (closestArtillery == null) return;

        String controllingFaction = closestArtillery.getFaction().getId();
        if (controllingFaction.equals(Ids.DERELICT_FACTION_ID) || controllingFaction.equals(Factions.REMNANTS)) watchtower.setFaction(closestArtillery.getFaction().getId());
    }
}
