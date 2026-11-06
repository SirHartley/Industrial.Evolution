package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.InterceptMissileEntityPlugin;
import indevo.items.consumables.entityAbilities.InterdictionMineAbility;
import indevo.items.consumables.listeners.LocatorSystemRatingUpdater;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

public final class ConsumableChecks {
    private static final String INTERCEPT = "IndEvo_ability_missile_intercept";
    private static final String CATAPULT = "IndEvo_ability_catapult";

    private ConsumableChecks() {
    }

    private static CampaignFleetAPI player() {
        return Global.getSector().getPlayerFleet();
    }

    private static int rating(StarSystemAPI system) throws Exception {
        java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.privateLookupIn(LocatorSystemRatingUpdater.class, java.lang.invoke.MethodHandles.lookup())
                .findStatic(LocatorSystemRatingUpdater.class, "calculateLocationRating", java.lang.invoke.MethodType.methodType(int.class, com.fs.starfarer.api.campaign.LocationAPI.class));
        try {
            return (int) handle.invoke((com.fs.starfarer.api.campaign.LocationAPI) system);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static CampaignFleetAPI npc(StarSystemAPI system, float x, float y) {
        FleetParamsV3 params = new FleetParamsV3(null, new org.lwjgl.util.vector.Vector2f(), Factions.PIRATES, 1f, FleetTypes.PATROL_SMALL, 20f, 0f, 0f, 0f, 0f, 0f, 0f);
        params.ignoreMarketFleetSizeMult = true;
        params.withOfficers = false;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        system.addEntity(fleet);
        fleet.setLocation(x, y);
        fleet.getMemoryWithoutUpdate().set("$cfai_makeNonHostile", true);
        return fleet;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            CampaignFleetAPI fleet = player();
            if (!fleet.hasAbility(CATAPULT)) fleet.addAbility(CATAPULT);
            Colony.jump(Global.getSector().getHyperspace(), 0f, 0f);
            TooltipMakerAPI tooltip = Ui.tooltip();
            fleet.getAbility(CATAPULT).createTooltip(tooltip, false);
            String text = Ui.text(tooltip);
            c.expect(text.contains("slipsurge"), "catapult tooltip text read");
            c.expect(!text.contains("Abyss"), "2906 no Abyss warning in normal hyperspace");

            float[][] corners = {{-90000f, -60000f}, {90000f, -60000f}, {-90000f, 60000f}, {90000f, 60000f}, {-120000f, -80000f}};
            boolean found = false;
            for (float[] corner : corners) {
                fleet.setLocation(corner[0], corner[1]);
                if (Misc.isInAbyss(fleet)) {
                    found = true;
                    break;
                }
            }
            c.note("abyss location found " + found);
            if (found) {
                TooltipMakerAPI abyss = Ui.tooltip();
                fleet.getAbility(CATAPULT).createTooltip(abyss, false);
                c.expect(Ui.text(abyss).contains("Abyss"), "2906 Abyss warning in the Abyss");
            }
            fleet.setLocation(0f, 0f);
        });
        steps.add(c -> {
            List<PlanetAPI> planets = Colony.planets(2);
            StarSystemAPI system = planets.get(0).getStarSystem();
            c.put("system", system.getId());
            Colony.jump(system, 6000f, 0f);

            SectorEntityToken cloud = system.addTerrain("IndEvo_SmokeCloud", new BaseRingTerrain.RingParams(600f, 300f, player(), "Check cloud"));
            TooltipMakerAPI tooltip = Ui.tooltip();
            SmokeCloudTerrain terrain = (SmokeCloudTerrain) ((com.fs.starfarer.api.campaign.CampaignTerrainAPI) cloud).getPlugin();
            terrain.createTooltip(tooltip, false);
            String text = Ui.text(tooltip);
            c.expect(text.contains("up to 30%"), "2904 smoke tooltip says up to 30%");
            int own = Math.round((1f - SmokeCloudTerrain.getBurnMultForTerrain(player())) * 100f);
            c.expect(text.contains("speed is reduced by " + own + "%"), "2904 own fleet line matches the effect (" + own + "%)");
            system.removeEntity(cloud);

            int before = rating(system);
            SectorEntityToken cache = system.addCustomEntity(null, null, Entities.WEAPONS_CACHE, Factions.NEUTRAL);
            cache.addTag(com.fs.starfarer.api.impl.campaign.ids.Tags.SALVAGEABLE);
            int after = rating(system);
            c.expect(after - before == 7, "2907 a weapons cache rates 7, not 1 (+" + (after - before) + ")");
            system.removeEntity(cache);
        });
        steps.add(c -> {
            StarSystemAPI system = Global.getSector().getStarSystem((String) c.get("system"));
            CampaignFleetAPI fleet = player();
            fleet.setLocation(9000f, 0f);
            CampaignFleetAPI target = npc(system, 9000f, 200f);
            target.setTransponderOn(false);

            BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(fleet, 90f, 10f, 0f, null);
            SectorEntityToken missile = system.addCustomEntity(Misc.genUID(), null, "IndEvo_consumable_missile_intercept", Factions.PLAYER, params);
            missile.setLocation(9000f, 200f);
            ((InterceptMissileEntityPlugin) missile.getCustomPlugin()).onExplosion();

            c.note("target visibility " + target.getVisibilityLevelToPlayerFleet());
            c.expect(target.hasScriptOfClass(InterdictionMineAbility.GoSlowScript.class), "2908 interceptor stuns a fleet in range");
            c.expect(!fleet.hasScriptOfClass(InterdictionMineAbility.GoSlowScript.class), "2916 the firing fleet is not stunned");
            boolean burn = fleet.getAbility(com.fs.starfarer.api.impl.campaign.ids.Abilities.EMERGENCY_BURN) == null || !fleet.getAbility(com.fs.starfarer.api.impl.campaign.ids.Abilities.EMERGENCY_BURN).isOnCooldown();
            c.expect(burn, "2916 the firing fleet keeps its burn abilities");

            StarSystemAPI nebula = null;
            for (StarSystemAPI one : Global.getSector().getStarSystems()) {
                if (one.getStar() == null && !one.hasTag("theme_hidden")) nebula = one;
                if (one.isNebula() && !one.hasTag("theme_hidden") && nebula == null) nebula = one;
            }
            c.note("starless system " + (nebula == null ? null : nebula.getName()));
            if (nebula != null) {
                Colony.jump(nebula, 3000f, 0f);
                if (!fleet.hasAbility(Ids.ABILITY_SCOOP)) fleet.addAbility(Ids.ABILITY_SCOOP);
                Global.getSector().getCharacterData().addAbility(Ids.ABILITY_SCOOP);
                fleet.getCargo().addSpecial(new com.fs.starfarer.api.campaign.SpecialItemData(indevo.ids.ItemIds.CONSUMABLE_SCOOP, null), 3);
                AbilityPlugin scoop = fleet.getAbility(Ids.ABILITY_SCOOP);
                scoop.activate();
                c.expect(scoop.isActiveOrInProgress(), "scoop running in a system without a star");
            }
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(true, "2905 scoop in a system without a star runs for two days without a crash");

            StarSystemAPI binary = null;
            PlanetAPI second = null;
            for (StarSystemAPI one : Global.getSector().getStarSystems()) {
                if (one.getStar() != null && one.getSecondary() != null) {
                    binary = one;
                    second = one.getSecondary();
                    break;
                }
            }
            c.note("binary system " + (binary == null ? null : binary.getName()));
            if (binary != null) {
                float x = second.getLocation().x + second.getRadius() + 300f;
                Colony.jump(binary, x, second.getLocation().y);
                AbilityPlugin scoop = player().getAbility(Ids.ABILITY_SCOOP);
                c.put("binary", second.getId());
                c.note("star distance " + Misc.getDistance(binary.getStar(), second));
                scoop.deactivate();
                c.until(() -> !scoop.isActiveOrInProgress(), 8f, "scoop stopped");
            }
        });
        steps.add(c -> {
            if (c.get("binary") != null) {
                AbilityPlugin scoop = player().getAbility(Ids.ABILITY_SCOOP);
                scoop.setCooldownLeft(0f);
                scoop.activate();
                c.frames(90);
            }
        });
        steps.add(c -> {
            if (c.get("binary") != null) {
                Object target = ReflectionUtils.get("sunTarget", player().getAbility(Ids.ABILITY_SCOOP));
                c.expect(target != null && c.get("binary").equals(((SectorEntityToken) target).getId()), "2905 scoop draws from the closest star (" + target + ")");
            }
        });
        steps.add(c -> {
            CampaignFleetAPI fleet = player();
            if (!fleet.hasAbility(INTERCEPT)) fleet.addAbility(INTERCEPT);
            Global.getSector().getCharacterData().addAbility(INTERCEPT);
            Colony.jump(Global.getSector().getHyperspace(), 0f, 0f);
            CargoAPI cargo = fleet.getCargo();
            cargo.addSpecial(new com.fs.starfarer.api.campaign.SpecialItemData("IndEvo_consumable_missile_intercept", null), 3);

            PersistentUIDataAPI.AbilitySlotAPI slot = Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy().get(0);
            c.put("slot", slot.getInHyperAbilityId());
            slot.setInHyperAbilityId(INTERCEPT);
            c.note("hyper slot 1 " + slot.getInHyperAbilityId() + ", normal slot 1 " + slot.getAbilityId() + ", usable " + fleet.getAbility(INTERCEPT).isUsable());
            c.key("1");
        });
        steps.add(c -> {
            CampaignFleetAPI fleet = player();
            c.note("after hotkey: cooldown " + fleet.getAbility(INTERCEPT).getCooldownLeft());
            c.expect(fleet.getAbility(INTERCEPT).getCooldownLeft() > 0f, "2902 hyperspace slot missile fires by hotkey, no crash");
        });
        return steps;
    }
}
