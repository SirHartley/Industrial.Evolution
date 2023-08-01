package indevo.items.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.ids.Ids;
import indevo.ids.ItemIds;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ShipComponentLootManager {
    private static void log(String Text) {
        Global.getLogger(ShipComponentLootManager.class).info(Text);
    }

    public static float MIN_FLEET_POINT_ADVANTAGE_FOR_DROP = Global.getSettings().getFloat("IndEvo_relicComponentHardbattleFPAdvantage");
    public static float HARD_BATTLE_FP_TO_PARTS_RATION = 0.3f;
    public static float FP_DESTROYED_FRACTION = Global.getSettings().getFloat("IndEvo_relicComponentFPDestroyedFract");

    public static final Map<String, Float> RARE_PARTS_FACTIONS_AND_WEIGHTS = new HashMap<String, Float>() {{
        put(Factions.OMEGA, 0.5f);
        put(Factions.REMNANTS, 0.25f);
        put(Factions.DERELICT, 0.1f);
        put(Ids.DERELICT_FACTION_ID, 0.2f);
        put("rat_abyssals", 0.3f);
    }};

    public static class PartsLootAdder extends BaseCampaignEventListener {

        public PartsLootAdder(boolean permaRegister) {
            super(permaRegister);
        }

        public void reportFleetSpawned(CampaignFleetAPI fleet) {
            if (FleetUtils.isDetachmentFleet(fleet)) return;

            Random random = new Random(Misc.getSalvageSeed(fleet));
            String factionId = fleet.getFaction().getId();
            float fp = fleet.getFleetPoints();

            if (RARE_PARTS_FACTIONS_AND_WEIGHTS.containsKey(factionId)) {

                int amt = (int) Math.round(fp * RARE_PARTS_FACTIONS_AND_WEIGHTS.get(factionId) + (0.3 * random.nextFloat() * fp));
                fleet.getCargo().addCommodity(ItemIds.RARE_PARTS, amt);
            }

            if (Global.getSettings().getBoolean("IndEvo_PartsDropInCampaign") && Global.getSettings().getBoolean("ScrapYard")) {
                int amt = (int) Math.round(fp * 0.5f + (0.4 * random.nextFloat() * fp));
                fleet.getCargo().addCommodity(ItemIds.PARTS, amt);
            }
        }
    }

    public static class PartsCargoInterceptor implements ShowLootListener {

        @Override
        public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
            if (Global.getSettings().getBoolean("IndEvo_PartsDropInCampaign") && Global.getSettings().getBoolean("ScrapYard")) {
                if (Entities.WRECK.equals(dialog.getInteractionTarget().getCustomEntityType())) {
                    DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) dialog.getInteractionTarget().getCustomPlugin();
                    ShipRecoverySpecial.PerShipData shipData = plugin.getData().ship;
                    Random random = new Random(dialog.getInteractionTarget().getClass().hashCode());

                    if (shipData.getVariant().getHullSpec() != null) {
                        int fp = shipData.getVariant().getHullSpec().getFleetPoints();
                        int amt = (int) Math.round(fp * 0.3f + (0.2 * random.nextFloat() * fp));

                        loot.addCommodity(ItemIds.PARTS, amt);
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
                log("FP Qualification:" + (totalFPbefore > (originalPlayerFP * MIN_FLEET_POINT_ADVANTAGE_FOR_DROP)) + " destroyed qualification: " + (fpDestroyed > originalPlayerFP * 0.5f));

                if (totalFPbefore > (originalPlayerFP * MIN_FLEET_POINT_ADVANTAGE_FOR_DROP) && fpDestroyed > originalPlayerFP * FP_DESTROYED_FRACTION) {
                    loot.addCommodity(ItemIds.RARE_PARTS, fpDestroyed * HARD_BATTLE_FP_TO_PARTS_RATION);
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
            loot.addCommodity(ItemIds.RARE_PARTS, amt);
        }
    }
}
