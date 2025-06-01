package indevo.exploration.gacha.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.misc.HypershuntIntel;
import com.fs.starfarer.api.impl.campaign.intel.misc.MapMarkerIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.gacha.GachaStationDialoguePlugin;
import indevo.ids.Ids;
import indevo.ids.ItemIds;

import java.awt.*;
import java.util.Set;

public class GachaStationIntel extends MapMarkerIntel {

    public GachaStationIntel(SectorEntityToken entity, TextPanelAPI textPanel) {

        //String icon = Global.getSettings().getSpriteName("intel", "hypershunt");
        String title = entity.getName();

        String text = null;
//		if (entity.getStarSystem() != null) {
//			text = "Located in the " + entity.getStarSystem().getNameWithLowercaseTypeShort();
//		}
        setSound("ui_discovered_entity");

        setWithDeleteButton(false);
        //setWithTimestamp(false);

        init(entity, title, text, null, true, textPanel);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("IndEvo", "log_gachastation");
    }

    @Override
    protected boolean withTextInDesc() {
        return false;
    }

    @Override
    protected void addExtraBulletPoints(TooltipMakerAPI info, Color tc, float initPad, ListInfoMode mode) {
        if (entity.getMemoryWithoutUpdate().getBoolean("$IndEvo_stationRepaired")) {
            info.addPara("Active", tc, initPad);
        } else {
            info.addPara("Inactive", tc, initPad);
        }
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI info, float width, float height, float opad) {
        //if (!entity.getMemoryWithoutUpdate().getBoolean("$hasDefenders")) {
        info.addPara("Give, and thou shalt be given unto.",
                opad);

        boolean active = entity.getMemoryWithoutUpdate().getBoolean("$IndEvo_stationRepaired");

        if (active) info.addPara("Offer metal sanctified by the light of a star to the machine, and it may bestow a blessing of silicone and steel made anew",
                opad);
        else {
            // these must match the quantities specified in the rule "cTap_infoText"
            info.showCost("Resources required to activate", false, (int) ((width - opad) / 3f),
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), opad,
                    new String[]{Commodities.METALS, Commodities.HEAVY_MACHINERY, ItemIds.PARTS},
                    new int[]{GachaStationDialoguePlugin.METALS_REPAIR_COST, GachaStationDialoguePlugin.MACHINERY_REPAIR_COST, GachaStationDialoguePlugin.PARTS_REPAIR_COST}, new boolean[]{false, false, false});
        }

    }

    public static GachaStationIntel getGachaStationIntel(SectorEntityToken entity) {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GachaStationIntel.class)) {
            if (((GachaStationIntel)intel).getEntity() == entity) return (GachaStationIntel)intel;
        }
        return null;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        //tags.add(Tags.INTEL_);
        return tags;
    }


}