package indevo.dialogue.research.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.BaseGenericPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.util.Map;

public class CamouflageFieldEmitter extends BaseHullMod {

    public static final String IS_ATTACHED_KEY = "$IndEvo_GlitcherOverlayAttached_";
    public static final String ID = "IndEvo_CamouflageFieldEmitter";
    public static final String TAG_UNIQUE = "IndEvo_ship_unique_signature";

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        super.advanceInCombat(ship, amount);

        String key = IS_ATTACHED_KEY + ship.getId();

        Map<String, Object> customData = Global.getCombatEngine().getCustomData();
        if (!customData.containsKey(key)){
            ShipSpriteGlitcherOverlay overlay = new ShipSpriteGlitcherOverlay(ship);
            Global.getCombatEngine().addLayeredRenderingPlugin(overlay);
            ship.addListener(overlay);
            customData.put(key, true);
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);
    }
}
