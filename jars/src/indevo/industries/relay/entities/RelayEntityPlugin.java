package indevo.industries.relay.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;

public class RelayEntityPlugin extends BaseCampaignObjectivePlugin {

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        readResolve();
    }

    Object readResolve() {
        return this;
    }

    public void advance(float amount) {
        if (entity.getContainingLocation() == null || entity.isInHyperspace()) return;

        if (entity.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL)) return;

        // everything else is handled by the relay condition - it picks what relay to use and when to remove itself
        for (MarketAPI market : MiscIE.getMarketsInLocation(entity.getContainingLocation())) {
            CommRelayCondition mc = CommRelayCondition.get(market);
            if (mc == null) {
                market.addCondition(Conditions.COMM_RELAY);
                mc = CommRelayCondition.get(market);
            }
            if (mc != null) {
                mc.getRelays().add(entity);
            }
        }
    }


    protected boolean isMakeshift() {
        return entity.hasTag(Tags.MAKESHIFT);
    }

    public void printEffect(TooltipMakerAPI text, float pad) {
        int bonus = Math.abs(Math.round(
                CommRelayCondition.COMM_RELAY_BONUS));
        if (isMakeshift()) {
            bonus = Math.abs(Math.round(
                    CommRelayCondition.MAKESHIFT_COMM_RELAY_BONUS));
        }
        text.addPara(BaseIntelPlugin.INDENT + "%s stability for same-faction colonies in system",
                pad, Misc.getHighlightColor(), "+" + bonus);
    }

}