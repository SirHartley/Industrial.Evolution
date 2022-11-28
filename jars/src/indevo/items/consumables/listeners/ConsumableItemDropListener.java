package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import indevo.dialogue.research.DropDataCreator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConsumableItemDropListener implements ShowLootListener {
    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(ConsumableItemDropListener.class))
            manager.addListener(new ConsumableItemDropListener(), true);
    }

    public static final float FP_PER_ROLL = 100f;
    public static final float HARD_BATTLE_MULT = 1.2f;
    public static float MIN_FLEET_POINT_ADVANTAGE_FOR_HARD = 1.5f;
    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {

        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
            Random random = new Random(Misc.getSalvageSeed(fleet));

            if (Factions.PIRATES.equals(fleet.getFaction().getId()) || Factions.LUDDIC_PATH.equals(fleet.getFaction().getId()))
                return; //no parts if pirates are involved

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

            boolean isHard = totalFPbefore > (originalPlayerFP * MIN_FLEET_POINT_ADVANTAGE_FOR_HARD);
            float chances = (fpDestroyed / FP_PER_ROLL) * (isHard ? HARD_BATTLE_MULT : 1f);
            int flooredChances = (int) Math.floor(chances);

            List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>();
            dropRandom.add(DropDataCreator.createDropData("indEvo_consumables", flooredChances));
            loot.addAll(SalvageEntity.generateSalvage(random, 1f, 1f, 1f, 1f, null, dropRandom));
        }
    }
}
