package com.fs.starfarer.api.plugins.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_RessourceCondition;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.splinterFleet.plugins.FleetUtils;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.util.Random;

public class IndEvo_PartsManager {
    private static void log(String Text) {
        Global.getLogger(IndEvo_PartsManager.class).info(Text);
    }

    public static float MIN_FLEET_POINT_ADVANTAGE_FOR_DROP = 1.5f;
    public static float HARD_BATTLE_FP_TO_PARTS_RATION = 0.3f;

    public static class PartsLootAdder extends BaseCampaignEventListener {

        public PartsLootAdder(boolean permaRegister) {
            super(permaRegister);
        }

        public void reportFleetSpawned(CampaignFleetAPI fleet) {
            if (FleetUtils.isDetachmentFleet(fleet)) return;

            Random random = new Random(Misc.getSalvageSeed(fleet));
            String factionId = fleet.getFaction().getId();
            float fp = fleet.getFleetPoints();

            if (Factions.OMEGA.equals(factionId) || Factions.REMNANTS.equals(factionId) || Factions.DERELICT.equals(factionId)) {
                int amt = (int) Math.round(fp * 0.2f + (0.3 * random.nextFloat() * fp));
                fleet.getCargo().addCommodity(IndEvo_Items.RARE_PARTS, amt);
            }

            if(Global.getSettings().getBoolean("IndEvo_PartsDropInCampaign") && Global.getSettings().getBoolean("ScrapYard")){
                int amt = (int) Math.round(fp * 0.5f + (0.4 * random.nextFloat() * fp));
                fleet.getCargo().addCommodity(IndEvo_Items.PARTS, amt);
            }
        }
    }

    public static class PartsCargoInterceptor implements ShowLootListener {

        @Override
        public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
            if(Global.getSettings().getBoolean("IndEvo_PartsDropInCampaign") && Global.getSettings().getBoolean("ScrapYard")){
                if (Entities.WRECK.equals(dialog.getInteractionTarget().getCustomEntityType())) {
                    DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) dialog.getInteractionTarget().getCustomPlugin();
                    ShipRecoverySpecial.PerShipData shipData = plugin.getData().ship;
                    Random random = new Random(dialog.getInteractionTarget().getClass().hashCode());

                    if (shipData.getVariant().getHullSpec() != null) {
                        int fp = shipData.getVariant().getHullSpec().getFleetPoints();
                        int amt = (int) Math.round(fp * 0.3f + (0.2 * random.nextFloat() * fp));

                        loot.addCommodity(IndEvo_Items.PARTS, amt);
                    }
                }
            }

            if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
                CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
                if (Factions.PIRATES.equals(fleet.getFaction().getId())) return; //no parts if pirates are involved

                float totalFPbefore = 0;
                float fpDestroyed = Misc.getSnapshotFPLost(fleet);

                for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
                    totalFPbefore += member.getFleetPointCost();
                }

                //check if it was a hard battle
                int originalPlayerFP = 0;
                for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getSnapshot()) {
                    originalPlayerFP += member.getFleetPointCost();
                }

                log("Before Player: " + originalPlayerFP + " before enemy: " + totalFPbefore + " destroyed: " + fpDestroyed);
                log("FP Qualification:" + (totalFPbefore > (originalPlayerFP * MIN_FLEET_POINT_ADVANTAGE_FOR_DROP)) + " destroyed qualification: " + (fpDestroyed > originalPlayerFP*0.5f));

                if (totalFPbefore > (originalPlayerFP * MIN_FLEET_POINT_ADVANTAGE_FOR_DROP) && fpDestroyed > originalPlayerFP*0.5f) {
                    loot.addCommodity(IndEvo_Items.RARE_PARTS, fpDestroyed * HARD_BATTLE_FP_TO_PARTS_RATION);
                }
            }

            SectorEntityToken target = dialog.getInteractionTarget();
            if (target.getCustomEntitySpec() == null) return;

            Pair<Integer, Integer> toAdd = new Pair<>();

            switch (target.getCustomEntitySpec().getId()) {
                case Entities.DERELICT_SURVEY_PROBE:
                    toAdd.one = 1;
                    toAdd.two = 10;
                    break;
                case Entities.DERELICT_SURVEY_SHIP:
                    toAdd.one = 20;
                    toAdd.two = 200;
                    break;
                case Entities.DERELICT_MOTHERSHIP:
                    toAdd.one = 150;
                    toAdd.two = 400;
                    break;
                case Entities.DERELICT_CRYOSLEEPER:
                    toAdd.one = 250;
                    toAdd.two = 500;
                    break;
                default:
                    return;
            }

            int amt = toAdd.one + new Random(Misc.getSalvageSeed(target)).nextInt(toAdd.two - toAdd.one + 1);
            loot.addCommodity(IndEvo_Items.RARE_PARTS, amt);
        }

        /*// TODO proper drop group impl

        SectorEntityToken entity = ...;

        *//* Adding to dropRandom: a custom drop group (not defined in the csv) *//*
        DropData d = new DropData();
        d.chances = 5;
        d.addCustom("item_:{tags:[single_bp], p:{tags:[rare_bp]}}", 1f);
        d.addCustom("item_industry_bp:planetaryshield", 1f);
        entity.addDropRandom(d);

        *//* Adding 1000 chances at "ai_cores3" to dropRandom *//*
        d = new DropData();
        d.chances = 1000;
        d.group = "ai_cores3";
        entity.addDropRandom(d);

        *//* A custom group with a 50% chance to drop a Synchrotron *//*
        d = new DropData();
        d.chances = 100;
        d.addCustom("item_synchrotron", 1f);
        d.addNothing(1f);
        entity.addDropRandom(d);

        *//* 5000 to 15000 worth of food and organics *//*
         *//* A 2-1 ratio of units of food to units of organics *//*
        d = new DropData();
        d.value = 10000;
        d.addCommodity(Commodities.FOOD, 10f);
        d.addCommodity(Commodities.ORGANICS, 5f);
        entity.addDropValue(d);*/
    }

    public static class RessCondApplicator implements PlayerColonizationListener, EconomyTickListener {

        @Override
        public void reportPlayerColonizedPlanet(PlanetAPI planetAPI) {
            MarketAPI m = planetAPI.getMarket();
            IndEvo_RessourceCondition.applyRessourceCond(m);
        }

        @Override
        public void reportPlayerAbandonedColony(MarketAPI marketAPI) {

        }

        @Override
        public void reportEconomyTick(int i) {
            applyRessourceCondToAllMarkets();
        }

        @Override
        public void reportEconomyMonthEnd() {

        }
    }

    public static void applyRessourceCondToAllMarkets() {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            IndEvo_RessourceCondition.applyRessourceCond(m);
        }
    }
}
