package com.fs.starfarer.api.campaign.impl.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.impl.items.consumables.entities.IndEvo_NebulaParticle;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.OrbitFocus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignClock;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class IndEvo_ScoopAbilityPlugin extends IndEvo_BaseConsumableAbilityPlugin {
    //vectors suck

    public static final float MAX_FUEL_AMT = 1000f;
    public static final float MAX_SUCTION_RANGE = 3000f; //10 dorra
    public static final float PARTICLES_PER_DISTANCE = 0.1f;
    public static final float RANGE_PING_SECONDS = 4f;
    public static final float PARTICLE_SPAWN_INTERVAL = 0.05f;
    public static final float SUCTION_CONE_DEFAULT_RADIUS = 300f;
    public static final float PARTICLE_DEFAULT_RADIUS = 600f;
    public static final float MAX_BOUNDS = 40f;

    private IntervalUtil interval = new IntervalUtil(PARTICLE_SPAWN_INTERVAL, PARTICLE_SPAWN_INTERVAL);
    private IntervalUtil depositInterval = new IntervalUtil(1f, 1f);

    public LocationAPI loc;
    protected List<IndEvo_NebulaParticle> particles = new ArrayList<>();
    transient protected SpriteAPI sprite;
    transient protected SpriteAPI spriteNoColour;
    private float elapsed = 0;

    public enum TargetType {
        SUN,
        NEBULA,
        NULL
    }

    public TargetType targetType = TargetType.NULL;
    public Vector2f currentTargetLoc = null;

    @Override
    protected void activateImpl() {
        loc = entity.getContainingLocation();
    }
    
    @Override
    protected void applyEffect(float amount, float level) {
        if (entity.getContainingLocation() != loc) {
            deactivate();
            return;
        }

        interval.advance(amount);
        depositInterval.advance(amount);

        updateTargetLoc();
        depositFuelInCargo();
        showRangePing(amount);

        for (IndEvo_NebulaParticle p : new ArrayList<>(particles)) {
            p.advance(amount);

            if (p.isExpired()) {
                particles.remove(p);
            }
        }

        boolean active = getProgressFraction() < 0.85f;

        //void playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel, float fadeIn, float fadeOut);
        if(active) Global.getSoundPlayer().playLoop("IndEvo_succ", entity, 1f, 2f, entity.getLocation(), entity.getVelocity(), 2f, 2f);

        if (active && interval.intervalElapsed() && canSucc()) {
            int particleNum = Math.round(Misc.getDistance(entity.getLocation(), currentTargetLoc) * PARTICLES_PER_DISTANCE);
            boolean maxParticles = particles.size() >= particleNum;

            //IndEvo_modPlugin.log("particle check - " + particleNum + " allowed " + maxParticles);

            if (Global.getSettings().isDevMode() || !maxParticles) createNewParticleV2();
        }
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return super.isUsable() && canSucc();
    }

    private boolean canSucc() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        boolean validFleetLoc = !fleet.isInHyperspace();
        boolean validTarget = updateTargetLoc();

        return validFleetLoc && validTarget && Misc.getDistance(currentTargetLoc, fleet.getLocation()) < MAX_SUCTION_RANGE;
    }

    protected void showRangePing(float amount) {
        elapsed += amount;
        if (elapsed > RANGE_PING_SECONDS) {
            elapsed = 0;

            CampaignPingSpec custom = new CampaignPingSpec();
            custom.setColor(getFleet().getFaction().getColor());
            custom.setWidth(7);
            custom.setMinRange(MAX_SUCTION_RANGE / 2f - 100f);
            custom.setRange(MAX_SUCTION_RANGE / 2f);
            custom.setDuration(RANGE_PING_SECONDS / 2f);
            custom.setAlphaMult(0.25f);
            custom.setInFraction(0.2f);
            custom.setNum(1);

            Global.getSector().addPing(entity, custom);

            if (targetType == TargetType.NULL) getFleet().addFloatingText("No volatile source in range!", Misc.getNegativeHighlightColor(), 0.5f);
        }
    }

    private void depositFuelInCargo(){
        if(!depositInterval.intervalElapsed() || targetType == TargetType.NULL) return;

        StarSystemAPI system = getFleet().getStarSystem();
        StarAge age = system.getAge();
        String type = system.getStar().getTypeId();

        Map<String, Float> starTypes = new HashMap<>();
        starTypes.put("star_yellow", 0.8f);
        starTypes.put("star_white", 0.7f);
        starTypes.put("star_blue_giant", 0.75f);
        starTypes.put("star_blue_supergiant", 0.8f);
        starTypes.put("star_orange", 0.8f);
        starTypes.put("star_orange_giant", 0.85f);
        starTypes.put("star_red_supergiant", 1f);
        starTypes.put("star_red_giant", 0.95f);
        starTypes.put("star_red_dwarf", 0.8f);
        starTypes.put("star_browndwarf", 0.6f);

        Map<String, Float> ageTypes = new HashMap<>();
        ageTypes.put(StarAge.YOUNG.toString(), 1f);
        ageTypes.put(StarAge.AVERAGE.toString(), 0.8f);
        ageTypes.put(StarAge.OLD.toString(), 0.7f);
        ageTypes.put(StarAge.ANY.toString(), 0.7f);

        float starMult = starTypes.get(type);
        float ageMult;

        if (age != null && ageTypes.containsKey(age.toString())) ageMult = ageTypes.get(age.toString());
        else ageMult = 0.7f;

        float dur = getDurationDays() * CampaignClock.SECONDS_PER_GAME_DAY;
        float amt = targetType == TargetType.NEBULA ? (MAX_FUEL_AMT / dur) * ageMult : (MAX_FUEL_AMT / dur) * starMult;

        CargoAPI cargo = getFleet().getCargo();
        cargo.addFuel(Math.min(amt, cargo.getFreeFuelSpace()));
    }

    private boolean updateTargetLoc() {
        LocationAPI loc = entity.getContainingLocation();
        if (loc instanceof StarSystemAPI) {
            PlanetAPI star = ((StarSystemAPI) loc).getStar();
            if (star != null && !loc.isNebula() && Misc.getDistance(star.getLocation(), entity.getLocation()) < MAX_SUCTION_RANGE) {
                currentTargetLoc = star.getLocation();
                targetType = TargetType.SUN;
                return true;
            }
        }

        Vector2f nebulaPos = OrbitFocus.getClosestNebulaTilePosition(entity);
        if (nebulaPos != null) {
            currentTargetLoc = nebulaPos;
            targetType = TargetType.NEBULA;
            return true;
        }

        currentTargetLoc = null;
        targetType = TargetType.NULL;
        return false;
    }

    SectorEntityToken token = null;
    SectorEntityToken token2 = null;

    private void createNewParticleV2(){
        CampaignFleetAPI playerFleet = getFleet();
        Vector2f playerLocation = playerFleet.getLocation();

        if (currentTargetLoc == null) return;

        float radius;
        if(targetType == TargetType.SUN){
            radius = playerFleet.getStarSystem().getStar().getRadius();
        } else {
            radius = SUCTION_CONE_DEFAULT_RADIUS;
        }

        //draw line from a point on the target circumference to the player, then make it into a coordinate by subtracting the vectors
        Vector2f dir = VectorUtils.getDirectionalVector(currentTargetLoc, playerLocation);
        dir = VectorUtils.resize(dir, Misc.getDistance(playerLocation, currentTargetLoc));
        dir = Vector2f.sub(playerLocation, dir ,null);

        //get an angle on the target circumfence within +/- 20 deg(rad) of the current one
        double angle = Math.toRadians(VectorUtils.getAngle(playerLocation, dir)) - Math.PI; //have to invert angle here or it'll point the wrong way, dunno why
        double limit = Math.toRadians(Math.min(radius * 0.3f, MAX_BOUNDS));
        double lowerLimit = angle - limit;
        double upperLimit = angle + limit;
        double randomAngleInRange = ThreadLocalRandom.current().nextDouble(lowerLimit, upperLimit);

        //every point on the arc has coords
        //x = r * cos(t), y = r * sin(t), r is the distance to target, t is your angle
        float x = (float) (radius * Math.cos(randomAngleInRange) * 1f);
        float y = (float) (radius * Math.sin(randomAngleInRange) * 1f);

        dir = new Vector2f(dir.x + x, dir.y + y); //adjust spawn point on target circ.

        //make particle size depend on target rad, but not larger
        float size = (float) (PARTICLE_DEFAULT_RADIUS * (1f - (Math.random() * 0.3f)));
        float baseAlphaMult = (float) (1f - (0.4f * Math.random()));

        Color color = null;
        if (targetType == TargetType.SUN) {
            color = entity.getStarSystem().getStar().getLightColor();
        }

        IndEvo_NebulaParticle data = new IndEvo_NebulaParticle(dir, size, baseAlphaMult, color);
        particles.add(data);

        if(Global.getSettings().isDevMode()){
            if (token != null) playerFleet.getContainingLocation().removeEntity(token);
            token = playerFleet.getContainingLocation().addCustomEntity(null, "indicator", "development_SplinterFleet_OrbitFocus", null, radius, 1f, 1f);
            token.setLocation(currentTargetLoc.x, currentTargetLoc.y);

            if (token2 != null) playerFleet.getContainingLocation().removeEntity(token2);
            token2 = playerFleet.getContainingLocation().addCustomEntity(null, "indicator", "development_SplinterFleet_OrbitFocus", null, 50f, 1f, 1f);
            token2.setLocation(dir.x, dir.y);
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    private StarAge age = null;

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if(getFleet() == null || getFleet().isInHyperspace() || getFleet().getStarSystem() == null) return;

        StarAge age = getFleet().getStarSystem().getAge();
        if(sprite == null || spriteNoColour == null || age != this.age){
            String nebulaType = StarSystemGenerator.nebulaTypes.get(age);
            if (nebulaType == null) nebulaType = StarSystemGenerator.nebulaTypes.get(StarAge.ANY);

            this.age = age;
            this.sprite = Global.getSettings().getSprite("terrain", nebulaType);
            this.spriteNoColour = Global.getSettings().getSprite("misc", "nebula_particles");

            IndEvo_modPlugin.log("sprite null, loading " + nebulaType);
        }

//        float alphaMult = viewport.getAlphaMult();
//        alphaMult *= entity.getSensorFaderBrightness();
//        alphaMult *= entity.getSensorContactFaderBrightness();
//        if (alphaMult <= 0) return;

        for (IndEvo_NebulaParticle p : particles) {
            if(p.isExpired()) continue;

            float size = p.size;
            SpriteAPI sprite = p.color != null ? spriteNoColour : this.sprite;

            sprite.setTexWidth(0.25f);
            sprite.setTexHeight(0.25f);
            sprite.setAdditiveBlend();

            sprite.setTexX(p.i * 0.25f);
            sprite.setTexY(p.j * 0.25f);

            sprite.setAngle(p.angle);
            sprite.setSize(size, size);
            sprite.setAlphaMult(p.currentAlpha);
            if (p.color != null) sprite.setColor(p.color);
            sprite.renderAtCenter(p.currentPos.x, p.currentPos.y);
        }
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        particles.clear();
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Extracts volatiles from stars or nebulae within %s and converts them to fuel. " +
                        "More efficient on %s sources.", opad, highlight,
                (int) Math.round(MAX_SUCTION_RANGE) + " SU", "young and large");

        tooltip.addPara("Stays active for %s. Stops providing fuel when out of range of a source.", opad, highlight,
                "3 days");

        if (getFleet().isInHyperspace()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Not usable in Hyperspace!");
        else if (!canSucc()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "No volatile source in range!");
    }
}
