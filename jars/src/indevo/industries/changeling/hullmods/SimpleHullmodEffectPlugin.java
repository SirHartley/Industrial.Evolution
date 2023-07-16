package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;

public interface SimpleHullmodEffectPlugin {
    void apply(MutableShipStatsAPI stats, float amt);
    String getName();
}
