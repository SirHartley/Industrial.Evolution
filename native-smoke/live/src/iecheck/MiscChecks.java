package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import indevo.config.Setting;
import indevo.console.SpawnStation;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.dialogue.research.listeners.HyperspaceTopoProgressChecker;
import indevo.ids.Ids;
import indevo.industries.Supercomputer;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.privateer.industry.PrivateerBase;
import indevo.utils.helper.ReflectionUtils;
import lunalib.lunaSettings.LunaSettings;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

public final class MiscChecks {
    private static final String MOD = "IndEvo";
    private static CampaignAttackScript artillery = null;

    private MiscChecks() {
    }

    private static void save(Setting setting, Object value) throws Exception {
        lunalib.backend.ui.settings.LunaSettingsLoader.getSettings().get(MOD).put(setting.id, value);
        LunaSettings.reportSettingsChanged(MOD);
    }

    private static StarSystemAPI raidTarget(PrivateerBase base) throws Exception {
        java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.privateLookupIn(PrivateerBase.class, java.lang.invoke.MethodHandles.lookup())
                .findVirtual(PrivateerBase.class, "getRaidTarget", java.lang.invoke.MethodType.methodType(StarSystemAPI.class));
        try {
            return (StarSystemAPI) handle.invoke(base);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static CampaignAttackScript findArtillery(SectorEntityToken entity) {
        for (EveryFrameScript script : entity.getScripts()) {
            if (script instanceof CampaignAttackScript) return (CampaignAttackScript) script;
        }
        return null;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            c.expect(player.hasAbility(Ids.ABILITY_PET_MANAGEMENT), "pet ability on by default");
            save(Setting.PETS, false);
            c.expect(!player.hasAbility(Ids.ABILITY_PET_MANAGEMENT), "saving pets off removes the ability");
            save(Setting.PETS, true);
            c.expect(player.hasAbility(Ids.ABILITY_PET_MANAGEMENT), "saving pets on adds it back");

            save(Setting.METEOR_MIN_INTENSITY, 4.0);
            save(Setting.METEOR_MAX_INTENSITY, 2.0);
            c.near(Setting.METEOR_MAX_INTENSITY.value(), 4f, 0.001f, "max saved below min is raised to the min");
            c.near(LunaSettings.getDouble(MOD, Setting.METEOR_MAX_INTENSITY.id).floatValue(), 4f, 0.001f, "the menu value follows");
            save(Setting.METEOR_MIN_INTENSITY, 1.0);
            save(Setting.METEOR_MAX_INTENSITY, 3.0);

            save(Setting.SHIP_PICKER_ROW_COUNT, 0);
            c.expect(Setting.SHIP_PICKER_ROW_COUNT.intValue() == 1, "ship picker row count stops at 1 (" + Setting.SHIP_PICKER_ROW_COUNT.intValue() + ")");
            save(Setting.SHIP_PICKER_ROW_COUNT, 8);
            save(Setting.COURIER_BASE_FEE, 1500);
            c.expect(Setting.COURIER_BASE_FEE.intValue() == 1500, "courier base fee goes past its default (" + Setting.COURIER_BASE_FEE.intValue() + ")");
            save(Setting.COURIER_BASE_FEE, 1000);
            save(Setting.SY_PART_VALUE_MULT, 1.25);
            c.near(Setting.SY_PART_VALUE_MULT.value(), 1.25f, 0.0001f, "ship component value mult keeps decimals");
            save(Setting.SY_PART_VALUE_MULT, 1.2);

            List<PlanetAPI> planets = Colony.planets(2);
            StarSystemAPI system = planets.get(0).getStarSystem();
            SectorEntityToken station = null;
            for (SectorEntityToken entity : Global.getSector().getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION)) {
                if (findArtillery(entity) != null) {
                    station = entity;
                    system = entity.getStarSystem();
                    break;
                }
            }
            if (station == null) {
                c.note("no artillery station in this sector, placing one");
                ArtilleryStationPlacer.addArtilleryToPlanet(planets.get(0), false);
            }
            c.note("artillery system " + system.getName());
            c.put("system", system.getId());
            Colony.jump(system, 12000f, 0f);
            c.days(0.3f);
        });
        steps.add(c -> {
            StarSystemAPI system = Global.getSector().getStarSystem((String) c.get("system"));
            for (SectorEntityToken entity : system.getAllEntities()) {
                CampaignAttackScript script = findArtillery(entity);
                if (script != null) artillery = script;
            }
            c.expect(artillery != null, "artillery script found");
            c.expect(ReflectionUtils.get("terrain", artillery) != null, "artillery range terrain placed");

            save(Setting.ARTILLERY_MAX_RANGE, 8000.0);
            c.near(artillery.getRange(), 8000f, 1f, "range setting applies to a placed station without a restart");
            save(Setting.ARTILLERY_MIN_DELAY_BETWEEN_SHOTS, 5.0);
            save(Setting.ARTILLERY_MAX_DELAY_BETWEEN_SHOTS, 6.0);
            save(Setting.ENABLE_ARTILLERY, false);
            c.days(0.3f);
        });
        steps.add(c -> {
            c.expect(ReflectionUtils.get("terrain", artillery) == null, "artillery off removes the range terrain");
            save(Setting.ENABLE_ARTILLERY, true);
            c.days(0.3f);
        });
        steps.add(c -> {
            c.expect(ReflectionUtils.get("terrain", artillery) != null, "artillery on brings the range back");
            com.fs.starfarer.api.util.IntervalUtil interval = (com.fs.starfarer.api.util.IntervalUtil) ReflectionUtils.get("stationFireInterval", artillery);
            c.near(interval.getMinInterval(), 5f, 0.01f, "delay setting applies to a placed station");
            c.near(interval.getMaxInterval(), 6f, 0.01f, "max delay setting applies to a placed station");
        });
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI b = c.market("b");
            b.addIndustry(Ids.PIRATEHAVEN);
            b.reapplyIndustries();
            PrivateerBase base = (PrivateerBase) b.getIndustry(Ids.PIRATEHAVEN);
            for (String faction : new String[]{Factions.HEGEMONY, Factions.TRITACHYON, Factions.PERSEAN, Factions.DIKTAT, Factions.LUDDIC_CHURCH}) {
                Global.getSector().getFaction(faction).setRelationship(Factions.PLAYER, -1f);
            }
            int found = 0;
            for (int i = 0; i < 30; i++) {
                if (raidTarget(base) != null) found++;
            }
            c.expect(found == 30, "1101 privateer base always finds a target with 5 hostile factions (" + found + " of 30)");

            float plain = 0f;
            for (int i = 0; i < 4000; i++) plain += base.getBaseRaidFP();
            b.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult("ie_check", 1.5f);
            float boosted = 0f;
            for (int i = 0; i < 4000; i++) boosted += base.getBaseRaidFP();
            b.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult("ie_check");
            c.near(boosted / plain, 1.5f, 0.05f, "1104 raid strength follows the colony fleet size");

            ShippingContract contract = new ShippingContract();
            c.expect(!contract.getInvalidReason().isEmpty(), "501 invalid contract has a reason: " + contract.getInvalidReason());

            c.expect(Math.round(new Supercomputer().getCoreUseTimes("modded_core")) > 0, "1601 modded ai core lasts like a gamma core");

            Colony.jump(Global.getSector().getHyperspace(), 0f, 0f);
            BaseCommand.CommandResult hyper = new SpawnStation().runCommand("mining", BaseCommand.CommandContext.CAMPAIGN_MAP);
            c.expect(hyper != BaseCommand.CommandResult.SUCCESS, "3503 station spawn refused in hyperspace, no crash (" + hyper + ")");
            BaseCommand.CommandResult bad = new SpawnStation().runCommand("foo", BaseCommand.CommandContext.CAMPAIGN_MAP);
            c.expect(bad != BaseCommand.CommandResult.SUCCESS, "3504 unknown station type refused (" + bad + ")");

            c.expect(Global.getSector().getListenerManager().hasListenerOfClass(HyperspaceTopoProgressChecker.class), "3103 topography checker registered");
            c.reload();
        });
        steps.add(c -> {
            c.expect(Global.getSector().getListenerManager().hasListenerOfClass(HyperspaceTopoProgressChecker.class), "3103 topography checker registered after a reload");
            HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();
            c.note("topography intel " + intel);
            if (intel == null) intel = new HyperspaceTopographyEventIntel(null, false);
            intel.setProgress(100);
            c.days(4f);
        });
        steps.add(c -> {
            c.expect(Global.getSector().getMemoryWithoutUpdate().getBoolean(HyperspaceTopoProgressChecker.MEM_KEY), "3103 prospector notice fired after a reload");
            c.expect(!Global.getSector().getIntelManager().getIntel(GalatiaNewProjectsIntel.class).isEmpty(), "3103 galatia notice posted");
        });
        return steps;
    }
}
