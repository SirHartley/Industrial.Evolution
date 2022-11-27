package indevo.abilities.splitfleet.locationFollower;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class PlayerFleetFollower implements EveryFrameScript {
    public static final String TOKEN_MEMORY_KEY = "$IndEvo_followToken";
    public SectorEntityToken token;

    public static void register(){
        PlayerFleetFollower script = new PlayerFleetFollower();
        Global.getSector().addTransientScript(script);

        script.token = getToken();
    }

    public static SectorEntityToken getToken(){
        SectorEntityToken token;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(TOKEN_MEMORY_KEY)) token = mem.getEntity(TOKEN_MEMORY_KEY);
        else {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            token = fleet.getContainingLocation().addCustomEntity(Misc.genUID(), null, "SplinterFleet_OrbitFocus", Factions.NEUTRAL);
            //fleet.getContainingLocation().addEntity(token);

            mem.set(TOKEN_MEMORY_KEY, token);
        }

        return token;
    }

    public PlayerFleetFollower() {
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI containingLoc = fleet.getContainingLocation();

        if (containingLoc != token.getContainingLocation()) update();

        Vector2f loc = fleet.getLocation();
        token.setLocation(loc.x, loc.y);
    }

    private void update(){
        this.token.getContainingLocation().removeEntity(this.token);
        Global.getSector().getMemoryWithoutUpdate().unset(TOKEN_MEMORY_KEY);

        SectorEntityToken token = getToken();
        for (LocationFollowListenerAPI f : Global.getSector().getListenerManager().getListeners(LocationFollowListenerAPI.class)){
            f.reportLocationUpdated(token);
        }
    }
}
