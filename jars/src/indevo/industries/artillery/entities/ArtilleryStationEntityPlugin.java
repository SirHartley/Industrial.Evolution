package indevo.industries.artillery.entities;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.conditions.ArtilleryStationCondition;
import indevo.industries.artillery.industry.ArtilleryStation;
import indevo.industries.artillery.scripts.CampaignAttackScript;

import java.awt.*;
import java.util.List;

public class ArtilleryStationEntityPlugin extends BaseCustomEntityPlugin {
    public static final String TYPE_RAILGUN = "railgun";
    public static final String TYPE_MORTAR = "mortar";
    public static final String TYPE_MISSILE = "missile";

    private String type;

    public boolean disrupted = true;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.type = (String) pluginParams;
        if (type == null) type = TYPE_MORTAR;

        if (!entity.hasScriptOfClass(CampaignAttackScript.class)) entity.addScript(new CampaignAttackScript(entity, type));
    }

    @Override
    public boolean hasCustomMapTooltip() {
        return true;
    }

    public CampaignAttackScript getOrInitScript(){
        for (EveryFrameScript s : entity.getScripts()){
            if (s instanceof CampaignAttackScript) return (CampaignAttackScript) s;
        }

        CampaignAttackScript s = new CampaignAttackScript(entity, type);
        entity.addScript(s);
        return s;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        Color color = entity.getFaction().getBaseUIColor();

        tooltip.addPara(Misc.ucFirst(type) + " Artillery", color, 0);
        CampaignAttackScript s = getOrInitScript();
        if (s != null) tooltip.addPara("Targets enemy fleets at %s range", 3f, Misc.getHighlightColor(), (int) Math.round(s.range) + " SU");

        boolean hostile = getOrInitScript().isHostileTo(Global.getSector().getPlayerFleet());
        Color c = hostile ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        String hostileString = hostile ? "hostile" : " not hostile";

        tooltip.addPara("You are %s to the controlling faction.", 3f, c, hostileString);
    }

    public static SectorEntityToken placeAtMarket(MarketAPI m, String forceType, boolean showFleetVisual) {
        if (m.getPrimaryEntity() == null) return null;

        SectorEntityToken primaryEntity = m.getPrimaryEntity();
        SectorEntityToken station = primaryEntity.getTags().contains(Tags.STATION) ? null : getOrbitalStationAtMarket(m); //only get orbital if station is not orbital station

        String factionID = m.isPlanetConditionMarketOnly() ? Ids.DERELICT_FACTION_ID : m.getFactionId();

        LocationAPI loc = primaryEntity.getContainingLocation();
        SectorEntityToken artillery = loc.addCustomEntity(Misc.genUID(), null, "IndEvo_ArtilleryStation", factionID, forceType);

        float angle = station != null ? station.getCircularOrbitAngle() - 180 : (float) Math.random() * 360f;
        float radius = station != null ? station.getCircularOrbitRadius() : primaryEntity.getRadius() + 150f;
        float period = station != null ? station.getCircularOrbitPeriod() : radius / 10f;

        artillery.setCircularOrbitWithSpin(primaryEntity,
                angle,
                radius,
                period,
                5f,
                5f);

        m.getConnectedEntities().add(artillery);
        artillery.setMarket(m);

        artillery.addTag(Ids.TAG_ARTILLERY_STATION);
        if (showFleetVisual) artillery.addTag(Tags.USE_STATION_VISUAL);
        artillery.getMemoryWithoutUpdate().set(ArtilleryStationCondition.ARTILLERY_KEY, true);

        return artillery;
    }

    public static SectorEntityToken getOrbitalStationAtMarket(MarketAPI market) {
        SectorEntityToken station = null;

        for (SectorEntityToken t : market.getConnectedEntities()) {
            if (t.hasTag(Tags.STATION) && !t.hasTag(Ids.TAG_ARTILLERY_STATION)) {
                station = t;
                break;
            }
        }

        if (station == null) {
            for (SectorEntityToken t : market.getStarSystem().getEntitiesWithTag(Tags.STATION)) {
                if (t.getCustomEntityType().equals(Entities.STATION_BUILT_FROM_INDUSTRY) && !t.hasTag(Ids.TAG_ARTILLERY_STATION)) {
                    if (t.getOrbitFocus() != null && t.getOrbitFocus().getId().equals(market.getPrimaryEntity().getId())) {
                        station = t;
                        break;
                    }
                }
            }
        }

        return station;
    }

    public boolean isDisrupted() {
        return disrupted;
    }

    public void setDisrupted(boolean disrupted) {
        this.disrupted = disrupted;
        getOrInitScript().disabled = disrupted;
    }

    public static List<SectorEntityToken> getArtilleriesInLoc(LocationAPI loc) {
        return loc.getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION);
    }

    public String getType() {
        return type;
    }

    public Industry getRelatedIndustry() {
        MarketAPI m = entity.getMarket();

        if (m.isPlanetConditionMarketOnly()) return null;
        Industry ind = null;

        for (Industry i : m.getIndustries()) {
            if (i instanceof ArtilleryStation) {
                ind = i;
                break;
            }
        }

        return ind;
    }

    @Override
    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {
        super.appendToCampaignTooltip(tooltip, level);
        float opad = 10f;
        float spad = 3f;
        Color highlight = Misc.getHighlightColor();

        if (entity.isDiscoverable()) return;

        if (disrupted) {
            tooltip.addPara("This defence platform is %s.", opad, Misc.getNegativeHighlightColor(), "not functional");
        } else switch (type) {
            case TYPE_RAILGUN:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will fire multiple extremely fast projectiles at an extreme range.", opad, highlight, "railgun");
                break;
            case TYPE_MISSILE:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will launch long-range target seeking ECM missiles.", opad, highlight, "missile launcher");
                break;
            case TYPE_MORTAR:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will bombard targets with a high volume of explosive ordinance at extreme range.", opad, highlight, "mortar");
                break;
        }

        tooltip.addPara("Old safety protocols prohibit targeting of inhabited areas.", opad);
    }
}