package indevo.industries.derelicts.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.campaign.CircularOrbit;
import com.fs.starfarer.campaign.CircularOrbitPointDown;
import com.fs.starfarer.campaign.CircularOrbitWithSpin;
import indevo.ids.Ids;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.industries.derelicts.listeners.PlanetJumpListener;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.auroraColors;
import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.baseColors;

/**
 * Moves a planet to the specified location
 * can not move planets with a gravity well (Gas Giants)
 * can not re-assign ringBandAPI objects (next version)
 * <p>
 * Author: SirHartley
 * <p>
 * If you steal this I will break your legs
 */

public class PlanetMovingScript implements EveryFrameScript {

    public static final Logger log = Global.getLogger(PlanetMovingScript.class);
    boolean debug = false;

    protected final MarketAPI market;
    protected final StarSystemAPI targetSystem;
    protected StarSystemAPI originSystem;
    public static final String ARTIFICIAL_RIFT_ID = "IndEvo_planet_replacer";
    protected final String originalOrbitTag = "$IndEvo_original_planet_orbit";
    float elapsed = 0;

    private int phase = 0;
    protected boolean done = false;

    SectorEntityToken newFocus;
    final SectorEntityToken oldToken;
    final float oldRadius;

    public PlanetMovingScript(MarketAPI market, StarSystemAPI target) {
        this.market = market;
        this.originSystem = market.getStarSystem();
        if (originSystem == null) log.info("Origin market system is null");
        this.targetSystem = target;
        this.oldRadius = market.getPlanetEntity().getRadius();
        this.oldToken = market.getPrimaryEntity();
    }

    public void advancePhase() {
        phase++;
    }

    public SectorEntityToken getTarget() {
        return oldToken;
    }

    public void advance(float amount) {
        elapsed += amount;

        if (isDone()) return;

        if (market.getPlanetEntity() == null) {
            setDone();
            log.warn("Can't move entity, not a planet");
            return;
        }

        if (elapsed > 10) {
            log.info("forcing phase 2");
            phase = 2; //after 5 seconds of nothing, we override and just teleport the planet to account for aborted or failed animations.
        }

        if (elapsed < 5 || isDone()) return; //this is the 5s wait period before it triggers

        switch (phase) {
            case 0:
                log.info("phase 0");
                //initialize, render the warp
                debug = Global.getSettings().isDevMode();

                renderWarpEffect(oldToken); //the warp effect plugin (animation) increments the phase a second time when it is finished
                phase++;
                break;
            case 2:
                log.info("phase 2");
                //move the planet
                StarSystemAPI oldSystem = market.getStarSystem();

                OrbitAPI orbit = market.getPlanetEntity().getOrbit();
                JumpPointAPI newJumpPoint = createJumpPoint(oldSystem, orbit);
                newFocus = newJumpPoint;

                //clear any mirrors, astropoli, custom entities
                clearSpecialEntitiesWithOrbitTarget(oldSystem, oldToken);

                //transfer focus to the rift
                transferOrbitFocus(oldToken, newFocus);

                SectorEntityToken newPlanet = movePlanet();

                //the jump point had no destination set since we can't call autoGenerate, as that duplicates all jump points in the system, so we do it now
                newJumpPoint.addDestination(new JumpPointAPI.JumpDestination(newPlanet, "the " + targetSystem.getName()));

                reApplyStation();

                triggerJumpListeners(newPlanet, oldSystem, targetSystem);

                ((RiftGenerator) market.getIndustry(Ids.RIFTGEN)).setMoving(false);

                setDone();
                break;
        }
    }

    private void triggerJumpListeners(SectorEntityToken planet, StarSystemAPI oldSystem, StarSystemAPI newSystem) {
        List<PlanetJumpListener> list = Global.getSector().getListenerManager().getListeners(PlanetJumpListener.class);

        for (Iterator<PlanetJumpListener> i = list.iterator(); i.hasNext(); ) {
            PlanetJumpListener x = i.next();
            x.reportPlanetJump(planet, oldSystem, newSystem);
        }
    }

    private void renderWarpEffect(SectorEntityToken target) {
        Global.getSector().addScript(new WarpEffectAnimationScript(target));
        //target.getStarSystem().addCustomEntity("IndEvo_WarpEffect", null, "IndEvo_WormHoleEffect", null, target);
    }

    private JumpPointAPI createJumpPoint(StarSystemAPI system, OrbitAPI orbit) {
        log.info("Creating a jump point at the old location");

        JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(
                ARTIFICIAL_RIFT_ID,
                "Artificial Hyperspace Tear");

        jumpPoint.setOrbit(orbit);
        jumpPoint.setStandardWormholeToHyperspaceVisual();
        jumpPoint.setRadius(50f);

        jumpPoint.setAutoCreateEntranceFromHyperspace(false);
        jumpPoint.setAutogenJumpPointNameInHyper("Artificial Hyperspace Tear");

        jumpPoint.setCustomDescriptionId("IndEvo_JumpRift");

        system.addEntity(jumpPoint);


        //SectorEntityToken ringBand = system.addRingBand(jumpPoint, "IndEvo", "rings_hyperspace0", 256f, 0, new Color(255,0,255 ), 170f, jumpPoint.getRadius() + 70f, 365);
        //system.addEntity(ringBand);

        generateMagneticField(jumpPoint, 1f, 250f, true);

        return jumpPoint;
    }

    private void transferOrbitFocus(SectorEntityToken from, SectorEntityToken to) {
        log.info("Transferring orbit focus to rift");

        for (SectorEntityToken token : getEntitiesWithOrbitTarget(from)) {

            if (token.getOrbit() instanceof CircularOrbit) {
                ((CircularOrbit) token.getOrbit()).setFocus(to);
                continue;
            }

            if (token.getOrbit() instanceof CircularOrbitWithSpin) {
                ((CircularOrbitWithSpin) token.getOrbit()).setFocus(to);
                continue;
            }

            if (token.getOrbit() instanceof CircularOrbitPointDown) {
                ((CircularOrbitPointDown) token.getOrbit()).setFocus(to);

            }
        }
    }

    private void clearSpecialEntitiesWithOrbitTarget(StarSystemAPI system, SectorEntityToken orbitFocus) {
        log.info("clearing special entities");
        List<SectorEntityToken> entityTokenList = new ArrayList<>();

        for (SectorEntityToken e : system.getAllEntities()) {
            if (e.getOrbitFocus() != null
                    && e.getOrbitFocus().equals(orbitFocus)
                    && e.getCustomEntitySpec() != null
                    && !e.getCustomEntitySpec().getTags().contains(Tags.PLANET)
                    && !e.getCustomEntitySpec().getTags().contains(Tags.STATION)) {

                entityTokenList.add(e);
            }
        }

        for (SectorEntityToken e : system.getTerrainCopy()) {
            if (e instanceof BaseRingTerrain && ((BaseRingTerrain) e).getRelatedEntity().equals(orbitFocus))
                entityTokenList.add(e);

        }

        for (SectorEntityToken e : entityTokenList) {
            system.removeEntity(e);
        }
    }

    public static List<SectorEntityToken> getEntitiesWithOrbitTarget(SectorEntityToken orbitFocus) {
        if (orbitFocus == null || orbitFocus.getStarSystem() == null) return null;

        List<SectorEntityToken> entityTokenList = new ArrayList<>();

        for (SectorEntityToken e : orbitFocus.getStarSystem().getAllEntities()) {
            if (e.getOrbitFocus() != null && e.getOrbitFocus().equals(orbitFocus)) {
                entityTokenList.add(e);
            }
        }

        return entityTokenList;
    }

    private void addOriginalOrbitRadiusTag(PlanetAPI planet, float radius) {
        MemoryAPI mem = planet.getMemoryWithoutUpdate();

        if (!mem.contains(originalOrbitTag)) {
            mem.set(originalOrbitTag, radius);
        }
    }

    private float getOriginalOrbitRadius(PlanetAPI planet) {
        MemoryAPI mem = planet.getMemoryWithoutUpdate();

        if (!mem.contains(originalOrbitTag)) {
            float orb = planet.getCircularOrbitRadius();
            if (orb < 100) orb = 100;
            return orb;
        }

        return mem.getFloat(originalOrbitTag);
    }

    private float getValidOrbitRadius(PlanetAPI planet, StarSystemAPI inSystem) {
        log.info("Getting valid orbit");

        List<Pair<Float, Float>> blockedOrbitList = new ArrayList<>();
        List<SectorEntityToken> entityList = inSystem.getAllEntities();

        float minStarDistance = 700f;
        float blockOutDistance = 200f;

        float oldOrbit = getOriginalOrbitRadius(planet);
        float planetSize = planet.getRadius();
        float orbit;

        //fill blocked orbit list
        for (SectorEntityToken e : entityList) {

            if (e.getOrbit() != null
                    && (e.getOrbitFocus().isStar() || inSystem.isNebula())
                    && e != e.getStarSystem().getStar()) {

                //if it's a ring, let's just ban 150f on either side, since there are no api hooks to get how broad it is.
                if (e instanceof RingBandAPI) {
                    if (debug) log.info("Adding: Ringband");

                    blockedOrbitList.add(new Pair<>(
                            e.getCircularOrbitRadius() - blockOutDistance * 1.5f,
                            e.getCircularOrbitRadius() + blockOutDistance * 1.5f
                    ));

                } else {
                    List<SectorEntityToken> orbitTargetList = getEntitiesWithOrbitTarget(e);

                    //if it doesn't have anything orbiting it, add the radius +/- planet size to the list
                    if (orbitTargetList.isEmpty()) {
                        blockedOrbitList.add(new Pair<>(
                                e.getCircularOrbitRadius() - e.getRadius() - blockOutDistance,
                                e.getCircularOrbitRadius() + e.getRadius() + blockOutDistance
                        ));
                        if (debug) log.info("Adding: " + e.getName() + " without children");

                        //if something orbits it, add the rad +/- largest orbiting orbital orbit radius + orbital planet radius to the list
                    } else {
                        float largestOrbitalOrbit = 0f;
                        //get largest orbital orbit + token
                        for (SectorEntityToken orbital : orbitTargetList) {
                            if (orbital.getCircularOrbitRadius() > largestOrbitalOrbit)
                                largestOrbitalOrbit = orbital.getCircularOrbitRadius() + orbital.getRadius();
                        }

                        //add and subtract it, add both to list
                        float minOrbit = e.getCircularOrbitRadius() - largestOrbitalOrbit - blockOutDistance;
                        float maxOrbit = e.getCircularOrbitRadius() + largestOrbitalOrbit + blockOutDistance;

                        blockedOrbitList.add(new Pair<>(minOrbit, maxOrbit));

                        if (debug) log.info("Adding: " + e.getName() + " with " + orbitTargetList.size() + " children");
                    }
                }
            }
        }

        //correct for star presence, else set up initial orbit
        if (!inSystem.isNebula() && (inSystem.getStar().getRadius() * 3 + minStarDistance >= oldOrbit)) {
            orbit = targetSystem.getStar().getRadius() * 2 + oldOrbit + minStarDistance;
        } else {
            orbit = oldOrbit;
        }

        //check if the orbit is within the illegal zones
        float currentOrbitMin = orbit - planetSize;
        float currentOrbitMax = orbit + planetSize;

        boolean hadToUpdateOrbit = true;
        int limiter = 1;

        while (hadToUpdateOrbit && limiter <= 10) {
            hadToUpdateOrbit = false;

            for (Pair<Float, Float> blockedOrbitPair : blockedOrbitList) {
                //if it intersects with a blocked orbit in any way, always put it further out, it gotta have space eventually

                //if either min or max orbit intersects (is between)
                //if both min orbit and max orbit are between the limits, it is contained within the blocked orbit
                //if both min orbit and max orbit are larger than the limits, the blocked orbit is contained
                if (isBetween(currentOrbitMax, blockedOrbitPair.one, blockedOrbitPair.two)
                        || isBetween(currentOrbitMin, blockedOrbitPair.one, blockedOrbitPair.two)
                        || (currentOrbitMin > blockedOrbitPair.one && currentOrbitMax < blockedOrbitPair.two)
                        || (currentOrbitMin < blockedOrbitPair.one && currentOrbitMax > blockedOrbitPair.two)
                ) {
                    log.info("Recalculating orbit due to intersect, " + limiter + "/10");
                    //update orbits, add 100f just to make sure
                    orbit = blockedOrbitPair.two + planetSize + 100f;
                    currentOrbitMin = orbit - planetSize;
                    currentOrbitMax = orbit + planetSize;

                    hadToUpdateOrbit = true;
                    limiter++;
                    break;
                }
            }
        }

        return orbit;
    }

    private boolean isBetween(Float check, Float min, Float max) {
        return (check > min && check < max);
    }

    private SectorEntityToken movePlanet() {
        log.info("Moving planet to " + targetSystem.getName());

        //get a valid orbit, getOriginalOrbitRadius will return 100f for orbits < 100f (nebulas)
        PlanetAPI oldPlanet = market.getPlanetEntity();
        float oldOrbitRadius = getOriginalOrbitRadius(oldPlanet);
        float newOrbitRadius = getValidOrbitRadius(oldPlanet, targetSystem);

        //set orbit period, adjust for transfer to/from a nebula
        float orbitBasePeriod = oldPlanet.getCircularOrbitPeriod() / (oldOrbitRadius / newOrbitRadius);
        orbitBasePeriod = orbitBasePeriod > 0 ? orbitBasePeriod : 31f;
        float orbitPeriod = targetSystem.isNebula() ? Float.MAX_VALUE : orbitBasePeriod;

        //spawn new planet
        PlanetAPI newPlanet = targetSystem.addPlanet(oldPlanet.getId(), targetSystem.getCenter(), oldPlanet.getName(), oldPlanet.getTypeId(), oldPlanet.getCircularOrbitAngle(), oldRadius, newOrbitRadius, orbitPeriod);

        //tag it
        addOriginalOrbitRadiusTag(newPlanet, oldOrbitRadius);

        //make planet look the same
        copySpecData(newPlanet.getSpec(), oldPlanet.getSpec());
        newPlanet.applySpecChanges();
        moveMagneticField(oldPlanet, newPlanet);

        //prepare new planet for player interactions
        newPlanet.getMemoryWithoutUpdate().set("$isSurveyed", true);
        newPlanet.getMemoryWithoutUpdate().set("$hasUnexploredRuins", false);
        newPlanet.getMemoryWithoutUpdate().set("$isPlanetConditionMarketOnly", false);

        newPlanet.setFaction(market.getFaction().getId());

        //clear old market
        Global.getSector().getEconomy().removeMarket(market);
        Misc.removeRadioChatter(market);

        //remove junk
        for (SectorEntityToken junk : market.getContainingLocation().getEntitiesWithTag(Tags.ORBITAL_JUNK)) {
            market.getContainingLocation().removeEntity(junk);
        }

        //not sure if this is good - remove everything except the planet
        ArrayList<SectorEntityToken> entityListCopy = new ArrayList<>(market.getConnectedEntities());
        for (SectorEntityToken entity : entityListCopy) {
            if (entity != newPlanet) {
                entity.getContainingLocation().removeEntity(entity);
                market.getConnectedEntities().remove(entity);
            }
        }

        //clear the old jump points
        cycleJumpPoints(oldPlanet, newFocus);

        //since list is copy, clear original
        market.getConnectedEntities().clear();

        //set new planet loc
        market.setPrimaryEntity(newPlanet);
        newPlanet.setMarket(market);
        Global.getSector().getEconomy().addMarket(market, true);

        expire(oldPlanet);

        log.info(market.getName() + " successfully moved to " + market.getStarSystem());
        Global.getSector().getCampaignUI().addMessage(market.getName() + " moved to " + market.getStarSystem());

        return newPlanet;
    }

    private void cycleJumpPoints(SectorEntityToken entity, SectorEntityToken newFocus) {
        for (SectorEntityToken token : Global.getSector().getHyperspace().getEntitiesWithTag(Tags.JUMP_POINT)) {
            if (token instanceof JumpPointAPI) {
                JumpPointAPI jumpPoint = ((JumpPointAPI) token);

                for (JumpPointAPI.JumpDestination dest : jumpPoint.getDestinations()) {
                    if (dest.getDestination().getId().equals(entity.getId())) {
                        jumpPoint.setStandardWormholeToNothingVisual();
                        jumpPoint.setName("Artificial Hyperspace Tear");

                        jumpPoint.removeDestination(entity);

                        //add new jump point as dest
                        JumpPointAPI.JumpDestination destination = new JumpPointAPI.JumpDestination(newFocus, "Artificial Hyperspace Tear");
                        jumpPoint.addDestination(destination);
                        jumpPoint.advance(1f);
                    }
                }
            }
        }
        /*for (SectorEntityToken jp : system.getAutogeneratedJumpPointsInHyper()) {
            if (jp instanceof JumpPointAPI) {
                JumpPointAPI jumpPoint = ((JumpPointAPI) jp);

                for(JumpPointAPI.JumpDestination dest : jumpPoint.getDestinations()) {
                    if(dest.getDestination().getId().equals(entity.getId())){
                        jumpPoint.setStandardWormholeToNothingVisual();
                        jumpPoint.setName("Artificial Hyperspace Tear");

                        jumpPoint.removeDestination(entity);

                        //add new jump point as dest
                        JumpPointAPI.JumpDestination destination = new JumpPointAPI.JumpDestination(newFocus, "Artificial Hyperspace Tear");
                        jumpPoint.addDestination(destination);
                        jumpPoint.advance(1f);
                    }
                }
            }
        }*/
    }

    public static void expire(final SectorEntityToken entity) {
        entity.addTag(Tags.NON_CLICKABLE);
        StarSystemAPI system = entity.getStarSystem();

        entity.forceSensorFaderBrightness(Math.min(entity.getSensorFaderBrightness(), 0));
        entity.setAlwaysUseSensorFaderBrightness(true);

        entity.setExpired(true);
        system.removeEntity(entity);
    }

    private void copySpecData(PlanetSpecAPI newSpec, PlanetSpecAPI oldSpec) {
        newSpec.setAtmosphereColor(oldSpec.getAtmosphereColor());
        newSpec.setAtmosphereThickness(oldSpec.getAtmosphereThickness());
        newSpec.setAtmosphereThicknessMin(oldSpec.getAtmosphereThicknessMin());

        newSpec.setCloudColor(oldSpec.getCloudColor());
        newSpec.setCloudRotation(oldSpec.getCloudRotation());
        newSpec.setCloudTexture(oldSpec.getCloudTexture());

        newSpec.setCoronaColor(oldSpec.getCoronaColor());
        newSpec.setCoronaSize(oldSpec.getCoronaSize());
        newSpec.setCoronaTexture(oldSpec.getCoronaTexture());

        newSpec.setGlowColor(oldSpec.getGlowColor());
        newSpec.setGlowTexture(oldSpec.getGlowTexture());

        newSpec.setIconColor(oldSpec.getIconColor());

        newSpec.setPitch(oldSpec.getPitch());
        newSpec.setPlanetColor(oldSpec.getPlanetColor());
        newSpec.setRotation(oldSpec.getRotation());
        newSpec.setTexture(oldSpec.getTexture());
        newSpec.setTilt(oldSpec.getTilt());
        newSpec.setUseReverseLightForGlow(oldSpec.isUseReverseLightForGlow());

        newSpec.setScaleMultMapIcon(oldSpec.getScaleMultMapIcon());
        newSpec.setScaleMultStarscapeIcon(oldSpec.getScaleMultStarscapeIcon());
        newSpec.setStarscapeIcon(oldSpec.getStarscapeIcon());
    }

    private void reApplyStation() {
        for (Industry ind : market.getIndustries()) {
            //flash OrbitalStation to make the station entity reappear
            if (ind instanceof OrbitalStation) {
                String aiCore = ind.getAICoreId();
                ind.setAICoreId(null);
                ind.notifyBeingRemoved(null, false);
                ind.setAICoreId(aiCore);
            }
        }

        Global.getSector().getEconomy().tripleStep();
    }

    public void setDone() {
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return false;
    }

    private void moveMagneticField(SectorEntityToken from, SectorEntityToken to) {
        List<CampaignTerrainAPI> terrainList = from.getStarSystem().getTerrainCopy();
        SectorEntityToken toRemove = null;
        for (CampaignTerrainAPI terrain : terrainList) {
            if (terrain.getType().equals(Terrain.MAGNETIC_FIELD)) {
                MagneticFieldTerrainPlugin plugin = ((MagneticFieldTerrainPlugin) terrain.getPlugin());
                if (plugin.getRelatedEntity().equals(from)) {

                    toRemove = terrain;
                    generateMagneticField(to, plugin.getFlareProbability(), 200f, false);
                }
            }
        }

        from.getStarSystem().removeEntity(toRemove);
    }

    public void generateMagneticField(SectorEntityToken token, float flareProbability, float width, boolean jp) {
        //if (!(context.star instanceof PlanetAPI)) return null;

        StarSystemAPI system = token.getStarSystem();

        int baseIndex = (int) (baseColors.length * StarSystemGenerator.random.nextFloat());
        int auroraIndex = (int) (auroraColors.length * StarSystemGenerator.random.nextFloat());

        float bandWidth = token.getRadius() + width;
        float midRadius = jp ? token.getRadius() / 2f : (token.getRadius() + width) / 2f;
        float visStartRadius = token.getRadius();
        float visEndRadius = token.getRadius() + width + 50f;

        SectorEntityToken magField = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(bandWidth, // terrain effect band width
                        midRadius, // terrain effect middle radius
                        token, // entity that it's around
                        visStartRadius, // visual band start
                        visEndRadius, // visual band end
                        baseColors[baseIndex], // base color
                        flareProbability, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        auroraColors[auroraIndex]
                ));
        magField.setCircularOrbit(token, 0, 0, 100);
    }
}
