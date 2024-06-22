package indevo.industries.changeling.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.MarineLossesStatModifier;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import indevo.industries.changeling.hullmods.Hellpods;

import java.util.List;

public class MarineLossAmplifcationHullmodEffectListener implements MarineLossesStatModifier {

    //when ships have the hellpods hullmod, amplify the marine loss
    //for every ship increase effectiveness by 5% and losses by 10%

    public static final float MARINE_LOSSES_MULT_PER_SHIP_PERCENT = 10f;

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(MarineLossAmplifcationHullmodEffectListener.class))
            manager.addListener(new MarineLossAmplifcationHullmodEffectListener(), true);
    }

    public void modifyMarineLossesStatPreRaid(MarketAPI market, List<GroundRaidObjectivePlugin> objectives, MutableStat stat) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        int amt = 0;
        for(FleetMemberAPI member : player.getFleetData().getMembersListCopy()){
            if(member.getVariant().hasHullMod(Hellpods.HULLMOD_ID)) amt++;
        }

        float mod = MARINE_LOSSES_MULT_PER_SHIP_PERCENT * amt;

        stat.modifyMult("helldiver_loss_mult", mod, "Hellpods (x" + amt + ")");
    }
}
