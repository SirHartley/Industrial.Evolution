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
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.plugins.PetCard;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public PetSheet sheet;

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
    public boolean favourite = false;
    public PetWant want = null;
    public float wantDaysLeft = 0f;
    public float wantCooldownDays = 0f;
    public int lapsedWants = 0;

    public Pet(String typeID, String name) {
        this.id = Misc.genUID();
        this.typeID = typeID;
        this.name = name;
        this.lifetime = MathUtils.getRandomNumberInRange(0.9f, 1.1f) * getData().maxLife;
        this.sheet = PetGenetics.rollSheet(getData(), PetDataRepo.getTraits(), Misc.random);

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
        if (want == PetWant.ATTENTION || want == PetWant.COMFORT) answerWant();
    }

    // answered wants and shared events; past the time hearts only a favourite takes it
    public void addBond(float days) {
        float cap = PetConstants.HEART_DAYS[(favourite ? PetConstants.MAX_HEARTS : PetConstants.TIME_HEARTS) - 1];
        if (bondDays < cap) setBondDays(Math.min(cap, bondDays + days));
    }

    public void setBondDays(float days) {
        int hearts = getHearts();
        bondDays = days;
        if (getHearts() != hearts) effectsChanged();
    }

    // a talent shows itself when its heart switches it on, and every talent at READ_HEARTS
    private void effectsChanged() {
        List<String> talents = getTalents();
        int hearts = getHearts();
        int on = PetGenetics.activeCount(talents.size(), hearts);

        for (int i = 0; i < talents.size(); i++) {
            String id = talents.get(i);
            if ((i < on || hearts >= PetConstants.READ_HEARTS) && sheet.known.add(id)) {
                PetStatusManager.getInstance().reportTalentShown(this, PetDataRepo.getTrait(id));
            }
        }

        syncShip();
    }

    // ship stats are only rebuilt on a fleet sync, the next fleet advance runs it
    private void syncShip() {
        if (assignedFleetMember != null && assignedFleetMember.getFleetData() != null) assignedFleetMember.getFleetData().setSyncNeeded();
    }

    // console: sets a grade and reads it, 0 removes the trait
    public void setTrait(String traitId, int grade) {
        if (grade <= 0) {
            sheet.traits.remove(traitId);
            sheet.known.remove(traitId);
        } else {
            sheet.traits.put(traitId, grade);
            sheet.known.add(traitId);
        }

        syncShip();
    }

    public void setFavourite(boolean on) {
        favourite = on;
        if (on) return;

        if (want != null) endWant();
        lapsedWants = 0;
    }

    public void play() {
        if (want == PetWant.PLAY) answerWant();
    }

    public void openWant(PetWant w) {
        want = w;
        wantDaysLeft = PetConstants.WANT_DAYS;
    }

    public void answerWant() {
        if (want == null) return;

        lapsedWants = 0;
        addBond(PetConstants.WANT_BOND_DAYS);
        happyDays = PetConstants.HAPPY_DAYS;
        endWant();
    }

    private void endWant() {
        want = null;
        wantCooldownDays = PetConstants.WANT_COOLDOWN_DAYS;
        PetStatusManager.getInstance().wantEnded();
    }

    public boolean isSulking() {
        return lapsedWants >= PetConstants.SULK_LAPSES;
    }

    // in the player fleet: on one of its ships or in the menagerie; wants wait anywhere else
    public boolean isWithPlayer() {
        if (isDead || storage != null) return false;
        if (assignedFleetMember == null) return true;
        return assignedFleetMember.getFleetData() != null && assignedFleetMember.getFleetData().getFleet() == Global.getSector().getPlayerFleet();
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
        syncShip();
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

        syncShip();
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
        int hearts = getHearts();
        float mult = getTalentMult();

        if (ages) {
            age += dayAmt;

            if (age > lifetime) {
                PetStatusManager.getInstance().reportPetDied(this, PetStatusManager.PetDeathCause.NATURAL);
            }
        }

        unsettledDays = Math.max(0f, unsettledDays - dayAmt);
        shellShockedDays = Math.max(0f, shellShockedDays - dayAmt);
        happyDays = Math.max(0f, happyDays - dayAmt);

        // a want of a pet that leaves the player fleet is dropped, not lapsed, so one away never blocks the fleet's want
        if (want != null && !isWithPlayer()) endWant();
        if (favourite && isWithPlayer()) {
            wantCooldownDays = Math.max(0f, wantCooldownDays - dayAmt);
            if (want != null) {
                wantDaysLeft -= dayAmt;
                if (wantDaysLeft <= 0f) {
                    lapsedWants++;
                    endWant();
                }
            }
        }

        if (storage != null) return;

        feedInterval.advance(amt);
        if (isAboard()) {
            nestRebuildDays = Math.max(0f, nestRebuildDays - dayAmt);
            float cap = PetConstants.HEART_DAYS[PetConstants.TIME_HEARTS - 1];
            if (!isStarving && !isUnsettled() && bondDays < cap) bondDays = Math.min(cap, bondDays + dayAmt * getHeartRate());
        }

        if (getHearts() != hearts || getTalentMult() != mult) effectsChanged();

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
        if (isSulking()) return PetMood.SULKING;
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
        if (getData().isHamster()) mult *= PetConstants.HAMSTER_MULT;
        if (hasNest()) mult *= PetConstants.NEST_MULT;
        return mult;
    }

    // off while Unsettled; the hamster and a nest raise it like the mascot effect
    public float getTalentMult() {
        if (isUnsettled()) return 0f;

        float mult = 1f;
        if (getData().isHamster()) mult *= PetConstants.HAMSTER_MULT;
        if (hasNest()) mult *= PetConstants.NEST_MULT;
        return mult;
    }

    public List<String> getTalents() {
        return PetGenetics.talents(sheet, PetDataRepo.getTraits());
    }

    // switched on by hearts: the best one per heart, all at MAX_HEARTS
    public List<String> getActiveTalents() {
        List<String> talents = getTalents();
        return talents.subList(0, PetGenetics.activeCount(talents.size(), getHearts()));
    }

    // what an active talent gives on its ship now, 0 when it is off
    public float getTalentValue(String traitId) {
        if (!getActiveTalents().contains(traitId)) return 0f;
        return PetDataRepo.getTrait(traitId).value(sheet.getGrade(traitId), getTalentMult());
    }

    public List<String> getKnownTalentNames() {
        List<String> names = new ArrayList<>();
        Map<String, PetDataRepo.Trait> traits = PetDataRepo.getTraits();
        for (String id : getTalents()) if (sheet.isKnown(id)) names.add(traits.get(id).name());
        return names;
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
