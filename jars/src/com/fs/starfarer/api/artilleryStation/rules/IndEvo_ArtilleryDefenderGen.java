package com.fs.starfarer.api.artilleryStation.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

import static com.fs.starfarer.api.artilleryStation.scripts.IndEvo_DerelictArtilleryStationScript.TYPE_KEY;

public class IndEvo_ArtilleryDefenderGen {

    public static CampaignFleetAPI getFleetForPlanet(SectorEntityToken planet){
        CampaignFleetAPI defenders = planet.getMemoryWithoutUpdate().getFleet("$defenderFleet");
        if (defenders != null) return defenders;
        else {
            defenders = getNewFleet(planet);
        }

        return defenders;
    }

    public static CampaignFleetAPI getNewFleet(SectorEntityToken planet){
        MarketAPI m = planet.getMarket();

        CampaignFleetAPI defenders = createDefenderFleet(m);

        //add station
        String variantId = null;

        try {
            JSONObject json = new JSONObject(getSpec(m).getData());
            variantId = json.getString("variant");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (variantId != null) defenders.getFleetData().addFleetMember(variantId);
        defenders.getFleetData().sort();

        return defenders;
    }

    public static String getType(MarketAPI market) {
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        return mem.getString(TYPE_KEY);
    }

    public static IndustrySpecAPI getSpec(MarketAPI market) {
        return Global.getSettings().getIndustrySpec("IndEvo_Artillery_" + getType(market));
    }

    public static final float MIN_FLEET_SIZE = 100f;
    public static final float MAX_HAZARD = 200f;

    public static CampaignFleetAPI createDefenderFleet(MarketAPI market){
        float defenderBonus = 1 + Math.max(1, (MAX_HAZARD - market.getHazardValue()) / 100f);

        long seed = market.getPrimaryEntity().getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED);
        Random random = Misc.getRandom(seed, 1);
        String factionId = IndEvo_ids.DERELICT;

        FleetParamsV3 fParams = new FleetParamsV3(null, null,
                factionId,
                1f,
                FleetTypes.PATROL_LARGE,
                (int) MIN_FLEET_SIZE * defenderBonus,
                0, 0, 0, 0, 0, 0);

        FactionAPI faction = Global.getSector().getFaction(factionId);

        fParams.withOfficers = faction.getCustomBoolean(Factions.CUSTOM_OFFICERS_ON_AUTOMATED_DEFENSES);
        fParams.random = random;

        CampaignFleetAPI defenders = FleetFactoryV3.createFleet(fParams);

        defenders.getInflater().setRemoveAfterInflating(false);
        defenders.setName("Automated Artillery Defenses");
        defenders.clearAbilities();

        return defenders;
    }
}
