package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
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
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.plugins.PetCard;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // the Captain talent's person while the talent is on, in the seat whenever no one else is
    public PersonAPI captain = null;
    // s-mod room its Tinkerer gave the ship at the last sync, read from the ship stats; a drop takes that many s-mods back
    public int sModRoom = 0;
    // hearts are read from it, never stored
    public float bondDays = 0f;
    // 0: the nest stands once it has NEST_HEARTS
    public float nestRebuildDays = 0f;
    public boolean favourite = false;
    public PetWant want = null;
    public float wantDaysLeft = 0f;
    public float wantCooldownDays = 0f;
    public int lapsedWants = 0;
    // title counters; titles holds PetTitle names in the order earned
    public int fights = 0;
    public int jumps = 0;
    public int answeredWants = 0;
    public int finds = 0;
    public float flagshipDays = 0f;
    public float hyperspaceDays = 0f;
    // market ids, kept only until Globetrotter is earned
    public Set<String> markets = new HashSet<>();
    public List<String> titles = new ArrayList<>();
    // pet ids, null for a bought, found or adopted pet; the family tree and inbreeding read them
    public String parentA = null;
    public String parentB = null;

    public Pet(String typeID, String name) {
        this.id = Misc.genUID();
        this.typeID = typeID;
        this.name = name;
        this.lifetime = MathUtils.getRandomNumberInRange(0.9f, 1.1f) * getData().maxLife;
        this.sheet = PetGenetics.rollSheet(getData(), PetDataRepo.getTraits(), Misc.random);

        if (Global.getSettings().isDevMode()) this.feedInterval = new IntervalUtil(10f, 10f); //10 seconds
    }

    // a young of a brood, rolled when its brood time is up
    public Pet(String typeID, String name, PetSheet sheet, String parentA, String parentB) {
        this(typeID, name);
        this.sheet = sheet;
        this.parentA = parentA;
        this.parentB = parentB;
        this.age = 0f;
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

    // a parent in a habitat: held by its Animal Center, outside pet storage
    public void toHabitat(PetShop shop) {
        leave();
        storage = shop;
    }

    public void unsettle(float days) {
        unsettledDays = Math.max(unsettledDays, days);
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
        if (bondDays < cap) setBondDays(Math.min(cap, bondDays + days * PetGenetics.heartMult(sheet, PetDataRepo.getTraits())));
    }

    public void rename(String newName) {
        name = newName;
        if (captain != null) captain.getName().setFirst(newName);
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

        if (hearts >= PetConstants.MAX_HEARTS) earn(PetTitle.BELOVED);
        syncShip();
    }

    // ship stats are only rebuilt on a fleet sync, the next fleet advance runs it
    private void syncShip() {
        FleetMemberAPI member = assignedFleetMember;
        if (member == null) return;

        updateCaptain();
        if (member.getFleetData() == null) return;

        member.getFleetData().setSyncNeeded();
        // a ship in market storage has no fleet, a lost ship's wings are gone with it
        if (member.getFleetData().getFleet() == null || !member.getFleetData().getMembersListCopy().contains(member)) return;

        List<String> lost = PetHullmod.updateShip(member, this);
        if (!lost.isEmpty()) PetStatusManager.getInstance().reportRoomLost(this, member, lost);
    }

    // an officer, an ai core or the player keeps the seat; a lost ship gives its captain back too
    private void updateCaptain() {
        FleetMemberAPI member = assignedFleetMember;
        int skills = isAssigned && isAboard() ? Math.round(getTalentValue(PetHullmod.CAPTAIN)) : 0;

        if (skills <= 0) {
            if (captain != null && member.getCaptain() == captain) member.setCaptain(null);
            captain = null;
            return;
        }

        if (captain == null) captain = PetHullmod.createCaptain(this);
        PetHullmod.fitCaptain(captain, this, skills);
        if (isSeatFree(member)) member.setCaptain(captain);
    }

    private static boolean isSeatFree(FleetMemberAPI member) {
        PersonAPI seat = member.getCaptain();
        return seat == null || seat.isDefault();
    }

    // a gene scan reads every trait, flaws included
    public void scan() {
        sheet.known.addAll(sheet.traits.keySet());
        sheet.flawsRead = true;
    }

    public boolean isRead() {
        return sheet.flawsRead && sheet.known.containsAll(sheet.traits.keySet());
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

    public void addFight() {
        if (++fights >= PetConstants.VETERAN_FIGHTS) earn(PetTitle.VETERAN);
    }

    public void addJump() {
        if (++jumps >= PetConstants.NAVIGATOR_JUMPS) earn(PetTitle.NAVIGATOR);
    }

    public void addFind() {
        if (++finds >= PetConstants.TREASURE_HUNTER_FINDS) earn(PetTitle.TREASURE_HUNTER);
    }

    public void addMarket(String marketId) {
        if (getTitleIds().contains(PetTitle.GLOBETROTTER.name())) return;
        if (markets == null) markets = new HashSet<>();
        if (markets.add(marketId) && markets.size() >= PetConstants.GLOBETROTTER_MARKETS) {
            earn(PetTitle.GLOBETROTTER);
            markets.clear();
        }
    }

    public void earn(PetTitle title) {
        List<String> earned = getTitleIds();
        if (earned.contains(title.name())) return;

        earned.add(title.name());
        PetStatusManager.getInstance().reportTitle(this, title);
    }

    // a pet from a save before titles has none
    public List<String> getTitleIds() {
        if (titles == null) titles = new ArrayList<>();
        return titles;
    }

    // an id no PetTitle has any more is left out
    public List<PetTitle> getTitles() {
        List<PetTitle> list = new ArrayList<>();
        for (String id : getTitleIds()) {
            for (PetTitle title : PetTitle.values()) if (title.name().equals(id)) list.add(title);
        }
        return list;
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
        if (++answeredWants >= PetConstants.PAMPERED_WANTS) earn(PetTitle.PAMPERED);
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

        isAssigned = false;
        syncShip();
        sModRoom = 0;
        assignedFleetMember = null;
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
            if (age > getData().maxLife) earn(PetTitle.OLD_TIMER);

            if (age > getLifetime()) {
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

        // no callback tells of an officer put on or taken off a ship
        if (captain != null && isAboard() && assignedFleetMember.getCaptain() != captain && isSeatFree(assignedFleetMember)) {
            assignedFleetMember.setCaptain(captain);
        }

        if (isWithPlayer()) {
            if (isAboard() && assignedFleetMember.isFlagship() && (flagshipDays += dayAmt) >= PetConstants.ADMIRAL_DAYS) earn(PetTitle.ADMIRAL);
            if (Global.getSector().getPlayerFleet().isInHyperspace() && (hyperspaceDays += dayAmt) >= PetConstants.STARGAZER_DAYS) earn(PetTitle.STARGAZER);
        }

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

    public boolean isYoung() {
        return age < getYoungDays();
    }

    public float getYoungDays() {
        return PetGenetics.youngDays(getData(), sheet, PetDataRepo.getTraits());
    }

    public boolean isOld() {
        return age > getLifetime() * PetConstants.SLEEPY_AGE_FRACTION;
    }

    // the rolled lifetime with its mutations and flaws; at or under 0 it lives only where it does not age
    public float getLifetime() {
        return PetGenetics.lifetime(getData(), sheet, lifetime, PetDataRepo.getTraits());
    }

    public float getFoodPerMonth() {
        return getData().foodPerMonth * PetGenetics.foodMult(sheet, PetDataRepo.getTraits());
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
        return (lastMealSupplies ? PetConstants.SUPPLIES_HEART_RATE : 1f) * PetGenetics.heartMult(sheet, PetDataRepo.getTraits());
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

        float mult = PetGenetics.bodyMult(sheet, PetGenetics.GIANT, PetDataRepo.getTraits());
        if (getData().isHamster()) mult *= PetConstants.HAMSTER_MULT;
        if (hasNest()) mult *= PetConstants.NEST_MULT;
        return mult;
    }

    // off while Unsettled; the hamster and a nest raise it like the mascot effect
    public float getTalentMult() {
        if (isUnsettled()) return 0f;

        float mult = PetGenetics.bodyMult(sheet, PetGenetics.SAVANT, PetDataRepo.getTraits());
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

    // what an active talent gives on its ship now, 0 when it is off; Split mind counts the best one again per level
    public float getTalentValue(String traitId) {
        List<String> active = getActiveTalents();
        if (!active.contains(traitId)) return 0f;

        float mult = getTalentMult();
        if (active.get(0).equals(traitId)) mult *= 1f + PetGenetics.bodyValue(sheet, PetGenetics.SPLIT_MIND, PetDataRepo.getTraits());
        return PetDataRepo.getTrait(traitId).value(sheet.getGrade(traitId), mult);
    }

    // known talents, every mutation, flaws once read
    public List<String> getKnownTraitNames() {
        List<String> names = new ArrayList<>();
        Map<String, PetDataRepo.Trait> traits = PetDataRepo.getTraits();
        for (String id : getTalents()) if (sheet.isKnown(id)) names.add(traits.get(id).name());
        for (String id : sheet.traits.keySet()) {
            PetDataRepo.Trait trait = traits.get(id);
            if (trait != null && (trait.isMutation() || trait.isFlaw() && sheet.isKnown(id))) names.add(trait.name());
        }
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
