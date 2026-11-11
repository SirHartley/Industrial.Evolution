package indevo.ambience.shuttletraffic.listeners;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.ObjectiveEventListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import indevo.ambience.shuttletraffic.ShuttleTraffic;

// transient, added once by ShuttleTrafficRenderer.register; every reaction waits for the dialog to close
public class ShuttleColonyListener implements ColonyPlayerHostileActListener, ObjectiveEventListener, PlayerColonizationListener, ColonyDecivListener {

    private static final int RAID_MIN = 4;
    private static final int RAID_MAX = 8;
    private static final int SATURATION_MIN = 12;
    private static final int SATURATION_MAX = 20;
    private static final int TACTICAL_MIN = 4;
    private static final int TACTICAL_MAX = 6;
    private static final int DECIV_MIN = 10;
    private static final int DECIV_MAX = 16;

    private final ShuttleTraffic traffic;

    public ShuttleColonyListener(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) {
        traffic.getFlights().getFleets().queueRaid(market, roll(RAID_MIN, RAID_MAX));
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry industry) {
        traffic.getFlights().getFleets().queueRaid(market, roll(RAID_MIN, RAID_MAX));
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
        traffic.getFlights().getPods().queueEvacuation(market, roll(TACTICAL_MIN, TACTICAL_MAX));
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
        traffic.getFlights().getPods().queueEvacuation(market, roll(SATURATION_MIN, SATURATION_MAX));
    }

    @Override
    public void reportObjectiveChangedHands(SectorEntityToken objective, FactionAPI from, FactionAPI to) {
        traffic.getFlights().getEvents().queueObjective(objective, to);
    }

    @Override
    public void reportObjectiveDestroyed(SectorEntityToken objective, SectorEntityToken stableLocation, FactionAPI enemy) {
    }

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planet) {
        traffic.getFlights().getEvents().queueSettlers(planet);
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI colony) {
    }

    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {
    }

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {
        traffic.getFlights().getPods().queueEvacuation(market, roll(DECIV_MIN, DECIV_MAX));
    }

    private int roll(int min, int max) {
        return min + traffic.getRandom().nextInt(max - min + 1);
    }
}
