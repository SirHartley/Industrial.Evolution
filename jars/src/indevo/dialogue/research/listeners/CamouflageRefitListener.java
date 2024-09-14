package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.dialogue.research.hullmods.CamouflageFieldEmitter;

import java.util.List;

public class CamouflageRefitListener extends BaseCampaignEventListener {

    public CamouflageRefitListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        updateMembers(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
    }

    public static void register(){
        Global.getSector().addTransientListener(new CamouflageRefitListener(false));
    }


    public void updateMembers(List<FleetMemberAPI> members){
        for (FleetMemberAPI m : members){
            ShipVariantAPI variant = m.getVariant();

            if (variant.hasHullMod(CamouflageFieldEmitter.ID)){
                variant.removeTag(Tags.SHIP_UNIQUE_SIGNATURE);
                variant.addTag(CamouflageFieldEmitter.TAG_UNIQUE);
            } else if (variant.hasTag(CamouflageFieldEmitter.TAG_UNIQUE)){
                variant.addTag(Tags.SHIP_UNIQUE_SIGNATURE);
            }
        }
    }
}
