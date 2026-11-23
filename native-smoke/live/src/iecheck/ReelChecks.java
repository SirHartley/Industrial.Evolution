package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.dialogue.DetachmentView;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.renderers.ShuttleTrafficRenderer;
import indevo.config.Setting;
import indevo.console.PetCommand;
import indevo.exploration.fog.FogWeatherManager;
import indevo.exploration.fog.terrain.FogTerrainPlugin;
import indevo.ids.Ids;
import indevo.industries.academy.industry.Academy;
import indevo.industries.academy.plugins.AcademyView;
import indevo.industries.artillery.entities.LighthouseEntityPlugin;
import indevo.industries.artillery.entities.SearchlightEntityPlugin;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.terrain.SearchlightTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer.VisionType;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.courierport.dialogue.CourierView;
import indevo.industries.derelicts.dialogue.AncientLabRelicView;
import indevo.industries.engineeringhub.dialogue.EngineeringHubView;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.industries.petshop.plugins.PetYoungDialog;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import indevo.industries.petshop.plugins.ReelProbe;
import indevo.industries.salvageyards.plugins.SalvageYardsView;
import indevo.utils.helper.ReflectionUtils;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

// test only: the sizzle reel takes (docs/SMOKE_TESTS.md#sizzle-reel), one group per take. setup runs fast, the take in real time
public final class ReelChecks {
    private static final Logger log = Global.getLogger(ReelChecks.class);
    // up right on screen, the heading of every flight take
    static final float HEADING = 45f;
    static final float BURN = 7f;
    static final String[] LOOKS = {"doge_1", null, "doge_2", "doge_3", null, "doge_4"};

    private ReelChecks() {
    }

    private static CampaignFleetAPI player() {
        return Global.getSector().getPlayerFleet();
    }

    private static float speed() {
        return Misc.getSpeedForBurnLevel(BURN);
    }

    private static Vector2f at(Vector2f from, float angle, float dist) {
        return ArtilleryChecks.at(from, angle, dist);
    }

    // place in the current location, already at reel speed along the heading, so the take starts mid flight
    private static void place(Vector2f p, boolean moving) {
        CampaignFleetAPI fleet = player();
        fleet.setLocation(p.x, p.y);
        Vector2f v = Misc.getUnitVectorAtDegreeAngle(HEADING);
        v.scale(moving ? speed() : 0f);
        fleet.setVelocity(v.x, v.y);
        fleet.setMoveDestinationOverride(p.x + v.x * 100f, p.y + v.y * 100f);
    }

    private static void settings() throws Exception {
        Colony.setting(Setting.SHUTTLE_TRAFFIC, 120);
        Colony.setting(Setting.FOG_ENABLED, true);
        Colony.setting(Setting.ENABLE_ARTILLERY, true);
        Colony.setting(Setting.PET_MOTION, true);
        Colony.setting(Setting.PET_TOUR, false);
        Colony.setting(Setting.PET_LINE_DAYS, 0);
    }

    private static List<Step> start(boolean ui) {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            settings();
            ArtilleryChecks.fastTime();
            ArtilleryChecks.unpinAll();
            c.keepDialog(ui);
            c.note("REEL window " + Global.getSettings().getScreenWidthPixels() + "x" + Global.getSettings().getScreenHeightPixels()
                    + ", ui scale " + Global.getSettings().getScreenScaleMult() + ", obs " + (ReelObs.on() ? System.getProperty(ReelObs.PROPERTY) : "off"));
        });
        return steps;
    }

    private static void done(List<Step> steps) {
        steps.add(c -> {
            ArtilleryChecks.unpinAll();
            ArtilleryChecks.fastTime();
            c.keepDialog(false);
        });
    }

    // ---------------------------------------------------------------- 1 and 2: loaded at Chicomoztoc, mortar volley, take off

    public static List<Step> planet() {
        List<Step> steps = start(false);
        steps.add(c -> {
            PlanetAPI planet = (PlanetAPI) Global.getSector().getEntityById("chicomoztoc");
            StarSystemAPI s = planet.getStarSystem();
            SectorEntityToken st = ArtilleryChecks.station(s);
            c.expect(st != null, "REEL planet: Chicomoztoc has its artillery station");
            float toStation = st == null ? 45f : Misc.getAngleInDegrees(planet.getLocation(), st.getLocation());
            Vector2f p = at(planet.getLocation(), toStation, planet.getRadius() + 120f);
            Colony.jump(s, p.x, p.y);
            place(p, false);
            c.put("reel_station", st == null ? null : st.getId());
            c.note("REEL planet: radius " + planet.getRadius() + ", station " + (st == null ? "none" : Misc.getDistance(planet, st) + " su out at " + toStation));
            // the traffic warms up while the fleet sits there
            c.days(1f);
        });
        steps.add(c -> {
            PlanetAPI planet = (PlanetAPI) Global.getSector().getEntityById("chicomoztoc");
            MarketAPI market = planet.getMarket();
            StarSystemAPI s = planet.getStarSystem();
            SectorEntityToken st = Global.getSector().getEntityById((String) c.get("reel_station"));
            int[] craft = {0};
            Reel reel = new Reel("planet", 13f);
            reel.heading = null;
            reel.zoom(0.6f);
            reel.at(0.5f, r -> {
                PlayerMarketTransaction trade = new PlayerMarketTransaction(market, market.getSubmarket(com.fs.starfarer.api.impl.campaign.ids.Submarkets.SUBMARKET_OPEN), CampaignUIAPI.CoreUITradeMode.OPEN);
                trade.getBought().addSupplies(400f);
                trade.getBought().addFuel(300f);
                ShuttleTraffic traffic = traffic();
                if (traffic == null) r.note("no shuttle traffic");
                else traffic.getFlights().getFleets().queueTrade(trade);
            });
            reel.at(1f, r -> {
                if (st == null) return;
                // up left and clear of the market's safe radius, or the station holds fire
                Vector2f aim = at(planet.getLocation(), 150f, planet.getRadius() + 1100f);
                SectorEntityToken target = ArtilleryChecks.token(s, "ie_reel_mortar", aim);
                ArtilleryChecks.pin(target, () -> aim);
                ArtilleryChecks.attack(st).forceTarget(target, 30f);
            });
            reel.when("shells", () -> ArtilleryChecks.count(s, ArtilleryChecks.projectile(ArtilleryChecks.MORTAR)) > 0, null);
            reel.at(7f, r -> r.heading = HEADING);
            reel.at(7.3f, r -> r.ease(2f, 1.5f));
            reel.frame(() -> craft[0] = Math.max(craft[0], around(planet)));
            reel.at(12.5f, r -> {
                c.expect(r.mark("shells") > 0f, "REEL planet: the mortar fired during the take");
                c.note("REEL planet: up to " + craft[0] + " craft within radius + 800 su");
                r.cut(0f);
            });
            Reel.play(c, reel);
        });
        steps.add(c -> ArtilleryChecks.dropToken(Global.getSector().getStarSystem("Aztlan"), "ie_reel_mortar"));
        done(steps);
        return steps;
    }

    private static ShuttleTraffic traffic() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        return renderer == null ? null : ((ShuttleTrafficRenderer) renderer).getTraffic();
    }

    private static int around(SectorEntityToken e) {
        ShuttleTraffic t = traffic();
        if (t == null) return 0;
        int n = 0;
        for (indevo.ambience.shuttletraffic.Shuttle s : t.getShuttles()) {
            if (Misc.getDistance(s.pos, e.getLocation()) < e.getRadius() + 800f) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- 3: fog bank

    public static List<Step> fog() {
        List<Step> steps = start(false);
        steps.add(c -> {
            StarSystemAPI s = fogSystem();
            c.expect(s != null, "REEL fog: a quiet system for the bank");
            SectorEntityToken fog = FogWeatherManager.spawn(s, FogTerrainPlugin.Kind.BANK, new Random(4207), false, false);
            c.put("reel_fog_sys", s.getName());
            c.put("reel_fog", fog.getId());
            Vector2f away = at(fog.getLocation(), 270f, 30000f);
            Colony.jump(s, away.x, away.y);
            place(away, false);
            c.note("REEL fog: bank in " + s.getName());
            // rolls in over FogWeatherManager.ROLL_IN_DAYS
            c.days(FogWeatherManager.ROLL_IN_DAYS + 0.5f);
        });
        steps.add(c -> {
            StarSystemAPI s = Global.getSector().getStarSystem((String) c.get("reel_fog_sys"));
            SectorEntityToken fog = s.getEntityById((String) c.get("reel_fog"));
            FogTerrainPlugin plugin = (FogTerrainPlugin) ((CampaignTerrainAPI) fog).getPlugin();
            float run = speed() * 6f;
            Vector2f best = null;
            float bestMin = -1f;
            Random random = new Random(11);
            for (int i = 0; i < 400; i++) {
                Vector2f p = at(fog.getLocation(), random.nextFloat() * 360f, random.nextFloat() * plugin.getRadius() * 0.8f);
                float min = 1f;
                for (float d = 0f; d <= run; d += 100f) min = Math.min(min, plugin.getDensityAt(at(p, HEADING, d)));
                if (min > bestMin) {
                    bestMin = min;
                    best = p;
                }
            }
            c.expect(bestMin > 0.5f, "REEL fog: a dense line through the bank (min density " + bestMin + ")");
            Vector2f start = at(best, HEADING + 180f, speed() * 3f);
            player().setLocation(start.x, start.y);
            place(start, true);
            Reel reel = new Reel("fog", 10f);
            reel.heading = HEADING;
            reel.zoom(2f);
            reel.when("dense", () -> plugin.getDensityAt(player().getLocation()) > 0.6f, null);
            Reel.play(c, reel);
        });
        done(steps);
        return steps;
    }

    // no markets, no fog yet, no nebula over the fog cap; a nebula colours the bank
    private static StarSystemAPI fogSystem() {
        StarSystemAPI plain = null;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s.hasTag(Tags.THEME_HIDDEN) || s.getStar() == null || !Misc.getMarketsInLocation(s).isEmpty()) continue;
            if (FogWeatherManager.hasFog(s) || !FogWeatherManager.isValidSystem(s, FogTerrainPlugin.Kind.BANK) || !s.getFleets().isEmpty()) continue;
            boolean nebula = false;
            for (CampaignTerrainAPI terrain : s.getTerrainCopy()) if ("nebula".equals(terrain.getType())) nebula = true;
            if (nebula) return s;
            if (plain == null) plain = s;
        }
        return plain;
    }

    // ---------------------------------------------------------------- 4: lighthouse beam

    public static List<Step> lighthouse() {
        List<Step> steps = start(false);
        steps.add(c -> ArtilleryChecks.make(c, "reellight", VisionType.LIGHTHOUSE, ArtilleryChecks.MORTAR));
        steps.add(c -> {
            ArtilleryChecks.unpinAll();
            StarSystemAPI s = ArtilleryChecks.sys(c, "reellight");
            SectorEntityToken pick = null;
            for (SectorEntityToken h : ArtilleryChecks.houses(s)) {
                if (pick == null || ArtilleryChecks.rolledBeams(h) == 2 && ArtilleryChecks.rolledBeams(pick) != 2) pick = h;
            }
            SectorEntityToken h = pick;
            c.expect(h != null && ArtilleryChecks.rolledBeams(h) == 2, "REEL light: a 2 beam lighthouse (" + (h == null ? "none" : ArtilleryChecks.rolledBeams(h) + " beams") + ")");
            c.put("reel_house", h.getId());
            c.until(() -> ArtilleryChecks.house(h).isLit() && ArtilleryChecks.beam(h) != null, 1f, "REEL light: lighthouse lit");
        });
        steps.add(c -> {
            StarSystemAPI s = ArtilleryChecks.sys(c, "reellight");
            SectorEntityToken h = s.getEntityById((String) c.get("reel_house"));
            // beam pointing down, so the house sits top right of the crossing and the fleet crosses at an angle
            float beam = 260f;
            ArtilleryChecks.ensurePinner();
            ArtilleryChecks.beamAims.put(h, () -> beam);
            Vector2f cross = at(h.getLocation(), beam, 900f);
            Vector2f start = at(cross, HEADING + 180f, speed() * 4f);
            place(start, true);
            ArtilleryChecks.resetEye();
            Reel reel = new Reel("light", 12f);
            reel.heading = HEADING;
            reel.zoom(2.4f);
            reel.when("beam", () -> distToRay(player().getLocation(), h.getLocation(), beam) < 60f, r -> r.cut(3f));
            Reel.play(c, reel);
        });
        steps.add(c -> c.expect(ArtilleryChecks.playerSeen() || ArtilleryChecks.eyeElapsed() > 0f, "REEL light: the beam saw the fleet"));
        done(steps);
        return steps;
    }

    private static float distToRay(Vector2f p, Vector2f from, float angle) {
        Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);
        float rx = p.x - from.x;
        float ry = p.y - from.y;
        float along = rx * dir.x + ry * dir.y;
        if (along < 0f) return Float.MAX_VALUE;
        return Math.abs(rx * dir.y - ry * dir.x);
    }

    // ---------------------------------------------------------------- 5: searchlights

    public static List<Step> searchlight() {
        List<Step> steps = start(false);
        steps.add(c -> ArtilleryChecks.make(c, "reelsearch", VisionType.SEARCHLIGHT, ArtilleryChecks.MORTAR));
        steps.add(c -> {
            ArtilleryChecks.unpinAll();
            StarSystemAPI s = ArtilleryChecks.sys(c, "reelsearch");
            List<SectorEntityToken> towers = ArtilleryChecks.entities(s, SearchlightEntityPlugin.ENTITY_ID);
            c.expect(!towers.isEmpty(), "REEL search: searchlight towers placed (" + towers.size() + ")");
            SectorEntityToken tower = towers.get(0);
            c.put("reel_tower", tower.getId());
            float range = SearchlightTerrain.getRange();
            // pass the tower on its up left side, inside the outer ring
            Vector2f closest = at(tower.getLocation(), HEADING + 90f, range * 0.6f);
            Vector2f start = at(closest, HEADING + 180f, range);
            place(start, true);
            c.until(() -> ((SearchlightEntityPlugin) tower.getCustomPlugin()).isLit(), 1f, "REEL search: tower lit");
        });
        steps.add(c -> {
            StarSystemAPI s = ArtilleryChecks.sys(c, "reelsearch");
            SectorEntityToken tower = s.getEntityById((String) c.get("reel_tower"));
            ArtilleryChecks.resetEye();
            Reel reel = new Reel("search", 25f);
            reel.heading = HEADING;
            reel.zoom(2.2f);
            reel.when("seen", () -> spots(tower, spot -> spot.sees), null);
            reel.when("lock", () -> spots(tower, spot -> spot.locked), r -> r.cut(1.5f));
            Reel.play(c, reel);
        });
        steps.add(c -> c.expect(ArtilleryChecks.playerSeen(), "REEL search: a spot locked on the fleet"));
        done(steps);
        return steps;
    }

    private static boolean spots(SectorEntityToken tower, Predicate<SearchlightTerrain.Spot> test) {
        Object lights = ReflectionUtils.get("lights", tower.getCustomPlugin());
        if (!(lights instanceof CampaignTerrainAPI terrain)) return false;
        Object spots = ReflectionUtils.get("spots", terrain.getPlugin());
        if (!(spots instanceof SearchlightTerrain.Spot[] all)) return false;
        for (SearchlightTerrain.Spot spot : all) if (spot != null && test.test(spot)) return true;
        return false;
    }

    // ---------------------------------------------------------------- 6: drone chase, railgun misses

    public static List<Step> drones() {
        List<Step> steps = start(false);
        steps.add(c -> ArtilleryChecks.make(c, "reeldrones", VisionType.DRONES, ArtilleryChecks.RAILGUN));
        steps.add(c -> {
            ArtilleryChecks.unpinAll();
            StarSystemAPI s = ArtilleryChecks.sys(c, "reeldrones");
            DroneNetScript net = ArtilleryChecks.net(s);
            List<DroneNetScript.Spotter> live = ArtilleryChecks.live(net);
            c.expect(live.size() >= 2, "REEL drones: two spotters (" + live.size() + ")");
            List<DroneNetScript.Spotter> chase = live.subList(0, 2);
            ArtilleryChecks.park(net, chase);
            SectorEntityToken nest = net.getNests().get(0);
            Vector2f p = at(nest.getLocation(), 0f, 4000f);
            place(p, false);
            float[] sides = {HEADING + 160f, HEADING + 200f};
            for (int i = 0; i < 2; i++) {
                float side = sides[i];
                ArtilleryChecks.pinSpotter(chase.get(i), () -> at(player().getLocation(), side, 750f), () -> side + 180f);
            }
            ArtilleryChecks.realTime();
            ArtilleryChecks.resetEye();
            // two seconds in their cones, so both follow once released
            ArtilleryChecks.hold(c, 0.2f, "REEL drones: spotters see the fleet");
        });
        steps.add(c -> {
            StarSystemAPI s = ArtilleryChecks.sys(c, "reeldrones");
            String shot = ArtilleryChecks.projectile(ArtilleryChecks.RAILGUN);
            Reel reel = new Reel("drones", 45f);
            reel.heading = HEADING;
            reel.zoom(1.6f);
            reel.at(0f, r -> {
                ArtilleryChecks.spotterPins.clear();
                ArtilleryChecks.aims.clear();
            });
            // turn off the line the lances will take
            reel.when("lines", () -> ArtilleryChecks.count(s, shot) > 0, r -> {
                r.heading = HEADING + 55f;
                r.cut(3f);
            });
            Reel.play(c, reel);
        });
        steps.add(c -> c.note("REEL drones: " + ArtilleryChecks.live(ArtilleryChecks.net(ArtilleryChecks.sys(c, "reeldrones"))).size() + " spotters left"));
        done(steps);
        return steps;
    }

    // ---------------------------------------------------------------- 7 to 10: pet message, Play, family tree, young cards

    private static PetStatusManager petStatus() {
        return PetStatusManager.getInstance();
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static int looks = 0;

    private static Pet pet(Ctx c, PetShop shop, String name, boolean ship, Pet a, Pet b) {
        Pet pet = new Pet("doge", name);
        petStatus().register(pet);
        if (ship) {
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
            member.setShipName("ISS " + name);
            player().getFleetData().addFleetMember(member);
            member.getRepairTracker().setCR(0.7f);
            pet.place(member, null);
        } else pet.place(null, shop);
        pet.age = 400f;
        if (a != null) pet.parentA = a.id;
        if (b != null) pet.parentB = b.id;
        pet.sheet.look = LOOKS[looks++ % LOOKS.length];
        c.put(name, pet.id);
        return pet;
    }

    private static PetShop center(MarketAPI market) {
        if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
        PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
        shop.finishBuildingOrUpgrading();
        return shop;
    }

    public static List<Step> pets() {
        List<Step> steps = start(true);
        steps.add(c -> {
            looks = 0;
            Colony.pair(c, 6);
            MarketAPI market = c.market("a");
            PetShop shop = center(market);
            player().getCargo().getCredits().add(100000f);
            // four generations of one species: two founding pairs, their young, two crosses, then Pip on a ship
            Pet a1 = pet(c, shop, "Juniper", false, null, null);
            Pet a2 = pet(c, shop, "Bramble", false, null, null);
            Pet b1 = pet(c, shop, "Saffron", false, null, null);
            Pet b2 = pet(c, shop, "Tumble", false, null, null);
            Pet a3 = pet(c, shop, "Clover", false, a1, a2);
            Pet a4 = pet(c, shop, "Hazel", false, a1, a2);
            Pet b3 = pet(c, shop, "Biscuit", false, b1, b2);
            Pet b4 = pet(c, shop, "Pepper", false, b1, b2);
            Pet c1 = pet(c, shop, "Maple", false, a3, b3);
            Pet c2 = pet(c, shop, "Rusty", false, a3, b3);
            Pet c3 = pet(c, shop, "Nutmeg", false, a4, b4);
            pet(c, shop, "Pip", true, c1, c3);
            pet(c, shop, "Dot", false, c1, c3);
            pet(c, shop, "Sprout", false, c1, c3);
            shop.startBrood(c2, c3, 0);
            Vector2f near = at(market.getPrimaryEntity().getLocation(), 0f, market.getPrimaryEntity().getRadius() + 300f);
            Colony.jump(market.getContainingLocation(), near.x, near.y);
            place(near, false);
            c.until(() -> {
                PetBrood brood = shop.getBrood(0);
                return brood != null && brood.getYoung() != null && !brood.getYoung().isEmpty();
            }, 150f, "REEL pets: the brood is ready");
        });
        steps.add(c -> {
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            PetBrood brood = shop.getBrood(0);
            tiers(c, brood);
            // the popup opens from the reel, not by itself when the panel opens
            brood.shown = true;
            brood.opened.clear();
            Pet pip = petStatus().get((String) c.get("Pip"));
            pip.want = null;
            pip.wantCooldownDays = 0f;
            pip.favourite = true;
            c.frames(30);
        });
        steps.add(c -> {
            MarketAPI market = c.market("a");
            PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
            PetBrood brood = shop.getBrood(0);
            String pipId = (String) c.get("Pip");
            Pet pip = petStatus().get(pipId);
            Reel reel = new Reel("pets", 16f).ui(false);
            reel.heading = null;
            reel.zoom(1f);
            reel.at(0.5f, r -> cmd("want " + pip.name + " play"));
            reel.when("want", () -> pip.want != null, null);
            reel.at(2.6f, r -> c.expect(PetsDialogPlugin.show(shop, new PetsView(pipId)), "REEL pets: the Pets panel opens on Pip"));
            reel.at(3.8f, r -> {
                UiAudit.Found found = UiAudit.find();
                boolean clicked = found != null && UiAudit.click(found.content(), "Play");
                c.expect(clicked, "REEL pets: Play pressed" + (found == null ? ", no panel" : clicked ? "" : ", buttons " + UiAudit.buttonTexts(found.content())));
            });
            reel.when("played", () -> pip.want == null && r0(reel) > 3.8f, null);
            reel.at(6.5f, r -> UiAudit.dismissDialog());
            reel.at(6.7f, r -> c.expect(PetsDialogPlugin.show(shop, ReelProbe.tree(pipId)), "REEL pets: the family tree opens in full view"));
            reel.at(9.5f, r -> new PetYoungDialog(shop, brood).show(null));
            for (int i = 0; i < 3; i++) reel.at(10.7f + 0.3f * i, r -> openCard(c));
            reel.at(14f, r -> UiAudit.dismissDialog());
            reel.at(14.5f, r -> {
                c.expect(brood.opened.size() == brood.getYoung().size(), "REEL pets: every young card opened (" + brood.opened.size() + " of " + brood.getYoung().size() + ")");
                r.cut(0.5f);
            });
            Reel.play(c, reel);
        });
        done(steps);
        return steps;
    }

    private static float r0(Reel reel) {
        return reel.t;
    }

    // gold, bright and red: a sum talent, a mutation, a malformed young; a short brood gets more young
    private static void tiers(Ctx c, PetBrood brood) {
        List<Pet> young = brood.getYoung();
        Pet parent = petStatus().get(brood.parentA);
        while (young.size() < 3) {
            PetSheet sheet = new PetSheet(young.get(0).sheet.disposition);
            sheet.from = new HashMap<>();
            young.add(new Pet(parent.typeID, "Kit" + young.size(), sheet, brood.parentA, brood.parentB));
        }
        String talent = null;
        String mutation = null;
        for (Map.Entry<String, PetDataRepo.Trait> e : PetDataRepo.getTraits().entrySet()) {
            if (talent == null && e.getValue().isTalent()) talent = e.getKey();
            if (mutation == null && e.getValue().isMutation()) mutation = e.getKey();
        }
        for (int i = 0; i < 3; i++) {
            PetSheet sheet = young.get(i).sheet;
            if (sheet.from == null) sheet.from = new HashMap<>();
            sheet.from.replaceAll((k, v) -> PetSheet.FROM_MUTATION.equals(v) || PetSheet.FROM_SUM.equals(v) ? PetSheet.FROM_A : v);
            sheet.malformed = false;
            sheet.look = LOOKS[i];
        }
        PetSheet sum = young.get(0).sheet;
        sum.traits.put(talent, 3);
        sum.from.put(talent, PetSheet.FROM_SUM);
        sum.known.add(talent);
        PetSheet mutant = young.get(1).sheet;
        mutant.traits.put(mutation, 1);
        mutant.from.put(mutation, PetSheet.FROM_MUTATION);
        mutant.known.add(mutation);
        young.get(2).sheet.malformed = true;
        c.note("REEL pets: young " + young.get(0).name + " sum " + talent + ", " + young.get(1).name + " mutation " + mutation + ", " + young.get(2).name + " malformed, " + young.size() + " in all");
    }

    // the first closed card back: a kit row of the card size, its Runnable opens the card
    private static void openCard(Ctx c) {
        List<Object> queue = new ArrayList<>();
        queue.add(UiAudit.screen());
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) queue.add(Global.getSector().getCampaignUI().getCurrentInteractionDialog());
        while (!queue.isEmpty()) {
            Object node = queue.remove(0);
            if (node instanceof ButtonAPI b && b.getCustomData() instanceof Runnable open
                    && Math.abs(b.getPosition().getWidth() - 250f) < 1f && Math.abs(b.getPosition().getHeight() - 374f) < 1f) {
                open.run();
                return;
            }
            queue.addAll(UiAudit.children(node));
        }
        c.note("REEL pets: no closed young card found");
    }

    // ---------------------------------------------------------------- 11 and 12: colony industries, the Edict panel the way a player opens it

    static final String[] COLONY_INDUSTRIES = {Industries.MINING, Industries.REFINING, Ids.SCRAPYARD, Ids.ACADEMY, Ids.SENATE, Ids.ENGHUB, Ids.PET_STORE, Ids.LAB, Ids.PORT};

    public static List<Step> colony() {
        List<Step> steps = start(true);
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI market = c.market("a");
            for (String id : COLONY_INDUSTRIES) {
                if (!market.hasIndustry(id)) market.addIndustry(id);
                Industry industry = market.getIndustry(id);
                if (industry != null) industry.finishBuildingOrUpgrading();
            }
            market.reapplyIndustries();
            c.note("REEL colony: " + market.getName() + " with " + market.getIndustries().size() + " industries");
            Vector2f near = at(market.getPrimaryEntity().getLocation(), 0f, market.getPrimaryEntity().getRadius() + 300f);
            Colony.jump(market.getContainingLocation(), near.x, near.y);
            place(near, false);
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI market = c.market("a");
            Industry senate = market.getIndustry(Ids.SENATE);
            Reel reel = new Reel("colony", 14f).ui(false);
            reel.heading = null;
            reel.at(0.4f, r -> Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.OUTPOSTS));
            reel.at(1.2f, r -> ReelMouse.calibrate());
            reel.at(2.2f, r -> clickLabel(c, r, market.getName(), 0.5f));
            reel.at(3.9f, r -> clickLabel(c, r, senate.getCurrentName(), 0.6f));
            reel.at(5.2f, r -> clickLabel(c, r, "Visit the " + senate.getCurrentName(), 0.35f));
            reel.when("edict", () -> r0(reel) > 5.2f && UiAudit.find() != null, r -> r.cut(2.5f));
            reel.at(13.5f, r -> c.expect(r.mark("edict") > 0f, "REEL colony: the Edict panel opened from the Senate tile"));
            Reel.play(c, reel);
        });
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found != null) UiAudit.close(found);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        done(steps);
        return steps;
    }

    // the label with this text on screen, exact first, then containing it; logs the screen's labels when there is none
    private static void clickLabel(Ctx c, Reel reel, String text, float seconds) {
        LabelAPI label = findLabel(text);
        if (label == null) {
            reel.note("no label \"" + text + "\" on screen, labels: " + labels());
            return;
        }
        boolean ok = ReelMouse.click(label.getPosition(), seconds);
        reel.note("click \"" + text + "\" at " + label.getPosition().getCenterX() + ", " + label.getPosition().getCenterY() + (ok ? "" : " skipped"));
    }

    private static LabelAPI findLabel(String text) {
        LabelAPI contains = null;
        List<Object> queue = new ArrayList<>(UiAudit.children(UiAudit.screen()));
        while (!queue.isEmpty()) {
            Object node = queue.remove(0);
            if (node instanceof LabelAPI l && l.getText() != null) {
                String s = l.getText().trim();
                if (s.equalsIgnoreCase(text)) return l;
                if (contains == null && s.toLowerCase().contains(text.toLowerCase())) contains = l;
            }
            queue.addAll(UiAudit.children(node));
        }
        return contains;
    }

    private static String labels() {
        StringBuilder out = new StringBuilder();
        List<Object> queue = new ArrayList<>(UiAudit.children(UiAudit.screen()));
        int n = 0;
        while (!queue.isEmpty() && n < 200) {
            Object node = queue.remove(0);
            if (node instanceof LabelAPI l && l.getText() != null && !l.getText().isBlank()) {
                out.append('[').append(l.getText().replace('\n', ' ')).append(']');
                n++;
            }
            queue.addAll(UiAudit.children(node));
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- 13: flash cut stills

    public static List<Step> flash() {
        List<Step> steps = start(true);
        still(steps, "reel-flash-1-academy", c -> {
            AcademyUiChecks.setup(c);
            Academy academy = Academy.get(c.market("a"));
            academy.setOfficerForTraining(AcademyUiChecks.person(c, "trainee"), academy.getReachablePersonalities(AcademyUiChecks.person(c, "trainee")).get(0));
            AcademyUiChecks.worst(c);
        }, c -> AcademyUiChecks.open(c, new AcademyView(false, AcademyUiChecks.id(c, "selected"), Personalities.STEADY)));
        still(steps, "reel-flash-2-salvage", c -> {
            SalvageUiChecks.setup(c);
            SalvageUiChecks.ships(c);
        }, c -> SalvageUiChecks.open(c, SalvageUiChecks.order(new SalvageYardsView(SalvageYardsView.Tab.ASSEMBLY))));
        still(steps, "reel-flash-3-government", GovernmentUiChecks::setup,
                c -> c.expect(ChangelingIndustryDialogueDelegate.show(c.market("a").getIndustry(Industries.POPULATION), GovernmentCondition.TARGET, null), "REEL flash: government picker opens"));
        still(steps, "reel-flash-4-courier", CourierUiChecks::setup, c -> CourierUiChecks.open(c, CourierView.contract((String) c.get("cargo"))));
        still(steps, "reel-flash-5-hub", c -> {
            HubUiChecks.setup(c);
            HubUiChecks.reverse(c, 6);
            HubUiChecks.hullmods(c, 8);
        }, c -> HubUiChecks.open(c, new EngineeringHubView(false, null, "eagle", null)));
        still(steps, "reel-flash-6-lab", c -> {
            LabUiChecks.setup(c);
            LabUiChecks.templates(c);
            LabUiChecks.relics(c, 5000f);
        }, c -> LabUiChecks.open(c, new AncientLabRelicView(false, AncientLabRelicView.FIRST, null)));
        still(steps, "reel-flash-7-detach", DetachmentUiChecks::setup, c -> DetachmentUiChecks.open(c, new DetachmentView()));
        done(steps);
        return steps;
    }

    private static void still(List<Step> steps, String name, Step setup, UiViews.Open open) {
        steps.add(c -> {
            c.keepDialog(true);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(setup);
        steps.add(c -> {
            open.open(c);
            // the dialog fades in for about a second
            c.frames(120);
        });
        steps.add(c -> {
            c.expect(UiAudit.find() != null, name + " is open");
            c.shot(name);
        });
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found != null) UiAudit.close(found);
            c.frames(20);
        });
        steps.add(c -> {
            UiAudit.dismissDialog();
            c.frames(5);
        });
    }
}
