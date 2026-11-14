package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.LighthouseEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEyeIndicator;
import indevo.industries.artillery.projectiles.MortarProjectileEntityPlugin;
import indevo.industries.artillery.renderers.ArtilleryFireRenderer;
import indevo.industries.artillery.scripts.ArtilleryStationScript;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.scripts.EyeIndicatorScript;
import indevo.industries.artillery.terrain.LighthouseBeamTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import indevo.industries.artillery.utils.ArtilleryStationPlacer.VisionType;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.SmokeCloudEntityPlugin;
import indevo.items.consumables.fleet.MissileMemFlags;
import indevo.utils.helper.ReflectionUtils;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ArtilleryChecks {
    private ArtilleryChecks() {
    }

    static final String MORTAR = ArtilleryStationEntityPlugin.TYPE_MORTAR;
    static final String RAILGUN = ArtilleryStationEntityPlugin.TYPE_RAILGUN;
    static final String MISSILE = ArtilleryStationEntityPlugin.TYPE_MISSILE;
    static final Vector2f HIDE = new Vector2f(25000f, 25000f);
    static final float LOCK = EyeIndicatorScript.MAX_TIME_TO_TARGET_LOCK;
    static final String RELICS = "IndEvo_rare_parts";

    static final Map<SectorEntityToken, Supplier<Vector2f>> pins = new LinkedHashMap<>();
    static final Map<DroneNetScript.Spotter, Supplier<Vector2f>> spotterPins = new LinkedHashMap<>();
    static final Map<DroneNetScript.Spotter, Supplier<Float>> aims = new HashMap<>();
    static final Map<SectorEntityToken, Supplier<Float>> beamAims = new HashMap<>();
    static Runnable afterLoad = null;
    static long windowStart;
    static float zoomBefore = -1f;
    static float minZoomBefore = -1f;

    static Fx fx;
    static Probe probe;
    static final Map<String, Object> keep = new HashMap<>();

    // pins run every frame, also paused, so captures and checks see the placed geometry
    public static class Pinner implements EveryFrameScript {
        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            for (Map.Entry<SectorEntityToken, Supplier<Vector2f>> e : pins.entrySet()) {
                Vector2f p = e.getValue().get();
                if (p == null) continue;
                SectorEntityToken t = e.getKey();
                t.setLocation(p.x, p.y);
                if (t instanceof CampaignFleetAPI fleet) {
                    fleet.setVelocity(0f, 0f);
                    fleet.setMoveDestination(p.x, p.y);
                }
            }
            for (Map.Entry<DroneNetScript.Spotter, Supplier<Vector2f>> e : spotterPins.entrySet()) {
                DroneNetScript.Spotter s = e.getKey();
                if (s.entity == null) continue;
                Vector2f p = e.getValue().get();
                s.entity.setLocation(p.x, p.y);
                Supplier<Float> aim = aims.get(s);
                if (aim != null) {
                    float a = aim.get();
                    s.coneAngle = a;
                    s.heading = a;
                    s.entity.setFacing(a);
                }
            }
            for (Map.Entry<SectorEntityToken, Supplier<Float>> e : beamAims.entrySet()) {
                LighthouseBeamTerrain beam = beam(e.getKey());
                if (beam != null) ReflectionUtils.set("angle", beam, e.getValue().get());
            }
        }
    }

    public static class Frame implements EveryFrameScript {
        private final Runnable run;

        Frame(Runnable run) {
            this.run = run;
        }

        @Override
        public boolean isDone() {
            return afterLoad != run;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            if (afterLoad == run) run.run();
        }
    }

    // called from CheckPlugin.onGameLoad, statics survive quickLoad but entities are new objects
    public static void loaded() {
        pins.clear();
        spotterPins.clear();
        aims.clear();
        beamAims.clear();
        if (afterLoad != null) Global.getSector().addTransientScript(new Frame(afterLoad));
    }

    // wall time between frames, only meaningful in a --perf run
    static final class FrameTimer {
        long last;
        final List<Float> ms = new ArrayList<>();

        void tick() {
            long now = System.nanoTime();
            if (last != 0L) ms.add((now - last) / 1e6f);
            last = now;
        }

        @Override
        public String toString() {
            if (ms.isEmpty()) return "no frames";
            List<Float> sorted = new ArrayList<>(ms);
            Collections.sort(sorted);
            float sum = 0f;
            for (float v : sorted) sum += v;
            return String.format("%d frames, mean %.2f ms, p50 %.2f, p95 %.2f, max %.2f", sorted.size(), sum / sorted.size(),
                    sorted.get(sorted.size() / 2), sorted.get((int) (sorted.size() * 0.95f)), sorted.get(sorted.size() - 1));
        }
    }

    static FrameTimer timer;

    static final class Fx {
        final FrameTimer timer = new FrameTimer();
        final SectorEntityToken station;
        final SectorEntityToken planet;
        final long start = Global.getSector().getClock().getTimestamp();
        final Set<Object> flashes = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Float> shotTimes = new ArrayList<>();
        Object effect;
        float maxCharge;
        float heat;
        int particles;
        boolean venting;
        float ventTime;
        float ventStart = -1f;
        float lastShotBeforeVent = -1f;
        boolean ventStoppedByShot;
        int maxPulses;
        boolean sawEffect;
        boolean idle;
        float maxStep;
        float maxTurn;
        float minDist = Float.MAX_VALUE;
        float maxDist;
        Vector2f lastLoc;
        float lastFacing = Float.NaN;
        int frames;

        Fx(SectorEntityToken station, SectorEntityToken planet) {
            this.station = station;
            this.planet = planet;
        }

        float secs() {
            return Global.getSector().getClock().getElapsedDaysSince(start) * 10f;
        }

        void sample() {
            timer.tick();
            float t = secs();
            frames++;
            Object e = effect(station);
            if (e != null) {
                effect = e;
                sawEffect = true;
                idle = false;
                float charge = f(e, "charge");
                if (shotTimes.isEmpty()) maxCharge = Math.max(maxCharge, charge);
                boolean wasVenting = venting;
                int before = shotTimes.size();
                for (Object flash : (List<?>) ReflectionUtils.get("flashes", e)) {
                    if (flashes.add(flash)) shotTimes.add(t);
                }
                venting = (Boolean) ReflectionUtils.get("venting", e);
                ventTime = f(e, "ventTime");
                heat = f(e, "heat");
                particles = ((List<?>) ReflectionUtils.get("particles", e)).size();
                maxPulses = Math.max(maxPulses, (Integer) ReflectionUtils.get("pulses", e));
                if (venting && ventStart < 0f && !shotTimes.isEmpty()) {
                    ventStart = t;
                    lastShotBeforeVent = shotTimes.get(shotTimes.size() - 1);
                }
                if (wasVenting && shotTimes.size() > before && !venting) ventStoppedByShot = true;
            } else if (sawEffect) idle = true;

            Vector2f loc = new Vector2f(station.getLocation());
            float dist = Misc.getDistance(loc, planet.getLocation());
            minDist = Math.min(minDist, dist);
            maxDist = Math.max(maxDist, dist);
            if (lastLoc != null) maxStep = Math.max(maxStep, Misc.getDistance(loc, lastLoc));
            if (!Float.isNaN(lastFacing)) maxTurn = Math.max(maxTurn, Math.abs(Misc.getAngleDiff(lastFacing, station.getFacing())));
            lastLoc = loc;
            lastFacing = station.getFacing();
        }
    }

    static final class Probe {
        final long start = Global.getSector().getClock().getTimestamp();
        float lockAt = -1f;
        float last;
        float maxJump;
        float firstProj = -1f;
        String projType;
        int projBefore;
        StarSystemAPI system;
        float e1 = -1f;
        float e4 = -1f;
        float minE = Float.MAX_VALUE;
        float maxE;
        boolean flag;

        Probe(StarSystemAPI system, String projType) {
            this.system = system;
            this.projType = projType;
            this.last = eyeElapsed();
            if (projType != null) projBefore = count(system, projType);
        }

        float secs() {
            return Global.getSector().getClock().getElapsedDaysSince(start) * 10f;
        }

        void sample() {
            float t = secs();
            float e = eyeElapsed();
            maxJump = Math.max(maxJump, e - last);
            last = e;
            minE = Math.min(minE, e);
            maxE = Math.max(maxE, e);
            if (lockAt < 0f && e >= LOCK) lockAt = t;
            if (e1 < 0f && t >= 1f) e1 = e;
            if (e4 < 0f && t >= 4f) e4 = e;
            if (playerSeen()) flag = true;
            if (projType != null && firstProj < 0f && count(system, projType) > projBefore) firstProj = t;
        }
    }

    // ---------------------------------------------------------------- helpers

    static CampaignFleetAPI player() {
        return Global.getSector().getPlayerFleet();
    }

    static float f(Object o, String field) {
        return ((Number) ReflectionUtils.get(field, o)).floatValue();
    }

    static void ensurePinner() {
        if (!Global.getSector().hasTransientScript(Pinner.class)) Global.getSector().addTransientScript(new Pinner());
    }

    static void pin(SectorEntityToken e, Supplier<Vector2f> p) {
        ensurePinner();
        pins.put(e, p);
    }

    static void pinPlayer(Supplier<Vector2f> p) {
        pin(player(), p);
    }

    static void pinSpotter(DroneNetScript.Spotter s, Supplier<Vector2f> p, Supplier<Float> aim) {
        ensurePinner();
        spotterPins.put(s, p);
        if (aim != null) aims.put(s, aim);
        else aims.remove(s);
    }

    static void unpinAll() {
        pins.clear();
        spotterPins.clear();
        aims.clear();
        beamAims.clear();
    }

    static Vector2f at(Vector2f from, float angle, float dist) {
        Vector2f v = Misc.getUnitVectorAtDegreeAngle(angle);
        v.scale(dist);
        return Vector2f.add(v, from, v);
    }

    static Vector2f at(SectorEntityToken from, float angle, float dist) {
        return at(from.getLocation(), angle, dist);
    }

    static void realTime() {
        Time.mult = 1f;
    }

    static void fastTime() {
        Time.mult = 60f;
    }

    // a timed window with an ok line, game time
    static void hold(Ctx c, float days, String label, Runnable hook) {
        long start = Global.getSector().getClock().getTimestamp();
        windowStart = start;
        c.until(() -> Global.getSector().getClock().getElapsedDaysSince(start) >= days, days + 0.5f, label, hook);
    }

    // vision flags update every 0.25 to 0.5 s, the first part of a window still shows the last placement
    static boolean settled() {
        return Global.getSector().getClock().getElapsedDaysSince(windowStart) * 10f >= 0.6f;
    }

    static void hold(Ctx c, float days, String label) {
        hold(c, days, label, null);
    }

    static com.fs.starfarer.api.characters.AbilityPlugin ability(String id) {
        if (player().getAbility(id) == null) {
            Global.getSector().getCharacterData().addAbility(id);
            player().addAbility(id);
        }
        return player().getAbility(id);
    }

    static void zoom(float factor) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (zoomBefore < 0f) {
            zoomBefore = ui.getZoomFactor();
            minZoomBefore = ui.getMinZoomFactor();
        }
        if (factor < ui.getMinZoomFactor()) ui.setMinZoomFactor(factor);
        ui.setZoomFactor(Math.min(factor, ui.getMaxZoomFactor()));
    }

    static void zoomReset() {
        if (zoomBefore < 0f) return;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        ui.setMinZoomFactor(minZoomBefore);
        ui.setZoomFactor(zoomBefore);
        zoomBefore = -1f;
    }

    static void weights(VisionType vision) throws Exception {
        Colony.setting(Setting.ARTILLERY_VISION_WEIGHT_WATCHTOWER, vision == VisionType.WATCHTOWER ? 1.0 : 0.0);
        Colony.setting(Setting.ARTILLERY_VISION_WEIGHT_LIGHTHOUSE, vision == VisionType.LIGHTHOUSE ? 1.0 : 0.0);
        Colony.setting(Setting.ARTILLERY_VISION_WEIGHT_DRONES, vision == VisionType.DRONES ? 1.0 : 0.0);
    }

    static StarSystemAPI make(Ctx c, String key, VisionType vision, String type) throws Exception {
        weights(vision);
        int n = c.get("art_n") == null ? 0 : (Integer) c.get("art_n");
        c.put("art_n", n + 1);

        String name = "Arty Test " + key.toUpperCase();
        StarSystemAPI s = Global.getSector().createStarSystem(name);
        s.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
        // sprites with useLightColor draw black in a system without a light colour
        s.setLightColor(new java.awt.Color(255, 245, 225));
        PlanetAPI star = s.initStar("ieart_" + key + "_star", "star_yellow", 500f, -20000f - 2500f * (n % 12), -38000f - 2500f * (n / 12), 300f);
        PlanetAPI p1 = s.addPlanet("ieart_" + key + "_p1", star, name + " I", "barren", 0f, 200f, 4000f, 200f);
        PlanetAPI p2 = s.addPlanet("ieart_" + key + "_p2", star, name + " II", "barren", 180f, 150f, 9000f, 450f);
        Misc.initConditionMarket(p1);
        Misc.initConditionMarket(p2);
        // autogenerate only mirrors in-system jump points into hyperspace
        JumpPointAPI jp = Global.getFactory().createJumpPoint("ieart_" + key + "_jp", name + " Jump-point");
        jp.setCircularOrbit(star, 90f, 6500f, 300f);
        jp.setStandardWormholeToHyperspaceVisual();
        s.addEntity(jp);
        p1.getMarket().getMemoryWithoutUpdate().set(ArtilleryStationScript.TYPE_KEY, type);
        s.autogenerateHyperspaceJumpPoints(false, false);

        ArtilleryStationPlacer.addArtilleryToPlanet(p1, false);
        c.put("sys_" + key, name);

        String placed = s.getMemoryWithoutUpdate().getString(Ids.MEM_SYSTEM_ARTILLERY_VISION);
        c.expect(vision.name().equals(placed), "T1 vision weights live, " + name + " got " + placed + " (want " + vision + ")");

        Colony.jump(s, HIDE.x, HIDE.y);
        unpinAll();
        zoomReset();
        pinPlayer(() -> HIDE);
        resetEye();
        hold(c, 0.1f, "setup " + key + " runs");
        return s;
    }

    static StarSystemAPI sys(Ctx c, String key) {
        return Global.getSector().getStarSystem((String) c.get("sys_" + key));
    }

    static PlanetAPI planet(StarSystemAPI s, int n) {
        for (PlanetAPI p : s.getPlanets()) {
            if (p.getId().endsWith("_p" + n)) return p;
        }
        return null;
    }

    static SectorEntityToken station(StarSystemAPI s) {
        for (SectorEntityToken t : s.getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION)) {
            if ("IndEvo_ArtilleryStation".equals(t.getCustomEntityType())) return t;
        }
        return null;
    }

    static CampaignAttackScript attack(SectorEntityToken station) {
        return ((ArtilleryStationEntityPlugin) station.getCustomPlugin()).getOrInitScript();
    }

    static List<SectorEntityToken> entities(LocationAPI s, String type) {
        List<SectorEntityToken> list = new ArrayList<>();
        for (CustomCampaignEntityAPI e : s.getCustomEntities()) {
            if (type.equals(e.getCustomEntityType()) && !e.isExpired()) list.add(e);
        }
        return list;
    }

    static int count(LocationAPI s, String type) {
        return entities(s, type).size();
    }

    static String projectile(String type) {
        return switch (type) {
            case MORTAR -> "IndEvo_artillery_projectile";
            case RAILGUN -> "IndEvo_RailgunShotEntity";
            default -> "IndEvo_missileCarrierPlugin";
        };
    }

    static Object effect(SectorEntityToken station) {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ArtilleryFireRenderer.class);
        if (renderer == null) return null;
        return ((Map<?, ?>) ReflectionUtils.get("effects", renderer)).get(station);
    }

    static EyeIndicatorScript eye() {
        return EyeIndicatorScript.getInstance();
    }

    static float eyeElapsed() {
        return f(eye(), "elapsed");
    }

    static WatchtowerEyeIndicator.State eyeState() {
        SectorEntityToken indicator = (SectorEntityToken) ReflectionUtils.get("indicator", eye());
        return ((WatchtowerEyeIndicator) indicator.getCustomPlugin()).state;
    }

    static boolean playerSeen() {
        return player().getMemoryWithoutUpdate().getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY);
    }

    static void resetEye() {
        eye().reset();
        player().getMemoryWithoutUpdate().unset(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY);
    }

    static void clearFleets(LocationAPI s) {
        for (CampaignFleetAPI f : new ArrayList<>(s.getFleets())) {
            if (!f.isPlayerFleet() && !f.isStationMode()) s.removeEntity(f);
        }
    }

    static List<SectorEntityToken> houses(StarSystemAPI s) {
        return entities(s, LighthouseEntityPlugin.ENTITY_ID);
    }

    static LighthouseEntityPlugin house(SectorEntityToken h) {
        return (LighthouseEntityPlugin) h.getCustomPlugin();
    }

    static LighthouseBeamTerrain beam(SectorEntityToken h) {
        Object beam = ReflectionUtils.get("beam", house(h));
        if (!(beam instanceof CampaignTerrainAPI terrain)) return null;
        return (LighthouseBeamTerrain) terrain.getPlugin();
    }

    static float beamAngle(SectorEntityToken h) {
        return f(beam(h), "angle");
    }

    static int rolledBeams(SectorEntityToken h) {
        return (Integer) ReflectionUtils.get("beamCount", house(h));
    }

    // same angular speed as the planet, so the geometry against it holds
    static void relocate(SectorEntityToken h, StarSystemAPI s, float radius, float angleOffset) {
        PlanetAPI p1 = planet(s, 1);
        float angle = Misc.getAngleInDegrees(s.getCenter().getLocation(), p1.getLocation()) + angleOffset;
        h.setCircularOrbit(s.getCenter(), angle, radius, 200f);
        Vector2f at = at(s.getCenter(), angle, radius);
        h.setLocation(at.x, at.y);
        // the beam shadow eases toward new geometry, a teleported house needs a fresh one
        Object terrain = ReflectionUtils.get("beam", house(h));
        if (terrain instanceof SectorEntityToken t) {
            t.setLocation(at.x, at.y);
            ReflectionUtils.set("blocker", ((CampaignTerrainAPI) t).getPlugin(), null);
        }
    }

    static DroneNetScript net(StarSystemAPI s) {
        return DroneNetScript.get(s);
    }

    static List<DroneNetScript.Spotter> live(DroneNetScript net) {
        List<DroneNetScript.Spotter> list = new ArrayList<>();
        if (net == null) return list;
        for (DroneNetScript.Spotter s : net.getSpotters()) if (s.entity != null) list.add(s);
        return list;
    }

    static void park(DroneNetScript net, List<DroneNetScript.Spotter> except) {
        int i = 0;
        for (DroneNetScript.Spotter s : net.getSpotters()) {
            if (except.contains(s)) continue;
            Vector2f p = new Vector2f(-25000f + 600f * i++, -25000f);
            pinSpotter(s, () -> p, () -> 270f);
        }
    }

    static CampaignFleetAPI npc(LocationAPI s, Vector2f at) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, "Test Patrol", true);
        fleet.getFleetData().addFleetMember("onslaught_Elite");
        fleet.getFleetData().ensureHasFlagship();
        s.addEntity(fleet);
        fleet.setLocation(at.x, at.y);
        return fleet;
    }

    static SectorEntityToken cloud(LocationAPI s, Vector2f at, float radius) {
        return s.addCustomEntity(Misc.genUID(), null, "IndEvo_SmokeCloud", null, new SmokeCloudEntityPlugin.SmokeCloudParams(s, new Vector2f(at), 120f, radius));
    }

    static void removeCloud(SectorEntityToken cloud) {
        Object terrain = ReflectionUtils.get("terrain", cloud.getCustomPlugin());
        if (terrain instanceof SectorEntityToken t) cloud.getContainingLocation().removeEntity(t);
        cloud.getContainingLocation().removeEntity(cloud);
    }

    static SectorEntityToken token(LocationAPI s, String id, Vector2f at) {
        SectorEntityToken t = s.addCustomEntity(id, null, "IndEvo_token", Factions.NEUTRAL, null);
        t.setLocation(at.x, at.y);
        return t;
    }

    static void map(Ctx c, String name) {
        Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.MAP);
        c.frames(40);
        keep.put("map", name);
    }

    // ---------------------------------------------------------------- steps

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        oldSave(steps);
        fire(steps, MORTAR, "f1");
        fire(steps, RAILGUN, "f2");
        fire(steps, MISSILE, "f3");
        reloadVolley(steps);
        watchtower(steps);
        lighthouse(steps);
        lighthouseRules(steps);
        lighthouseReload(steps);
        lighthouseSight(steps);
        drones(steps);
        droneSight(steps);
        droneBlasts(steps);
        droneLife(steps);
        droneReload(steps);
        droneColony(steps);
        nestDialog(steps);
        planetFleet(steps);
        steps.add(c -> {
            for (Setting s : new Setting[]{Setting.ARTILLERY_VISION_WEIGHT_WATCHTOWER, Setting.ARTILLERY_VISION_WEIGHT_LIGHTHOUSE, Setting.ARTILLERY_VISION_WEIGHT_DRONES}) Colony.setting(s, 1.0);
            unpinAll();
            zoomReset();
            fastTime();
            c.note("artillery settings back to default");
        });
        return steps;
    }

    // S4: a save from before #141 keeps its watchtowers and gets no lighthouse or drone net
    private static void oldSave(List<Step> steps) {
        steps.add(c -> {
            int houses = 0, nests = 0, nets = 0, towers = 0, systems = 0;
            for (StarSystemAPI s : Global.getSector().getStarSystems()) {
                houses += count(s, LighthouseEntityPlugin.ENTITY_ID);
                nests += count(s, DroneNetScript.NEST_ENTITY_ID);
                towers += count(s, WatchtowerEntityPlugin.ENTITY_ID);
                if (s.getMemoryWithoutUpdate().contains(DroneNetScript.MEM_KEY)) nets++;
                if (s.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)) systems++;
            }
            c.note("S4 old save: " + systems + " artillery systems, " + towers + " watchtowers");
            c.expect(houses == 0, "S4 no IndEvo_Lighthouse on the old save (got " + houses + ")");
            c.expect(nests == 0, "S4 no IndEvo_DroneNest on the old save (got " + nests + ")");
            c.expect(nets == 0, "S4 no $IndEvo_droneNet on the old save (got " + nets + ")");
            c.expect(towers > 0, "S4 watchtowers still present on the old save");
        });
    }

    // F1 to F5, R3, T2, R1 volley: one station per type firing at a token at mid range
    private static void fire(List<Step> steps, String type, String tag) {
        String id = type.equals(MORTAR) ? "F1" : type.equals(RAILGUN) ? "F2" : "F3";
        steps.add(c -> make(c, tag, VisionType.WATCHTOWER, type));
        steps.add(c -> {
            StarSystemAPI s = sys(c, tag);
            SectorEntityToken st = station(s);
            c.expect(st != null && type.equals(((ArtilleryStationEntityPlugin) st.getCustomPlugin()).getType()), id + " " + type + " station spawned");
            st.setDiscoverable(false);
            PlanetAPI p1 = planet(s, 1);
            // fire east with the fleet below the station, so the station sits in the top third of the capture
            float out = 0f;
            Vector2f tl = at(st, out, 6000f);
            SectorEntityToken target = token(s, "ieart_" + tag + "_t1", tl);
            pin(target, () -> tl);
            pinPlayer(() -> at(st, 270f, 110f));
            zoom(Global.getSector().getCampaignUI().getZoomFactor() / 3f);
            c.note(id + " zoom " + Global.getSector().getCampaignUI().getZoomFactor() + " (was " + zoomBefore + ")");
            realTime();
            fx = new Fx(st, p1);
            attack(st).forceTarget(target, 30f);
            c.until(() -> fx.maxCharge >= 0.5f || !fx.shotTimes.isEmpty(), 1f, id + " volley starts", fx::sample);
        });
        steps.add(c -> {
            if (type.equals(MISSILE)) {
                StringBuilder delays = new StringBuilder();
                for (SectorEntityToken e : entities(sys(c, tag), "IndEvo_missileCarrierPlugin")) {
                    indevo.industries.artillery.projectiles.MissileCarrierEntityPlugin m = (indevo.industries.artillery.projectiles.MissileCarrierEntityPlugin) e.getCustomPlugin();
                    delays.append(m.projectileDelaySeconds).append(' ');
                }
                c.note(id + " carrier launch delays at 6000 su: " + delays.toString().trim());
            }
            c.expect(fx.maxCharge >= 0.5f && fx.shotTimes.isEmpty(), id + " wind-up glow before the first shot (charge " + fx.maxCharge + ")");
            c.shot(tag + "-windup");
        });
        steps.add(c -> c.until(() -> !fx.shotTimes.isEmpty(), 1f, id + " first shot", fx::sample));
        steps.add(c -> c.shot(tag + "-shot"));
        steps.add(c -> c.until(() -> fx.secs() - fx.shotTimes.get(0) >= 0.6f, 1f, id + " smoke phase", fx::sample));
        steps.add(c -> {
            c.expect(fx.particles > 0, id + " smoke particles after the first shot (" + fx.particles + ")");
            keep.put("f5", new float[]{fx.heat, fx.particles, sumParticles(fx.effect)});
            c.shot(tag + "-smoke");
        });
        steps.add(c -> c.frames(120));
        steps.add(c -> {
            float[] before = (float[]) keep.get("f5");
            Object e = effect(fx.station);
            float[] now = {f(e, "heat"), ((List<?>) ReflectionUtils.get("particles", e)).size(), sumParticles(e)};
            c.expect(before[0] == now[0] && before[1] == now[1] && before[2] == now[2], "F5 " + type + " paused 2 s leaves the effect unchanged (heat " + before[0] + "/" + now[0] + ", particles " + before[1] + "/" + now[1] + ")");
            c.until(() -> fx.venting && fx.ventTime >= 1f, 3f, id + " vent runs", fx::sample);
        });
        steps.add(c -> c.shot(tag + "-vent"));
        steps.add(c -> c.until(() -> fx.idle, 3f, id + " cools to idle", fx::sample));
        steps.add(c -> {
            int want = switch (type) {
                case MORTAR -> Setting.ARTILLERY_MORTAR_PROJECTILES_PER_SHOT.intValue();
                case RAILGUN -> Setting.ARTILLERY_RAILGUN_PROJECTILES_PER_SHOT.intValue();
                default -> Setting.ARTILLERY_MISSILE_PROJECTILES_PER_SHOT.intValue();
            };
            int expected = type.equals(MORTAR) ? 8 : type.equals(RAILGUN) ? 3 : 4;
            c.expect(fx.shotTimes.size() == want && want == expected, id + " reportShot calls per " + type + " volley " + fx.shotTimes.size() + " (setting " + want + ", default " + expected + ")");
            c.note(id + " shot times " + fx.shotTimes);
            float delay = fx.ventStart - fx.lastShotBeforeVent;
            c.expect(fx.lastShotBeforeVent == fx.shotTimes.get(fx.shotTimes.size() - 1), "F4 " + type + " vent starts after the last shot of the volley");
            c.expect(delay >= StationFireEffect_VENT_DELAY() - 0.02f && delay <= StationFireEffect_VENT_DELAY() + 0.1f, "F4 " + type + " vent starts last shot + 0.45 s (got " + delay + ")");
            if (type.equals(MORTAR)) c.expect(fx.maxPulses == 5, "F1 mortar vents 5 puffs (got " + fx.maxPulses + ")");
            c.note("R3 " + type + " station over " + fx.frames + " frames: planet distance " + fx.minDist + " to " + fx.maxDist + ", max step " + fx.maxStep + " su/frame, max turn " + fx.maxTurn + " deg/frame");
            c.expect(fx.maxDist - fx.minDist < 1f, "R3 " + type + " station keeps its orbit radius during the volley");
            c.expect(fx.maxStep < 2f, "R3 " + type + " station never kicks (max step " + fx.maxStep + ")");
            c.expect(fx.maxTurn < 1f, "R3 " + type + " station never turns (max turn " + fx.maxTurn + ")");
            c.note("PERF " + type + " volley, wind-up to cooled: " + fx.timer);
            c.shot(tag + "-cooled");
            dropToken(sys(c, tag), tag + "_t1");
        });
        // T2 at default zoom, then F4 vent stop and F5 double speed on the next volleys
        steps.add(c -> {
            zoomReset();
            StarSystemAPI s = sys(c, tag);
            SectorEntityToken st = fx.station;
            float out = Misc.getAngleInDegrees(fx.planet.getLocation(), st.getLocation());
            Vector2f tl = at(st, out, 6000f);
            SectorEntityToken target = token(s, "ieart_" + tag + "_t2", tl);
            pin(target, () -> tl);
            pinPlayer(() -> at(st, out, 900f));
            fx = new Fx(st, fx.planet);
            attack(st).forceTarget(target, 30f);
            c.until(() -> !fx.shotTimes.isEmpty() && fx.secs() - fx.shotTimes.get(0) >= 0.8f, 1.5f, "T2 " + type + " volley at default zoom", fx::sample);
        });
        steps.add(c -> c.shot(tag + "-t2"));
        steps.add(c -> c.until(() -> fx.venting, 3f, "F4 " + type + " vent before the next volley", fx::sample));
        steps.add(c -> {
            StarSystemAPI s = sys(c, tag);
            SectorEntityToken st = fx.station;
            float out = Misc.getAngleInDegrees(fx.planet.getLocation(), st.getLocation()) + 40f;
            Vector2f tl = at(st, out, 6000f);
            SectorEntityToken target = token(s, "ieart_" + tag + "_t3", tl);
            pin(target, () -> tl);
            attack(st).forceTarget(target, 30f);
            int before = fx.shotTimes.size();
            keep.put("f4", before);
            c.until(() -> fx.shotTimes.size() > before, 1f, "F4 " + type + " second volley fires during the vent", fx::sample);
        });
        steps.add(c -> {
            c.expect(fx.ventStoppedByShot, "F4 " + type + " a new shot stops the vent");
            c.until(() -> fx.idle, 4f, id + " second volley cools", fx::sample);
        });
        steps.add(c -> {
            dropToken(sys(c, tag), tag + "_t2");
            dropToken(sys(c, tag), tag + "_t3");
        });
        if (type.equals(MORTAR)) {
            steps.add(c -> {
                StarSystemAPI s = sys(c, tag);
                SectorEntityToken st = fx.station;
                float out = Misc.getAngleInDegrees(fx.planet.getLocation(), st.getLocation()) - 40f;
                Vector2f tl = at(st, out, 6000f);
                SectorEntityToken target = token(s, "ieart_" + tag + "_t4", tl);
                pin(target, () -> tl);
                fx = new Fx(st, fx.planet);
                Time.mult = 2f;
                attack(st).forceTarget(target, 30f);
                c.until(() -> fx.idle, 4f, "F5 mortar volley at x2", fx::sample);
            });
            steps.add(c -> {
                realTime();
                c.expect(fx.maxPulses == 5, "F5 mortar still does 5 puffs at x2 (got " + fx.maxPulses + ")");
                c.expect(fx.shotTimes.size() == Setting.ARTILLERY_MORTAR_PROJECTILES_PER_SHOT.intValue(), "F5 mortar volley at x2 shows every shot (" + fx.shotTimes.size() + ")");
                dropToken(sys(c, tag), tag + "_t4");
            });
        }
        steps.add(c -> {
            unpinAll();
            fastTime();
        });
    }

    static void dropToken(StarSystemAPI s, String id) {
        SectorEntityToken t = s.getEntityById("ieart_" + id);
        if (t == null) return;
        pins.remove(t);
        s.removeEntity(t);
    }

    static float StationFireEffect_VENT_DELAY() {
        return indevo.industries.artillery.renderers.StationFireEffect.VENT_DELAY;
    }

    static float sumParticles(Object effect) {
        float sum = 0f;
        for (Object p : (List<?>) ReflectionUtils.get("particles", effect)) sum += f(p, "x") + f(p, "y") + f(p, "age");
        return sum;
    }

    // S3: reload between the 3rd and 5th mortar shell
    private static void reloadVolley(List<Step> steps) {
        steps.add(c -> make(c, "s3", VisionType.WATCHTOWER, MORTAR));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "s3");
            SectorEntityToken st = station(s);
            st.setDiscoverable(false);
            PlanetAPI p1 = planet(s, 1);
            float out = Misc.getAngleInDegrees(p1.getLocation(), st.getLocation());
            pinPlayer(() -> at(st, out, 6000f));
            realTime();
            fx = new Fx(st, p1);
            attack(st).forceTarget(player(), 30f);
            c.until(() -> fx.shotTimes.size() >= 3, 1.5f, "S3 three mortar shells out", fx::sample);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "s3");
            int shots = fx.shotTimes.size();
            c.expect(shots >= 3 && shots < 5, "S3 reload between the 3rd and 5th shell (shots " + shots + ")");
            List<String> ids = new ArrayList<>();
            Map<String, float[]> targets = new HashMap<>();
            for (SectorEntityToken p : entities(s, "IndEvo_artillery_projectile")) {
                MortarProjectileEntityPlugin plugin = (MortarProjectileEntityPlugin) p.getCustomPlugin();
                ids.add(p.getId());
                targets.put(p.getId(), new float[]{plugin.target.x, plugin.target.y, Float.MAX_VALUE, 0f});
            }
            c.note("S3 " + ids.size() + " shells alive at save, " + shots + " fired");
            keep.put("s3effect", fx.effect);
            keep.put("s3targets", targets);
            keep.put("s3carried", Boolean.FALSE);
            keep.put("s3newshot", Boolean.FALSE);
            keep.put("s3system", (String) c.get("sys_s3"));
            String stationId = fx.station.getId();
            afterLoad = () -> {
                StarSystemAPI sys = Global.getSector().getStarSystem((String) keep.get("s3system"));
                SectorEntityToken station = sys.getEntityById(stationId);
                Object e = station == null ? null : effect(station);
                if (e != null && !(Boolean) keep.get("s3newshot")) {
                    if (!((List<?>) ReflectionUtils.get("flashes", e)).isEmpty()) keep.put("s3newshot", Boolean.TRUE);
                    else if (f(e, "heat") > 0f || !((List<?>) ReflectionUtils.get("particles", e)).isEmpty()) keep.put("s3carried", Boolean.TRUE);
                    if (e == keep.get("s3effect")) keep.put("s3carried", Boolean.TRUE);
                }
                @SuppressWarnings("unchecked") Map<String, float[]> t = (Map<String, float[]>) keep.get("s3targets");
                for (Map.Entry<String, float[]> entry : t.entrySet()) {
                    SectorEntityToken shell = sys.getEntityById(entry.getKey());
                    float[] v = entry.getValue();
                    if (shell != null && !shell.isExpired()) v[2] = Misc.getDistance(shell.getLocation(), new Vector2f(v[0], v[1]));
                    else v[3] = 1f;
                }
            };
            c.reload();
        });
        steps.add(c -> {
            realTime();
            @SuppressWarnings("unchecked") Map<String, float[]> t = (Map<String, float[]>) keep.get("s3targets");
            c.until(() -> {
                for (float[] v : t.values()) if (v[3] == 0f) return false;
                return true;
            }, 2f, "S3 every shell left after the reload is gone");
        });
        steps.add(c -> {
            @SuppressWarnings("unchecked") Map<String, float[]> t = (Map<String, float[]>) keep.get("s3targets");
            int landed = 0;
            for (float[] v : t.values()) if (v[2] <= 80f) landed++;
            c.expect(landed == t.size(), "S3 remaining shells land on their reticules after the reload (" + landed + " of " + t.size() + ")");
            c.expect(!(Boolean) keep.get("s3carried"), "S3 no heat, smoke or effect carried over the reload before the next shot");
            c.expect((Boolean) keep.get("s3newshot"), "S3 a shell fired after the reload restarts the effect");
            afterLoad = null;
            unpinAll();
            fastTime();
        });
    }

    // E1, E4, E5 on a watchtower system
    private static void watchtower(List<Step> steps) {
        steps.add(c -> make(c, "w", VisionType.WATCHTOWER, MORTAR));
        steps.add(c -> {
            realTime();
            timer = new FrameTimer();
            hold(c, 1f, "PERF watchtower system window", timer::tick);
        });
        steps.add(c -> c.note("PERF watchtower system, player out of range, 10 s: " + timer));
        for (float share : new float[]{0.1f, 0.9f}) {
            int pct = Math.round(share * 100f);
            steps.add(c -> {
                StarSystemAPI s = sys(c, "w");
                clearFleets(s);
                List<SectorEntityToken> towers = entities(s, WatchtowerEntityPlugin.ENTITY_ID);
                c.expect(!towers.isEmpty(), "E1 watchtower placed");
                SectorEntityToken tower = towers.get(0);
                SectorEntityToken focus = tower.getOrbitFocus();
                float range = Setting.ARTILLERY_WATCHTOWER_RANGE.value();
                SectorEntityToken st = station(s);
                pinPlayer(() -> at(tower, Misc.getAngleInDegrees(tower.getLocation(), st.getLocation()), share * range));
                c.note("E1 " + towers.size() + " towers, station " + Misc.getDistance(station(s), tower) + " su from the tower");
                resetEye();
                realTime();
                probe = new Probe(s, projectile(MORTAR));
                c.until(() -> probe.lockAt >= 0f && (probe.firstProj >= 0f || probe.secs() > probe.lockAt + 4f), 3.5f, "E1 lock at " + pct + "% of range", probe::sample);
            });
            steps.add(c -> {
                float lo = share < 0.5f ? 10f : 18f;
                c.expect(probe.lockAt >= lo && probe.lockAt <= lo + 2f, "E1 eye locks in " + lo + " to " + (lo + 2f) + " s at " + pct + "% of IndEvo_Artillery_WatchtowerRange (got " + probe.lockAt + ")");
                c.expect(probe.maxJump < 1.5f, "E1 eye fills smoothly at " + pct + "% (max jump " + probe.maxJump + ")");
                float delay = probe.firstProj - probe.lockAt;
                c.expect(probe.firstProj >= 0f && delay <= Setting.ARTILLERY_MAX_DELAY_BETWEEN_SHOTS.value() + 0.5f, "E4 mortar shell spawns within the station delay after the lock (got " + delay + " s)");
                pinPlayer(() -> HIDE);
                probe = new Probe(sys(c, "w"), null);
                c.until(() -> eyeElapsed() <= 0f && !playerSeen(), 3f, "E1 eye drains to 0 outside range", probe::sample);
            });
        }
        steps.add(c -> {
            StarSystemAPI s = sys(c, "w");
            SectorEntityToken tower = entities(s, WatchtowerEntityPlugin.ENTITY_ID).get(0);
            SectorEntityToken focus = tower.getOrbitFocus();
            pinPlayer(() -> at(tower, Misc.getAngleInDegrees(focus.getLocation(), tower.getLocation()), 300f));
            resetEye();
            probe = new Probe(s, null);
            c.until(() -> probe.lockAt >= 0f, 2f, "E5 locked before the jump", probe::sample);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "w");
            unpinAll();
            JumpPointAPI jp = (JumpPointAPI) s.getJumpPoints().get(0);
            player().setLocation(jp.getLocation().x, jp.getLocation().y);
            c.note("E5 elapsed before the jump " + eyeElapsed() + ", seen " + playerSeen());
            Global.getSector().doHyperspaceTransition(player(), jp, jp.getDestinations().get(0));
            c.until(() -> player().isInHyperspace() && !player().isInHyperspaceTransition(), 2f, "E5 jump to hyperspace");
        });
        steps.add(c -> {
            c.expect(!playerSeen(), "E5 jump clears $IndEvo_WasSeenByOtherEntity");
            c.expect(eyeState() == WatchtowerEyeIndicator.State.NONE, "E5 jump clears the eye (state " + eyeState() + ")");
            c.note("E5 eye elapsed in hyperspace " + eyeElapsed());
            Colony.jump(sys(c, "w"), HIDE.x, HIDE.y);
            pinPlayer(() -> HIDE);
            probe = new Probe(sys(c, "w"), null);
            c.until(() -> probe.secs() >= 1.5f, 1f, "E5 back in the system out of range", probe::sample);
        });
        steps.add(c -> {
            c.expect(!probe.flag, "E5 back in the system out of every source, not seen again (elapsed " + eyeElapsed() + ")");
            map(c, "r2-watchtower");
        });
        steps.add(c -> c.shot((String) keep.get("map")));
        steps.add(c -> c.key("Escape"));
        steps.add(c -> {
            c.expect(Global.getSector().getCampaignUI().getCurrentCoreTab() == null, "R2 watchtower map closed");
            resetEye();
            unpinAll();
            fastTime();
        });
    }

    // E2, E4, H4, H5, H1, H2 on lighthouse systems
    private static void lighthouse(List<Step> steps) {
        steps.add(c -> make(c, "l", VisionType.LIGHTHOUSE, RAILGUN));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            List<SectorEntityToken> list = houses(s);
            c.expect(list.size() >= ArtilleryStationPlacer.MIN_LIGHTHOUSES_PER_SYSTEM, "E2 at least 4 lighthouses (" + list.size() + ")");
            SectorEntityToken h = list.get(0);
            relocate(h, s, 2500f, 0f);
            c.until(() -> house(h).isLit() && beam(h) != null, 0.5f, "E2 lighthouse lit");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(0);
            PlanetAPI p1 = planet(s, 1);
            beamAims.put(h, () -> Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) + 90f);
            pinPlayer(() -> at(h, Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) + 90f, 2000f));
            resetEye();
            probe = new Probe(s, projectile(RAILGUN));
            c.until(() -> probe.lockAt >= 0f && (probe.firstProj >= 0f || probe.secs() > probe.lockAt + 4f), 1f, "E2 lock in the beam", probe::sample);
        });
        steps.add(c -> {
            c.expect(probe.lockAt >= 0f && probe.lockAt <= 0.55f, "E2 lit beam on the player locks within one 0.5 s check (got " + probe.lockAt + ")");
            float delay = probe.firstProj - probe.lockAt;
            c.expect(probe.firstProj >= 0f && delay <= Setting.ARTILLERY_MAX_DELAY_BETWEEN_SHOTS.value() + 0.5f, "E4 railgun shot spawns within the station delay after the lock (got " + delay + " s)");
            pinPlayer(() -> HIDE);
            probe = new Probe(sys(c, "l"), null);
            c.until(() -> eyeElapsed() <= 0f, 3f, "E2 eye drains to 0 out of every beam", probe::sample);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            ability(Abilities.GO_DARK).activate();
            c.until(() -> ability(Abilities.GO_DARK).isActive(), 0.5f, "H4 go dark active");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(0);
            PlanetAPI p1 = planet(s, 1);
            beamAims.put(h, () -> Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) + 90f);
            pinPlayer(() -> at(h, Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) + 90f, 2000f));
            resetEye();
            probe = new Probe(s, null);
            c.until(() -> probe.lockAt >= 0f, 1f, "H4 lock in the beam with go dark", probe::sample);
        });
        steps.add(c -> {
            c.expect(probe.lockAt >= 0f && probe.lockAt <= 0.55f, "H4 go dark gives no cover from a beam (lock after " + probe.lockAt + " s)");
            ability(Abilities.GO_DARK).deactivate();
            beamAims.clear();
            pinPlayer(() -> HIDE);
            resetEye();
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(0);
            Vector2f spot = at(h, Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) - 90f, 1800f);
            CampaignFleetAPI fleet = npc(s, spot);
            pin(fleet, () -> spot);
            beamAims.put(h, () -> Misc.getAngleInDegrees(h.getLocation(), spot));
            keep.put("h5fleet", fleet);
            hold(c, 0.1f, "H5 beam window");
        });
        steps.add(c -> {
            CampaignFleetAPI fleet = (CampaignFleetAPI) keep.get("h5fleet");
            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            c.expect(mem.getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY), "H5 hostile NPC fleet in a beam is seen");
            float expire = mem.getExpire(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY);
            c.expect(expire > 4.8f && expire <= 5.01f, "H5 NPC fleet seen for 5 days (expire " + expire + ")");
            fleet.getContainingLocation().removeEntity(fleet);
            unpinAll();
            pinPlayer(() -> HIDE);
        });
        // H1 hack
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(1);
            relocate(h, s, 6500f, 40f);
            hold(c, 0.1f, "H1 relocation settles");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(1);
            c.expect(house(h).isLit(), "H1 lighthouse lit before the hack");
            house(h).setHacked(true, 30f);
            Vector2f spot = at(h, 0f, 2000f);
            CampaignFleetAPI fleet = npc(s, spot);
            pin(fleet, () -> at(h, 0f, 2000f));
            keep.put("h1fleet", fleet);
            realTime();
            c.until(() -> fleet.getMemoryWithoutUpdate().getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY), 3.5f, "H1 hacked house spots by range for its owner");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "l");
            SectorEntityToken h = houses(s).get(1);
            c.expect(!house(h).isLit() && !beam(h).isLit(), "H1 hack turns the beams off");
            CampaignFleetAPI fleet = (CampaignFleetAPI) keep.get("h1fleet");
            fleet.getContainingLocation().removeEntity(fleet);
            unpinAll();
            pinPlayer(() -> HIDE);
            ArtilleryStationScript script = (ArtilleryStationScript) planet(s, 1).getMemoryWithoutUpdate().get(ArtilleryStationScript.SCRIPT_KEY);
            int lit = 0;
            for (SectorEntityToken house : houses(s)) if (house(house).isLit()) lit++;
            c.note("H2 lit houses before the station is beaten: " + lit);
            script.setDestroyed(true);
            c.until(() -> {
                for (SectorEntityToken house : houses(s)) if (house(house).isLit() || (beam(house) != null && beam(house).isLit())) return false;
                return true;
            }, 0.2f, "H2 disrupted station turns every beam in its system off");
        });
        steps.add(c -> {
            unpinAll();
            fastTime();
        });
    }

    // T1 beam length, width, turn time; R1 and R2 for lighthouses
    private static void lighthouseRules(List<Step> steps) {
        steps.add(c -> make(c, "lt", VisionType.LIGHTHOUSE, RAILGUN));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "lt");
            SectorEntityToken h = houses(s).get(0);
            relocate(h, s, 2500f, 0f);
            c.until(() -> house(h).isLit() && beam(h) != null, 0.5f, "T1 lighthouse lit");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "lt");
            SectorEntityToken h = houses(s).get(0);
            LighthouseBeamTerrain beam = beam(h);
            ReflectionUtils.set("angle", beam, Misc.getAngleInDegrees(planet(s, 1).getLocation(), h.getLocation()));
            float a = beamAngle(h);
            boolean in5000 = beam.sees(at(h, a, 5000f), 0f, 0f);
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_BEAM_LENGTH, 3000.0);
            boolean short5000 = beam.sees(at(h, a, 5000f), 0f, 0f);
            boolean short2500 = beam.sees(at(h, a, 2500f), 0f, 0f);
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_BEAM_LENGTH, 6000.0);
            c.expect(in5000 && !short5000 && short2500, "T1 beam length applies live (5000 su " + in5000 + " -> " + short5000 + ", 2500 su " + short2500 + ")");

            boolean wide = beam.sees(at(h, a + 8f, 2000f), 0f, 0f);
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_BEAM_WIDTH, 10.0);
            boolean narrow = beam.sees(at(h, a + 8f, 2000f), 0f, 0f);
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_BEAM_WIDTH, 20.0);
            c.expect(wide && !narrow, "T1 beam width applies live (8 deg off axis: width 20 " + wide + ", width 10 " + narrow + ")");
            keep.put("t1a", a);
            realTime();
            hold(c, 0.2f, "T1 two seconds at turn time 60");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "lt");
            SectorEntityToken h = houses(s).get(0);
            float turned60 = Math.abs(Misc.getAngleDiff((Float) keep.get("t1a"), beamAngle(h)));
            keep.put("t60", turned60);
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_TURN_TIME, 20.0);
            keep.put("t1a", beamAngle(h));
            hold(c, 0.2f, "T1 two seconds at turn time 20");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "lt");
            SectorEntityToken h = houses(s).get(0);
            float turned20 = Math.abs(Misc.getAngleDiff((Float) keep.get("t1a"), beamAngle(h)));
            float turned60 = (Float) keep.get("t60");
            Colony.setting(Setting.ARTILLERY_LIGHTHOUSE_TURN_TIME, 60.0);
            c.expect(turned60 > 9f && turned60 < 15f && turned20 > 30f && turned20 < 42f, "T1 turn time applies live (2 s: " + turned60 + " deg at 60 s, " + turned20 + " deg at 20 s)");
            pinPlayer(() -> at(h, Misc.getAngleInDegrees(s.getCenter().getLocation(), h.getLocation()) + 90f, 1500f));
            zoom(Global.getSector().getCampaignUI().getMaxZoomFactor());
            c.frames(30);
        });
        steps.add(c -> c.shot("r1-beams"));
        steps.add(c -> {
            zoomReset();
            map(c, "r2-lighthouse");
        });
        steps.add(c -> c.shot((String) keep.get("map")));
        steps.add(c -> c.key("Escape"));
        steps.add(c -> {
            c.expect(Global.getSector().getCampaignUI().getCurrentCoreTab() == null, "R2 lighthouse map closed");
            unpinAll();
            fastTime();
        });
    }

    // S2
    private static void lighthouseReload(List<Step> steps) {
        steps.add(c -> make(c, "s2", VisionType.LIGHTHOUSE, RAILGUN));
        steps.add(c -> {
            realTime();
            c.until(() -> {
                for (SectorEntityToken h : houses(sys(c, "s2"))) if (beam(h) == null) return false;
                return true;
            }, 0.5f, "S2 every lighthouse has its beam");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "s2");
            Map<String, float[]> saved = new HashMap<>();
            for (SectorEntityToken h : houses(s)) saved.put(h.getId(), new float[]{beam(h).getBeamCount(), house(h).isLit() ? 1f : 0f, beamAngle(h), -1f});
            c.note("S2 saved " + saved.size() + " houses");
            keep.put("s2", saved);
            keep.put("s2system", (String) c.get("sys_s2"));
            afterLoad = () -> {
                @SuppressWarnings("unchecked") Map<String, float[]> m = (Map<String, float[]>) keep.get("s2");
                StarSystemAPI sys = Global.getSector().getStarSystem((String) keep.get("s2system"));
                for (Map.Entry<String, float[]> e : m.entrySet()) {
                    if (e.getValue()[3] >= 0f) continue;
                    SectorEntityToken h = sys.getEntityById(e.getKey());
                    if (h != null && beam(h) != null) e.getValue()[3] = beamAngle(h);
                }
            };
            c.reload();
        });
        steps.add(c -> {
            @SuppressWarnings("unchecked") Map<String, float[]> m = (Map<String, float[]>) keep.get("s2");
            StarSystemAPI s = sys(c, "s2");
            int same = 0;
            for (Map.Entry<String, float[]> e : m.entrySet()) {
                SectorEntityToken h = s.getEntityById(e.getKey());
                float[] v = e.getValue();
                if (h == null || beam(h) == null) {
                    c.expect(false, "S2 lighthouse " + e.getKey() + " and its beam survive the reload");
                    continue;
                }
                boolean ok = beam(h).getBeamCount() == (int) v[0] && (house(h).isLit() ? 1f : 0f) == v[1] && Math.abs(Misc.getAngleDiff(v[2], v[3])) < 1f;
                if (!ok) c.note("S2 " + e.getKey() + " beams " + v[0] + "/" + beam(h).getBeamCount() + " lit " + v[1] + "/" + house(h).isLit() + " angle " + v[2] + "/" + v[3]);
                if (ok) same++;
            }
            c.expect(same == m.size(), "S2 every lighthouse keeps beam count, lit state and beam angle over the reload (" + same + " of " + m.size() + ")");
            afterLoad = null;
            fastTime();
        });
    }

    // L1 and L2 for beams, the beam is held on the target each frame
    private static void lighthouseSight(List<Step> steps) {
        steps.add(c -> make(c, "ls", VisionType.LIGHTHOUSE, RAILGUN));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "ls");
            SectorEntityToken h = houses(s).get(0);
            relocate(h, s, 2400f, 0f);
            c.until(() -> house(h).isLit() && beam(h) != null, 0.5f, "L1 lighthouse lit");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "ls");
            SectorEntityToken h = houses(s).get(0);
            PlanetAPI p1 = planet(s, 1);
            Supplier<Vector2f> behind = () -> at(p1, Misc.getAngleInDegrees(h.getLocation(), p1.getLocation()), p1.getRadius() + 500f);
            pinPlayer(behind);
            beamAims.put(h, () -> Misc.getAngleInDegrees(h.getLocation(), player().getLocation()));
            resetEye();
            realTime();
            keep.put("l1seen", Boolean.FALSE);
            keep.put("l1control", Boolean.FALSE);
            hold(c, 0.2f, "L1 two seconds behind the planet", () -> {
                if (settled() && house(h).beamSees(player())) keep.put("l1seen", Boolean.TRUE);
                float front = Misc.getDistance(h.getLocation(), p1.getLocation()) - p1.getRadius() - 200f;
                if (beam(h).sees(at(h, Misc.getAngleInDegrees(h.getLocation(), p1.getLocation()), front), 0f, 0f)) keep.put("l1control", Boolean.TRUE);
            });
        });
        steps.add(c -> {
            c.expect(!(Boolean) keep.get("l1seen"), "L1 planet between lighthouse and player: not seen by its beam (eye " + eyeElapsed() + ")");
            c.expect((Boolean) keep.get("l1control"), "L1 control point in front of the planet is in the beam");
            zoom(Math.min(2.5f, Global.getSector().getCampaignUI().getMaxZoomFactor()));
            c.frames(20);
        });
        steps.add(c -> c.shot("l1-beam"));
        // L2 beam
        steps.add(c -> {
            zoomReset();
            StarSystemAPI s = sys(c, "ls");
            SectorEntityToken h = houses(s).get(0);
            PlanetAPI p1 = planet(s, 1);
            float side = Misc.getAngleInDegrees(h.getLocation(), p1.getLocation()) + 70f;
            pinPlayer(() -> at(h, side, 3000f));
            beamAims.put(h, () -> Misc.getAngleInDegrees(h.getLocation(), player().getLocation()));
            hold(c, 0.05f, "L2 beam settles");
        });
        for (String mode : new String[]{"control", "on", "between"}) {
            steps.add(c -> {
                StarSystemAPI s = sys(c, "ls");
                SectorEntityToken h = houses(s).get(0);
                PlanetAPI p1 = planet(s, 1);
                float side = Misc.getAngleInDegrees(h.getLocation(), p1.getLocation()) + 70f;
                Object old = keep.remove("cloud");
                if (old != null) removeCloud((SectorEntityToken) old);
                if (mode.equals("on")) keep.put("cloud", cloud(s, at(h, side, 3000f), SmokeCloudEntityPlugin.BASE_RADIUS));
                if (mode.equals("between")) keep.put("cloud", cloud(s, at(h, side, 1500f), SmokeCloudEntityPlugin.BASE_RADIUS));
                keep.put("l2seen", Boolean.FALSE);
                realTime();
                hold(c, 0.15f, "L2 beam " + mode + " window", () -> {
                    if (settled() && house(h).beamSees(player())) keep.put("l2seen", Boolean.TRUE);
                });
            });
            steps.add(c -> {
                boolean seen = (Boolean) keep.get("l2seen");
                if (mode.equals("control")) c.expect(seen, "L2 beam sees the player without chaff");
                else c.expect(!seen, "L2 chaff cloud " + mode + " the player: not seen by the beam");
            });
        }
        steps.add(c -> {
            Object old = keep.remove("cloud");
            if (old != null) removeCloud((SectorEntityToken) old);
            unpinAll();
            fastTime();
        });
    }

    // E3, E4 on drone nets, N1 to N4, T1 cone length, R1 cones, R2 map
    private static void drones(List<Step> steps) {
        steps.add(c -> make(c, "d", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            realTime();
            DroneNetScript net = net(sys(c, "d"));
            SectorEntityToken nest = net.getNests().get(0);
            pinPlayer(() -> new Vector2f(nest.getLocation().x + 1500f, nest.getLocation().y));
            zoom(Global.getSector().getCampaignUI().getMaxZoomFactor());
            timer = new FrameTimer();
            hold(c, 1f, "PERF drone system window", timer::tick);
        });
        steps.add(c -> {
            c.note("PERF drone system, " + live(net(sys(c, "d"))).size() + " spotters and cones on screen at max zoom out, 10 s: " + timer);
            zoomReset();
            pinPlayer(() -> HIDE);
            resetEye();
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d");
            DroneNetScript net = net(s);
            c.expect(net != null, "E3 drone net registered");
            int nests = net.getNests().size();
            c.expect(nests >= 3 && nests <= 6, "N5 test system has 3 to 6 nests (" + nests + ")");
            c.expect(live(net).size() == nests * DroneNetScript.SPOTTERS_PER_NEST, "E3 two spotters per nest (" + live(net).size() + ")");
            park(net, List.of());
            SectorEntityToken nest = net.getNests().get(0);
            pinPlayer(() -> at(nest, net.getNestLightAngle(nest, 0), 50f));
            resetEye();
            realTime();
            probe = new Probe(s, projectile(MISSILE));
            c.until(() -> probe.lockAt >= 0f && (probe.firstProj >= 0f || probe.secs() > probe.lockAt + 4f), 2.5f, "E3 lock 50 su from a nest", probe::sample);
        });
        steps.add(c -> {
            float rate = (probe.e4 - probe.e1) / 3f;
            c.expect(rate >= 1f && rate <= 2f, "E3 nest light fills 1 to 2 per second (got " + rate + ")");
            c.expect(probe.maxJump < 1.5f, "E3 nest light never instant (max jump " + probe.maxJump + ")");
            float delay = probe.firstProj - probe.lockAt;
            c.expect(probe.firstProj >= 0f && delay <= Setting.ARTILLERY_MAX_DELAY_BETWEEN_SHOTS.value() + 0.5f, "E4 missile carrier spawns within the station delay after the lock (got " + delay + " s)");
            pinPlayer(() -> HIDE);
            c.until(() -> eyeElapsed() <= 0f, 3f, "E3 eye drains after the nest");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d");
            DroneNetScript net = net(s);
            DroneNetScript.Spotter sp = live(net).get(0);
            Vector2f q = new Vector2f(18000f, -18000f);
            float len = net.getConeLength();
            pinSpotter(sp, () -> q, () -> 0f);
            pinPlayer(() -> new Vector2f(q.x + len / 2f, q.y));
            keep.put("e3len", len);
            resetEye();
            probe = new Probe(s, null);
            c.until(() -> probe.secs() >= 4.2f, 1f, "E3 cone sample", probe::sample);
        });
        steps.add(c -> {
            float rate = (probe.e4 - probe.e1) / 3f;
            c.expect(rate >= 1f && rate <= 2f, "E3 spotter cone at half length fills 1 to 2 per second (got " + rate + ", cone " + keep.get("e3len") + ")");
            c.expect(probe.maxJump < 1.5f, "E3 spotter cone never instant (max jump " + probe.maxJump + ")");
            pinPlayer(() -> HIDE);
            resetEye();
            // N2
            DroneNetScript net = net(sys(c, "d"));
            keep.put("n2base", net.getConeLength());
            ability(Abilities.GO_DARK).activate();
            hold(c, 0.2f, "N2 go dark window");
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "d"));
            keep.put("n2dark", net.getConeLength());
            ability(Abilities.GO_DARK).deactivate();
            hold(c, 0.4f, "N2 go dark off window");
        });
        steps.add(c -> {
            ability(Abilities.SUSTAINED_BURN).activate();
            hold(c, 0.4f, "N2 burn window");
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "d"));
            float base = (Float) keep.get("n2base"), dark = (Float) keep.get("n2dark"), burn = net.getConeLength();
            ability(Abilities.SUSTAINED_BURN).deactivate();
            c.expect(dark < base, "N2 go dark shortens cones (" + base + " -> " + dark + ")");
            c.expect(burn > base, "N2 sustained burn lengthens cones (" + base + " -> " + burn + ")");
            Colony.setting(Setting.ARTILLERY_DRONE_CONE_LENGTH, 3000.0);
            keep.put("t1base", base);
            hold(c, 0.1f, "T1 cone length window");
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "d"));
            float base = (Float) keep.get("t1base");
            float now = net.getConeLength();
            Colony.setting(Setting.ARTILLERY_DRONE_CONE_LENGTH, 2000.0);
            c.near(now / base, 1.5f, 0.02f, "T1 drone cone length applies live (" + base + " -> " + now + ")");
            // N3
            unpinAll();
            pinPlayer(() -> HIDE);
            hold(c, 0.3f, "N3 settle");
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "d"));
            DroneNetScript.Spotter sp = live(net).get(1);
            Vector2f q = new Vector2f(sp.entity.getLocation());
            float aim = sp.coneAngle;
            pinSpotter(sp, () -> q, () -> aim);
            Vector2f spot = at(q, aim, 1000f);
            pinPlayer(() -> spot);
            keep.put("n3", sp);
            c.until(() -> sp.seesPlayer, 0.2f, "N3 spotter sees the player");
        });
        steps.add(c -> {
            DroneNetScript.Spotter sp = (DroneNetScript.Spotter) keep.get("n3");
            spotterPins.remove(sp);
            aims.remove(sp);
            hold(c, 0.8f, "N3 follow window");
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "d"));
            DroneNetScript.Spotter sp = (DroneNetScript.Spotter) keep.get("n3");
            float want = net.getConeLength() * DroneNetScript.FOLLOW_FRACTION;
            float dist = Misc.getDistance(sp.entity, player());
            float off = Math.abs(Misc.getAngleDiff(sp.coneAngle, Misc.getAngleInDegrees(sp.entity.getLocation(), player().getLocation())));
            c.expect(sp.following && Math.abs(dist - want) < want * 0.15f, "N3 spotter follows at 45% of cone length (" + dist + ", want " + want + ")");
            c.expect(off < 10f, "N3 spotter keeps its cone on the player (" + off + " deg off)");
            pinPlayer(() -> HIDE);
            probe = new Probe(sys(c, "d"), null);
            keep.put("n3lost", -1f);
            c.until(() -> !sp.following, 1f, "N3 spotter gives up after losing sight", () -> {
                if (!sp.following && (Float) keep.get("n3lost") < 0f) keep.put("n3lost", probe.secs());
            });
        });
        steps.add(c -> {
            float lost = (Float) keep.get("n3lost");
            c.expect(lost >= DroneNetScript.LOSE_SECONDS - DroneNetScript.CHECK_SECONDS && lost <= DroneNetScript.LOSE_SECONDS + 0.6f, "N3 roams again 3 s after losing sight, one 0.25 s check either way (got " + lost + ")");
            // N4
            StarSystemAPI s = sys(c, "d");
            DroneNetScript net = net(s);
            DroneNetScript.Spotter sp = live(net).get(2);
            park(net, List.of(sp));
            Vector2f q = new Vector2f(-18000f, 18000f);
            pinSpotter(sp, () -> q, () -> 90f);
            Vector2f inCone = new Vector2f(q.x, q.y + 800f);
            CampaignFleetAPI a = npc(s, inCone);
            pin(a, () -> inCone);
            SectorEntityToken nest = net.getNests().get(1);
            CampaignFleetAPI b = npc(s, nest.getLocation());
            pin(b, () -> at(nest, net.getNestLightAngle(nest, 0), 300f));
            keep.put("n4a", a);
            keep.put("n4b", b);
            c.until(() -> a.getMemoryWithoutUpdate().getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY) && b.getMemoryWithoutUpdate().getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY), 0.2f, "N4 NPC fleets in a cone and a nest light are seen");
        });
        steps.add(c -> {
            for (String k : new String[]{"n4a", "n4b"}) {
                CampaignFleetAPI f = (CampaignFleetAPI) keep.get(k);
                float expire = f.getMemoryWithoutUpdate().getExpire(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY);
                c.expect(expire > 4.8f && expire <= 5.01f, "N4 " + (k.equals("n4a") ? "cone" : "nest light") + " sighting lasts 5 days (expire " + expire + ")");
                f.getContainingLocation().removeEntity(f);
            }
            unpinAll();
            pinPlayer(() -> HIDE);
            // N1
            StarSystemAPI s = sys(c, "d");
            DroneNetScript net = net(s);
            SectorEntityToken nest = net.getNests().get(0);
            Vector2f dl = new Vector2f(nest.getLocation().x + 1500f, nest.getLocation().y);
            SectorEntityToken decoy = s.addCustomEntity(Misc.genUID(), "Decoy", DroneNetScript.DECOY_ENTITY_ID, null, 15f, 0f, 0f);
            decoy.setLocation(dl.x, dl.y);
            keep.put("decoy", decoy);
            List<DroneNetScript.Spotter> all = live(net);
            for (int i = 0; i < all.size(); i++) {
                Vector2f p = i == 0 ? at(dl, 0f, 6000f) : at(dl, i * 360f / all.size(), 1200f + 250f * (i % 5));
                all.get(i).entity.setLocation(p.x, p.y);
                all.get(i).waypoint = null;
            }
            Vector2f farAt = at(dl, 0f, 6000f);
            pinSpotter(all.get(0), () -> farAt, () -> 0f);
            keep.put("n1far", all.get(0));
            hold(c, 1.4f, "N1 decoy window");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d");
            DroneNetScript net = net(s);
            SectorEntityToken decoy = (SectorEntityToken) keep.get("decoy");
            int pulled = 0, held = 0, far = 0, farPulled = 0;
            for (DroneNetScript.Spotter sp : live(net)) {
                if (sp == keep.get("n1far")) continue;
                float dist = Misc.getDistance(sp.entity, decoy);
                if (sp.decoy == decoy) {
                    pulled++;
                    float off = Math.abs(Misc.getAngleDiff(sp.coneAngle, Misc.getAngleInDegrees(sp.entity.getLocation(), decoy.getLocation())));
                    if (dist < DroneNetScript.DECOY_STANDOFF + 100f && off < 10f) held++;
                    else c.note("N1 pulled spotter at " + dist + " su, cone " + off + " deg off");
                } else if (dist > 3000f) far++;
                if (sp.decoy == decoy && dist > 3000f) farPulled++;
            }
            c.note("N1 pulled " + pulled + ", held " + held + ", far " + far);
            c.expect(pulled > 0 && held == pulled, "N1 decoy within 3000 su pulls spotters, cones held on it (" + held + " of " + pulled + ")");
            DroneNetScript.Spotter farOne = (DroneNetScript.Spotter) keep.get("n1far");
            c.expect(farPulled == 0 && (farOne.entity == null || farOne.decoy != decoy), "N1 spotters beyond 3000 su are not pulled");
            s.removeEntity(decoy);
            // R1 cones
            pinPlayer(() -> new Vector2f(nest(net).getLocation().x + 800f, nest(net).getLocation().y));
            zoom(Global.getSector().getCampaignUI().getMaxZoomFactor());
            c.frames(40);
        });
        steps.add(c -> c.shot("r1-cones"));
        steps.add(c -> {
            zoomReset();
            map(c, "r2-drones");
        });
        steps.add(c -> c.shot((String) keep.get("map")));
        steps.add(c -> c.key("Escape"));
        steps.add(c -> {
            c.expect(Global.getSector().getCampaignUI().getCurrentCoreTab() == null, "R2 drone map closed");
            unpinAll();
            resetEye();
            fastTime();
        });
    }

    static SectorEntityToken nest(DroneNetScript net) {
        return net.getNests().get(0);
    }

    // L1 and L2 for cones and nest lights
    private static void droneSight(List<Step> steps) {
        steps.add(c -> make(c, "ds", VisionType.DRONES, MISSILE));
        // L1 cone
        for (String mode : new String[]{"control", "behind"}) {
            steps.add(c -> {
                StarSystemAPI s = sys(c, "ds");
                DroneNetScript net = net(s);
                DroneNetScript.Spotter sp = live(net).get(0);
                park(net, List.of(sp));
                PlanetAPI p1 = planet(s, 1);
                float side = Misc.getAngleInDegrees(s.getCenter().getLocation(), p1.getLocation()) + 90f;
                float gap = Math.max(100f, Math.min(700f, 0.4f * net.getConeLength() - p1.getRadius()));
                c.note("L1 cone " + net.getConeLength() + ", spotter and player " + (p1.getRadius() + gap) + " su off the planet centre");
                Supplier<Vector2f> from = () -> at(p1, side, p1.getRadius() + gap);
                Supplier<Vector2f> to = mode.equals("behind") ? () -> at(p1, side + 180f, p1.getRadius() + gap) : () -> at(from.get(), side + 90f, 2f * (p1.getRadius() + gap));
                pinSpotter(sp, from, () -> Misc.getAngleInDegrees(from.get(), to.get()));
                pinPlayer(to);
                keep.put("seen", Boolean.FALSE);
                realTime();
                hold(c, 0.15f, "L1 cone " + mode + " window", () -> {
                    if (settled() && sp.seesPlayer) keep.put("seen", Boolean.TRUE);
                });
            });
            steps.add(c -> {
                boolean seen = (Boolean) keep.get("seen");
                if (mode.equals("control")) c.expect(seen, "L1 control: spotter sees the player at the same distance without a planet between");
                else {
                    c.expect(!seen, "L1 planet between spotter and player: not seen");
                    zoom(Math.min(2f, Global.getSector().getCampaignUI().getMaxZoomFactor()));
                    c.frames(20);
                }
            });
        }
        steps.add(c -> c.shot("l1-cone"));
        // L2 cone and nest light
        for (String mode : new String[]{"cone-control", "cone-on", "cone-between", "nest-control", "nest-on", "nest-between"}) {
            steps.add(c -> {
                zoomReset();
                StarSystemAPI s = sys(c, "ds");
                DroneNetScript net = net(s);
                Object old = keep.remove("cloud");
                if (old != null) removeCloud((SectorEntityToken) old);
                DroneNetScript.Spotter sp = live(net).get(0);
                if (mode.startsWith("cone")) {
                    park(net, List.of(sp));
                    Vector2f q = new Vector2f(16000f, 16000f);
                    pinSpotter(sp, () -> q, () -> 0f);
                    float d = 0.75f * net.getConeLength();
                    Vector2f p = new Vector2f(q.x + d, q.y);
                    pinPlayer(() -> p);
                    float r = Math.min(SmokeCloudEntityPlugin.BASE_RADIUS, d / 3f);
                    if (mode.endsWith("on")) keep.put("cloud", cloud(s, p, r));
                    if (mode.endsWith("between")) keep.put("cloud", cloud(s, new Vector2f(q.x + d / 2f, q.y), r));
                } else {
                    park(net, List.of());
                    SectorEntityToken nest = net.getNests().get(0);
                    float base = DroneNetScript.getNestLightBase(nest, 0);
                    float d = mode.endsWith("between") ? 530f : 500f;
                    pinPlayer(() -> at(nest, base, d));
                    if (mode.endsWith("on")) keep.put("cloud", cloud(s, at(nest, base, d), 100f));
                    if (mode.endsWith("between")) keep.put("cloud", cloud(s, at(nest, base, 280f), 100f));
                }
                keep.put("seen", Boolean.FALSE);
                realTime();
                // a fixed point on the swing centre is crossed at least once per half swing
                hold(c, mode.startsWith("nest") ? 0.65f : 0.15f, "L2 " + mode + " window", () -> {
                    boolean seen = mode.startsWith("cone") ? sp.seesPlayer : net.getPlayerFraction() > 0f;
                    if (settled() && seen) keep.put("seen", Boolean.TRUE);
                });
            });
            steps.add(c -> {
                boolean seen = (Boolean) keep.get("seen");
                String what = mode.startsWith("cone") ? "cone" : "nest light";
                if (mode.endsWith("control")) c.expect(seen, "L2 " + what + " sees the player without chaff");
                else c.expect(!seen, "L2 chaff cloud " + (mode.endsWith("on") ? "on" : "between") + " the player: not seen by the " + what);
            });
        }
        steps.add(c -> {
            Object old = keep.remove("cloud");
            if (old != null) removeCloud((SectorEntityToken) old);
            unpinAll();
            fastTime();
        });
    }

    // D1, D2, D3
    private static void droneBlasts(List<Step> steps) {
        steps.add(c -> make(c, "db", VisionType.DRONES, MISSILE));
        for (String how : new String[]{"mine", "pulse", "missile", "mortar"}) {
            steps.add(c -> {
                StarSystemAPI s = sys(c, "db");
                DroneNetScript net = net(s);
                DroneNetScript.Spotter sp = live(net).get(0);
                park(net, List.of(sp));
                Vector2f q = new Vector2f(15000f + (how.hashCode() % 7) * 900f, 15000f);
                pinSpotter(sp, () -> q, () -> 0f);
                keep.put("d1", sp);
                realTime();
                switch (how) {
                    case "mine" -> {
                        SectorEntityToken spike = s.addCustomEntity(Misc.genUID(), "Drive Bubble Spike", "IndEvo_field_spike", null, 15f, 0f, 0f);
                        spike.setLocation(q.x + 200f, q.y);
                        spike.addAbility(Ids.ABILITY_MINE_INTERDICT);
                        spike.getAbility(Ids.ABILITY_MINE_INTERDICT).activate();
                        pinPlayer(() -> HIDE);
                    }
                    case "pulse" -> {
                        if (ability(Abilities.INTERDICTION_PULSE) == null) {
                            Global.getSector().getCharacterData().addAbility(Abilities.INTERDICTION_PULSE);
                            player().addAbility(Abilities.INTERDICTION_PULSE);
                        }
                        Vector2f p = new Vector2f(q.x - 200f, q.y);
                        pinPlayer(() -> p);
                        keep.put("pulse", Boolean.FALSE);
                    }
                    case "missile" -> {
                        Vector2f p = new Vector2f(q.x - 600f, q.y);
                        pinPlayer(() -> p);
                        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(player(), 0f, 3f, 800f, null);
                        SectorEntityToken m = s.addCustomEntity(Misc.genUID(), null, "IndEvo_consumable_missile_explosive", Factions.PLAYER, params);
                        m.setLocation(p.x + 60f, p.y);
                    }
                    case "mortar" -> {
                        pinPlayer(() -> HIDE);
                        MortarProjectileEntityPlugin.spawn(s, station(s), new Vector2f(q), 3f);
                    }
                }
                c.until(() -> sp.entity == null, 2f, "D1 " + how + " blows a spotter", () -> {
                    if (how.equals("pulse") && !(Boolean) keep.get("pulse") && ability(Abilities.INTERDICTION_PULSE).isUsable()) {
                        ability(Abilities.INTERDICTION_PULSE).activate();
                        keep.put("pulse", Boolean.TRUE);
                    }
                });
            });
            steps.add(c -> {
                if (how.equals("pulse")) c.note("D1 pulse activated " + keep.get("pulse"));
                unpinAll();
                pinPlayer(() -> HIDE);
            });
        }
        steps.add(c -> make(c, "d2", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d2");
            DroneNetScript net = net(s);
            List<DroneNetScript.Spotter> four = live(net).subList(0, 4);
            park(net, four);
            Vector2f q = new Vector2f(-15000f, 15000f);
            float[] xs = {0f, 300f, 600f, 1200f};
            for (int i = 0; i < 4; i++) {
                Vector2f p = new Vector2f(q.x + xs[i], q.y);
                pinSpotter(four.get(i), () -> p, () -> 90f);
            }
            keep.put("d2", new ArrayList<>(four));
            c.frames(5);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d2");
            @SuppressWarnings("unchecked") List<DroneNetScript.Spotter> four = (List<DroneNetScript.Spotter>) keep.get("d2");
            float[] gone = {-1f, -1f, -1f, -1f};
            keep.put("d2gone", gone);
            DroneNetScript.reportBlast(s, new Vector2f(four.get(0).entity.getLocation()), 20f);
            realTime();
            probe = new Probe(s, null);
            c.until(() -> probe.secs() >= 2f, 0.4f, "D2 chain window", () -> {
                for (int i = 0; i < 4; i++) if (gone[i] < 0f && four.get(i).entity == null) gone[i] = probe.secs();
            });
        });
        steps.add(c -> {
            float[] gone = (float[]) keep.get("d2gone");
            c.note("D2 spotters gone at " + gone[0] + ", " + gone[1] + ", " + gone[2] + ", survivor " + gone[3]);
            c.expect(gone[0] >= 0f && gone[1] >= 0f && gone[2] >= 0f && gone[2] <= 0.5f, "D2 three spotters 300 su apart all gone within 0.5 s");
            c.expect(gone[3] < 0f, "D2 spotter 600 su from the last one survives");
            unpinAll();
            pinPlayer(() -> HIDE);
            // D3
            StarSystemAPI s = sys(c, "d2");
            DroneNetScript net = net(s);
            SectorEntityToken nest = net.getNests().get(0);
            keep.put("d3nest", nest);
            keep.put("d3loc", new Vector2f(nest.getLocation()));
            nest.getMemoryWithoutUpdate().set(MissileMemFlags.MEM_CAUGHT_BY_MISSILE, new Vector2f(nest.getLocation()), 1f);
            c.until(() -> !net.getNests().contains(nest), 0.1f, "D3 bonked nest destroyed", () -> keep.put("d3loc", new Vector2f(nest.getLocation())));
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "d2");
            Vector2f at = (Vector2f) keep.get("d3loc");
            SectorEntityToken debris = null;
            for (CampaignTerrainAPI t : s.getTerrainCopy()) {
                if (t.getPlugin() instanceof DebrisFieldTerrainPlugin && Misc.getDistance(t.getLocation(), at) <= DroneNetScript.DEBRIS_RADIUS) debris = t;
            }
            c.expect(debris != null, "D3 debris field within 150 su of the nest");
            if (debris != null) {
                int min = Integer.MAX_VALUE, max = 0;
                for (int i = 0; i < 40; i++) {
                    CargoAPI loot = SalvageEntity.generateSalvage(new java.util.Random(), 1f, 1f, 1f, debris.getDropValue(), debris.getDropRandom());
                    int n = Math.round(loot.getCommodityQuantity(RELICS));
                    min = Math.min(min, n);
                    max = Math.max(max, n);
                }
                c.note("D3 debris drop " + debris.getDropRandom().get(0).group + " chances " + debris.getDropRandom().get(0).chances + " maxChances " + debris.getDropRandom().get(0).maxChances + " value " + debris.getDropRandom().get(0).value);
                // generateSalvage scales chances by easySalvageMult on easy
                float easy = Misc.isEasy() ? Global.getSettings().getFloat("easySalvageMult") : 1f;
                int lo = (int) Math.floor(DroneNetScript.MIN_NEST_RELICS * easy), hi = (int) Math.ceil(DroneNetScript.MAX_NEST_RELICS * easy);
                c.expect(min >= lo && max <= hi, "D3 debris field holds " + lo + " to " + hi + " " + RELICS + " (6 to 10 x" + easy + ", 40 rolls: " + min + " to " + max + ")");
                int dmin = Integer.MAX_VALUE, dmax = 0;
                for (int i = 0; i < 40; i++) {
                    int n = Math.round(SalvageEntity.generateSalvage(new java.util.Random(), 1f, 1f, 1f, null, List.of(DroneNetScript.createNestDrop())).getCommodityQuantity(RELICS));
                    dmin = Math.min(dmin, n);
                    dmax = Math.max(dmax, n);
                }
                c.note("D3 for comparison, DroneNetScript.createNestDrop (the nest dialog's salvage) over 40 rolls: " + dmin + " to " + dmax);
            }
            unpinAll();
            fastTime();
        });
    }

    // D5
    private static void droneLife(List<Step> steps) {
        steps.add(c -> make(c, "dl", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = net(s);
            SectorEntityToken nest = net.getNests().get(0);
            DroneNetScript.Spotter a = null;
            for (DroneNetScript.Spotter sp : live(net)) if (sp.nest == nest) a = sp;
            park(net, List.of());
            keep.put("d5nest", nest);
            DroneNetScript.reportBlast(s, new Vector2f(a.entity.getLocation()), 10f);
            DroneNetScript.Spotter dead = a;
            c.until(() -> dead.entity == null, 0.2f, "D5 one spotter blown");
        });
        steps.add(c -> {
            unpinAll();
            pinPlayer(() -> HIDE);
            c.days(3.3f);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = net(s);
            SectorEntityToken nest = (SectorEntityToken) keep.get("d5nest");
            int n = 0;
            for (DroneNetScript.Spotter sp : live(net)) if (sp.nest == nest) n++;
            c.expect(n == DroneNetScript.SPOTTERS_PER_NEST, "D5 3 days after a spotter is lost its nest has 2 again (" + n + ")");
            SectorEntityToken other = net.getNests().get(1);
            keep.put("d5other", other);
            other.getMemoryWithoutUpdate().set(MissileMemFlags.MEM_CAUGHT_BY_MISSILE, new Vector2f(other.getLocation()), 1f);
            c.until(() -> !net.getNests().contains(other), 0.1f, "D5 second nest destroyed");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = net(s);
            SectorEntityToken other = (SectorEntityToken) keep.get("d5other");
            List<DroneNetScript.Spotter> orphans = new ArrayList<>();
            for (DroneNetScript.Spotter sp : live(net)) if (sp.nest == other) orphans.add(sp);
            c.expect(orphans.size() == 2, "D5 spotters of a destroyed nest fly on (" + orphans.size() + ")");
            if (!orphans.isEmpty()) {
                park(net, List.of());
                DroneNetScript.reportBlast(s, new Vector2f(orphans.get(0).entity.getLocation()), 10f);
            }
            hold(c, 0.05f, "D5 orphan blast");
        });
        steps.add(c -> {
            unpinAll();
            pinPlayer(() -> HIDE);
            c.days(3.3f);
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = net(s);
            SectorEntityToken other = (SectorEntityToken) keep.get("d5other");
            int n = 0, slots = 0;
            for (DroneNetScript.Spotter sp : net.getSpotters()) if (sp.nest == other) {
                slots++;
                if (sp.entity != null) n++;
            }
            c.expect(n == 1 && slots == 1, "D5 a destroyed nest's lost spotter is not replaced (" + n + " live, " + slots + " slots)");
            for (SectorEntityToken nest : net.getNests()) nest.getMemoryWithoutUpdate().set(MissileMemFlags.MEM_CAUGHT_BY_MISSILE, new Vector2f(nest.getLocation()), 1f);
            hold(c, 0.05f, "D5 nests bonked");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = net(s);
            keep.put("d5net", net);
            for (DroneNetScript.Spotter sp : live(net)) DroneNetScript.reportBlast(s, new Vector2f(sp.entity.getLocation()), 10f);
            realTime();
            c.until(() -> net.isDone() && DroneNetScript.get(s) == null, 0.3f, "D5 destroying every nest and spotter ends the script");
        });
        steps.add(c -> hold(c, 0.2f, "D5 explosions fade"));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dl");
            DroneNetScript net = (DroneNetScript) keep.get("d5net");
            boolean listed = false;
            for (EveryFrameScript script : s.getScripts()) if (script == net) listed = true;
            c.expect(!listed, "D5 drone net script removed from the system");
            c.expect(count(s, DroneNetScript.NEST_ENTITY_ID) == 0 && count(s, DroneNetScript.SPOTTER_ENTITY_ID) == 0, "D5 no nest or spotter entity left");
            fastTime();
        });
    }

    // S1
    private static void droneReload(List<Step> steps) {
        steps.add(c -> make(c, "s1", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "s1");
            DroneNetScript net = net(s);
            List<String> nests = new ArrayList<>();
            for (SectorEntityToken n : net.getNests()) nests.add(n.getId());
            Map<String, float[]> spotters = new HashMap<>();
            for (DroneNetScript.Spotter sp : live(net)) spotters.put(sp.entity.getId(), new float[]{sp.entity.getLocation().x, sp.entity.getLocation().y, Float.NaN, Float.NaN});
            keep.put("s1nests", nests);
            keep.put("s1spotters", spotters);
            keep.put("s1system", (String) c.get("sys_s1"));
            afterLoad = () -> {
                @SuppressWarnings("unchecked") Map<String, float[]> m = (Map<String, float[]>) keep.get("s1spotters");
                StarSystemAPI sys = Global.getSector().getStarSystem((String) keep.get("s1system"));
                for (Map.Entry<String, float[]> e : m.entrySet()) {
                    if (!Float.isNaN(e.getValue()[2])) continue;
                    SectorEntityToken t = sys.getEntityById(e.getKey());
                    if (t != null) {
                        e.getValue()[2] = t.getLocation().x;
                        e.getValue()[3] = t.getLocation().y;
                    }
                }
            };
            c.reload();
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "s1");
            DroneNetScript net = net(s);
            @SuppressWarnings("unchecked") List<String> nests = (List<String>) keep.get("s1nests");
            @SuppressWarnings("unchecked") Map<String, float[]> spotters = (Map<String, float[]>) keep.get("s1spotters");
            c.expect(net != null, "S1 drone net back after the reload");
            List<String> now = new ArrayList<>();
            for (SectorEntityToken n : net.getNests()) now.add(n.getId());
            c.expect(now.equals(nests), "S1 same nest ids and count after the reload (" + now.size() + " of " + nests.size() + ")");
            int back = 0;
            Map<String, Vector2f> after = new HashMap<>();
            for (DroneNetScript.Spotter sp : live(net)) {
                float[] v = spotters.get(sp.entity.getId());
                if (v != null && !Float.isNaN(v[2]) && Misc.getDistance(new Vector2f(v[0], v[1]), new Vector2f(v[2], v[3])) < 5f) back++;
                after.put(sp.entity.getId(), new Vector2f(sp.entity.getLocation()));
            }
            c.expect(live(net).size() == spotters.size() && back == spotters.size(), "S1 spotters back at their positions after the reload (" + back + " of " + spotters.size() + ", live " + live(net).size() + ")");
            keep.put("s1after", after);
            afterLoad = null;
            c.days(1f);
        });
        steps.add(c -> {
            DroneNetScript net = net(sys(c, "s1"));
            @SuppressWarnings("unchecked") Map<String, Vector2f> after = (Map<String, Vector2f>) keep.get("s1after");
            int moved = 0, origin = 0;
            for (DroneNetScript.Spotter sp : live(net)) {
                Vector2f was = after.get(sp.entity.getId());
                if (was != null && Misc.getDistance(was, sp.entity.getLocation()) > 10f) moved++;
                if (sp.entity.getLocation().length() < 1f) origin++;
            }
            c.expect(moved == after.size(), "S1 a day after the reload every spotter moved (" + moved + " of " + after.size() + ")");
            c.expect(origin == 0, "S1 no spotter at 0,0 (" + origin + ")");
        });
    }

    // D6
    private static void droneColony(List<Step> steps) {
        steps.add(c -> make(c, "dc", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dc");
            Colony.make(planet(s, 2), 3);
            realTime();
            c.until(() -> count(s, DroneNetScript.NEST_ENTITY_ID) == 0 && count(s, DroneNetScript.SPOTTER_ENTITY_ID) == 0, 0.5f, "D6 colony removes every nest and spotter");
        });
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dc");
            c.expect(DroneNetScript.get(s) == null, "D6 drone net script gone");
            c.expect(count(s, WatchtowerEntityPlugin.ENTITY_ID) > 0, "D6 watchtowers placed (" + count(s, WatchtowerEntityPlugin.ENTITY_ID) + ")");
            c.expect("WATCHTOWER".equals(s.getMemoryWithoutUpdate().getString(Ids.MEM_SYSTEM_ARTILLERY_VISION)), "D6 $IndEvo_artilleryVision is WATCHTOWER");
            fastTime();
        });
    }

    // D4 through the real rules rows, options picked as the dialog would
    private static void nestDialog(List<Step> steps) {
        steps.add(c -> make(c, "dn", VisionType.DRONES, MISSILE));
        for (String exit : new String[]{"salvage", "leave"}) {
            steps.add(c -> {
                StarSystemAPI s = sys(c, "dn");
                DroneNetScript net = net(s);
                SectorEntityToken nest = net.getNests().get(exit.equals("salvage") ? 0 : 1);
                nest.setDiscoverable(false);
                keep.put("d4nest", nest);
                keep.put("d4loc", new Vector2f(nest.getLocation()));
                pinPlayer(() -> new Vector2f(nest.getLocation().x + 120f, nest.getLocation().y));
                c.keepDialog(true);
                c.frames(10);
                c.note("D4 nest discoverable " + nest.isDiscoverable() + ", visible to the fleet " + nest.isVisibleToPlayerFleet() + ", sensor profile " + nest.getSensorProfile() + ", light " + sys(c, "dn").getLightColor());
            });
            steps.add(c -> c.shot("d4-" + exit + "-nest"));
            steps.add(c -> {
                SectorEntityToken nest = (SectorEntityToken) keep.get("d4nest");
                c.note("D4 before open: showing dialog " + Global.getSector().getCampaignUI().isShowingDialog() + ", core tab " + Global.getSector().getCampaignUI().getCurrentCoreTab() + ", nest visible " + nest.isVisibleToPlayerFleet() + ", alive " + nest.isAlive());
                // the one argument call picks the dialog plugin the way a click on the nest does
                boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(nest);
                c.expect(shown, "D4 nest dialog opens through the campaign plugin pick, as a click does");
                c.frames(60);
            });
            steps.add(c -> c.shot("d4-" + exit + "-open"));
            steps.add(c -> {
                InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
                c.expect(dialog != null && Ui.text(dialog.getTextPanel()).contains("clean firing solution"), "D4 nest dialog shows the IndEvo_droneNest_open text");
                if (dialog != null) dialog.getPlugin().optionSelected("Open fire on the nest", "IndEvo_droneNest_fire");
                c.frames(60);
            });
            steps.add(c -> c.shot("d4-" + exit + "-fire"));
            steps.add(c -> {
                StarSystemAPI s = sys(c, "dn");
                SectorEntityToken nest = (SectorEntityToken) keep.get("d4nest");
                c.expect(!net(s).getNests().contains(nest), "D4 open fire destroys the nest");
                InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
                if (dialog != null) {
                    if (exit.equals("salvage")) dialog.getPlugin().optionSelected("Salvage the wreckage", "IndEvo_droneNest_salvage");
                    else dialog.getPlugin().optionSelected("Leave", "IndEvo_droneNest_leave");
                }
                c.frames(60);
            });
            steps.add(c -> c.shot("d4-" + exit + "-after"));
            steps.add(c -> {
                StarSystemAPI s = sys(c, "dn");
                Vector2f at = (Vector2f) keep.get("d4loc");
                int debris = 0;
                for (CampaignTerrainAPI t : s.getTerrainCopy()) {
                    if (t.getPlugin() instanceof DebrisFieldTerrainPlugin && Misc.getDistance(t.getLocation(), at) <= 300f) debris++;
                }
                InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
                if (exit.equals("salvage")) {
                    c.expect(dialog != null, "D4 salvage keeps the dialog for the loot screen");
                    c.expect(debris == 0, "D4 salvage leaves no debris field (" + debris + ")");
                } else {
                    c.expect(debris == 1, "D4 leave leaves a debris field (" + debris + ")");
                }
                if (dialog != null) dialog.dismiss();
                c.keepDialog(false);
                c.frames(20);
            });
        }
        steps.add(c -> {
            c.expect(Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null, "D4 dialogs closed");
            unpinAll();
        });
    }

    // R1: after cones, beams and volleys, ordinary sprites still draw
    private static void planetFleet(List<Step> steps) {
        steps.add(c -> {
            StarSystemAPI s = sys(c, "dn");
            PlanetAPI p2 = planet(s, 2);
            Supplier<Vector2f> spot = () -> at(p2, 45f, p2.getRadius() + 250f);
            pinPlayer(spot);
            CampaignFleetAPI fleet = npc(s, spot.get());
            pin(fleet, () -> at(p2, 45f, p2.getRadius() + 450f));
            keep.put("r1fleet", fleet);
            zoomReset();
            c.frames(60);
        });
        steps.add(c -> c.shot("r1-planet-fleet"));
        steps.add(c -> {
            CampaignFleetAPI fleet = (CampaignFleetAPI) keep.get("r1fleet");
            fleet.getContainingLocation().removeEntity(fleet);
            unpinAll();
        });
    }

    // D4 and the R1 planet capture alone, for a game left open with --keep
    public static List<Step> nest() {
        List<Step> steps = new ArrayList<>();
        nestDialog(steps);
        planetFleet(steps);
        return steps;
    }

    // D4 by hand: a nest 250 su west of the fleet, game left open for gamectl clicks
    public static List<Step> holdNest() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> make(c, "hn", VisionType.DRONES, MISSILE));
        steps.add(c -> {
            StarSystemAPI s = sys(c, "hn");
            DroneNetScript net = net(s);
            park(net, List.of());
            pins.remove(player());
            SectorEntityToken nest = net.getNests().get(0);
            nest.setDiscoverable(false);
            Vector2f at = new Vector2f(nest.getLocation().x + 250f, nest.getLocation().y);
            player().setLocation(at.x, at.y);
            player().setMoveDestination(at.x, at.y);
            zoomReset();
            c.keepDialog(true);
            c.note("hold nest " + nest.getId() + " 250 su west of the fleet, zoom " + Global.getSector().getCampaignUI().getZoomFactor());
            Global.getLogger(ArtilleryChecks.class).info("LIVE_CHECK hold");
            c.frames(Integer.MAX_VALUE);
        });
        return steps;
    }

    // run b: vision placement across a new sector, H3 and N5
    public static List<Step> placement() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            c.expect(Setting.ARTILLERY_VISION_WEIGHT_WATCHTOWER.value() == 1f && Setting.ARTILLERY_VISION_WEIGHT_LIGHTHOUSE.value() == 1f && Setting.ARTILLERY_VISION_WEIGHT_DRONES.value() == 1f,
                    "placement ran with vision weights 1/1/1");
            Map<String, Integer> types = new LinkedHashMap<>();
            int one = 0, two = 0, systems = 0, badNets = 0, unlabeled = 0;
            StringBuilder nests = new StringBuilder();
            for (StarSystemAPI s : Global.getSector().getStarSystems()) {
                if (!s.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)) continue;
                systems++;
                String type = s.getMemoryWithoutUpdate().getString(Ids.MEM_SYSTEM_ARTILLERY_VISION);
                if (type == null) unlabeled++;
                types.merge(String.valueOf(type), 1, Integer::sum);
                for (SectorEntityToken h : houses(s)) {
                    int n = rolledBeams(h);
                    if (n == 1) one++;
                    if (n == 2) two++;
                }
                if ("DRONES".equals(type)) {
                    DroneNetScript net = net(s);
                    int n = net == null ? 0 : net.getNests().size();
                    int entities = count(s, DroneNetScript.NEST_ENTITY_ID);
                    nests.append(n).append(' ');
                    if (n < 3 || n > 6 || entities != n) badNets++;
                }
            }
            c.note("placement: " + systems + " artillery systems, vision " + types + ", unlabeled " + unlabeled);
            c.note("H3 lighthouses with 1 beam " + one + ", with 2 beams " + two);
            c.note("N5 nests per drone system: " + nests.toString().trim());
            c.expect(one > 0 && two > 0, "H3 lighthouses roll 1 and 2 beams (" + one + " / " + two + ")");
            c.expect(badNets == 0 && nests.length() > 0, "N5 every drone system has 3 to 6 nests");
            c.expect(types.size() >= 3, "every vision type placed with weights 1/1/1 (" + types + ")");
        });
        return steps;
    }
}
