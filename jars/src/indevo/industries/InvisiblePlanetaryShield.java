package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PlanetaryShield;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.utils.helper.Settings;

import java.awt.*;

public class InvisiblePlanetaryShield extends PlanetaryShield {

    public void apply() {
        super.apply(false);
        if (isFunctional()) {

            if (Settings.getBoolean(Settings.CLOUD_PAINTER_SHIELD_OVERRIDE)) applyAlternateVisuals(market.getPlanetEntity());
            else applyVisuals(market.getPlanetEntity());

            if (Settings.getBoolean(Settings.CLOUD_PAINTER_SHIELD_REMOVE)) PlanetaryShield.unapplyVisuals(market.getPlanetEntity());
        }
    }

    @Override
    public String getId() {
        return Industries.PLANETARYSHIELD;
    }

    public static void applyVisuals(PlanetAPI planet) {
        if (planet == null) return;
        planet.getSpec().setShieldTexture(Global.getSettings().getSpriteName("industry", "shield_texture"));
        //planet.getSpec().setShieldThickness(0.07f);
        //planet.getSpec().setShieldColor(new Color(255,0,0,255));
        planet.getSpec().setShieldThickness(0.1f);
        planet.getSpec().setShieldColor(new Color(255,255,255,175));
        planet.applySpecChanges();
    }

    public static void applyAlternateVisuals(PlanetAPI planet) {
        if (planet == null) return;
        planet.getSpec().setShieldTexture(Global.getSettings().getSpriteName("IndEvo", "shield_texture"));
        planet.getSpec().setShieldThickness(0.1f);
        planet.getSpec().setShieldColor(new Color(255,255,255,175));
        planet.applySpecChanges();
    }
}
