package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.combatEntities.IndEvo_CombatSlowFieldTerrain;
import data.scripts.weapons.combatEntities.IndEvo_ForcedOverload;
import indevo.abilities.splitfleet.dialogue.SplinterFleetDialoguePluginAPI;
import indevo.abilities.splitfleet.dialogue.TwoFleetCargoPicker;
import indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript;
import indevo.console.SpawnRuins;
import indevo.config.Setting;
import indevo.dialogue.rules.IndEvo_AddSpacerFineMenuPoint;
import indevo.dialogue.rules.IndEvo_DeductSpacerCreditsAndSetNeutral;
import indevo.exploration.minefields.MineBeltTerrainPlugin;
import indevo.exploration.salvage.specials.CreditStashSpecial;
import indevo.exploration.salvage.specials.ShipRouletteSpecial;
import indevo.exploration.surveystories.memory.StoryEntry;
import indevo.exploration.surveystories.plugins.Historian;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.RequisitionCenter;
import indevo.industries.courierport.ShippingCostCalculator;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.industries.derelicts.scripts.PlanetMovingScript;
import indevo.industries.embassy.industry.Embassy;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.item.PetItemPlugin;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.items.listeners.ShipComponentLootManager;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.ReflectionUtils;
import org.lazywizard.console.BaseCommand;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RestChecks {
    private static TwoFleetCargoPicker picker = null;

    private RestChecks() {
    }

    private static CampaignFleetAPI player() {
        return Global.getSector().getPlayerFleet();
    }

    private static InteractionDialogAPI dialog() {
        return Global.getSector().getCampaignUI().getCurrentInteractionDialog();
    }

    private static Object call(MethodType type, Class<?> owner, String name, Object... args) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        try {
            if (args.length == 0) return lookup.findStatic(owner, name, type).invoke();
            return lookup.findVirtual(owner, name, type).invokeWithArguments(args);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static FleetMemberAPI ship(String variant) {
        return Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
    }

    private static String tooltip(Industry industry) {
        TooltipMakerAPI tooltip = Ui.tooltip();
        industry.createTooltip(Industry.IndustryTooltipMode.NORMAL, tooltip, false);
        return Ui.text(tooltip);
    }

    private static String centre(RequisitionCenter centre) {
        TooltipMakerAPI tooltip = Ui.tooltip();
        centre.publicAddAfterDescriptionSection(tooltip, Industry.IndustryTooltipMode.NORMAL);
        return Ui.text(tooltip);
    }

    private static CampaignFleetAPI spacer(StarSystemAPI system) {
        for (CampaignFleetAPI fleet : system.getFleets()) if (fleet.getMemoryWithoutUpdate().contains("$IndEvo_poor_spacer")) return fleet;
        return null;
    }

    private static Map<String, MemoryAPI> memory(SectorEntityToken entity) {
        Map<String, MemoryAPI> map = new HashMap<>();
        map.put(MemKeys.ENTITY, entity.getMemoryWithoutUpdate());
        return map;
    }

    private static int ruinsOn(MarketAPI market) {
        int count = 0;
        for (MarketConditionAPI condition : market.getConditions()) if (Ids.COND_RUINS.equals(condition.getId())) count++;
        return count;
    }

    private static List<PlanetAPI> ruinPlanets() {
        List<PlanetAPI> list = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.getMarket() != null && ruinsOn(planet.getMarket()) > 0) list.add(planet);
            }
        }
        return list;
    }

    private static void sectorRows(Ctx c) throws Exception {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.initMothballedShips(Factions.PLAYER);
        FleetMemberAPI capital = ship("onslaught_Standard");
        cargo.getMothballedShips().addFleetMember(capital);
        float want = capital.getHullSpec().getSuppliesToRecover() * Setting.COURIER_COST_PER_SHIP_DP.value();
        c.near(ShippingCostCalculator.getSpecificShipCostForCargo(cargo, null, true), want, 1f, "505 ship cost follows supplies to recover");
        Colony.setting(Setting.COURIER_COST_PER_SHIP_DP, 340);
        c.near(ShippingCostCalculator.getSpecificShipCostForCargo(cargo, null, true), capital.getHullSpec().getSuppliesToRecover() * 340f, 1f, "Courier Port cost setting applies on next use");
        Colony.setting(Setting.COURIER_COST_PER_SHIP_DP, 170);

        String icon = Global.getSettings().getSpriteName("intel", "cryosleeper");
        c.expect(icon.contains("cryosleeper"), "Cryosleeper intel has its own icon (" + icon + ")");

        CampaignFleetAPI fleet = player();
        FleetMemberAPI story = ship("wolf_Assault");
        story.setVariant(story.getVariant().clone(), false, false);
        story.getVariant().addTag(Tags.SHIP_CAN_NOT_SCUTTLE);
        FleetMemberAPI plain = ship("wolf_Assault");
        fleet.getFleetData().addFleetMember(story);
        fleet.getFleetData().addFleetMember(plain);
        List<?> offered = (List<?>) call(MethodType.methodType(List.class), ShipRouletteSpecial.class, "getEligibleMembers");
        c.expect(!offered.contains(fleet.getFlagship()), "2405 flagship not offered for sacrifice");
        c.expect(!offered.contains(story), "2405 story ship not offered for sacrifice");
        c.expect(offered.contains(plain), "2405 ordinary ship offered");
        fleet.getFleetData().removeFleetMember(story);
        fleet.getFleetData().removeFleetMember(plain);

        int own = 0;
        int mixed = 0;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            boolean ie = false;
            boolean vanilla = false;
            for (SectorEntityToken entity : system.getAllEntities()) {
                String id = entity.getCustomEntityType();
                if (Ids.LAB_ENTITY.equals(id) || Ids.ARSENAL_ENTITY.equals(id) || Ids.ABANDONED_PETSHOP_ENTITY.equals(id)) {
                    ie = true;
                    own++;
                }
                if (Entities.STATION_MINING.equals(id) || Entities.STATION_RESEARCH.equals(id) || Entities.ORBITAL_HABITAT.equals(id)) vanilla = true;
            }
            if (ie && vanilla) mixed++;
        }
        c.expect(own > 0, "IE stations in the sector (" + own + ")");
        c.expect(mixed == 0, "2501 no IE station next to a vanilla station (" + mixed + " systems)");

        Historian historian = Historian.getHistorian();
        StoryEntry warlords = historian.getSpecificStory("IE-desert-warlords-1");
        c.note("warlords planet types " + warlords.getOptionalPlanetTypes());
        Map<String, Integer> worlds = new HashMap<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar() || planet.getMarket() == null) continue;
                String type = planet.getTypeId();
                if (type.contains("desert") || type.contains("arid")) worlds.merge(type, 1, Integer::sum);
                if (!worlds.containsKey("picked " + type) && (type.equals("desert") || type.equals("desert1") || type.equals("arid"))
                        && !system.hasTag(Tags.THEME_HIDDEN) && !system.hasTag(Tags.THEME_SPECIAL) && !planet.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) {
                    worlds.put("picked " + type, 1);
                    boolean had = planet.getMarket().hasCondition(Conditions.RUINS_WIDESPREAD);
                    if (!had) planet.getMarket().addCondition(Conditions.RUINS_WIDESPREAD);
                    boolean told = false;
                    for (int i = 0; i < 200 && !told; i++) told = historian.getStoryForPlanet(planet) == warlords;
                    if (!had) planet.getMarket().removeCondition(Conditions.RUINS_WIDESPREAD);
                    c.expect(told, "2601 desert warlords story on " + type + " worlds");
                }
            }
        }
        c.note("desert and arid worlds " + worlds);
    }

    private static void minefieldRows(Ctx c) {
        PlanetAPI planet = Colony.planets(1).get(0);
        SectorEntityToken belt = MineBeltTerrainPlugin.addMineBelt(planet, 500f, 100f, 30f, 40f, "Check Minefield");
        StarSystemAPI system = planet.getStarSystem();
        MineBeltTerrainPlugin plugin = (MineBeltTerrainPlugin) ((com.fs.starfarer.api.campaign.CampaignTerrainAPI) belt).getPlugin();
        plugin.regenerateAsteroids();
        int[] sectors = new int[12];
        int mines = 0;
        for (SectorEntityToken entity : system.getCustomEntities()) {
            if (!"IndEvo_mine".equals(entity.getCustomEntityType()) || entity.getOrbitFocus() != belt) continue;
            float angle = Misc.getAngleInDegrees(belt.getLocation(), entity.getLocation());
            sectors[(int) (Misc.normalizeAngle(angle) / 30f) % 12]++;
            mines++;
        }
        int used = 0;
        int most = 0;
        for (int count : sectors) {
            if (count > 0) used++;
            most = Math.max(most, count);
        }
        c.note("mines " + mines + ", in " + used + " of 12 sectors, most in one " + most);
        c.expect(mines > 20, "mine belt filled");
        c.expect(used >= 10 && most <= mines * 0.35f, "2302 mines spread around the belt");
    }

    private static void ruinRows(Ctx c) {
        List<PlanetAPI> old = ruinPlanets();
        for (PlanetAPI planet : old) planet.getMarket().removeCondition(Ids.COND_RUINS);
        c.note("ruins in the save " + old.size());

        Set<String> remnant = new HashSet<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.getTags().contains(Tags.THEME_REMNANT)) continue;
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.getMarket() == null || planet.isStar()) continue;
                planet.getMarket().setSurveyLevel(MarketAPI.SurveyLevel.FULL);
                remnant.add(planet.getId());
            }
        }

        List<PlanetAPI> made = new ArrayList<>();
        for (int i = 0; i < 12 && made.isEmpty(); i++) {
            new SpawnRuins().runCommand("", BaseCommand.CommandContext.CAMPAIGN_MAP);
            made = ruinPlanets();
        }
        c.note("planets with spawned ruins " + made.size());
        c.expect(!made.isEmpty(), "indevo_spawnRuins places ruins");
        int doubled = 0;
        int noSpec = 0;
        for (PlanetAPI planet : made) {
            if (ruinsOn(planet.getMarket()) > 1) doubled++;
            if (!planet.getMarket().getMemoryWithoutUpdate().contains(indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY)) noSpec++;
        }
        c.expect(doubled == 0, "3501 ruins added once per planet");
        c.expect(noSpec == 0, "3502 surveyed planets get their forge type (" + noSpec + " without)");

        new SpawnRuins().runCommand("", BaseCommand.CommandContext.CAMPAIGN_MAP);
        c.expect(ruinPlanets().size() == made.size(), "indevo_spawnRuins stops when ruins exist");

        for (PlanetAPI planet : made) planet.getMarket().removeCondition(Ids.COND_RUINS);
        for (PlanetAPI planet : old) planet.getMarket().addCondition(Ids.COND_RUINS);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            // no battle here: the timers against the real engine's pause flag, the stale engine stays usable in the campaign
            CombatEngineAPI engine = Global.getCombatEngine();
            IndEvo_ForcedOverload overload = new IndEvo_ForcedOverload(null, 20f);
            ReflectionUtils.set("engine", overload, engine);
            IndEvo_CombatSlowFieldTerrain field = new IndEvo_CombatSlowFieldTerrain(20f, 1200f, new Vector2f());
            engine.setPaused(true);
            overload.advance(2f, new ArrayList<>());
            c.near(overload.elapsed, 0f, 0.001f, "3401 forced overload timer holds while paused");
            try {
                field.advance(2f, new ArrayList<>());
            } catch (RuntimeException e) {
                c.note("slow field draw outside combat " + e);
            }
            c.near(field.elapsed, 0f, 0.001f, "3401 slow field timer holds while paused");
            engine.setPaused(false);
            overload.advance(2f, new ArrayList<>());
            c.near(overload.elapsed, 2f, 0.001f, "forced overload timer runs unpaused");
        });
        steps.add(RestChecks::sectorRows);
        steps.add(RestChecks::minefieldRows);
        steps.add(RestChecks::ruinRows);

        steps.add(c -> {
            List<PlanetAPI> planets = Colony.planets(1);
            MarketAPI a = Colony.make(planets.get(0), 6);
            c.put("a", a.getId());
            a.addIndustry(Ids.PET_STORE);
            a.addIndustry(Ids.REQCENTER);
            a.addIndustry(Ids.RIFTGEN);
            a.reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            PetShop shop = (PetShop) a.getIndustry(Ids.PET_STORE);
            float upkeep = shop.getUpkeep().getModifiedValue();
            Pet pet = new Pet("slaghound", "Check");
            pet.storage = shop;
            shop.store(pet);
            PetStatusManager.getInstance().register(pet);
            a.reapplyIndustries();
            c.put("pet", pet);

            float mult = shop.getUpkeep().getMult();
            float cost = shop.getTotalStorageCost() * mult;
            c.expect(cost > 0f, "stored pet costs something");
            c.near(shop.getUpkeep().getModifiedValue() - upkeep, cost, 1f, "1002 player colony bills the listed fee as upkeep");
            c.expect(tooltip(shop).contains(Misc.getDGSCredits(cost)), "1002 tooltip shows that fee (" + Misc.getDGSCredits(cost) + ")");

            a.setPlayerOwned(false);
            a.reapplyIndustries();
            float fee = shop.getTotalStorageCost();
            c.expect(fee > PetShop.FLAT_MANAGEMENT_FEE, "npc colony adds the management fee");
            c.expect(tooltip(shop).contains(Misc.getDGSCredits(fee)), "1002 npc colony tooltip shows that fee (" + Misc.getDGSCredits(fee) + ")");
            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode node = report.getNode(report.getNode(report.getNode(BillingHelper.SERVICES_NODE), a.getId()), shop.getId() + "_storage");
            float before = node.upkeep;
            shop.reportEconomyTick((int) Global.getSettings().getFloat("economyIterPerMonth") - 1);
            c.near(node.upkeep - before, fee, 1f, "1002 npc colony bills the listed fee");
            node.upkeep = before;
            a.setPlayerOwned(true);

            shop.setAICoreId(Commodities.BETA_CORE);
            a.reapplyIndustries();
            TooltipMakerAPI core = Ui.tooltip();
            shop.addAICoreSection(core, Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP);
            String text = Ui.text(core);
            c.expect(text.contains("Removes tariffs") && text.contains("ageing in storage"), "1003 beta core text names both effects");
            c.put("age", pet.age);
            c.days(4f);
        });
        steps.add(c -> {
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            Pet pet = (Pet) c.get("pet");
            c.near(pet.age, (Float) c.get("age"), 0.01f, "1003 beta core stops pets ageing in storage");
            shop.setAICoreId(null);
            c.market("a").reapplyIndustries();
            c.days(4f);
        });
        steps.add(c -> {
            Pet pet = (Pet) c.get("pet");
            c.expect(pet.age > (Float) c.get("age") + 1f, "without the core pets age in storage (" + pet.age + ")");

            CargoAPI cargo = Global.getFactory().createCargo(true);
            cargo.addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, "no_such_pet"), 1);
            CargoStackAPI stack = cargo.getStacksCopy().get(0);
            PetItemPlugin box = (PetItemPlugin) stack.getPlugin();
            c.expect(box.isInvalid(), "pet box with an unknown id found");
            c.note("unknown box name " + box.getName());
            TooltipMakerAPI text = Ui.tooltip();
            box.createTooltip(text, false, null, null);
            c.expect(Ui.text(text).contains("Invalid item"), "unknown pet box tooltip shows the error");
            Global.getSector().getCampaignUI().showCoreUITab(com.fs.starfarer.api.campaign.CoreUITabId.CARGO);
            c.frames(30);
        });
        steps.add(c -> {
            CargoAPI cargo = Global.getFactory().createCargo(true);
            cargo.addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, "no_such_pet"), 1);
            PetItemPlugin box = (PetItemPlugin) cargo.getStacksCopy().get(0).getPlugin();
            cargo.addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, "hampter"), 1);
            c.note("in the cargo tab: " + box.isFleetCargo());
            c.expect(box.isFleetCargo(), "cargo tab open");
            c.expect(!box.hasRightClickAction(), "unknown pet box has no right click, nothing to crash on");
            c.key("Escape");
        });

        steps.add(c -> {
            c.expect(Global.getSector().getCampaignUI().getCurrentCoreTab() == null, "cargo tab closed by key");
            MarketAPI a = c.market("a");
            RequisitionCenter req = (RequisitionCenter) a.getIndustry(Ids.REQCENTER);
            c.expect(centre(req).contains("Weapon sourcing overview"), "1607 overview without an embassy");

            a.addIndustry(Ids.EMBASSY);
            a.reapplyIndustries();
            AmbassadorPersonManager.createAmbassador(a, true, null);
            c.expect(AmbassadorPersonManager.hasAmbassador(a), "ambassador in office");
            Embassy embassy = (Embassy) a.getIndustry(Ids.EMBASSY);
            embassy.alignedFaction = Global.getSector().getFaction(Factions.HEGEMONY);
            String player = Global.getSector().getPlayerFaction().getId();
            float standing = embassy.alignedFaction.getRelationship(player);
            embassy.alignedFaction.setRelationship(player, 0.5f);
            c.expect(centre(req).contains("of weapons from"), "1607 ambassador sourcing shows without a core");
            embassy.alignedFaction.setRelationship(player, -0.3f);
            c.expect(centre(req).contains("is not high enough"), "1607 low standing warning shows");
            req.setAICoreId(Commodities.GAMMA_CORE);
            c.expect(centre(req).contains("Weapon sourcing overview"), "1607 gamma core ignores the ambassador");
            req.setAICoreId(null);
            embassy.alignedFaction.setRelationship(player, standing);
            AmbassadorPersonManager.deleteAmbassador(a);
            a.removeIndustry(Ids.EMBASSY, null, false);
        });

        steps.add(c -> {
            MarketAPI a = c.market("a");
            RiftGenerator rift = (RiftGenerator) a.getIndustry(Ids.RIFTGEN);
            Vector2f center = Global.getSector().getStarSystem("Askonia").getLocation();
            List<StarSystemAPI> core = new ArrayList<>();
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system != a.getStarSystem() && Misc.getDistanceLY(center, system.getLocation()) <= 6f) core.add(system);
            }
            c.note("core systems " + core.size());
            StarSystemAPI last = core.remove(0);
            List<JumpPointAPI> marks = new ArrayList<>();
            for (StarSystemAPI system : core) {
                JumpPointAPI mark = Global.getFactory().createJumpPoint(PlanetMovingScript.ARTIFICIAL_RIFT_ID + "_check_" + system.getId(), "Check rift");
                system.addEntity(mark);
                marks.add(mark);
            }
            Object target = null;
            for (int i = 0; i < 40; i++) {
                target = call(MethodType.methodType(StarSystemAPI.class, RiftGenerator.TargetMode.class), RiftGenerator.class, "getTarget", rift, RiftGenerator.TargetMode.CORE);
            }
            c.note("target " + target + ", last core system " + last);
            c.expect(target != null, "604 rift generator picks a target with one core system left");
            for (JumpPointAPI mark : marks) mark.getContainingLocation().removeEntity(mark);
        });

        steps.add(c -> {
            StarSystemAPI system = c.market("a").getStarSystem();
            Colony.jump(system, 6000f, 0f);
            SectorEntityToken cache = system.addCustomEntity(null, null, Entities.WEAPONS_CACHE, Factions.NEUTRAL);
            cache.setLocation(6000f, 200f);
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(new Ui.Bare(), cache);
            c.frames(30);
        });
        steps.add(c -> {
            StarSystemAPI system = c.market("a").getStarSystem();
            FleetParamsV3 params = new FleetParamsV3(null, new Vector2f(), Factions.PIRATES, 1f, FleetTypes.PATROL_SMALL, 20f, 0f, 0f, 0f, 0f, 0f, 0f);
            params.ignoreMarketFleetSizeMult = true;
            params.withOfficers = false;
            CreditStashSpecial.CreditStashSpecialData data = new CreditStashSpecial.CreditStashSpecialData(10000f, params);
            data.prob = 1f;
            CreditStashSpecial special = new CreditStashSpecial();
            special.init(dialog(), data);
            float credits = player().getCargo().getCredits().get();
            special.optionSelected(null, CreditStashSpecial.OPEN);
            c.near(player().getCargo().getCredits().get() - credits, 10000f, 1f, "credit stash pays out");

            CampaignFleetAPI spacer = spacer(system);
            c.expect(spacer != null, "stash ambush fleet out");
            c.expect(spacer.getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_LOW_REP_IMPACT), "ambush fleet has the low rep flag");
            c.expect(!player().getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_LOW_REP_IMPACT), "3201 ambush leaves the player's rep penalties alone");

            player().getCargo().getCredits().set(100f);
            new IndEvo_AddSpacerFineMenuPoint().execute(null, dialog(), Collections.emptyList(), memory(spacer));
            c.expect(!Ui.button(dialog().getOptionPanel(), "Refund").isEnabled(), "3201 no payback without the credits");
            dialog().getOptionPanel().clearOptions();
            player().getCargo().getCredits().set(50000f);
            new IndEvo_AddSpacerFineMenuPoint().execute(null, dialog(), Collections.emptyList(), memory(spacer));
            c.expect(Ui.button(dialog().getOptionPanel(), "Refund").isEnabled(), "payback with the credits");
            dialog().setInteractionTarget(spacer);
        });
        steps.add(c -> {
            CampaignFleetAPI spacer = spacer(c.market("a").getStarSystem());
            new IndEvo_DeductSpacerCreditsAndSetNeutral().execute(null, dialog(), Collections.emptyList(), memory(spacer));
            c.near(player().getCargo().getCredits().get(), 38000f, 1f, "3201 payback takes the sum and 20%");
            c.expect(spacer.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE), "paid off fleet stands down");
            spacer.getContainingLocation().removeEntity(spacer);

            StarSystemAPI system = c.market("a").getStarSystem();
            ShipComponentLootManager.PartsCargoInterceptor listener = new ShipComponentLootManager.PartsCargoInterceptor();
            Set<Float> rolls = new HashSet<>();
            boolean same = true;
            for (int i = 0; i < 12; i++) {
                DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData("onslaught_Standard", ShipRecoverySpecial.ShipCondition.BATTERED), false);
                SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, params);
                dialog().setInteractionTarget(wreck);
                CargoAPI first = Global.getFactory().createCargo(true);
                listener.reportAboutToShowLootToPlayer(first, dialog());
                CargoAPI second = Global.getFactory().createCargo(true);
                listener.reportAboutToShowLootToPlayer(second, dialog());
                rolls.add(first.getCommodityQuantity(ItemIds.PARTS));
                same &= first.getCommodityQuantity(ItemIds.PARTS) == second.getCommodityQuantity(ItemIds.PARTS);
                system.removeEntity(wreck);
            }
            c.note("parts per wreck " + rolls);
            c.expect(rolls.size() > 1, "3003 ship parts vary per wreck");
            c.expect(same, "3003 one wreck rolls one amount");

            DerelictShipEntityPlugin.DerelictShipData last = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData("onslaught_Standard", ShipRecoverySpecial.ShipCondition.BATTERED), false);
            SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, last);
            dialog().setInteractionTarget(wreck);
            Colony.setting(Setting.SCRAPYARD, false);
            CargoAPI none = Global.getFactory().createCargo(true);
            listener.reportAboutToShowLootToPlayer(none, dialog());
            c.expect(none.getCommodityQuantity(ItemIds.PARTS) == 0f, "Salvage Yards switch applies on next use");
            Colony.setting(Setting.SCRAPYARD, true);
            system.removeEntity(wreck);
            c.keepDialog(false);
            dialog().dismiss();
        });

        steps.add(c -> {
            StarSystemAPI system = c.market("a").getStarSystem();
            CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Check detachment", true);
            for (int i = 0; i < 4; i++) fleet.getFleetData().addFleetMember(ship("buffalo_Standard"));
            system.addEntity(fleet);
            fleet.setLocation(7000f, 0f);
            float capacity = fleet.getCargo().getMaxCapacity();
            // goods wrecks never roll as loot, so the sums stay exact
            fleet.getCargo().addCommodity(Commodities.LOBSTER, capacity);
            fleet.getCargo().addCommodity(Commodities.ORGANS, 400f);
            fleet.getFleetData().takeSnapshot();
            List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
            fleet.getFleetData().removeFleetMember(members.get(0));
            fleet.getFleetData().removeFleetMember(members.get(1));
            fleet.getFleetData().removeFleetMember(members.get(2));

            new CombatAndDerelictionScript(fleet).reportBattleOccurred(fleet, null, null);
            float keptLobster = fleet.getCargo().getCommodityQuantity(Commodities.LOBSTER);
            float keptOrgans = fleet.getCargo().getCommodityQuantity(Commodities.ORGANS);
            float wreckLobster = 0f;
            float wreckOrgans = 0f;
            int count = 0;
            for (SectorEntityToken entity : system.getCustomEntities()) {
                if (!Entities.WRECK.equals(entity.getCustomEntityType()) || !entity.getMemoryWithoutUpdate().contains(CombatAndDerelictionScript.SHIP_VARIANT_KEY)) continue;
                count++;
                BaseSalvageSpecial.ExtraSalvage extra = BaseSalvageSpecial.getExtraSalvage(entity);
                if (extra == null) continue;
                wreckLobster += extra.cargo.getCommodityQuantity(Commodities.LOBSTER);
                wreckOrgans += extra.cargo.getCommodityQuantity(Commodities.ORGANS);
            }
            c.note("kept " + keptLobster + " and " + keptOrgans + ", wrecks " + wreckLobster + " and " + wreckOrgans);
            c.expect(count == 3, "three wrecks from three lost ships");
            c.near(keptLobster + wreckLobster, capacity, 1f, "2806 fleet and wrecks hold all of the first cargo");
            c.near(keptOrgans + wreckOrgans, 400f, 1f, "2806 fleet and wrecks hold all of the second cargo");
            c.expect(keptLobster + keptOrgans <= fleet.getCargo().getMaxCapacity() + 20f, "2806 fleet is back within its capacity");
            system.removeEntity(fleet);
        });

        steps.add(c -> {
            CargoAPI cargo = player().getCargo();
            c.put("supplies", cargo.getSupplies());
            cargo.removeSupplies(cargo.getSupplies());
            cargo.addSupplies(30f);
            StarSystemAPI system = c.market("a").getStarSystem();
            Colony.jump(system, 6000f, 0f);
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(new Ui.Bare(), c.market("a").getPrimaryEntity());
            c.frames(30);
        });
        steps.add(c -> {
            CampaignFleetAPI to = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Check detachment", true);
            for (int i = 0; i < 3; i++) to.getFleetData().addFleetMember(ship("onslaught_Standard"));
            final InteractionDialogAPI current = dialog();
            picker = new TwoFleetCargoPicker(new SplinterFleetDialoguePluginAPI() {
                @Override
                public void showBaseOptions() {
                }

                @Override
                public InteractionDialogAPI getDialog() {
                    return current;
                }
            }, player(), to, false, true, false);
            picker.init();
            c.frames(60);
        });
        steps.add(c -> {
            CargoAPI copy = (CargoAPI) ReflectionUtils.get("fromFleetCargoCopy", picker);
            c.near(30f - copy.getSupplies(), 15f, 1f, "2803 auto load takes half the supplies");
            player().getCargo().removeSupplies(player().getCargo().getSupplies());
            player().getCargo().addSupplies((Float) c.get("supplies"));
            c.shot("autoload");
            c.key("Escape");
        });
        steps.add(c -> {
            c.keepDialog(false);
            if (dialog() != null) dialog().dismiss();
        });
        return steps;
    }
}
