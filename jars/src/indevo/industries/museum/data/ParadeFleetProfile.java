package indevo.industries.museum.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class ParadeFleetProfile {

    //the profile has the presets and name ect, if it's random or not, all the info
    //it spawns a parade fleet via ParadeFleetData

    private Random random = new Random();

    private List<FleetMemberAPI> memberPreset = null;
    private String namePreset = null;
    private int duration = MuseumConstants.DEFAULT_PARADE_DAYS;

    private ParadeFleetData currentData = null;


    public void update(){

        //remove old if dead, spawn new

        //activateAndSpawn parade if empty spot
        //clear the expired ones

        int activeParades = 0;

        for (ParadeFleetData data : new ArrayList<>(paradeFleetData)) {
            if (!data.isActive() && data.isExpired()) paradeFleetData.remove(data);
            else if (data.isActive()) activeParades++;
        }

        if (activeParades < maxParades){
            //check if we have a repeating one - there is no expired data on the list at this point
            for (ParadeFleetData data : paradeFleetData) if (!data.isActive() && data.isRepeating()) spawnSpecificParade(data);

            if (!randomParades) return;

            //still not filled? Spawn random parades until list is full
            for (int i = 0; i < maxParades; i++){
                ModPlugin.log("Spawning random parade");
                spawnRandomParade();
            }
        }
    }

    public void spawn(){

    }

    public void despawn(){

    }

    public void spawnParade(){
        WeightedRandomPicker<String> namePicker = new WeightedRandomPicker<>(random);
        namePicker.addAll(MiscIE.getCSVSetFromMemory(PARADE_FLEET_NAMES));

        String name = namePicker.pick();

        //pick X of the top 20 members that are not currently out on parade
        CargoAPI cargo = submarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();
        members.sort(Comparator.comparingDouble(this::getValueForShip).reversed());

        WeightedRandomPicker<String> fleetMemberPicker = new WeightedRandomPicker<>(random);
        int count = 0;

        for (FleetMemberAPI member : members){
            if (member.getVariant().hasTag(ON_PARADE_TAG)) continue;
            if (count >= TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION) break; //we only get the top X so we don't go on parade with a bunch of trash

            fleetMemberPicker.add(member.getId());
            count++;
        }

        if (fleetMemberPicker.isEmpty()) {
            ModPlugin.log("Aborting parade creation, no fleet members");
            return; //no members, no parade
        }

        List<String> paradeMembers = new ArrayList<>();
        for (int i = 0; i <= DEFAULT_MEMBERS_MAX; i++) {
            if (fleetMemberPicker.isEmpty()) break;
            paradeMembers.add(fleetMemberPicker.pickAndRemove());
        }

        //market target
        WeightedRandomPicker<String> marketPicker = new WeightedRandomPicker<>(random);
        for (MarketAPI m : MiscIE.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())) {
            if (m.hasTag(ON_PARADE_TAG)) continue; //added on spawn, removed with the condition on departure or death - fleetAI

            marketPicker.add(m.getId());
        }

        if (marketPicker.isEmpty()) {
            ModPlugin.log("Aborting parade creation, no target market");
            return; //no market, no parade...
        }

        String targetMarketId = marketPicker.pick();

        ParadeFleetData data = new ParadeFleetData(this, name, targetMarketId,paradeMembers,DEFAULT_DAYS);
        paradeFleetData.add(data);
        data.activateAndSpawn();
    }

    public void spawnSpecificParade(ParadeFleetData data){
        CargoAPI cargo = submarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();

        int num = 0;
        for (String memberId : data.fleetMemberIdList) for (FleetMemberAPI m : members) if (m.getId().equals(memberId) && !m.getVariant().hasTag(ON_PARADE_TAG)) num++;

        if (num <= 0) {
            ModPlugin.log("Aborting custom parade creation, no fleet members");
            return; //no members, no parade
        }

        MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(data.targetMarketId);
        if (targetMarket == null) {
            ModPlugin.log("Aborting custom parade creation, no target market");
            return;
        }

        data.activateAndSpawn();
    }


    public void setNamePreset(String namePreset) {
        this.namePreset = namePreset;
    }

    public void setMemberList(List<FleetMemberAPI> members) {
        this.memberPreset = members;
    }

    public void resetNamePreset(){
        namePreset = null;
    }

    public void resetMembers(){
        memberPreset = null;
    }

    public CampaignFleetAPI getCurrentFleet(){
        return currentData != null && currentData.isActive() ? currentData.getActiveFleet() : null;
    }

    public boolean isActive(){
        return currentData != null && currentData.isActive();
    }
}
