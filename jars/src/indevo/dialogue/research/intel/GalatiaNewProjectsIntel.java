package indevo.dialogue.research.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ResearchProjectTemplateRepo;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class GalatiaNewProjectsIntel extends BaseIntelPlugin {

    private List<String> newProjectIds;
    public float timePassed = 0f;
    public static float TIME_TO_DESPAWN_DAYS = 31f;

    public GalatiaNewProjectsIntel(String... ids) {
        this.newProjectIds = (Arrays.asList(ids));
    }

    public static void notifyNewProjects(String... ids){
        GalatiaNewProjectsIntel intel = new GalatiaNewProjectsIntel(ids);
        Global.getSector().getIntelManager().addIntel(intel);
        intel.setImportant(true);
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        info.addPara("Visit the Galatian Academy", initPad, tc);
        unindent(info);
    }


    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
        addBulletPoints(info, mode);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);

        info.addPara("There are %s available at the Galatian Academy.",
                opad, Misc.getHighlightColor(), "new research projects");

        info.addPara("Completing them can yield unique rewards.",
                opad);

        info.addPara("Now available:", opad);
        if (newProjectIds != null) for (String id : newProjectIds) info.addPara(BULLET + " " + ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(id).getName(), Misc.getHighlightColor(), 3f);

        addBulletPoints(info, ListInfoMode.IN_DESC);

        addDeleteButton(info, width);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("IndEvo", "galatia_proj");
    }

    public String getSortString() {
        return "Research";
    }

    public String getName() {
        return "New Research Projects Available";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(Factions.INDEPENDENT);
    }

    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return Global.getSector().getEntityById("station_galatia_academy");
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        timePassed += Global.getSector().getClock().convertToDays(amount);
        if (shouldRemoveIntel()) {
            Global.getSector().getIntelManager().removeIntel(this);
        }
    }

    @Override
    public boolean shouldRemoveIntel() {
        return timePassed > TIME_TO_DESPAWN_DAYS;
    }

    @Override
    public String getCommMessageSound() {
        return super.getCommMessageSound();
    }
}