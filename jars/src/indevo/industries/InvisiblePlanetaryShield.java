package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PlanetaryShield;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.awt.*;

public class InvisiblePlanetaryShield extends PlanetaryShield {
    public static String TAG_FULL = "IndEvo_PS_FULL";
    public static String TAG_LOW = "IndEvo_PS_LOW";
    public static String TAG_NONE = "IndEvo_PS_NONE";

    public static class AlternateTextureOptionProvider extends SingleIndustrySimpifiedOptionProvider {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            if (!listeners.hasListenerOfClass(AlternateTextureOptionProvider.class)) {
                listeners.addListener(new AlternateTextureOptionProvider(), true);
            }
        }

        @Override
        public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
            MarketAPI market = opt.ind.getMarket();

            //illegal ass code but tired
            if (market.hasTag(TAG_FULL)) {
                market.removeTag(TAG_FULL);
                market.addTag(TAG_LOW);
            } else if (market.hasTag(TAG_LOW)) {
                market.removeTag(TAG_LOW);
                market.addTag(TAG_NONE);
            } else {
                market.removeTag(TAG_NONE);
                market.addTag(TAG_FULL);
            }
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
            tooltip.addPara("Toggles between the visual modes of the planetary shield, from full visibility to invisible mode.", 0f);
        }

        @Override
        public String getOptionLabel(Industry ind) {
            return "Change Shield Visuals";
        }

        @Override
        public String getTargetIndustryId() {
            return Industries.PLANETARYSHIELD;
        }
    }

    public void apply() {
        super.apply(false);

        if (isFunctional()) {
            if (market.hasTag(TAG_FULL)) {
                applyVisuals(market.getPlanetEntity());
            } else if (market.hasTag(TAG_LOW)) {
                applyAlternateVisuals(market.getPlanetEntity());
            } else if (market.hasTag(TAG_NONE)) {
                PlanetaryShield.unapplyVisuals(market.getPlanetEntity());
            } else {
                market.addTag(TAG_FULL);
                applyVisuals(market.getPlanetEntity());
            }
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
        planet.getSpec().setShieldColor(new Color(255,255,255,150));
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
