package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.plugins.PetCard;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;

public class Pet {
    public static final float DEFAULT_FEED_INTERVAL = 31f;
    public static final String HULLMOD_DATA_PREFIX = "PET_DATA_ID_";

    public String typeID;
    public String id;
    public String name;
    public float age = 1f;
    public float lifetime;
    public FleetMemberAPI assignedFleetMember = null;
    public IntervalUtil feedInterval = new IntervalUtil(Global.getSector().getClock().getSecondsPerDay() * DEFAULT_FEED_INTERVAL, Global.getSector().getClock().getSecondsPerDay() * DEFAULT_FEED_INTERVAL);
    public String personality;

    private boolean isAssigned = false;
    private boolean isStarving = false;
    private boolean isDead = false;
    public Industry storage = null;

    public DeathData deathData = null;

    public float unsettledDays = 0f;
    public float shellShockedDays = 0f;
    public float happyDays = 0f;
    public boolean lastMealSupplies = false;
    public String homeShipId = null;
    // hearts are read from it, never stored
    public float bondDays = 0f;
    // 0: the nest stands once it has NEST_HEARTS
    public float nestRebuildDays = 0f;

    public Pet(String typeID, String name) {
        this.id = Misc.genUID();
        this.typeID = typeID;
        this.name = name;
        this.lifetime = MathUtils.getRandomNumberInRange(0.9f, 1.1f) * getData().maxLife;

        WeightedRandomPicker<String> personalities = new WeightedRandomPicker<>();
        personalities.addAll(Academy.COLOURS_BY_PERSONALITY.keySet());

        this.personality = personalities.pick();

        if (Global.getSettings().isDevMode()) this.feedInterval = new IntervalUtil(10f, 10f); //10 seconds
    }

    public void place(FleetMemberAPI toMember, Industry toStorage) {
        leave();

        if (toMember != null) {
            if (homeShipId != null && !toMember.getId().equals(homeShipId)) {
                boolean nest = getHearts() >= PetConstants.NEST_HEARTS && nestRebuildDays <= 0f;
                unsettledDays = nest ? PetConstants.NEST_UNSETTLED_DAYS : PetConstants.UNSETTLED_DAYS;
                if (nest) nestRebuildDays = PetConstants.NEST_REBUILD_DAYS;
            }

            homeShipId = toMember.getId();
            attach(toMember);
        } else if (toStorage != null) {
            storage = toStorage;
            ((PetShop) toStorage).store(this);
        }
    }

    public void shellShock() {
        shellShockedDays = PetConstants.SHELL_SHOCKED_DAYS;
    }

    public void pet() {
        happyDays = PetConstants.HAPPY_DAYS;
    }

    public static void cycleToCustomVariant(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();

        variant = variant.clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        variant.setSource(VariantSource.REFIT);
        member.setVariant(variant, false, true);
    }

    private void attach(FleetMemberAPI toMember) {
        ModPlugin.log("assigning " + name + " variety " + typeID + " to " + toMember.getShipName() + " with source " + toMember.getVariant().getSource().name());

        if (toMember.getVariant().getSource() != VariantSource.REFIT) cycleToCustomVariant(toMember);

        assignedFleetMember = toMember;
        isAssigned = true;
        toMember.getVariant().addPermaMod(getData().hullmod);
        toMember.getVariant().addTag(HULLMOD_DATA_PREFIX + id);
    }

    private void leave() {
        if (storage != null) {
            ((PetShop) storage).removeFromStorage(this);
            storage = null;
        }

        if (assignedFleetMember == null) return;

        ShipVariantAPI variant = assignedFleetMember.getVariant();
        String hullmod = getData().hullmod;

        variant.removePermaMod(hullmod);
        variant.removeMod(hullmod);

        for (String s : new ArrayList<>(variant.getTags())) {
            if (s.startsWith(HULLMOD_DATA_PREFIX)) variant.getTags().remove(s);
        }

        assignedFleetMember = null;
        isAssigned = false;
    }

    public PetData getData() {
        return PetDataRepo.get(typeID);
    }

    public void advance(float amt) {
        if (isDead) return;

        boolean ages = storage == null || !Commodities.BETA_CORE.equals(storage.getAICoreId());
        float dayAmt = Global.getSector().getClock().convertToDays(amt);

        if (ages) {
            age += dayAmt;

            if (age > lifetime) {
                PetStatusManager.getInstance().reportPetDied(this, PetStatusManager.PetDeathCause.NATURAL);
            }
        }

        unsettledDays = Math.max(0f, unsettledDays - dayAmt);
        shellShockedDays = Math.max(0f, shellShockedDays - dayAmt);
        happyDays = Math.max(0f, happyDays - dayAmt);

        if (storage != null) return;

        feedInterval.advance(amt);
        if (isAboard()) {
            nestRebuildDays = Math.max(0f, nestRebuildDays - dayAmt);
            float cap = PetConstants.HEART_DAYS[PetConstants.TIME_HEARTS - 1];
            if (!isStarving && !isUnsettled() && bondDays < cap) bondDays = Math.min(cap, bondDays + dayAmt * getHeartRate());
        }

        if (feedInterval.intervalElapsed()) {
            PetStatusManager manager = PetStatusManager.getInstance();
            boolean hasFed = manager.feed(this);

            if (!hasFed && isStarving) manager.reportPetDied(this, PetStatusManager.PetDeathCause.STARVED);
            else if (!hasFed) {
                isStarving = true;
                manager.reportPetStarving(this);
            } else if (isStarving) isStarving = false;
        }
    }

    public float getDaysToMeal() {
        return Global.getSector().getClock().convertToDays(Math.max(0f, feedInterval.getIntervalDuration() - feedInterval.getElapsed()));
    }

    public boolean isDead() {
        return isDead;
    }

    public boolean isStarving() {
        return isStarving;
    }

    public boolean isAssigned() {
        return isAssigned;
    }

    public boolean isInMenagerie() {
        return !isDead && !isAssigned && storage == null;
    }

    public boolean isOld() {
        return age > lifetime * PetConstants.SLEEPY_AGE_FRACTION;
    }

    public boolean isUnsettled() {
        return unsettledDays > 0f;
    }

    public PetMood getMood() {
        if (isStarving || storage == null && !PetStatusManager.getInstance().hasFood(this)) return PetMood.HUNGRY;
        if (isUnsettled()) return PetMood.UNSETTLED;
        if (shellShockedDays > 0f) return PetMood.SHELL_SHOCKED;
        if (lastMealSupplies) return PetMood.UNHAPPY_WITH_FOOD;
        if (happyDays > 0f) return PetMood.HAPPY;
        if (isOld()) return PetMood.SLEEPY;
        return PetMood.CONTENT;
    }

    public void setDead(PetStatusManager.PetDeathCause cause) {
        isDead = true;
        generateDeathData(cause);
        leave();
    }

    // on a ship of a live fleet, the player's or a detachment; a ship in storage is not aboard
    public boolean isAboard() {
        if (assignedFleetMember == null || assignedFleetMember.getFleetData() == null) return false;
        return assignedFleetMember.getFleetData().getFleet() != null;
    }

    public float getHeartRate() {
        return lastMealSupplies ? PetConstants.SUPPLIES_HEART_RATE : 1f;
    }

    public int getHearts() {
        int hearts = 0;
        while (hearts < PetConstants.MAX_HEARTS && bondDays >= PetConstants.HEART_DAYS[hearts]) hearts++;
        return hearts;
    }

    // share of the way to the next heart, 1 at the last
    public float getHeartProgress() {
        int hearts = getHearts();
        if (hearts >= PetConstants.MAX_HEARTS) return 1f;

        float from = hearts == 0 ? 0f : PetConstants.HEART_DAYS[hearts - 1];
        return Math.max(0f, Math.min(1f, (bondDays - from) / (PetConstants.HEART_DAYS[hearts] - from)));
    }

    // days aboard to the next heart at today's rate, -1 when time alone does not reach it
    public float getDaysToHeart() {
        int hearts = getHearts();
        if (hearts >= PetConstants.TIME_HEARTS) return -1f;
        return (PetConstants.HEART_DAYS[hearts] - bondDays) / getHeartRate();
    }

    public boolean hasNest() {
        return isAboard() && getHearts() >= PetConstants.NEST_HEARTS && nestRebuildDays <= 0f;
    }

    public float getMascotMult() {
        if (isUnsettled() || getHearts() < PetConstants.MASCOT_HEARTS) return 0f;

        float mult = 1f;
        if (getData().isHamster()) mult *= PetConstants.HAMSTER_MASCOT_MULT;
        if (hasNest()) mult *= PetConstants.NEST_MULT;
        return mult;
    }

    // kept for copies of indevo.other.YourMod_PetName_PetHullmod
    public float getEffectFract() {
        return getMascotMult();
    }

    public String getAgeString(){
        int years = (int) Math.ceil(age / 364f);
        int months = (int) Math.ceil(age / 31f);

        if (age < 31) return Misc.getStringForDays((int) Math.ceil(age));
        else if (age < 365) return months + (months <= 1 ? " month" : " months");
        else return years + (years <= 1 ? " year" : " years");
    }

    public DeathData generateDeathData(PetStatusManager.PetDeathCause cause){
        //String name, float date, int age, float serveTime, PetStatusManager.PetDeathCause cause, boolean inStorage, String marketName, String shipName
        deathData = new DeathData(name,
                Global.getSector().getClock().getDateString(),
                age,
                bondDays,
                cause,
                storage != null,
                (storage != null ? storage.getMarket().getName() : null),
                (assignedFleetMember != null ? assignedFleetMember.getShipName() : null));

        return deathData;
    }

    // kept for copies of indevo.other.YourMod_PetName_PetHullmod
    public void addHullmodDescriptionSection(TooltipMakerAPI tooltip) {
        PetCard.hover(tooltip, this, true, false);
    }
}
