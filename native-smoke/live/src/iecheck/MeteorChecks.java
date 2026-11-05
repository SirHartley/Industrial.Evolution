package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import indevo.config.Setting;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.intel.MeteorShowerLocationIntel;
import indevo.exploration.meteor.renderers.MeteorDebrisRenderer;
import indevo.exploration.meteor.renderers.RadiationEffectHandler;
import indevo.exploration.meteor.renderers.RadiationVFXHandler;
import indevo.exploration.meteor.scripts.PayloadOnFinishedModuleRunner;
import indevo.exploration.meteor.spawners.PlanetoidSwarmSpawner;
import indevo.utils.helper.ReflectionUtils;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MeteorChecks {
    private static MeteorShowerLocationIntel intel = null;
    private static long ended = 0L;

    private MeteorChecks() {
    }

    private static StarSystemAPI system(Ctx c) {
        return Global.getSector().getStarSystem((String) c.get("system"));
    }

    private static void clearTimeouts(StarSystemAPI system) {
        Global.getSector().getMemoryWithoutUpdate().unset(MeteorSwarmManager.MEM_TIMEOUT);
        system.getMemoryWithoutUpdate().unset(MeteorSwarmManager.MEM_TIMEOUT);
    }

    private static int rings(StarSystemAPI system) {
        int count = 0;
        for (SectorEntityToken entity : system.getAllEntities()) if (entity instanceof RingBandAPI) count++;
        return count;
    }

    private static PlanetAPI planetoid(StarSystemAPI system) {
        for (PlanetAPI planet : system.getPlanets()) if (planet.hasTag(PlanetoidSwarmSpawner.PLANETOID_TAG)) return planet;
        return null;
    }

    private static SectorEntityToken rock(StarSystemAPI system, String prefix) {
        for (CustomCampaignEntityAPI entity : system.getCustomEntities()) {
            String type = entity.getCustomEntityType();
            if (type != null && type.startsWith(prefix)) return entity;
        }
        return null;
    }

    private static MeteorShowerLocationIntel latestIntel() {
        MeteorShowerLocationIntel latest = null;
        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(MeteorShowerLocationIntel.class)) {
            latest = (MeteorShowerLocationIntel) plugin;
        }
        return latest;
    }

    private static float radiation() {
        return Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().getFloat(RadiationEffectHandler.MEM_EFFECT_LEVEL);
    }

    private static void followRocks(Ctx c, float level, String id) {
        StarSystemAPI system = system(c);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        c.until(() -> radiation() >= level, 25f, id, () -> {
            SectorEntityToken rock = rock(system, "IndEvo_spicy_rock_");
            if (rock != null) fleet.setLocation(rock.getLocation().x, rock.getLocation().y);
        });
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            StarSystemAPI system = Colony.planets(2).get(0).getStarSystem();
            Colony.ships();
            c.put("system", system.getId());
            c.put("rings", rings(system));
            Colony.jump(system, 12000f, 0f);
            Colony.setting(Setting.METEOR_IMMEDIATE_MODE, true);
            c.note("meteor system " + system.getName() + ", valid " + MeteorSwarmManager.isValidMeteorLoc(system));
            clearTimeouts(system);
            MeteorSwarmManager.getInstance().spawnShower(system, 3f, MeteorSwarmManager.MeteroidShowerType.PLANETOID, true);
            c.until(() -> planetoid(system) != null, 3f, "planetoid spawned");
        });
        steps.add(c -> {
            StarSystemAPI system = system(c);
            PlanetAPI planet = planetoid(system);
            c.put("planet", planet.getId());
            c.expect(rings(system) - (Integer) c.get("rings") == 5, "planetoid has 5 dust rings");

            List<String> stations = new ArrayList<>();
            for (SectorEntityToken entity : system.getAllEntities()) {
                if (entity.getName() != null && entity.getName().startsWith("Dust-covered")) stations.add(entity.getId());
            }
            c.put("stations", stations);
            c.expect(stations.size() == 2, "planetoid has 2 loot stations at intensity 3 (" + stations.size() + ")");

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            fleet.setCircularOrbit(planet, 0f, planet.getRadius() + 300f, 10f);
            c.put("arrival", Global.getSector().getClock().getTimestamp());
            intel = latestIntel();
            c.expect(intel != null, "swarm intel posted");
            c.put("date", ReflectionUtils.get("dateString", intel));
            c.note("intel date " + c.get("date"));
            c.days(3f);
        });
        steps.add(c -> {
            c.expect(c.get("date") != null && c.get("date").equals(ReflectionUtils.get("dateString", latestIntel())), "2202 entered on date stays fixed " + c.get("date"));
            c.reload();
        });
        steps.add(c -> {
            StarSystemAPI system = system(c);
            PlanetAPI planet = planetoid(system);
            c.expect(planet != null, "2210 planetoid survives reload");
            c.expect(planet.hasScriptOfClass(PayloadOnFinishedModuleRunner.class), "2210 despawn runner kept after reload");
            intel = latestIntel();
            c.until(() -> planetoid(system) == null, 90f, "2209 rogue planet despawns at arc end after reload", () -> {
                if (ended == 0L && intel.isEnded()) ended = Global.getSector().getClock().getTimestamp();
            });
        });
        steps.add(c -> c.days(1f));
        steps.add(c -> {
            StarSystemAPI system = system(c);
            c.expect(rings(system) == (Integer) c.get("rings"), "2209 dust rings gone");
            @SuppressWarnings("unchecked")
            List<String> stations = (List<String>) c.get("stations");
            for (String id : stations) c.expect(Global.getSector().getEntityById(id) == null, "2209 loot station gone " + id);

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            c.expect(fleet.isAlive() && fleet.getContainingLocation() == system, "player fleet still in the system");
            c.expect(fleet.getOrbit() == null, "player fleet lost the planetoid orbit and keeps flying");
            c.expect(Global.getSector().getCurrentLocation() == system, "current location unchanged");
            c.expect(fleet.getFleetData().getNumMembers() > 0, "player fleet has its ships");
            c.until(() -> {
                if (ended == 0L && intel.isEnded()) ended = Global.getSector().getClock().getTimestamp();
                return ended != 0L;
            }, 40f, "2202 intel ends");
        });
        steps.add(c -> {
            float days = (float) ((ended - (Long) c.get("arrival")) / 8.64E7);
            c.near(days, 20f, 3f, "2202 intel ends 20 days after arrival");
            c.expect(!Global.getSector().hasScript(MeteorShowerLocationIntel.class), "2202 intel script gone");

            StarSystemAPI system = system(c);
            clearTimeouts(system);
            MeteorSwarmManager.getInstance().spawnShower(system, 3f, MeteorSwarmManager.MeteroidShowerType.ASTEROID, false);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            c.until(() -> LunaCampaignRenderer.hasRendererOfClass(MeteorDebrisRenderer.class), 20f, "2205 meteor hit made a dust cloud", () -> {
                SectorEntityToken meteor = rock(system, "IndEvo_meteor_");
                if (meteor != null) fleet.setLocation(meteor.getLocation().x, meteor.getLocation().y);
            });
        });
        steps.add(c -> {
            Global.getSector().getPlayerFleet().setLocation(0f, 0f);
            c.until(() -> !LunaCampaignRenderer.hasRendererOfClass(MeteorDebrisRenderer.class), 15f, "2205 dust cloud expired");
        });
        steps.add(c -> {
            StarSystemAPI system = system(c);
            clearTimeouts(system);
            MeteorSwarmManager.getInstance().spawnShower(system, 3f, MeteorSwarmManager.MeteroidShowerType.IRRADIOID, true);
            followRocks(c, 0.5f, "2206 radiation rises in the rocks");
        });
        steps.add(c -> {
            c.expect(LunaCampaignRenderer.hasRendererOfClass(RadiationEffectHandler.class), "2206 radiation handler runs");
            c.expect(LunaCampaignRenderer.hasRendererOfClass(RadiationVFXHandler.class), "2206 radiation shader on");
            c.shot("radiation-on");
        });
        steps.add(c -> {
            StarSystemAPI system = system(c);
            for (StarSystemAPI other : Global.getSector().getStarSystems()) {
                if (other != system && !other.hasTag("theme_hidden")) {
                    c.put("other", other.getId());
                    Colony.jump(other, 12000f, 0f);
                    break;
                }
            }
            c.until(() -> radiation() <= 0f, 10f, "2206 radiation stops after a jump");
        });
        steps.add(c -> {
            c.expect(!LunaCampaignRenderer.hasRendererOfClass(RadiationVFXHandler.class), "2206 shader off after the jump");
            c.shot("radiation-off");
        });
        steps.add(c -> {
            Colony.jump(system(c), 0f, 0f);
            followRocks(c, 0.5f, "2206 radiation rises again");
        });
        steps.add(c -> {
            c.note("saving with radiation at " + radiation());
            c.reload();
        });
        steps.add(c -> {
            c.note("reloaded with radiation at " + radiation() + ", handler " + LunaCampaignRenderer.hasRendererOfClass(RadiationEffectHandler.class));
            Colony.jump(Global.getSector().getStarSystem((String) c.get("other")), 12000f, 0f);
            c.until(() -> radiation() <= 0f, 15f, "2206 radiation stops after reload and jump");
        });
        steps.add(c -> {
            c.expect(!LunaCampaignRenderer.hasRendererOfClass(RadiationVFXHandler.class), "2206 shader off after reload and jump");
            StarSystemAPI system = system(c);
            Colony.setting(Setting.METEOR_IMMEDIATE_MODE, false);
            Map<MeteorSwarmManager.MeteroidShowerType, Integer> counts = new EnumMap<>(MeteorSwarmManager.MeteroidShowerType.class);
            for (int i = 0; i < 60; i++) {
                clearTimeouts(system);
                MeteorSwarmManager.getInstance().spawnShower(system);
                MeteorShowerLocationIntel posted = latestIntel();
                MeteorSwarmManager.MeteroidShowerType type = (MeteorSwarmManager.MeteroidShowerType) ReflectionUtils.get("type", posted);
                counts.merge(type, 1, Integer::sum);
                Global.getSector().getIntelManager().removeIntel(posted);
            }
            c.note("random swarm types over 60 rolls: " + counts);
            for (MeteorSwarmManager.MeteroidShowerType type : MeteorSwarmManager.MeteroidShowerType.values()) {
                c.expect(counts.getOrDefault(type, 0) > 0, "2201 random swarms roll " + type);
            }
        });
        return steps;
    }
}
