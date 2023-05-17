package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.utils.ModPlugin;

import java.util.ArrayList;

public class Pet {
    public static final float DEFAULT_FEED_INTERVAL = 31f;
    public static final String HULLMOD_DATA_PREFIX = "PET_DATA_ID_";
    public static final float MAX_EFFECT_AFTER_DAYS = 365f;

    public String typeID;
    public String id;
    public String name;
    public float age = 1f;
    public FleetMemberAPI assignedFleetMember = null;
    private final IntervalUtil feedInterval = new IntervalUtil(DEFAULT_FEED_INTERVAL, DEFAULT_FEED_INTERVAL);
    public String personality;

    private float serveDuration = 0f;

    private boolean isStarving = false;
    private boolean isDead = false;

    private boolean isActive = false;

    public Pet(String typeID, String name) {
        this.id = Misc.genUID();
        this.typeID = typeID;
        this.name = name;

        WeightedRandomPicker<String> personalities = new WeightedRandomPicker<>();
        personalities.addAll(Academy.COLOURS_BY_PERSONALITY.keySet());

        this.personality = personalities.pick();
    }

    public void assign(FleetMemberAPI toMember){
        ModPlugin.log("assigning " + name + " variety " + typeID + " to " + toMember.getShipName());

        this.assignedFleetMember = toMember;
        isActive = true;
        serveDuration = 0f;
        toMember.getVariant().addPermaMod(getData().hullmod);
        toMember.getVariant().addTag(HULLMOD_DATA_PREFIX + id);
    }

    public void unassign(){
        if (assignedFleetMember == null) return;

        serveDuration = 0f;
        ShipVariantAPI variant = assignedFleetMember.getVariant();
        String hullmod = getData().hullmod;

        variant.removePermaMod(hullmod);
        variant.removeMod(hullmod);

        for (String s : new ArrayList<>(assignedFleetMember.getVariant().getTags())){
            if (s.startsWith(HULLMOD_DATA_PREFIX)) assignedFleetMember.getVariant().getTags().remove(s);
        }

        assignedFleetMember = null;
        isActive = false;
    }

    public PetData getData(){
        return PetDataRepo.get(typeID);
    }

    public void advance(float amt){
        if (!isActive || isDead) return;

        feedInterval.advance(amt);
        float dayAmt = Global.getSector().getClock().convertToDays(amt);

        age += dayAmt;
        serveDuration += dayAmt;

        PetStatusManager manager = PetStatusManager.getInstance();

        if (feedInterval.intervalElapsed()){
            boolean hasFed = manager.feed(this);

            if (!hasFed && isStarving) manager.reportPetDied(this, PetStatusManager.PetDeathCause.STARVED);
            else if (!hasFed) {
                isStarving = true;
                manager.reportPetStarving(this);
            }
            else if (isStarving) isStarving = false;
        }

        if (age > getData().maxLife) {
            manager.reportPetDied(this, PetStatusManager.PetDeathCause.NATURAL);
        }
    }

    public boolean isDead() {
        return isDead;
    }

    public boolean isStarving() {
        return isStarving;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setDead(boolean dead) {
        isDead = dead;
    }

    public float getEffectFract(){
        return Math.min(1f, serveDuration / MAX_EFFECT_AFTER_DAYS);
    }
}
