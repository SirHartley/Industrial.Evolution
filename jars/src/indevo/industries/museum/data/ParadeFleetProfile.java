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
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.museum.fleet.ParadeFleetAssignmentAI;
import indevo.industries.museum.industry.Museum;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static indevo.industries.museum.data.MuseumConstants.*;

public class ParadeFleetProfile implements FleetEventListener {

    //the profile has the presets and name ect, if it's random or not, all the info
    //it spawns a parade fleet

    private final Random random = new Random();
    private final Museum spawningMuseum;

    private List<String> memberIdPreset = null;
    private String namePreset = null;
    private int durationPreset = PARADE_DAY_OPTIONS[0];
    private boolean isEnabled = true; //this is for enabling or disabling the profile, not the fleet.

    private CampaignFleetAPI fleet = null;
    private List<String> fleetMembersOnParade = new ArrayList<>();

    public ParadeFleetProfile(Industry museum) {
        this.spawningMuseum = (Museum) museum;
    }

    public boolean spawnFleet(){
        CampaignFleetAPI paradeFleet = getNewParadeFleetFromPresetData();

        if (paradeFleet == null) return false;

        SectorEntityToken spawnLoc = spawningMuseum.getMarket().getPrimaryEntity();

        //Betting there's a modder with a null market adding random industries...
        if (spawnLoc == null){
            ModPlugin.log("Despawning Parade Fleet, primary entity null");
            paradeFleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
            return false;
        }

        spawnLoc.getStarSystem().spawnFleet(spawnLoc, 0f, 0f, paradeFleet);
        MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(ParadeFleetAssignmentAI.get(paradeFleet).getTargetMarketId());
        targetMarket.addTag(MuseumConstants.ON_PARADE_TAG);

        Global.getSector().getCampaignUI().addMessage("The %s has departed to travel towards %s.", Misc.getTextColor(),
                paradeFleet.getName(),
                targetMarket.getName(),
                Misc.getHighlightColor(), targetMarket.getFaction().getColor());

        fleet = paradeFleet;
        return true;
    }

    public void despawnFleet(){
        if (fleet != null) fleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null); //reset is handled through listener
    }

    private CampaignFleetAPI getNewParadeFleetFromPresetData(){
        if (spawningMuseum == null || spawningMuseum.getMarket() == null || !spawningMuseum.isFunctional()) return null;

        MarketAPI market = spawningMuseum.getMarket();
        SubmarketAPI sub = spawningMuseum.getSubmarket();
        CargoAPI cargo = sub.getCargo();

        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> shipsInStorage = cargo.getMothballedShips().getMembersListCopy();

        if (memberIdPreset == null && shipsInStorage.size() < MIN_SHIPS_FOR_PARADE) return null; //random parades require at least x members

        FleetParamsV3 params = getBaseParams(market);
        params.random = new Random();
        params.ignoreMarketFleetSizeMult = true;    // only use doctrine size, not source size
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        fleet.setNoFactionInName(true);

        List<FleetMemberAPI> paradeMembers = new ArrayList<>();

        if (memberIdPreset != null){
            //preset parade
            for (FleetMemberAPI m : shipsInStorage) if (memberIdPreset.contains(m.getId()) && !m.getVariant().hasTag(ON_PARADE_TAG)) paradeMembers.add(m);

        } else {
            //random parade

            //pick X of the top 20 members that are not currently out on parade
            shipsInStorage.sort(Comparator.comparingDouble(m -> spawningMuseum.getValueForShip((FleetMemberAPI) m)).reversed());

            WeightedRandomPicker<FleetMemberAPI> fleetMemberPicker = new WeightedRandomPicker<>(random);
            int count = 0;

            for (FleetMemberAPI member : shipsInStorage){
                if (member.getVariant().hasTag(ON_PARADE_TAG)) continue;
                if (count >= TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION) break; //we only get the top X so we don't go on parade with a bunch of trash

                fleetMemberPicker.add(member);
                count++;
            }

            for (int i = 0; i <= DEFAULT_PARADE_MEMBERS_MAX; i++) {
                if (fleetMemberPicker.isEmpty()) break;
                paradeMembers.add(fleetMemberPicker.pickAndRemove());
            }
        }

        if (paradeMembers.isEmpty()) return null; //no members for parade in storage, we abort

        //add member to fleet and apply tag
        for (FleetMemberAPI m : paradeMembers) {
            m.getVariant().addTag(MuseumConstants.ON_PARADE_TAG);
            fleetMembersOnParade.add(m.getId());

            ShipVariantAPI variant = m.getVariant().clone(); //clone so it doesn't affect the original ship in storage
            variant.setOriginalVariant(null);
            variant.addTag(Tags.TAG_NO_AUTOFIT);

            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
            fleet.getFleetData().addFleetMember(newMember);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.setNoAutoDespawn(true);

        fleet.addEventListener(this);

        //fleet name
        String name = namePreset;
        if (name == null){
            WeightedRandomPicker<String> namePicker = new WeightedRandomPicker<>(random);
            namePicker.addAll(MiscIE.getCSVSetFromMemory(PARADE_FLEET_NAMES));
            name = namePicker.pick();
        }

        fleet.setName("Parade Fleet \"" + name + "\"");

        //market target
        WeightedRandomPicker<String> marketPicker = new WeightedRandomPicker<>(random);
        for (MarketAPI m : MiscIE.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())) {
            if (m.hasTag(ON_PARADE_TAG)) continue; //added on spawn, removed with the condition on departure or death - in fleetAI

            marketPicker.add(m.getId());
        }

        if (marketPicker.isEmpty()) {
            fleetMembersOnParade.clear();
            return null; //no market, no parade...
        }

        String targetMarketId = marketPicker.pick();

        //fleet AI
        fleet.addScript(new ParadeFleetAssignmentAI(fleet, market.getId(), targetMarketId, durationPreset));

        return fleet;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (fleet != null && fleet == getCurrentFleet()){

            MarketAPI originMarket = spawningMuseum.getMarket();
            MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(ParadeFleetAssignmentAI.get(fleet).getTargetMarketId()); //can't be null: fleet always has AI. If null = mod conflict.
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

                //go through the preset - members in fleet are copies
                for (FleetMemberAPI m : shipsInStorage) if (fleetMembersOnParade.contains(m.getId())) m.getVariant().removeTag(MuseumConstants.ON_PARADE_TAG);
            }

            if (targetMarket != null) ParadeFleetAssignmentAI.get(fleet).removeParadeConditionFromMarket(targetMarket);

            fleet.removeEventListener(this);
            this.fleet = null;
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    public String getNamePreset() {
        return namePreset;
    }

    public void setNamePreset(String namePreset) {
        this.namePreset = namePreset;
    }

    public void setMemberList(List<String> members) {
        this.memberIdPreset = members;
    }

    public void resetNamePreset(){
        namePreset = null;
    }

    public void resetMembers(){
        memberIdPreset = null;
    }

    public List<String> getMemberIdPreset() {
        return memberIdPreset;
    }

    public int getDurationPreset() {
        return durationPreset;
    }

    public CampaignFleetAPI getCurrentFleet(){
        return fleet;
    }

    public void setDuration(int duration) {
        this.durationPreset = duration;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean hasActiveFleet(){
        return getCurrentFleet() != null;
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
}
