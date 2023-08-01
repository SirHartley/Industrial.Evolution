package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.petshop.hullmods.SelfRepairingBuiltInHullmod;
import indevo.utils.helper.StringHelper;

import java.util.Random;

public class HandBuiltHullmod extends SelfRepairingBuiltInHullmod {

    public static final int EFFECT_COUNT = 4;
    public static final float MIN_EFFECT = 0.7f;
    public static final float MAX_EFFECT = 1.3f;

    public static final String ID = "IndEvo_handBuilt";

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);

        HandBuiltEffectMemoryRepo repo = HandBuiltEffectMemoryRepo.getInstance();
        FleetMemberAPI member = stats.getFleetMember();
        if (member == null) return;

        id = member.getId();

        if (!repo.contains(id)) createHullmodEffect(id);
        repo.get(id).apply(stats);
    }

    public void createHullmodEffect(String id) {
        Random random = new Random(id.hashCode());
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        picker.addAll(SimpleHullmodEffectPluginRepo.HULLMOD_EFFECTS.keySet());

        HandBuiltEffect effect = new HandBuiltEffect(id);

        for (int i = 0; i <= EFFECT_COUNT; i++) {
            effect.add(picker.pickAndRemove(), random.nextFloat() * (MAX_EFFECT - MIN_EFFECT) + MIN_EFFECT);
        }

        HandBuiltEffectMemoryRepo.getInstance().add(effect);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        float opad = 10f;
        float spad = 3f;

        HandBuiltEffectMemoryRepo repo = HandBuiltEffectMemoryRepo.getInstance();
        String id = ship.getFleetMemberId();

        tooltip.addSectionHeading("Effects", Alignment.MID, opad);
        boolean first = true;

        for (Pair<SimpleHullmodEffectPlugin, Float> effects : repo.get(id).getPluginsWithEffectAmounts()) {
            boolean decrease = effects.two < 1;
            String increaseOrDecrease = decrease ? "decreased" : "increased";

            tooltip.addPara(
                    Misc.ucFirst(effects.one.getName()) + " " + increaseOrDecrease + " by %s",
                    first ? opad : spad,
                    Misc.getHighlightColor(),
                    StringHelper.getAbsPercentString(effects.two, true));

            first = false;
        }
    }
}
