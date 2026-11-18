package indevo.industries.petshop.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class PetStatusManager extends BaseCampaignEventListener implements EconomyTickListener, FleetEventListener, EveryFrameScript, ShowLootListener, SurveyPlanetListener, DiscoverEntityListener {

    public static void dev() {
        Pet pet = new Pet("slaghound", "Hartleys Pile o' Rocks");
        getInstance().register(pet);
        pet.place(Global.getSector().getPlayerFleet().getFlagship(), null);

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
        EUTHANIZED,
        REHOMED,
    }

    private Map<String, Float> foodTakenLastMonth = new HashMap<>(); //contains a list of commodity and amount taken last month for feeding purposes

    public static PetStatusManager getInstance() {
        ListenerManagerAPI listenerManagerAPI = Global.getSector().getListenerManager();

        if (listenerManagerAPI.hasListenerOfClass(PetStatusManager.class)) {
            return listenerManagerAPI.getListeners(PetStatusManager.class).get(0);
        } else {
            PetStatusManager manager = new PetStatusManager();
            Global.getSector().getListenerManager().addListener(manager);
            Global.getSector().addScript(manager);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null) fleet.addEventListener(manager);

            return manager;
        }
    }

    private List<Pet> pets = new ArrayList<>();
    private float daysSinceLine = 0f;
    private float lineRoll = 0f;
    private float wantGapDays = 0f;
    // the message list sits under the Pets panel, so its messages wait until it closes
    private transient List<Runnable> held = null;

    // the first pet is starred for the player
    public void register(Pet pet) {
        pets.add(pet);
        if (getFavourites().isEmpty()) pet.setFavourite(true);
    }

    public List<Pet> getFavourites() {
        List<Pet> list = new ArrayList<>();
        for (Pet pet : pets) if (!pet.isDead() && pet.favourite) list.add(pet);
        return list;
    }

    public boolean canFavourite(Pet pet) {
        return pet.favourite || getFavourites().size() < PetConstants.MAX_FAVOURITES;
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
        if (!Setting.PETS.enabled()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.getEventListeners().contains(this)) fleet.addEventListener(this);

        for (Pet pet : new ArrayList<>(pets)) pet.advance(amount);
        advanceLines(amount);
        advanceWants(amount);
    }

    // one want open in the fleet at a time, from a favourite whose cooldown is over
    private void advanceWants(float amount) {
        wantGapDays = Math.max(0f, wantGapDays - Global.getSector().getClock().convertToDays(amount));
        if (!canOpenWant()) return;

        WeightedRandomPicker<Pet> ready = new WeightedRandomPicker<>(Misc.random);
        for (Pet pet : getFavourites()) if (isReady(pet)) ready.add(pet);
        Pet pet = ready.pick();
        if (pet == null) return;

        WeightedRandomPicker<PetWant> wants = new WeightedRandomPicker<>(Misc.random);
        wants.add(PetWant.ATTENTION);
        wants.add(PetWant.PLAY);
        wants.add(PetWant.SHORE_LEAVE);
        wants.add(PetWant.COME_ALONG);
        if (pet.getData().getFavouriteFood() != null) wants.add(PetWant.TREAT);
        openWant(pet, wants.pick());
    }

    private boolean canOpenWant() {
        return wantGapDays <= 0f && getOpenWant() == null;
    }

    private static boolean isReady(Pet pet) {
        return isFree(pet) && pet.isWithPlayer();
    }

    private static boolean isFree(Pet pet) {
        return pet.favourite && pet.want == null && pet.wantCooldownDays <= 0f;
    }

    public Pet getOpenWant() {
        for (Pet pet : pets) if (!pet.isDead() && pet.want != null) return pet;
        return null;
    }

    public void openWant(Pet pet, PetWant want) {
        pet.openWant(want);
        say(pet, want.event(), null);
    }

    public void wantEnded() {
        wantGapDays = PetConstants.WANT_GAP_DAYS;
    }

    private void answer(PetWant want) {
        for (Pet pet : new ArrayList<>(pets)) if (pet.want == want && pet.isWithPlayer()) pet.answerWant();
    }

    // one unit of its favourite food from the cargo it eats from
    public boolean hasTreat(Pet pet) {
        String food = pet.getData().getFavouriteFood();
        CargoAPI cargo = getFoodCargo(pet);
        return food != null && cargo != null && cargo.getCommodityQuantity(food) >= 1f;
    }

    public void treat(Pet pet) {
        if (pet.want != PetWant.TREAT || !hasTreat(pet)) return;

        getFoodCargo(pet).removeCommodity(pet.getData().getFavouriteFood(), 1f);
        pet.answerWant();
    }

    private void advanceLines(float amount) {
        int interval = Setting.PET_LINE_DAYS.intValue();
        if (interval <= 0) return;

        daysSinceLine += Global.getSector().getClock().convertToDays(amount);
        if (lineRoll <= 0f) lineRoll = 1f + Misc.random.nextFloat() * PetConstants.LINE_DAYS_SPREAD;
        if (daysSinceLine < interval * lineRoll) return;

        Pet pet = pickSpeaker();
        if (pet != null) say(pet, PetDataRepo.EVENT_AMBIENT, null);
        resetLines();
    }

    public boolean sayDocking(InteractionDialogAPI dialog) {
        if (!isLineDue()) return false;

        Pet pet = pickSpeaker();
        if (pet == null || !say(pet, PetDataRepo.EVENT_DOCKING, dialog)) return false;

        resetLines();
        return true;
    }

    // a jump or a fight takes the next line once the cadence allows one
    private void sayMoment(String event, Collection<Pet> speakers) {
        if (!isLineDue()) return;

        Pet pet = pickSpeaker(speakers);
        if (pet != null && say(pet, event, null)) resetLines();
    }

    private boolean isLineDue() {
        int interval = Setting.PET_LINE_DAYS.intValue();
        return interval > 0 && daysSinceLine >= interval;
    }

    public boolean say(Pet pet, String event, InteractionDialogAPI dialog) {
        String line = getLine(pet, event, isDocked(dialog));
        if (line == null) return false;

        Color hl = Misc.getHighlightColor();

        if (dialog != null) {
            dialog.getTextPanel().addPara(line, hl, pet.name);
            return true;
        }

        MessageIntel intel = new MessageIntel(line, Misc.getTextColor(), new String[]{pet.name}, hl);
        intel.setIcon(pet.getData().icon);
        Global.getSector().getCampaignUI().addMessage(intel);
        return true;
    }

    public String getLine(Pet pet, String event, boolean docked) {
        return getLine(pet, event, docked, Misc.random);
    }

    public String getLine(Pet pet, String event, boolean docked, Random random) {
        Set<String> context = new HashSet<>();
        if (pet.storage != null) context.add(PetDataRepo.CONTEXT_STORAGE);
        else if (docked) context.add(PetDataRepo.CONTEXT_DOCKED);
        if (pet.isOld()) context.add(PetDataRepo.CONTEXT_OLD);
        if (Global.getSector().getPlayerFleet().isInHyperspace()) context.add(PetDataRepo.CONTEXT_HYPERSPACE);

        PetDataRepo.Line line = PetDataRepo.pickLine(PetDataRepo.getLines(), event, pet.getData(), pet.getMood().id(), pet.sheet.disposition, context, random);
        return line != null ? line.text() : null;
    }

    private Pet pickSpeaker() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        List<Pet> speakers = new ArrayList<>();

        for (Pet pet : pets) {
            if (pet.isDead() || pet.storage != null) continue;

            FleetMemberAPI member = pet.assignedFleetMember;
            if (member != null && (member.getFleetData() == null || member.getFleetData().getFleet() != fleet)) continue;

            speakers.add(pet);
        }

        return pickSpeaker(speakers);
    }

    private Pet pickSpeaker(Collection<Pet> speakers) {
        WeightedRandomPicker<Pet> picker = new WeightedRandomPicker<>(Misc.random);

        for (Pet pet : speakers) {
            FleetMemberAPI member = pet.assignedFleetMember;
            float weight = member != null && member.isFlagship() ? PetConstants.FLAGSHIP_LINE_WEIGHT : 1f;
            picker.add(pet, pet.favourite ? weight * PetConstants.FAVOURITE_LINE_WEIGHT : weight);
        }

        return picker.pick();
    }

    private void resetLines() {
        daysSinceLine = 0f;
        lineRoll = 0f;
    }

    private static boolean isDocked(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getInteractionTarget() == null) return false;

        MarketAPI market = dialog.getInteractionTarget().getMarket();
        return market != null && !market.isPlanetConditionMarketOnly();
    }

    public Pet get(String id) {
        for (Pet pet : pets) {
            if (pet.id.equals(id)) return pet;
        }

        return null;
    }

    public List<Pet> getPets() {
        return new ArrayList<>(pets);
    }

    public List<Pet> getMenageriePets() {
        List<Pet> list = new ArrayList<>();
        for (Pet pet : pets) if (pet.isInMenagerie()) list.add(pet);
        return list;
    }

    public FleetMemberAPI getFreeShip(CampaignFleetAPI fleet, Collection<FleetMemberAPI> except) {
        if (fleet == null) return null;
        if (isFree(fleet.getFlagship(), except)) return fleet.getFlagship();

        FleetMemberAPI best = null;

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (!isFree(m, except)) continue;

            if (best == null
                    || m.getHullSpec().getHullSize().ordinal() > best.getHullSpec().getHullSize().ordinal()
                    || m.getHullSpec().getHullSize() == best.getHullSpec().getHullSize() && m.getDeploymentPointsCost() > best.getDeploymentPointsCost()) {
                best = m;
            }
        }

        return best;
    }

    // ships of that fleet a pet can move to, in fleet order
    public List<FleetMemberAPI> getFreeShips(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> free = new ArrayList<>();
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) if (isFree(m, Collections.emptySet())) free.add(m);
        return free;
    }

    private boolean isFree(FleetMemberAPI m, Collection<FleetMemberAPI> except) {
        return m != null && !m.isFighterWing() && !except.contains(m) && getPet(m.getVariant()) == null;
    }

    public boolean handOver(Pet pet, Collection<FleetMemberAPI> leaving) {
        FleetMemberAPI from = pet.assignedFleetMember;
        FleetMemberAPI to = getFreeShip(Global.getSector().getPlayerFleet(), leaving);
        if (to == null) return false;

        pet.place(to, null);

        String fromName = from != null ? from.getShipName() : "fleet";
        Global.getSector().getCampaignUI().addMessage("%s the " + pet.getData().species + " moved from the " + fromName + " to the %s", Misc.getTextColor(), pet.name, to.getShipName(), Misc.getHighlightColor(), Misc.getHighlightColor());
        return true;
    }

    public void reportScuttled(FleetDataAPI fleetData, FleetMemberAPI member) {
        if (!Setting.PETS.enabled() || fleetData.getFleet() == null || !fleetData.getFleet().isPlayerFleet()) return;

        Pet pet = getPet(member.getVariant());
        if (pet == null || pet.isDead()) return;

        if (!handOver(pet, Collections.singletonList(member))) reportPetDied(pet, PetDeathCause.UNKNOWN);
    }

    public boolean feed(Pet pet) {
        CargoAPI cargo = getFoodCargo(pet);
        float amount = pet.getFoodPerMonth();
        String foodToEat = getFood(pet.getData(), amount, cargo);
        if (foodToEat == null) return false;

        cargo.removeCommodity(foodToEat, amount);
        pet.lastMealSupplies = Commodities.SUPPLIES.equals(foodToEat);

        if (foodTakenLastMonth.containsKey(foodToEat))
            foodTakenLastMonth.put(foodToEat, foodTakenLastMonth.get(foodToEat) + amount);
        else foodTakenLastMonth.put(foodToEat, amount);

        return true;
    }

    // what the pet eats next from that cargo: its first real food, Supplies only when none is aboard
    private static String getFood(PetData data, float amount, CargoAPI cargo) {
        if (cargo == null) return null;

        for (String food : data.foodCommodities) {
            if (!Commodities.SUPPLIES.equals(food) && cargo.getCommodityQuantity(food) >= amount) return food;
        }

        boolean supplies = data.foodCommodities.contains(Commodities.SUPPLIES) && cargo.getCommodityQuantity(Commodities.SUPPLIES) >= amount;
        return supplies ? Commodities.SUPPLIES : null;
    }

    public boolean hasFood(Pet pet) {
        return getFood(pet.getData(), pet.getFoodPerMonth(), getFoodCargo(pet)) != null;
    }

    private CargoAPI getFoodCargo(Pet pet) {
        return pet.assignedFleetMember != null ? getFoodCargo(pet.assignedFleetMember) : Global.getSector().getPlayerFleet().getCargo();
    }

    private CargoAPI getFoodCargo(FleetMemberAPI member) {
        FleetDataAPI fleetData = member.getFleetData();
        if (fleetData == null) return null;
        if (fleetData.getFleet() != null) return fleetData.getFleet().getCargo();

        // a stored ship's fleet data is the mothballed ships list of the submarket cargo holding it
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                CargoAPI cargo = submarket.getCargoNullOk();
                if (cargo != null && cargo.getMothballedShips() == fleetData) return Misc.getStorageCargo(market);
            }
        }

        return null;
    }

    private void doRoutineAliveCheck() {
        for (Pet pet : new ArrayList<>(pets)) {
            if (!pet.isDead() && pet.isAssigned()) {
                if (pet.assignedFleetMember != null) {
                    FleetDataAPI fleetData = pet.assignedFleetMember.getFleetData();

                    // ships in storage have fleet data without a fleet, their pets starve instead
                    if (fleetData == null || (fleetData.getFleet() != null && !fleetData.getFleet().isAlive())) {
                        reportPetDied(pet, PetDeathCause.UNKNOWN);
                    }
                }
            }
        }
    }

    public void reportPetDied(Pet pet, PetDeathCause cause) {
        if (pet.isDead()) return;

        WeightedRandomPicker<String> picker;

        ModPlugin.log("Pet died, cause: " + cause.name() + " pet: " + pet.name);

        // https://fractalsoftworks.com/forum/index.php?topic=18011.msg415042#msg415042
        String petLocation;
        if (pet.assignedFleetMember != null) petLocation = pet.assignedFleetMember.getShipName();
        else if (pet.storage != null) petLocation = pet.storage.getCurrentName();
        else petLocation = "fleet";
        switch (cause) {
            case COMBAT:
                picker = new WeightedRandomPicker<>();
                picker.addAll(MiscIE.getCSVSetFromMemory(COMBAT_DEATH_CAUSES));
                message("%s the " + pet.getData().species + ", living on the destroyed " + petLocation + ", " + picker.pick() + "", Misc.getTextColor(), pet.name, null, Misc.getHighlightColor(), null);
                break;
            case NATURAL:
                String message = "";
                if (pet.typeID.equals("hampter")) {
                    picker = new WeightedRandomPicker<>();
                    picker.addAll(MiscIE.getCSVSetFromMemory(HAMSTER_DEATH_CAUSES));
                    message = picker.pick();
                } else message = pet.getData().naturalDeath;

                if (pet.isAssigned()) message("%s the " + pet.getData().species + ", formerly living on the " + petLocation + ", %s", Misc.getTextColor(), pet.name, message, Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                else if (pet.storage != null)  message("%s the " + pet.getData().species + ", formerly living at the " + pet.storage.getMarket().getName() + " " + Global.getSettings().getIndustrySpec(Ids.PET_STORE).getName().toLowerCase() + ", %s", Misc.getTextColor(), pet.name, message, Misc.getHighlightColor(), Misc.getNegativeHighlightColor());

                break;
            case STARVED:
                message("%s the " + pet.getData().species + " has %s on the " + petLocation, Misc.getTextColor(), pet.name, "starved to death", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case SOLD:
                message("%s the " + pet.getData().species + " was sold with the " + petLocation + " and promptly %s by the new owners.", Misc.getTextColor(), pet.name, "euthanized", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case UNKNOWN:
                //if sold and not caught or magic'd away
                message("%s the " + pet.getData().species + ", formerly of the " + petLocation + ", has %s", Misc.getTextColor(), pet.name, "died.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case EUTHANIZED:
                message("%s the " + pet.getData().species + " has been %s", Misc.getTextColor(), pet.name, "euthanized.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
                break;
            case REHOMED:
                message("%s the " + pet.getData().species + " went to a new home.", Misc.getTextColor(), pet.name, null, Misc.getHighlightColor(), null);
                break;
        }

        pet.setDead(cause);
    }

    public void holdMessages() {
        if (held == null) held = new ArrayList<>();
    }

    public void releaseMessages() {
        List<Runnable> out = held;
        held = null;
        if (out != null) out.forEach(Runnable::run);
    }

    private void message(String format, Color color, String hl1, String hl2, Color hlColor1, Color hlColor2) {
        Runnable post = () -> Global.getSector().getCampaignUI().addMessage(format, color, hl1, hl2, hlColor1, hlColor2);
        if (held != null) held.add(post);
        else post.run();
    }

    public void reportTitle(Pet pet, PetTitle title) {
        message("%s the " + pet.getData().species + " earned a title: %s.", Misc.getTextColor(), pet.name, title.label, Misc.getHighlightColor(), Misc.getHighlightColor());
    }

    public void reportTalentShown(Pet pet, PetDataRepo.Trait trait) {
        message("%s the " + pet.getData().species + " shows a talent: %s.", Misc.getTextColor(), pet.name, trait.name(), Misc.getHighlightColor(), Misc.getHighlightColor());
    }

    public void reportRoomLost(Pet pet, FleetMemberAPI member, List<String> lost) {
        String them = lost.size() > 1 ? "them" : "it";
        message("The " + member.getShipName() + " lost %s, %s no longer makes room for " + them + ".", Misc.getTextColor(), Misc.getAndJoined(lost), pet.name, Misc.getNegativeHighlightColor(), Misc.getHighlightColor());
    }

    public void reportPetStarving(Pet pet) {
        message("%s the " + pet.getData().species + " has started %s", Misc.getTextColor(), pet.name, "starving. Feed it or it will die.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {
        if (!Setting.PETS.enabled()) return;

        doRoutineAliveCheck();
        showFoodMessage();
        foodTakenLastMonth.clear();
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        fleet.removeEventListener(this);

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
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

        boolean stored = transaction.getSubmarket().getPlugin().isFreeTransfer();
        boolean forgotPet = false;

        for (Pet pet : new ArrayList<>(pets)) {
            if (!pet.isAssigned() || !memberList.contains(pet.assignedFleetMember)) continue;
            if (handOver(pet, memberList)) continue;

            if (stored) forgotPet = true;
            else reportPetDied(pet, PetDeathCause.SOLD);
        }

        if (forgotPet) showPetForgottenMessage(transaction.getSubmarket());
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
    }

    // the global report also comes for battles of detachments, the fleet report only for fleets that carry this listener
    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!Setting.PETS.enabled()) return;

        for (CampaignFleetAPI fleet : battle.getSnapshotBothSides()) {
            if (!fleet.isPlayerFleet() && !FleetUtils.isDetachmentFleet(fleet)) continue;

            List<FleetMemberAPI> lost = Misc.getSnapshotMembersLost(fleet);
            for (Pet pet : new ArrayList<>(pets)) {
                if (!pet.isDead() && lost.contains(pet.assignedFleetMember)) escapeOrDie(pet, fleet, lost);
            }
        }
    }

    // autoresolve reports every round with the same snapshot, a survivor has left the lost ship by then
    private void escapeOrDie(Pet pet, CampaignFleetAPI fleet, List<FleetMemberAPI> lost) {
        if (Misc.random.nextFloat() >= pet.getHearts() * PetConstants.POD_CHANCE_PER_HEART) {
            reportPetDied(pet, PetDeathCause.COMBAT);
            return;
        }

        String ship = pet.assignedFleetMember.getShipName();
        FleetMemberAPI to = fleet.isPlayerFleet() ? null : getFreeShip(fleet, lost);
        pet.place(to, null);
        pet.shellShock();
        pet.earn(PetTitle.SURVIVOR);

        String start = "%s the " + pet.getData().species + " escaped the destroyed " + ship + " in an escape pod";
        if (to == null) message(start + " and waits in the %s.", Misc.getTextColor(), pet.name, "menagerie", Misc.getHighlightColor(), Misc.getHighlightColor());
        else message(start + " and moved to the %s.", Misc.getTextColor(), pet.name, to.getShipName(), Misc.getHighlightColor(), Misc.getHighlightColor());
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        if (!Setting.PETS.enabled()) return;

        for (EngagementResultForFleetAPI side : Arrays.asList(result.getWinnerResult(), result.getLoserResult())) {
            if (side == null || !side.isPlayer()) continue;

            List<FleetMemberAPI> fought = new ArrayList<>(side.getDeployed());
            fought.addAll(side.getRetreated());
            List<Pet> speakers = new ArrayList<>();

            for (FleetMemberAPI member : fought) {
                Pet pet = getPet(member.getVariant());
                if (pet == null || pet.isDead()) continue;

                pet.shellShock();
                pet.addBond(PetConstants.EVENT_BOND_DAYS);
                pet.addFight();
                // in battle the member sits in the battle's combined fleet, so isWithPlayer is false; its source fleet tells
                boolean withPlayer = result.getBattle() != null && result.getBattle().getSourceFleet(member) == Global.getSector().getPlayerFleet();
                if (!withPlayer) continue;

                speakers.add(pet);
                if (canOpenWant() && isFree(pet)) openWant(pet, PetWant.COMFORT);
            }

            sayMoment(PetDataRepo.EVENT_FIGHT, speakers);
        }
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        if (!fleet.isPlayerFleet() || !Setting.PETS.enabled()) return;

        List<Pet> speakers = new ArrayList<>();
        for (Pet pet : pets) {
            if (!pet.isAboard() || !pet.isWithPlayer()) continue;

            pet.addBond(PetConstants.EVENT_BOND_DAYS);
            pet.addJump();
            speakers.add(pet);
        }

        sayMoment(PetDataRepo.EVENT_JUMP, speakers);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        if (market == null || market.isPlanetConditionMarketOnly()) return;

        answer(PetWant.SHORE_LEAVE);
        if (!Setting.PETS.enabled()) return;
        for (Pet pet : pets) if (pet.isWithPlayer()) pet.addMarket(market.getId());
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        answer(PetWant.COME_ALONG);
        if (Setting.PETS.enabled() && loot != null && dialog != null) scavenge(loot, dialog.getInteractionTarget());
    }

    // battle loot has a fleet as target; seeded by the wreck, so a reload finds the same
    private void scavenge(CargoAPI loot, SectorEntityToken target) {
        if (target == null || !target.hasTag(Tags.SALVAGEABLE)) return;

        Pet pet = getLead(Global.getSector().getPlayerFleet(), PetHullmod.SCAVENGER);
        if (pet == null) return;

        Random random = Misc.getRandom(Misc.getSalvageSeed(target), 23);
        if (random.nextFloat() >= PetConstants.SCAVENGER_FIND_CHANCE) return;

        DropData find = new DropData();
        find.group = Drops.BASIC;
        find.value = PetConstants.SCAVENGER_FIND_VALUE;

        DropData rare = new DropData();
        rare.group = PetConstants.SCAVENGER_RARE_GROUP;
        rare.chances = 1;

        float mult = 1f + pet.getTalentValue(PetHullmod.SCAVENGER) * 0.01f;
        CargoAPI found = SalvageEntity.generateSalvage(random, mult, 1f, 1f, 1f, List.of(find), List.of(rare));
        if (found.isEmpty()) return;

        loot.addAll(found);
        reportFind(pet, found);
        pet.addFind();
    }

    // the loot screen hides the dialog text and vanilla salvage closes the dialog after it, so the line goes to the message area
    private void reportFind(Pet pet, CargoAPI found) {
        String line = getLine(pet, PetDataRepo.EVENT_SCAVENGER, false);
        if (line == null) return;

        Color hl = Misc.getHighlightColor();
        MessageIntel intel = new MessageIntel(line, Misc.getTextColor(), new String[]{pet.name}, hl);
        for (CargoStackAPI stack : found.getStacksCopy()) {
            int size = Math.round(stack.getSize());
            String item = size > 1 ? Misc.getWithDGS(size) + " " + stack.getDisplayName() : stack.getDisplayName();
            intel.addLine(BaseIntelPlugin.BULLET + "%s", Misc.getTextColor(), new String[]{item}, hl);
        }

        intel.setIcon(pet.getData().icon);
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    @Override
    public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
        answer(PetWant.COME_ALONG);
    }

    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {
        answer(PetWant.COME_ALONG);
    }

    private void showFoodMessage() {
        if (foodTakenLastMonth.isEmpty()) return;

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
                    new String[]{(amount + " units")},
                    Misc.getHighlightColor());
        }

        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "pets_small"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    private void showPetForgottenMessage(final SubmarketAPI onMarket) {
        Global.getSector().addScript(new EveryFrameScript() {
            boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return false;
            }

            @Override
            public void advance(float amount) {
                if (!done){
                    Color c = Misc.getHighlightColor();

                    MessageIntel intel = new MessageIntel(
                            "You %s some %s on ships you stored at the %s!",
                            Misc.getTextColor(),
                            new String[]{"forgot", "pets", onMarket.getMarket().getName() + " " + onMarket.getNameOneLine()},
                            Misc.getNegativeHighlightColor(), onMarket.getFaction().getColor());
                    intel.addLine("They will starve to death unless fed.");
                    intel.addLine("Store or sell them at a %s if you no longer want them.",
                            Misc.getTextColor(),
                            new String[]{Global.getSettings().getIndustrySpec(Ids.PET_STORE).getName()},
                            c);

                    intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "warning"));
                    intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                    Global.getSector().getCampaignUI().addMessage(intel);
                }

                done = true;
            }
        });
    }

    // fleet talents count once per fleet: the pet with the highest value, then more hearts, then the lower id
    public Pet getLead(CampaignFleetAPI fleet, String traitId) {
        Pet lead = null;
        float best = 0f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isMothballed()) continue;

            Pet pet = getPet(member.getVariant());
            float value = pet != null ? pet.getTalentValue(traitId) : 0f;
            if (value <= 0f) continue;

            boolean better = lead == null || value > best
                    || value == best && (pet.getHearts() > lead.getHearts() || pet.getHearts() == lead.getHearts() && pet.id.compareTo(lead.id) < 0);
            if (better) {
                lead = pet;
                best = value;
            }
        }

        return lead;
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
