package indevo.industries.derelicts.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.utils.helper.RelicHelper;

import java.util.*;

public class RiftPrecisePicker extends BaseCampaignEntityPickerListener {

    public static final String TARGET_KEY = "$IndEvo_riftPreciseTarget";
    public static final String PICKED_TRIGGER = "IndEvo_RiftPrecisePicked";

    private final InteractionDialogAPI dialog;
    private final Map<String, MemoryAPI> memoryMap;
    private final RiftGenerator gen;
    private final Set<StarSystemAPI> targets;

    private RiftPrecisePicker(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, RiftGenerator gen, List<StarSystemAPI> targets) {
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.gen = gen;
        this.targets = new LinkedHashSet<>(targets);
    }

    public static void show(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, RiftGenerator gen) {
        List<StarSystemAPI> targets = gen.getTargets().getPreciseTargets();
        if (targets.isEmpty()) throw new IllegalStateException("precise rift picker opened on " + gen.getMarket().getId() + " without targets, the option should be disabled");

        List<SectorEntityToken> anchors = new ArrayList<>();
        for (StarSystemAPI system : targets) anchors.add(system.getHyperspaceAnchor());

        dialog.showCampaignEntityPicker("Select a target system", "Target:", "Lock target",
                Global.getSector().getPlayerFaction(), anchors, new RiftPrecisePicker(dialog, memoryMap, gen, targets));
    }

    public static StarSystemAPI getPicked(Map<String, MemoryAPI> memoryMap) {
        String id = memoryMap.get(MemKeys.LOCAL).getString(TARGET_KEY);
        return id == null ? null : Global.getSector().getStarSystem(id);
    }

    public static float getDistanceLY(MarketAPI market, StarSystemAPI system) {
        return Misc.getDistanceLY(market.getLocationInHyperspace(), system.getLocation());
    }

    @Override
    public void pickedEntity(SectorEntityToken entity) {
        StarSystemAPI system = Misc.getStarSystemForAnchor(entity);
        memoryMap.get(MemKeys.LOCAL).set(TARGET_KEY, system.getId(), 0f);
        FireBest.fire(null, dialog, memoryMap, PICKED_TRIGGER);
    }

    @Override
    public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
        StarSystemAPI system = Misc.getStarSystemForAnchor(entity);
        return system == null ? null : system.getNameWithLowercaseTypeShort();
    }

    @Override
    public String getSelectedTextOverrideFor(SectorEntityToken entity) {
        StarSystemAPI system = Misc.getStarSystemForAnchor(entity);
        return system == null ? null : system.getNameWithLowercaseType();
    }

    @Override
    public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
        StarSystemAPI system = Misc.getStarSystemForAnchor(entity);
        if (system == null) return;

        MarketAPI market = gen.getMarket();
        info.setParaSmallInsignia();
        info.addPara("    Distance: %s light years. Cost: %s " + RelicHelper.getName() + " (%s available)", 0f, Misc.getHighlightColor(),
                Misc.getRoundedValueMaxOneAfterDecimal(getDistanceLY(market, system)), gen.getPreciseCost() + "", RelicHelper.getAvailable(market) + "");
    }

    @Override
    public boolean canConfirmSelection(SectorEntityToken entity) {
        return targets.contains(Misc.getStarSystemForAnchor(entity));
    }

    @Override
    public Set<StarSystemAPI> getStarSystemsToShow() {
        return targets;
    }

    @Override
    public List<MarkerData> getMarkers() {
        MarketAPI market = gen.getMarket();
        return new ArrayList<>(List.of(new MarkerData(market.getLocationInHyperspace(), null, market.getFaction().getBaseUIColor())));
    }

    @Override
    public float getFuelColorAlphaMult() {
        return 0f;
    }

    @Override
    public float getFuelRangeMult() {
        return 0f;
    }
}
