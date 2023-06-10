package indevo.industries.petshop.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.utils.ModPlugin;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PetStatusManager extends BaseCampaignEventListener implements EconomyTickListener, FleetEventListener, EveryFrameScript {

    public static void dev() {
        Pet pet = new Pet("slaghound", "Hartleys Pile o' Rocks");
        getInstance().register(pet);
        pet.assign(Global.getSector().getPlayerFleet().getFlagship());

        //runcode indevo.industries.petshop.listener.PetStatusManager.dev()
    }

    public static final String HAMSTER_DEATH_CAUSES = "data/strings/hamster_death_causes.csv";
    public static final String COMBAT_DEATH_CAUSES = "data/strings/combat_death_causes.csv";

    public PetStatusManager() {
        super(true);
    }

    public enum PetDeathCause {
        COMBAT,
        NATURAL,
        STARVED,
        SOLD,
        UNKNOWN,
    }

    private Map<String, Float> foodTakenLastMonth = new HashMap<>(); //contains a list of commodity and amount taken last month for feeding purposes

    public static PetStatusManager getInstance() {
        ListenerManagerAPI listenerManagerAPI = Global.getSector().getListenerManager();

        if (listenerManagerAPI.hasListenerOfClass(PetStatusManager.class))
            return listenerManagerAPI.getListeners(PetStatusManager.class).get(0);
        else {
            PetStatusManager manager = new PetStatusManager();
            listenerManagerAPI.addListener(manager);
            Global.getSector().addListener(manager);
            Global.getSector().addScript(manager);

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null) fleet.addEventListener(manager);

            return manager;
        }
    }

    private List<Pet> pets = new ArrayList<>();

    public void register(Pet pet) {
        pets.add(pet);
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
        if (!fleet.getEventListeners().contains(this)) fleet.addEventListener(this);

        for (Pet pet : pets) pet.advance(amount);
    }

    public Pet get(String id) {
        for (Pet pet : pets) {
            if (pet.id.equals(id)) return pet;
        }

        return null;
    }

    public boolean feed(Pet pet) {
        FleetDataAPI fleetData = pet.assignedFleetMember.getFleetData();
        if (fleetData == null || fleetData.getFleet() == null || !fleetData.getFleet().isAlive()) {
            //this should be caught but just in case
            reportPetDied(pet, PetDeathCause.UNKNOWN);
            return false;
        }

        CargoAPI fleetCargo = fleetData.getFleet().getCargo();

        boolean hasFood = false;
        String foodToEat = "";

        PetData data = pet.getData();

        for (String food : data.foodCommodities) {
            if (fleetCargo.getCommodityQuantity(food) >= data.foodPerMonth) {
                hasFood = true;
                foodToEat = food;
                break;
            }
        }

        if (!hasFood) return false;

        fleetCargo.removeCommodity(foodToEat, data.foodPerMonth);

        if (foodTakenLastMonth.containsKey(foodToEat))
            foodTakenLastMonth.put(foodToEat, foodTakenLastMonth.get(foodToEat) + data.foodPerMonth);
        else foodTakenLastMonth.put(foodToEat, data.foodPerMonth);

        return true;
    }

    private void doRoutineAliveCheck() {
        for (Pet pet : new ArrayList<>(pets)) {
            if (!pet.isDead() && pet.isActive()) {
                if (pet.assignedFleetMember != null) {
                    FleetDataAPI fleetData = pet.assignedFleetMember.getFleetData();

                    if (fleetData == null || fleetData.getFleet() == null || !fleetData.getFleet().isAlive()) {
                        reportPetDied(pet, PetDeathCause.UNKNOWN);
                    }
                }
            }
        }
    }

    public void reportPetDied(Pet pet, PetDeathCause cause) {
        WeightedRandomPicker<String> picker;

        ModPlugin.log("Pet died, cause: " + cause.name() + " pet: " + pet.name);

        switch (cause) {
            case COMBAT:
                picker = new WeightedRandomPicker<>();
                picker.addAll(IndustryHelper.getCSVSetFromMemory(COMBAT_DEATH_CAUSES));
                Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " living on the destroyed " + pet.assignedFleetMember.getShipName() + " " + picker.pick() + ".", Misc.getTextColor(), pet.name, null, Misc.getHighlightColor(), null);
                break;
            case NATURAL:
                String message = "";
                if (pet.typeID.equals("hamster")) {
                    picker = new WeightedRandomPicker<>();
                    picker.addAll(IndustryHelper.getCSVSetFromMemory(HAMSTER_DEATH_CAUSES));
                    message = picker.pick();
                } else message = pet.getData().naturalDeath;

                Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " formerly living on the " + pet.assignedFleetMember.getShipName() + ", %s.", Misc.getTextColor(), pet.name, message, Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case STARVED:
                Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " has %s on the " + pet.assignedFleetMember.getShipName(), Misc.getTextColor(), pet.name, "starved to death.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case SOLD:
                Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " was sold with the " + pet.assignedFleetMember.getShipName() + " and promptly %s by the new owners.", Misc.getTextColor(), pet.name, "euthanized", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case UNKNOWN:
                //if sold and not caught or magic'd away
                Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + ", formerly of the " + pet.assignedFleetMember.getShipName() + ", has %s", Misc.getTextColor(), pet.name, "died.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
        }

        pet.setDead(cause);
    }

    public void reportPetStarving(Pet pet) {
        Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " has started %s", Misc.getTextColor(), pet.name, "starving. Feed it or it will die.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        doRoutineAliveCheck();

        if (iterIndex == Global.getSettings().getInt("economyIterPerMonth") / 2) {
            showFoodMessage();
            foodTakenLastMonth.clear();
        }
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        fleet.removeEventListener(this);

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()){
            if (getPet(m.getVariant()) != null) reportPetDied(getPet(m.getVariant()), PetDeathCause.UNKNOWN);
        }
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        super.reportPlayerMarketTransaction(transaction);

        CargoAPI sold = transaction.getSold();
        if (sold == null) return;

        List<PlayerMarketTransaction.ShipSaleInfo> soldList = transaction.getShipsSold();
        List<FleetMemberAPI> memberList = new ArrayList<>();

        for (PlayerMarketTransaction.ShipSaleInfo info : soldList) memberList.add(info.getMember());

        if (memberList.isEmpty()) return;

        if (transaction.getSubmarket().getPlugin().isFreeTransfer()) {
            showPetForgottenMessage(transaction.getSubmarket());
            return;
        }

        for (Pet pet : new ArrayList<>(pets)) {
            if (!pet.isActive()) continue;

            if (memberList.contains(pet.assignedFleetMember)) {
                reportPetDied(pet, PetDeathCause.SOLD);
            }
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        List<FleetMemberAPI> membersLost = Misc.getSnapshotMembersLost(fleet);

        for (Pet pet : new ArrayList<>(pets)) {
            if (membersLost.contains(pet.assignedFleetMember)) {
                reportPetDied(pet, PetDeathCause.COMBAT);
            }
        }
    }

    private void showFoodMessage() {
        Color c = Misc.getHighlightColor();

        MessageIntel intel = new MessageIntel(
                "Report: Food consumed by %s in the last month:",
                Misc.getTextColor(),
                new String[]{"your pets"},
                c);

        for (Map.Entry<String, Float> entry : foodTakenLastMonth.entrySet()) {
            String name = Global.getSettings().getCommoditySpec(entry.getKey()).getName();
            int amount = (int) Math.ceil(entry.getValue());

            intel.addLine(BaseIntelPlugin.BULLET + name + ": %s",
                    Misc.getTextColor(),
                    new String[]{(amount + StringHelper.getString("unitsWithFrontSpace"))},
                    Misc.getHighlightColor());
        }

        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "pets_small"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    private void showPetForgottenMessage(SubmarketAPI onMarket) {
        Color c = Misc.getHighlightColor();

        MessageIntel intel = new MessageIntel(
                "You %s some %s on ships you stored at the %s!",
                Misc.getTextColor(),
                new String[]{"forgot", "pets", onMarket.getMarket().getName() + " " + onMarket.getNameOneLine()},
                Misc.getNegativeHighlightColor(), onMarket.getFaction().getColor());
        intel.addLine("They will starve to death if there is insufficient food present.");
        intel.addLine("Store or sell them at a %s if you no longer want them.",
                Misc.getTextColor(),
                new String[]{Global.getSettings().getIndustrySpec(Ids.PET_STORE).getName()},
                c);

        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "warning"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    public Pet getPet(ShipVariantAPI variant) {
        Pet pet = null;

        for (String tag : variant.getTags()) {
            if (tag.contains(Pet.HULLMOD_DATA_PREFIX)) {
                String id = tag.substring(Pet.HULLMOD_DATA_PREFIX.length());
                pet = get(id);
                break;
            }
        }

        return pet;
    }
}
