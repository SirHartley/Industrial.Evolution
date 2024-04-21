package indevo.industries.privateer.intel;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import indevo.industries.changeling.industry.population.HelldiversSubIndustry;
import indevo.industries.changeling.industry.population.SwitchablePopulation;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class PrivateerBaseRaidIntel extends RaidIntel {

    public PrivateerBaseRaidIntel(StarSystemAPI system, FactionAPI faction, RaidDelegate delegate) {
        super(system, faction, delegate);
    }

    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
        if (random == null) random = new Random();

        RouteManager.OptionalFleetData extra = route.getExtra();

        float combat = extra.fp;
        float tanker = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float transport = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float freighter = 0f;
        combat -= tanker;
        combat -= transport;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                locInHyper,
                factionId,
                route == null ? null : route.getQualityOverride(),
                extra.fleetType,
                combat, // combatPts
                freighter, // freighterPts
                tanker, // tankerPts
                transport, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod, won't get used since routes mostly have quality override set
        );
        //params.ignoreMarketFleetSizeMult = true; // already accounted for in extra.fp
//		if (DebugFlags.RAID_DEBUG) {
//			params.qualityOverride = 1f;
//		}
        if (route != null) {
            params.timestamp = route.getTimestamp();
        }
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);

        if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = Ranks.SPACE_COMMANDER;

        fleet.getCommander().setPostId(postId);
        fleet.getCommander().setRankId(rankId);

        if (market.getIndustry(Industries.POPULATION) instanceof SwitchablePopulation && ((SwitchablePopulation) market.getIndustry(Industries.POPULATION)).getCurrent() instanceof HelldiversSubIndustry) fleet.setName("Democratic Liberation Force");
        else fleet.setName(faction.getFleetTypeName(FleetTypes.MERC_PRIVATEER));

        return fleet;
    }
}
