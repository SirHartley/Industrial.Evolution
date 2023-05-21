package indevo.industries.salvageyards.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.CountingMap;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.salvageyards.rules.IndEvo_InitSYCustomProductionDiag;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.utils.timers.NewDayListener;

import java.awt.*;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static indevo.industries.salvageyards.rules.IndEvo_InitSYCustomProductionDiag.DELIVERY_TIME;
import static indevo.utils.helper.StringHelper.getDayOrDays;

public class YardsCustomProductionIntel extends BaseIntelPlugin implements NewDayListener {

    public enum Stage {
        WAITING,
        DELIVERED,
        ENDED,
        FAILED,
    }

    protected int elapsed = 0;
    boolean isDone = false;
    protected Random genRandom = new Random();
    protected MarketAPI market;
    protected IndEvo_InitSYCustomProductionDiag.YardsProductionData data;
    protected CargoAPI tooltipCargo;

    private Stage currentStage;

    public YardsCustomProductionIntel(MarketAPI deliverToMarket, IndEvo_InitSYCustomProductionDiag.YardsProductionData data) {
        this.market = deliverToMarket;
        this.data = data;
    }

    public void init() {
        Global.getSector().getListenerManager().addListener(this);
        currentStage = Stage.WAITING;
        tooltipCargo = convertProdToCargoForTooltip(data);
    }

    @Override
    public void onNewDay() {
        performStageActions();
    }

    private void deregister() {
        Global.getSector().getListenerManager().removeListener(this);
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        Color tc = getBulletColorForMode(mode);

        bullet(info);
        addNextStepText(info, tc, pad);
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

        info.addSectionHeading("Current Status:", Alignment.MID, opad);
        addDescriptionForCurrentStage(info, width, height);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "production_report");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_PRODUCTION);
        return tags;
    }

    public String getSortString() {
        return "Production";
    }

    public String getName() {
        return "Salvage Yards Production Order";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return market.getFaction();
    }

    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return market.getPrimaryEntity();
    }

    @Override
    public boolean shouldRemoveIntel() {
        return isDone;
    }

    public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == YardsCustomProductionIntel.Stage.WAITING) {
            int d = (int) Math.round(DELIVERY_TIME - elapsed);

            LabelAPI label = info.addPara("The order will be delivered to storage " + market.getOnOrAt() + " " + market.getName() +
                            " in %s " + getDayOrDays(d) + ".", opad,
                    Misc.getHighlightColor(), "" + d);
            label.setHighlight(market.getName(), "" + d);
            label.setHighlightColors(market.getFaction().getBaseUIColor(), h);

            //intel.createSmallDescription(info, width, height);
            showCargoContents(info, width, height);


        } else if (currentStage == Stage.DELIVERED || currentStage == Stage.ENDED) {
            int d = (int) Math.round(elapsed);

            LabelAPI label = info.addPara("The order was delivered to storage " + market.getOnOrAt() + " " + market.getName() + " %s " + getDayOrDays(d) + " ago.", opad,
                    Misc.getHighlightColor(), "" + d);
            label.setHighlight(market.getName(), "" + d);
            label.setHighlightColors(market.getFaction().getBaseUIColor(), h);

            showCargoContents(info, width, height);
            addDeleteButton(info, width);
        } else if (currentStage == YardsCustomProductionIntel.Stage.FAILED) {
            if (market.hasCondition(Conditions.DECIVILIZED)) {
                info.addPara("This order will not be completed because %s" +
                                " has decivilized.", opad,
                        market.getFaction().getBaseUIColor(), market.getName());
            } else {
                info.addPara("This order will not be completed as the Salvage Yards on %s" +
                                " no longer exist.", opad,
                        market.getFaction().getBaseUIColor(), market.getName());
            }
        }
    }

    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == YardsCustomProductionIntel.Stage.WAITING) {
            addDays(info, "until delivery", DELIVERY_TIME - elapsed, tc, pad);
            return true;
        } else if (currentStage == YardsCustomProductionIntel.Stage.DELIVERED) {
            info.addPara("Delivered to %s", pad, tc, market.getFaction().getBaseUIColor(), market.getName());
            return true;
        }
        return false;
    }

    public void performStageActions() {
        elapsed++;

        if (currentStage == Stage.ENDED || currentStage == Stage.FAILED) {
            if (elapsed > 60) {
                deregister();
                isDone = true;
            }

            return;
        }

        if (market.hasCondition(Conditions.DECIVILIZED) || !market.hasIndustry(Ids.SCRAPYARD)) {
            currentStage = Stage.FAILED;
            elapsed = 1;
            return;
        }

        if (currentStage == Stage.WAITING) {
            if (elapsed >= DELIVERY_TIME) {
                currentStage = Stage.DELIVERED;
                elapsed = 1;
            }
        }

        if (currentStage == Stage.DELIVERED) {
            StoragePlugin plugin = (StoragePlugin) Misc.getStorage(market);
            plugin.setPlayerPaidToUnlock(true);

            Misc.getStorageCargo(market).addAll(convertProdToCargo(data), true);
            currentStage = Stage.ENDED;
        }
    }

    protected CargoAPI convertProdToCargo(IndEvo_InitSYCustomProductionDiag.YardsProductionData prod) {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        for (Map.Entry<String, Integer> e : prod.productionList.entrySet()) {
            int count = e.getValue();

            for (int i = 0; i < count; i++) {
                String var = ForgeTemplateItemPlugin.getValidVariantIdForHullId(e.getKey());
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
                DModManager.addDMods(member, false, data.getAverageDModAmount(), genRandom);

                cargo.getMothballedShips().addFleetMember(member);
            }
        }

        return cargo;
    }

    //alex did it like this, fuck do I know why, I'll just do my own thing
    protected CargoAPI convertProdToCargoForTooltip(IndEvo_InitSYCustomProductionDiag.YardsProductionData prod) {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        float quality = ShipQuality.getShipQuality(market, market.getFactionId());

        CampaignFleetAPI ships = Global.getFactory().createEmptyFleet(market.getFactionId(), "temp", true);
        ships.setCommander(Global.getSector().getPlayerPerson());
        ships.getFleetData().setShipNameRandom(genRandom);
        DefaultFleetInflaterParams p = new DefaultFleetInflaterParams();
        p.quality = quality;
        p.mode = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
        p.persistent = false;
        p.seed = genRandom.nextLong();
        p.timestamp = null;

        FleetInflater inflater = Misc.getInflater(ships, p);
        ships.setInflater(inflater);

        for (Map.Entry<String, Integer> e : prod.productionList.entrySet()) {
            int count = e.getValue();

            for (int i = 0; i < count; i++) {
                String var = ForgeTemplateItemPlugin.getValidVariantIdForHullId(e.getKey());
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
                ships.getFleetData().addFleetMember(member);
            }
        }

        // so that it adds d-mods
        ships.inflateIfNeeded();
        for (FleetMemberAPI member : ships.getFleetData().getMembersListCopy()) {
            // it should be due to the inflateIfNeeded() call, this is just a safety check
            if (member.getVariant().getSource() == VariantSource.REFIT) {
                member.getVariant().clear();
            }
            cargo.getMothballedShips().addFleetMember(member);
        }

        return cargo;
    }

    public void showCargoContents(TooltipMakerAPI info, float width, float height) {
        if (data == null || tooltipCargo == null) return;

        float opad = 10f;

        if (!tooltipCargo.getMothballedShips().getMembersListCopy().isEmpty()) {
            CountingMap<String> counts = new CountingMap<String>();
            for (FleetMemberAPI member : tooltipCargo.getMothballedShips().getMembersListCopy()) {
                counts.add(member.getVariant().getHullSpec().getHullName() + " " + member.getVariant().getDesignation());
            }

            info.addPara("Ship hulls:", opad);
            info.showShips(tooltipCargo.getMothballedShips().getMembersListCopy(), 20, true,
                    currentStage == YardsCustomProductionIntel.Stage.WAITING, opad);
        }
    }
}
