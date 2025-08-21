package indevo.industries.museum.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.museum.fleet.ParadeFleetAssignmentAI;
import indevo.industries.museum.industry.Museum;
import indevo.utils.ModPlugin;

import java.util.List;
import java.util.Random;

@Deprecated //I don't actually need this anymore if I run off profiles?
public class ParadeFleetData implements FleetEventListener {

    // Name, Target planet, Ships, duration
    public String name;
    public String targetMarketId;
    public List<String> fleetMemberIdList;
    public int durationInDays;

    private Museum spawningMuseum;
    private CampaignFleetAPI activeFleet = null;
    private boolean remove = false;

    public ParadeFleetData(Industry museum, String name, String targetMarketId, List<String> fleetMemberIdList, int durationInDays) {
        this.spawningMuseum = (Museum) museum;
        this.name = name;
        this.targetMarketId = targetMarketId;
        this.fleetMemberIdList = fleetMemberIdList;
        this.durationInDays = durationInDays;
    }

    public boolean isActive(){
        return activeFleet != null;
    }

    public CampaignFleetAPI getActiveFleet(){
        return activeFleet;
    }

    public boolean isExpired(){
        return remove;
    }

    public boolean activateAndSpawn(){
        CampaignFleetAPI paradeFleet = getNewParadeFleet();
        SectorEntityToken spawnLoc = spawningMuseum.getMarket().getPrimaryEntity();

        //Betting there's a modder with a null market adding random industries...
        if (spawnLoc == null){
            ModPlugin.log("Despawning Parade Fleet, primary entity null");
            paradeFleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
            return false;
        }

        spawnLoc.getStarSystem().spawnFleet(spawnLoc, 0f, 0f, paradeFleet);
        MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(targetMarketId);
        targetMarket.addTag(MuseumConstants.ON_PARADE_TAG);

        Global.getSector().getCampaignUI().addMessage("The %s has departed to travel towards %s.", Misc.getTextColor(),
                paradeFleet.getName(),
                targetMarket.getName(),
                Misc.getHighlightColor(), targetMarket.getFaction().getColor());

        activeFleet = paradeFleet;
        return true;
    }

    public void despawn(){
        if (activeFleet != null) activeFleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null); //reset is handled through listener
    }

    private CampaignFleetAPI getNewParadeFleet(){
        MarketAPI market = spawningMuseum.getMarket();
        SubmarketAPI sub = spawningMuseum.getSubmarket();
        CargoAPI cargo = sub.getCargo();

        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> shipsInStorage = cargo.getMothballedShips().getMembersListCopy();

        FleetParamsV3 params = getBaseParams(market);
        params.random = new Random();
        params.ignoreMarketFleetSizeMult = true;    // only use doctrine size, not source size
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        fleet.setNoFactionInName(true);

        for (String memberId : fleetMemberIdList) {
            for (FleetMemberAPI m : shipsInStorage) if (m.getId().equals(memberId) && !m.getVariant().hasTag(MuseumConstants.ON_PARADE_TAG)) {
                m.getVariant().addTag(MuseumConstants.ON_PARADE_TAG);

                ShipVariantAPI variant = m.getVariant().clone(); //clone so it doesn't affect the original ship in storage
                variant.setOriginalVariant(null);
                variant.addTag(Tags.TAG_NO_AUTOFIT);

                FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                fleet.getFleetData().addFleetMember(newMember);
            }
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.setNoAutoDespawn(true);

        fleet.addEventListener(this);
        fleet.addScript(new ParadeFleetAssignmentAI(fleet, market.getId(), targetMarketId, durationInDays));

        fleet.setName("Parade Fleet \"" + name + "\"");

        return fleet;
    }

    private FleetParamsV3 getBaseParams(MarketAPI market) {
        float fuelAmt = 10f;
        float cargoAmt = 10f;

        String type = FleetTypes.TRADE_SMALL;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                null, // locInHyper
                market.getFactionId(),
                1f, // qualityOverride
                type,
                0, // combatPts
                cargoAmt, // freighterPts
                fuelAmt, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f //-0.5f // qualityBonus
        );
        return params;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (fleet != null){
            MarketAPI originMarket = spawningMuseum.getMarket();
            MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(targetMarketId);
            if (targetMarket != null) targetMarket.removeTag(MuseumConstants.ON_PARADE_TAG);

            if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION && originMarket != null) Global.getSector().getCampaignUI().addMessage("%s has returned to %s.", Misc.getTextColor(),
                    fleet.getName(),
                    originMarket.getName(),
                    Misc.getHighlightColor(), originMarket.getFaction().getColor());
            else Global.getSector().getCampaignUI().addMessage("%s has disbanded.", Misc.getTextColor(),
                    fleet.getName(),
                    null,
                    Misc.getHighlightColor(), null);

            if (targetMarket != null && spawningMuseum != null){
                SubmarketAPI sub = spawningMuseum.getSubmarket();
                CargoAPI cargo = sub.getCargo();

                cargo.initMothballedShips(Factions.PLAYER);
                List<FleetMemberAPI> shipsInStorage = cargo.getMothballedShips().getMembersListCopy();

                for (FleetMemberAPI m : shipsInStorage) if (fleetMemberIdList.contains(m.getId())) m.getVariant().removeTag(MuseumConstants.ON_PARADE_TAG);
            }

            fleet.removeEventListener(this);
            activeFleet = null;
            remove = true;
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }
}
