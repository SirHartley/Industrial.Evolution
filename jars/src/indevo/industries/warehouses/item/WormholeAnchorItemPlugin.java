package indevo.industries.warehouses.item;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.impl.items.WormholeAnchorPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WormholeAnchorItemPlugin extends WormholeAnchorPlugin {

    protected void addInstalledInSection(TooltipMakerAPI tooltip, float pad) {
        String list = "";
        String [] params = spec.getParams().split(",");
        String [] array = new String[params.length];
        int i = 0;
        for (String curr : params) {
            curr = curr.trim();
            IndustrySpecAPI ind = Global.getSettings().getIndustrySpec(curr);
            if (ind == null) continue;
            list += ind.getName() + ", ";
            array[i] = ind.getName();
            i++;
        }
        if (!list.isEmpty()) {
            list = list.substring(0, list.length() - 2);
            tooltip.addPara(list, pad,
                    Misc.getGrayColor(), Misc.getBasePlayerColor(), array);
            //Misc.getGrayColor(), Misc.getHighlightColor(), array);
            //Misc.getGrayColor(), Misc.getTextColor(), array);
        }
    }

    public static void addReqsSection(Industry industry, InstallableItemEffect effect, TooltipMakerAPI tooltip, boolean withRequiresText, float pad) {
        java.util.List<String> reqs = effect.getRequirements(industry);
        java.util.List<String> unmet = effect.getUnmetRequirements(industry);

        if (reqs == null) reqs = new ArrayList<String>();
        if (unmet == null) unmet = new ArrayList<String>();

        Color [] hl = new Color[reqs.size()];

        int i = 0;
        String list = "";
        for (String curr : reqs) {
            list += curr + ", ";

            if (unmet.contains(curr)) {
                hl[i] = Misc.getNegativeHighlightColor();
            } else {
                hl[i] = Misc.getBasePlayerColor();
                //hl[i] = Misc.getHighlightColor();
                //hl[i] = Misc.getTextColor();
            }
            i++;
        }
        if (!list.isEmpty()) {
            list = list.substring(0, list.length() - 2);
            list = Misc.ucFirst(list);
            reqs.set(0, Misc.ucFirst(reqs.get(0)));

            float bulletWidth = 70f;
            if (withRequiresText) {
                tooltip.setBulletWidth(bulletWidth);
                tooltip.setBulletColor(Misc.getGrayColor());
                tooltip.setBulletedListMode("Requires:");
            }

            LabelAPI label = tooltip.addPara(list, Misc.getGrayColor(), pad);
            label.setHighlightColors(hl);
            label.setHighlight(reqs.toArray(new String[0]));

            if (withRequiresText) {
                tooltip.setBulletedListMode(null);
            }
        }

    }

    public static void addSpecialNotesSection(Industry industry, InstallableItemEffect effect, TooltipMakerAPI tooltip, boolean withRequiresText, float pad) {
        String name = effect.getSpecialNotesName();
        if (name == null) return;

        List<String> reqs = effect.getSpecialNotes(industry);
        if (reqs == null) return;

        Color [] hl = new Color[reqs.size()];

        int i = 0;
        String list = "";
        for (String curr : reqs) {
            list += curr + ", ";
            hl[i] = Misc.getBasePlayerColor();
            i++;
        }
        if (!list.isEmpty()) {
            list = list.substring(0, list.length() - 2);
            list = Misc.ucFirst(list);
            reqs.set(0, Misc.ucFirst(reqs.get(0)));

            float bulletWidth = 70f;
            if (withRequiresText) {
                tooltip.setBulletWidth(bulletWidth);
                tooltip.setBulletColor(Misc.getGrayColor());
                tooltip.setBulletedListMode(name + ":");
            }

            LabelAPI label = tooltip.addPara(list, Misc.getGrayColor(), pad);
            label.setHighlightColors(hl);
            label.setHighlight(reqs.toArray(new String[0]));

            if (withRequiresText) {
                tooltip.setBulletedListMode(null);
            }
        }

    }

    protected transient boolean tooltipIsForPlanetSearch = false;
    public boolean isTooltipIsForPlanetSearch() {
        return tooltipIsForPlanetSearch;
    }
    public void setTooltipIsForPlanetSearch(boolean tooltipIsForPlanetSearch) {
        this.tooltipIsForPlanetSearch = tooltipIsForPlanetSearch;
    }


    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        //super.createTooltip(tooltip, expanded, transferHandler, stackSource, false);

        // doing this in core code instead where it catches all special items not just colony ones
//		if (!Global.CODEX_TOOLTIP_MODE) {
//			if (getSpec().hasTag(Items.TAG_COLONY_ITEM) || getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) {
//				SharedUnlockData.get().reportPlayerAwareOfSpecialItem(getId(), true);
//			}
//		}

        float pad = 0f;
        float opad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(getName());
        } else {
            tooltip.addSpacer(-opad);
        }

        LabelAPI design = null;

        if (!tooltipIsForPlanetSearch) {
            design = Misc.addDesignTypePara(tooltip, getDesignType(), opad);
        }

        float bulletWidth = 86f;
        if (design != null) {
            bulletWidth = design.computeTextWidth("Design type: ");
        }

        InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(getId());
        if (effect != null) {
            tooltip.setBulletWidth(bulletWidth);
            tooltip.setBulletColor(Misc.getGrayColor());

            tooltip.setBulletedListMode("Installed in:");
            addInstalledInSection(tooltip, opad);
            tooltip.setBulletedListMode("Requires:");
            addReqsSection(null, effect, tooltip, false, pad);
            if (effect.getSpecialNotesName() != null) {
                tooltip.setBulletedListMode(effect.getSpecialNotesName() + ":");
                addSpecialNotesSection(null, effect, tooltip, false, pad);
            }

            tooltip.setBulletedListMode(null);

            if (Global.CODEX_TOOLTIP_MODE) {
                tooltip.setParaSmallInsignia();
            }

            if (!tooltipIsForPlanetSearch) {
                if (!spec.getDesc().isEmpty()) {
                    Color c = Misc.getTextColor();
                    //if (useGray) c = Misc.getGrayColor();
                    tooltip.addPara(spec.getDesc(), c, opad);
                }
            }

            if (!tooltipIsForPlanetSearch) {
                effect.addItemDescription(null, tooltip, new SpecialItemData(getId(), null), InstallableIndustryItemPlugin.InstallableItemDescriptionMode.CARGO_TOOLTIP);
            }
        } else {
            if (!spec.getDesc().isEmpty() && !tooltipIsForPlanetSearch) {
                Color c = Misc.getTextColor();
                if (Global.CODEX_TOOLTIP_MODE) {
                    tooltip.setParaSmallInsignia();
                }
                tooltip.addPara(spec.getDesc(), c, opad);
            }
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }
}
