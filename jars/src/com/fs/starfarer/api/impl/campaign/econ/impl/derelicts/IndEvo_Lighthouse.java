package com.fs.starfarer.api.impl.campaign.econ.impl.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;


/*
* Ancient Beacon
Effect:	Does not work without AI Core (Start with phase 1/2/3 depending on AI core??)
    Feed AI Cores to increase the effect

    change out all hyperspace terrain in range to a custom one, allow player setting the terrain to storm more/less
        Change colour to yellow, gradually getting more blue to farther away from the beacon
        Make Remnant not affected by hypersace terrain

	Once repaired, description says something like "Pulses with a warm, orange light, seemingly from every direction. You are safe now."
	Effects extend to all colonies a certain light-year amount away, transcends hyperspace, more are unlocked by better AI core
		Good:	Clears hyperspace around the world
			Increases pop growth,
			* stability and
			* base colony income (pop&infra)
			Increases the effects of AI-core Administrator skills
			Increases burn speed in hyperspace by 1
			*
		Bad:	Phase one:	Minor remnant presence spawns in systems on the influence fringe
				Minor raids on systems on influence fringe, no notifications!
			Win condition: 	Stop if player shuts down beacon

			Phase two:	Spawn Remnant in hyperspace in range
				All remnant fleet in the range of the beacon get increased officer count
				Start relentlessly Saturation bombing all Systems in Range, do not target beacon system

				No notifications:
				If alpha cores are in storage/industries anywhere in range, place them as administrators
				Spawn new remnant stations in unexplored systems in range, Spawn market count /3 (tenative) "Special" stations
				Remove ability to shut down beacon
			Win condition:	Destroy the 3 stations
				or sat bomb the planet with the beacon yourself (Add "Military options" option to menu)

			Phase 3:	Turn over all colonies in range with AI admin to remnant, spawn remnant stations on all of them
				Start bombing runs on everything in the sector, prioritize close
				Force AI faction to react with runs of their own- once a bombing run on a remnant owned world is successful (twice??), turn the market over to the faction (cleanse)
				Don't target Tri-tach
				Try to build more beacons on worlds that fit the bill
			Win condition: 	destroy remnant super station orbiting the initial planet, and clean out all the remaining worlds


AI core effect:	All AI-Cores increase remnant build-up
Gamma	Increases stability bonus to 4
Beta	Increases all in-faction exports by 1
Alpha	Increases all population growth by 20
*/
public class IndEvo_Lighthouse extends BaseIndustry {
    boolean debug = false;

    @Override
    public void init(String id, MarketAPI market) {
        super.init(id, market);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
    }

    @Override
    public IndustrySpecAPI getSpec() {
        if (spec == null) spec = Global.getSettings().getIndustrySpec(id);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
        return spec;
    }

    @Override
    public void apply() {
        super.apply(true);

        debug = Global.getSettings().isDevMode();

        //needed so it doesn't display a downgrade option
        if (market.hasIndustry(getId())) spec.setDowngrade(null);

        Global.getSector().getListenerManager().addListener(this, true);

    }

    private void transformHyperspaceTerrainInRange(float range) {
        StringBuilder stringBuilder = new StringBuilder();

        for (CampaignTerrainAPI terrainAPI : Global.getSector().getHyperspace().getTerrainCopy()) {
            if (Misc.getDistanceLY(terrainAPI, market.getPrimaryEntity()) > range) continue;


            CampaignTerrainPlugin ctp = terrainAPI.getPlugin();
            if (ctp instanceof HyperspaceTerrainPlugin) {

                BaseTiledTerrain.TileParams tp = ((HyperspaceTerrainPlugin) ctp).getParams();
                tp.key = "IndEvo_deep_hyperspace_far";

                /*for (int i = 0; i < tp.w; i++) {
                    stringBuilder.append("x");
                }

                tp.tiles = stringBuilder.toString();

                ctp.init(ctp.getTerrainId(), ((HyperspaceTerrainPlugin) ctp).getEntity(), tp);*/
            }
        }

        //flickerTexture = Global.getSettings().getSprite(params.cat, params.key + "_glow");
    }

    @Override
    public void unapply() {
        super.unapply();

        //needed so it doesn't display as buildable
        spec.setDowngrade(IndEvo_ids.RUINS);

        Global.getSector().getListenerManager().removeListener(this);
    }

    private void applyColonyBonuses(Float mult, MarketAPI market) {

        //add the beacon condition to the eligible friendly colonies, set beaconMult for each
        /*
         * Increases pop growth,
         * stability and
         * base colony income (pop&infra)
         * and ground defence
         * Increases the effects of AI-core Administrator skills*/

    }

    private void applyFleetBonuses() {
        /*		Increases burn speed in hyperspace by 1, once ai deposit threshhold is reached, increase bonus to 2
                   decreases cr deg due to hyperstorms
         *
         * */
        Global.getSector().getPlayerFleet().getStats().getDynamic().getMod(Stats.CORONA_EFFECT_MULT).modifyFlat(getModId(), 0.2f, getNameForModifier());
        Global.getSector().getPlayerFleet().getStats().getDynamic().getMod(Stats.FLEET_BURN_BONUS).modifyFlat(getModId(), 1, getNameForModifier());
    }

    @Override
    public boolean showShutDown() {
        return super.showShutDown();

        //set to false to make unremovable
    }

    @Override
    public boolean isAvailableToBuild() {
        if (market.hasIndustry(IndEvo_ids.RUINS) && (market.getIndustry(IndEvo_ids.RUINS).isUpgrading())) return false;


        return super.isAvailableToBuild();
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        spec.setDowngrade(IndEvo_ids.RUINS);
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
