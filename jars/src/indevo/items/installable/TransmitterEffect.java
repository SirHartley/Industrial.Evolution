package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.relay.industry.MilitaryRelay;

import static indevo.items.installable.SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY;

public class TransmitterEffect extends BaseInstallableItemEffect {

    public TransmitterEffect(String id) {
        super(id);
    }

    @Override
    public void apply(Industry industry) {
        if (!(industry instanceof MilitaryRelay))
            Global.getSector().getMemoryWithoutUpdate().set(TRANSMITTER_UNLOCK_KEY, true);
    }

    @Override
    public void unapply(Industry industry) {
        if (!(industry instanceof MilitaryRelay) && !otherBaseHasTransmitter(industry))
            Global.getSector().getMemoryWithoutUpdate().unset(TRANSMITTER_UNLOCK_KEY);
    }

    private boolean otherBaseHasTransmitter(Industry industry) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (Industry ind : market.getIndustries()) {
                if (ind == industry || ind instanceof MilitaryRelay || !ind.isFunctional()) continue;
                if (ind.getSpecialItem() != null && ItemIds.TRANSMITTER.equals(ind.getSpecialItem().getId())) return true;
            }
        }

        return false;
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                          InstallableItemDescriptionMode mode, String pre, float pad) {
        if (industry == null) {
            text.addPara(pre + "In a Military Base / High Command: removes the Military Base construction requirement for Relays in the entire sector.\n" +
                            "In an Interstellar Relay: Improves the Comm Relay stability bonus to +2",
                    pad, Misc.getHighlightColor(), "without a military base", "stability bonus to +2");
        }

        if (industry instanceof MilitaryRelay) {
            text.addPara(pre + "Improves the Comm Relay into the Domain-Era equivalent",
                    pad, Misc.getHighlightColor(), "Improves the Comm Relay");
        } else if (industry instanceof MilitaryBase) {
            text.addPara(pre + "Removes the Military Base construction requirement for Relays in the entire sector.",
                    pad, Misc.getHighlightColor(), "without a military base");
        }
    }
}
