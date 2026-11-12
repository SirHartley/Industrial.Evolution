package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.dogfights.ShuttleDogfight;
import indevo.ambience.shuttletraffic.renderers.ShuttleTrafficRenderer;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.List;

// worst case shuttle traffic cost: busiest core system, two npc carrier battles in view, parked fleets for service craft, pods at their cap
public final class TrafficPerfChecks {
    private static final String[] CARRIERS = {"heron_Strike", "condor_Support", "drover_Strike", "astral_Strike", "condor_Attack", "heron_Attack"};
    private static final List<CampaignFleetAPI> parked = new ArrayList<>();
    private static final List<CampaignFleetAPI> fighting = new ArrayList<>();

    private TrafficPerfChecks() {
    }

    private static ShuttleTraffic traffic() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        return renderer == null ? null : ((ShuttleTrafficRenderer) renderer).getTraffic();
    }

    private static CampaignFleetAPI fleet(String faction, String type, int ships, boolean carriers, LocationAPI location, float x, float y) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(faction, "perf " + type, true);
        for (int i = 0; i < ships; i++) {
            String variant = carriers ? CARRIERS[i % CARRIERS.length] : (i % 2 == 0 ? "buffalo_Standard" : "tarsus_Standard");
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
            fleet.getFleetData().addFleetMember(member);
        }
        fleet.getFleetData().ensureHasFlagship();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }
        fleet.getCargo().addCrew(2000);
        fleet.getCargo().addSupplies(500);
        fleet.getCargo().addFuel(500);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, type);
        location.addEntity(fleet);
        fleet.setLocation(x, y);
        fleet.forceSync();
        return fleet;
    }

    private static String state() {
        ShuttleTraffic t = traffic();
        if (t == null) return "no traffic";
        int fighters = 0;
        for (ShuttleDogfight fight : t.getDogfights().getFights()) fighters += fight.getFighters().size();
        int battles = 0;
        for (CampaignFleetAPI fleet : fighting) if (fleet.getBattle() != null && !fleet.getBattle().isDone()) battles++;
        return t.getShuttles().size() + " craft, " + t.getPods().size() + " pods, " + t.getDogfights().getFights().size() + " dogfights, "
                + fighters + " fighters, " + t.getFleetRoutes().size() + " fleet routes, " + battles + " fleets in battle";
    }

    // pods stay near their cap of 32: a burst from every parked fleet every 180 frames
    private static void pods(float[] timer) {
        if (timer[0]++ % 180f != 0f) return;
        ShuttleTraffic t = traffic();
        if (t == null) return;
        for (CampaignFleetAPI fleet : parked) t.queuePods(fleet, null);
    }

    private static void timed(Ctx c, int frames, String key, boolean podsOn) {
        long[] t = {0L, 0L};
        float[] timer = {0f};
        c.until(() -> t[1] >= frames, 100f, key + " ran " + frames + " frames", () -> {
            if (podsOn) pods(timer);
            if (t[1]++ == 0L) t[0] = System.nanoTime();
            if (t[1] == frames) c.note("PERF " + key + " " + (System.nanoTime() - t[0]) / 1000000f / frames + " ms per frame, " + state());
        });
    }

    private static void battle(LocationAPI location, float x, float y, String a, String b) {
        CampaignFleetAPI one = fleet(a, FleetTypes.PATROL_LARGE, 16, true, location, x, y);
        CampaignFleetAPI two = fleet(b, FleetTypes.PATROL_LARGE, 16, true, location, x + 60f, y);
        Global.getFactory().createBattle(one, two);
        fighting.add(one);
        fighting.add(two);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            SectorEntityToken sindria = Global.getSector().getEntityById("sindria");
            Colony.jump(sindria.getContainingLocation(), sindria.getLocation().x + 500f, sindria.getLocation().y);
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 0);
            timed(c, 1800, "askonia traffic 0, quiet", false);
        });
        steps.add(c -> {
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 80);
            timed(c, 1200, "askonia traffic 80 warm up, quiet", false);
        });
        steps.add(c -> timed(c, 3600, "askonia traffic 80, quiet", false));
        for (int setting : new int[]{0, 80, 120}) {
            steps.add(c -> {
                Colony.setting(Setting.SHUTTLE_TRAFFIC, setting);
                scene();
                timed(c, 300, "worst case " + setting + " warm up", setting > 0);
            });
            steps.add(c -> {
                c.note("worst case " + setting + " scene: " + parkedState());
                timed(c, 900, "askonia traffic " + setting + ", worst case", setting > 0);
            });
            steps.add(c -> c.note("worst case " + setting + " after: " + state() + "; " + parkedState()));
        }
        steps.add(c -> {
            ShuttleTraffic t = traffic();
            c.expect(t != null && t.getShuttles().size() > 60, "traffic 120 fills past 60 craft (" + state() + ")");
            clearScene();
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 80);
        });
        return steps;
    }

    private static void clearScene() {
        for (CampaignFleetAPI fleet : parked) fleet.despawn();
        for (CampaignFleetAPI fleet : fighting) if (fleet.isAlive()) fleet.despawn();
        parked.clear();
        fighting.clear();
    }

    // six parked fleets at sindria for service craft, two carrier battles beside the player, nobody joins anybody
    private static void scene() {
        clearScene();
        SectorEntityToken sindria = Global.getSector().getEntityById("sindria");
        LocationAPI location = sindria.getContainingLocation();
        Colony.jump(location, sindria.getLocation().x + 500f, sindria.getLocation().y);
        float px = Global.getSector().getPlayerFleet().getLocation().x;
        float py = Global.getSector().getPlayerFleet().getLocation().y;
        for (int i = 0; i < 6; i++) {
            CampaignFleetAPI fleet = fleet(Factions.DIKTAT, i % 2 == 0 ? FleetTypes.TRADE : FleetTypes.PATROL_MEDIUM, 6, false, location,
                    sindria.getLocation().x + 250f * (float) Math.cos(i), sindria.getLocation().y + 250f * (float) Math.sin(i));
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, sindria, 1000f);
            parked.add(fleet);
        }
        battle(location, px + 300f, py + 500f, Factions.HEGEMONY, Factions.PIRATES);
        battle(location, px + 300f, py - 500f, Factions.TRITACHYON, Factions.LUDDIC_PATH);
    }

    private static String parkedState() {
        StringBuilder sb = new StringBuilder();
        for (CampaignFleetAPI fleet : parked) {
            com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI a = fleet.getAI() != null ? fleet.getAI().getCurrentAssignment() : null;
            sb.append(a == null ? "none" : a.getAssignment().name()).append(" v ").append(Math.round(fleet.getVelocity().length()))
                    .append(fleet.getBattle() != null ? " battle" : "").append(fleet.isAlive() ? "" : " dead").append(", ");
        }
        int battles = 0;
        for (CampaignFleetAPI fleet : fighting) if (fleet.getBattle() != null && !fleet.getBattle().isDone()) battles++;
        return "parked [" + sb + "], fighting fleets in battle " + battles + " of " + fighting.size();
    }
}
