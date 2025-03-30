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
import indevo.utils.helper.Settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ShipComponentLootManager {
    private static void log(String Text) {
        Global.getLogger(ShipComponentLootManager.class).info(Text);
    }

    public static float MIN_FLEET_POINT_ADVANTAGE_FOR_DROP = Settings.getFloat(Settings.RELIC_COMPONENT_HARD_BATTLE_FP_ADVANTAGE);
    public static float HARD_BATTLE_FP_TO_PARTS_RATION = 0.3f;
    public static float FP_DESTROYED_FRACTION = Settings.getFloat(Settings.RELIC_COMPONENT_FP_DESTROYED_FRACT);

    public static final Map<String, Float> RARE_PARTS_FACTIONS_AND_WEIGHTS = new HashMap<String, Float>() {{
        put(Factions.OMEGA, 0.5f);
        put(Factions.REMNANTS, 0.25f);
        put(Factions.DERELICT, 0.1f);
        put(Ids.DERELICT_FACTION_ID, 0.2f);
        put("rat_abyssals", 0.3f);
        put("zea_dawn", 0.3f);
        put("zea_dusk", 0.3f);
        put("zea_elysians", 0.3f);
        put("threat", 0.1f);
    }};

    public static final float DEFAULT_AI_SHIP_RARE_PARTS_DROP_DP_FRACTION = 0.2f;

    public static class PartsLootAdder extends BaseCampaignEventListener {

        public PartsLootAdder(boolean permaRegister) {
            super(permaRegister);
        }

        public static void register(){
            Global.getSector().addTransientListener(new PartsLootAdder(false));
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

            if (Settings.getBoolean(Settings.PARTS_DROP_IN_CAMPAIGN) && Settings.getBoolean(Settings.SCRAPYARD)) {
                int amt = (int) Math.round(fp * 0.5f + (0.4 * random.nextFloat() * fp));
                fleet.getCargo().addCommodity(ItemIds.PARTS, amt);
            }
        }
    }

    public static class PartsCargoInterceptor implements ShowLootListener {
        public static void register(){
            Global.getSector().getListenerManager().addListener(new PartsCargoInterceptor(), true);
        }

        @Override
        public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
            if (Settings.getBoolean(Settings.PARTS_DROP_IN_CAMPAIGN) && Settings.getBoolean(Settings.SCRAPYARD)) {
                if (Entities.WRECK.equals(dialog.getInteractionTarget().getCustomEntityType())) {
                    DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) dialog.getInteractionTarget().getCustomPlugin();
                    ShipRecoverySpecial.PerShipData shipData = plugin.getData().ship;
                    Random random = new Random(dialog.getInteractionTarget().getClass().hashCode());

                    if (shipData != null && shipData.getVariant() != null && shipData.getVariant().getHullSpec() != null) {
                        int fp = shipData.getVariant().getHullSpec().getFleetPoints();
                        int amt = (int) Math.round(fp * 0.3f + (0.2 * random.nextFloat() * fp));

                        loot.addCommodity(ItemIds.PARTS, amt);
                    }
                }
            }
            
            if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
                CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
                if (Factions.PIRATES.equals(fleet.getFaction().getId()) || Factions.LUDDIC_PATH.equals(fleet.getFaction().getId())) return; //no parts if pirates are involved

                //relic component drop on difficult battle
                calculateAndAddPartsOnDifficultBattle(fleet, loot);

                //relic component drop on automated ship defeat if said fleet is not covered by default behavior
                if (!RARE_PARTS_FACTIONS_AND_WEIGHTS.containsKey(fleet.getFaction().getId())){
                    float destroyedAiFp = getDestroyedAiFp(fleet);
                    int amt = (int) Math.round(destroyedAiFp * DEFAULT_AI_SHIP_RARE_PARTS_DROP_DP_FRACTION + (0.15 * new Random().nextFloat() * destroyedAiFp));
                    loot.addCommodity(ItemIds.RARE_PARTS, amt);
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

        private static float getDestroyedAiFp(CampaignFleetAPI fleet) {
            float destroyedAiFp = 0f;
            List<FleetMemberAPI> currentMembers = fleet.getFleetData().getMembersListCopy();

            for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
                if (currentMembers.contains(member)) continue;
                if (Misc.isAutomated(member)) destroyedAiFp += member.getVariant().getHullSpec().getFleetPoints();
            }
            return destroyedAiFp;
        }

        public void calculateAndAddPartsOnDifficultBattle(CampaignFleetAPI fleet, CargoAPI loot){
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
    }
}
